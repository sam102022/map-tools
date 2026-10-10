# Spécification de Conception — Alignement Java V2 sur Python V7 (Sprints 7, 8 et 9)

**Date :** 04/10/2026  
**Auteur :** AI Assistant & Samuel  
**Statut :** Validé par l'utilisateur  
**Fichiers sources de référence :**
* `maps/python/snap_cells_prototype_v7.py` (Script étalon de référence V7)
* `maps/python/snap_cells_prototype_v6.py` (Introduction de l'affinage alpha)
* `maps/python/snap_cells_prototype_v5.py` (Base initiale des Sprints 1 à 6)
* `docs/SPRINTS_V2.md` (Planification directrice du pipeline V2)
* `docs/SPEC_V2_ALGORITHME.md` (Spécification algorithmique générale)

---

## 1. Contexte & Problématique

L'algorithme de détourage cartographique V2 a été implémenté et validé avec succès en Java standard jusqu'au Sprint 6 (`com.sam102022.photoshop.v2.contour`), en se basant sur le prototype Python `snap_cells_prototype_v5.py`.

L'expérimentation d'une nouvelle version de référence Python, **`snap_cells_prototype_v7.py`**, a mis en évidence deux sauts qualitatifs majeurs améliorant significativement le rendu visuel et la fidélité géométrique :
1. **Lissage adaptatif multi-échelle sur tronçons droits (Étape 7 de v7) :**
   Sur les longues avenues ou rocades rectilignes, le lissage LQR standard ($\sigma = 22.0$) avait tendance à onduler légèrement ou à épouser les petites encoches causées par les icônes de signalisation, les flèches ou les cartouches de numérotation de route. La v7 introduit un double lissage LQR (échelle locale $\sigma = 22.0$ et échelle large $\sigma_{wide} = 2.7 \times 22.0 = 59.4$) combiné à un détecteur de rectitude $\Delta = \|\mathbf{sm}_b - \mathbf{sm}_a\|$, un filtre boîte de transition $C^1$ ($k = 10$) et une interpolation continue. Les encoches d'icônes sont éliminées sur les lignes droites sans aplatir les virages réels.
2. **Affinage spectral de la couverture & anti-aliasing réel (Étape 9 de v7) :**
   La simple rastérisation vectorielle du contour englobe parfois des artéfacts ou des franges blanches en limite extérieure de chaussée. La v7 introduit un post-traitement d'affinage spectral :
   - Masque des zones autorisées : $M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \text{îlots de ronds-points substitués}$ ;
   - Comblement des micro-trous intérieurs de chaussée ($< 2000\text{ px}$) ;
   - Flou gaussien ($\sigma = 1.4\text{ px}$) et seuillage doux raide ($FK = 2.0$) pour un fondu naturel ;
   - Zone tampon d'exemption elliptique des ronds-points ($z_w$) entre $\rho \in [2.3, 3.0]$ par *smoothstep* cubique ($3t^2 - 2t^3$) empêchant l'érosion du travail géométrique du Sprint 6 ;
   - Modulation continue de la couche alpha : $\alpha_{\text{final}} = \alpha_{ss} \times \text{factor}$. Sur le cas étalon CA01, près de 28 893 pixels blancs résiduels sont ainsi éliminés.

Pour intégrer ces avancées sans alourdir les composants existants, l'architecture V2 est découpée en **3 sprints dédiés : 7, 8 et 9**.

---

## 2. Décomposition Architecturale des Sprints 7, 8 et 9

```text
SPRINT 6 (validé) : SubpixelContourExtractor, CornerDetector, RobustLqrSmoother, RoundaboutDetector, HermiteSplineConnector
        │
        ▼
SPRINT 7 : Raffinement du Lissage Vectoriel (Tronçons Droits Multi-Échelles)
        ├── Package : com.sam102022.photoshop.v2.contour
        ├── StraightSegmentSmoother (double LQR, détection d'écart Δ, transition C1)
        ├── ContourSmoothingConfig (enrichi avec straightFactor, straightThreshold, etc.)
        └── ContourSmoothingEngine (intégration du lissage adaptatif)
        │
        ▼
SPRINT 8 : Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel
        ├── Package : com.sam102022.photoshop.v2.refine
        ├── AllowedRegionBuilder (Mfill | Rc | giratoires + comblement trous < 2000 px)
        ├── SoftThresholdFilter (flou gaussien FS=1.4 px + seuil raide FK=2.0)
        ├── RoundaboutExemptionModulator (zone tampon elliptique zw [2.3..3.0] smoothstep)
        ├── AlphaRefiner (façade d'orchestration de l'affinage)
        ├── AlphaRefinementConfig (hyperparamètres immuables)
        └── AlphaRefinementMap (matrice locale de modulation [0.0..1.0])
        │
        ▼
SPRINT 9 : Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Pipeline CLI V2
        ├── Packages : com.sam102022.photoshop.v2.render, v2.pipeline, cli
        ├── SupersampleRenderer (rastérisation SS=4 sur ROI + box filter)
        ├── ImageClipper (composition alpha avec AlphaRefinementMap, exports clipped/mask/overlay)
        ├── RenderResult (record immuable des livrables finaux)
        ├── V2Config (configuration unifiée des hyperparamètres V2)
        ├── V2Pipeline (orchestrateur de bout en bout Sprints 1 -> 9)
        └── V2CliRunner (commande CLI avec drapeau --v2 et options)
```

---

## 3. Détail Technique du Sprint 7 — Lissage Multi-Échelle des Tronçons Droits

### 3.1. Algorithme
Pour chaque segment de contour entre coins $\mathbf{seg} = [P_0, \dots, P_1]$ avec $n \ge 2.5 \sigma$ :
1. **Ligne de base et résidu :**
   $$\mathbf{base}(t) = P_0 + t (P_1 - P_0), \quad \mathbf{res}(t) = \mathbf{seg}(t) - \mathbf{base}(t) \quad \text{pour } t \in [0, 1]$$
2. **Double LQR :**
   $$\mathbf{sm}_a = \text{LQR}(\mathbf{res}, \sigma = \text{lqrSigma}, \text{scale} = \text{lqrScale}, \text{iters} = 6)$$
   $$\mathbf{sm}_b = \text{LQR}(\mathbf{res}, \sigma = \text{straightFactor} \times \text{lqrSigma}, \text{scale} = \text{straightScale}, \text{iters} = 8)$$
   *(avec par défaut $\text{straightFactor} = 2.7$, $\text{straightScale} = 3.0$)*
3. **Mesure locale de rectitude :**
   $$\Delta(i) = \|\mathbf{sm}_b(i) - \mathbf{sm}_a(i)\| = \sqrt{(x_b - x_a)^2 + (y_b - y_a)^2}$$
   $$w_{\text{raw}}(i) = \text{clamp}\left(\frac{ST\_T - \Delta(i)}{ST\_T / 2}, 0.0, 1.0\right) \quad \text{avec } ST\_T = 3.0\text{ px}$$
4. **Transition $C^1$ par convolution boîte :**
   Pour éviter tout à-coup ou discontinuité tangentielle :
   $$w_{\text{st}} = \text{box\_filter}(w_{\text{raw}}, k = 10) \quad \text{soit une moyenne sur } 21\text{ points avec réplication de bord}$$
5. **Combinaison linéaire adaptative :**
   $$\mathbf{sm}(i) = \mathbf{sm}_a(i) + w_{\text{st}}(i) \cdot (\mathbf{sm}_b(i) - \mathbf{sm}_a(i))$$
   $$\mathbf{out}(i) = \mathbf{base}(i) + \mathbf{sm}(i)$$

### 3.2. Contrats & Composants
* `StraightSegmentSmoother.java` :
  ```java
  public class StraightSegmentSmoother {
      public List<PixelPoint> smoothSegment(List<PixelPoint> segment, ContourSmoothingConfig config);
  }
  ```
* `ContourSmoothingConfig.java` : Enrichi avec `straightFactor` (2.7), `straightScale` (3.0), `straightThreshold` (3.0), `straightTransitionK` (10).

---

## 4. Détail Technique du Sprint 8 — Affinage Spectral & Anti-Aliasing Réel

### 4.1. Algorithme (Étape 9 de v7)
1. **Construction du masque autorisé $M_{\text{allowed}}$ :**
   - Fusion : $M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \text{cellules îlots des ronds-points substitués}$.
   - Comblement des trous intérieurs de moins de 2000 pixels dans $M_{\text{allowed}}$ (marquages au sol, zébras, micro-îlots).
2. **Flou gaussien et fonction de seuillage raide :**
   - Lissage gaussien 2D séparable sur $M_{\text{allowed}}$ avec $\sigma = FS = 1.4\text{ px}$.
   - Application de la fonction de contraste :
     $$\text{factor}(x, y) = \text{clamp}\left((\text{allowed}_s(x, y) - 0.5) \times FK + 0.5, 0.0, 1.0\right) \quad \text{avec } FK = 2.0$$
3. **Zone d'exemption et protection des ronds-points :**
   - Pour chaque ellipse giratoire $(x_e, y_e, a, b, \theta)$ substituée au Sprint 6 :
     - Pour chaque pixel de la zone englobante radiale ($3.0 a + 4$) :
       - Calcul des coordonnées unitaires : $(u_x, u_y) = \text{toUnit}(x, y, \text{ellipse})$ ;
       - Distance normalisée : $\rho = \sqrt{u_x^2 + u_y^2}$ ;
       - Poids d'exemption : $t = \text{clamp}\left(\frac{FZ_1 - \rho}{FZ_1 - FZ_0}, 0.0, 1.0\right)$ avec $FZ_0 = 2.3, FZ_1 = 3.0$ ;
       - Lissage smoothstep : $s(t) = 3t^2 - 2t^3$ ;
       - $z_w(x, y) = \max(z_w(x, y), s(t))$.
   - Facteur final protégé :
     $$\text{factor}_{\text{protected}}(x, y) = 1.0 - z_w(x, y) \cdot (1.0 - \text{factor}(x, y))$$

### 4.2. Contrats & Composants (`com.sam102022.photoshop.v2.refine`)
* `AllowedRegionBuilder.java` : fusion binaire et remplissage des trous résiduels $< 2000\text{ px}$.
* `SoftThresholdFilter.java` : filtre gaussien séparable $\sigma = 1.4\text{ px}$ et seuillage linéaire saturé raideur $FK = 2.0$.
* `RoundaboutExemptionModulator.java` : modulation smoothstep sur ellipses normalisées $[FZ_0, FZ_1]$.
* `AlphaRefiner.java` : façade de service exécutant la chaîne d'affinage.
* `AlphaRefinementMap.java` : Record immuable contenant la grille flottante `float[][] factor` sur la `CropWindow`.

---

## 5. Détail Technique du Sprint 9 — Rendu Sub-Pixel Supersampling (SS=4) & CLI V2

### 5.1. Algorithme de Rendu
1. **Rastérisation sub-pixel sur ROI rognée :**
   - Application du décalage de demi-pixel : $X_{ss} = (x_{local} + 0.5) \times SS, Y_{ss} = (y_{local} + 0.5) \times SS$.
   - Rastérisation vectorielle via `Graphics2D.fill(Path2D)` sur une `BufferedImage` monochrome à deux niveaux avec $SS = 4$.
   - Filtrage boîte (moyenne des 16 sous-pixels) produisant $\alpha_{ss} \in [0.0 .. 1.0]$.
2. **Modulation continue :**
   - $\alpha_{\text{final}}(x, y) = \alpha_{ss}(x, y) \times \text{factor}_{\text{protected}}(x, y)$.
   - Injection dans le `CoverageMask` global $W \times H$.
3. **Assemblage des images de sortie :**
   - `clipped.png` (ARGB 32-bit) : $\alpha = \lfloor (\alpha_{\text{source}} \times \text{coverage} + 127) / 255 \rfloor$.
   - `mask.png` (`TYPE_BYTE_GRAY`) : matrice continue $8\text{ bits}$ $[0..255]$.
   - `overlay.png` (RGB 24-bit) : bordure frontière rouge vif (`#FF0000`) issue du seuillage morphologique à 128.
4. **Orchestrateur & CLI :**
   - `V2Pipeline` reliant de manière purement fonctionnelle les Sprints 1 à 9.
   - `V2Config` validant et encapsulant l'ensemble des hyperparamètres validés.
   - `V2CliRunner` exposant la commande `--v2` avec options descriptives.

---

## 6. Matrice de Validation & Critères d'Acceptation Globaux

| Sprint | Entrée | Sortie | Test Pivot CA01 & Critères d'Acceptation |
| :--- | :--- | :--- | :--- |
| **Sprint 7** | `SmoothVectorContour` brut | `SmoothVectorContour` multi-échelle | Redressement des avenues droites, Hausdorff $\le 2.0\text{ px}$, RMS $\le 0.5\text{ px}$ vs `contour_smooth.npy` V7 |
| **Sprint 8** | `ConsolidatedMask`, `RoadMask`, `Roundabouts` | `AlphaRefinementMap` | Élimination de ~28 800 pixels blancs parasites en lisière de route, protection totale des ronds-points |
| **Sprint 9** | `SmoothVectorContour`, `AlphaRefinementMap`, Image source | `RenderResult` | Découpe ARGB nette, IoU $\ge 0.99$ vs `CA01_clipped_v7.png`, exécution complète $\le 2.5\text{ s}$ |

---

## 7. Conformité aux Directives du Workspace (GEMINI.md & ADRs)

* **100% Java Standard sans dépendances natives :** Aucune bibliothèque tierce OpenCV ou SciPy (ADR-001).
* **Immutabilité des modèles :** Usage exclusif des Java Records pour les configurations et résultats (ADR-004).
* **Responsabilité unique (SRP) :** Un seul rôle bien délimité par classe (ADR-008).
* **Complexité cognitive :** Plafonnée à 15 par méthode avec sous-fonctions explicites (ADR-011).
* **Documentation Javadoc exhaustive en français :** Sur l'intégralité des classes et méthodes (ADR-014).
* **Traçabilité :** Enregistrement des évolutions dans `JOURNAL.md` (ADR-010).
