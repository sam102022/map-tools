# Spécification de Conception — V2 Sprint 8 : Affinage Spectral de la Couverture Alpha & Protection des Ronds-Points

**Date :** 05/10/2026  
**Auteur :** AI Assistant & Samuel  
**Statut :** Validé par l'utilisateur  
**Fichiers sources de référence :**
* `maps/python/snap_cells_prototype_v7.py` (Étape 9 étalon de référence V7)
* `docs/SPRINTS_V2.md` (Planification directrice du pipeline V2)
* `docs/sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md` (Spécification de synthèse Sprint 8)
* `docs/superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md` (Spécification cadre V7)

---

## 1. Contexte & Problématique

Lors de la rastérisation sub-pixel d'un polygone ou d'un contour vectoriel lissé, deux imperfections critiques apparaissent sur le résultat détouré :
1. **Franges blanches et pixels résiduels en bordure :** Le contour vectoriel peut englober de fins liserés blancs situés immédiatement hors de la chaussée le long des limites de parcelles (environ 28 893 pixels blancs parasites sur le cas réel CA01).
2. **Risque d'érosion des ronds-points :** Un filtrage spectral uniforme non modulé risquerait d'éroder ou de dégrader les arcs de carrefours giratoires parfaitement modélisés géométriquement lors des Sprints 6 et 7.

Le **Sprint 8** résout ces deux problèmes par un module d'affinage spectral (`AlphaRefiner`) directement transcrit de l'étape 9 du prototype Python `snap_cells_prototype_v7.py`.

---

## 2. Architecture & Découpage Modulaire (ADR-008)

Le module est isolé dans le nouveau package `com.sam102022.photoshop.v2.refine` :

```text
com.sam102022.photoshop.v2.refine
├── AlphaRefinementConfig.java          (Record immuable des hyperparamètres)
├── AlphaRefinementMap.java             (Record immuable de la grille facteur [0.0..1.0])
├── AllowedRegionBuilder.java           (Construction de M_allowed et bouchage des micro-trous)
├── SoftThresholdFilter.java            (Flou gaussien séparable 2D et seuillage raide FK)
├── RoundaboutExemptionModulator.java   (Zone tampon smoothstep elliptique zw [FZ0..FZ1])
└── AlphaRefiner.java                   (Façade orchestrant la chaîne d'affinage)
```

### 2.1. `AlphaRefinementConfig` (Record immuable)
Encapsule les hyperparamètres avec validation stricte :
* `double gaussianBlurSigma` : Écart-type du filtre gaussien ($\sigma = 1.4\text{ px}$, $> 0$).
* `double contrastStiffness` : Facteur d'amplification du seuillage doux ($FK = 2.0$, $\ge 1.0$).
* `double roundaboutBufferInner` : Rayon normé interne d'exemption ($FZ_0 = 2.3$, $> 0$).
* `double roundaboutBufferOuter` : Rayon normé externe d'exemption ($FZ_1 = 3.0$, $> FZ_0$).
* `long roadHoleMaxArea` : Seuil d'aire maximale des cavités à combler ($2000\text{ px}$, $\ge 0$).

### 2.2. `AlphaRefinementMap` (Record immuable)
Matrice flottante locale encapsulant les coefficients d'atténuation $\text{factor} \in [0.0 .. 1.0]$ :
* `int width` : Largeur locale de la fenêtre de calcul.
* `int height` : Hauteur locale de la fenêtre de calcul.
* `float[][] factor` : Matrice rectangulaire des facteurs de modulation.
* `CropWindow cropWindow` : Fenêtre de recadrage d'origine pour l'ancrage global.
* Méthode d'accès sécurisée : `float factorAt(int x, int y)`.

### 2.3. `AllowedRegionBuilder`
Construit le masque binaire de l'espace autorisé $M_{\text{allowed}}$ :
1. Comblement intégral des cavités intérieures du masque consolidé $M_c$ pour obtenir $M_{\text{fill}}$.
2. Fusion binaire avec le masque routier fermé recadré $R_c$ et l'ensemble des îlots centraux des carrefours giratoires substitués :
   $$M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \bigcup_{rb \in \text{sub}} \text{cellule}(rb.\text{cellId})$$
3. Comblement sélectif des trous intérieurs de $M_{\text{allowed}}$ dont la surface est strictement inférieure à `roadHoleMaxArea` ($2000\text{ px}$) pour préserver les marquages au sol, zébras et îlots enclavés.

### 2.4. `SoftThresholdFilter`
1. Convolution gaussienne séparable 2D à $\sigma = 1.4\text{ px}$ appliquée sur $M_{\text{allowed}}$ :
   * Noyau 1D symétrique normalisé tronqué à un rayon $R = \lfloor 4.0 \times \sigma + 0.5 \rfloor = 6\text{ px}$.
   * Gestion des conditions aux limites par réflexion miroir (`reflect`).
2. Fonction de transfert à seuillage raide :
   $$\text{factor}(x, y) = \text{clamp}\big((\text{allowed}_s(x, y) - 0.5) \times FK + 0.5,\; 0.0,\; 1.0\big) \quad \text{avec } FK = 2.0$$

### 2.5. `RoundaboutExemptionModulator`
Pour chaque carrefour giratoire substitué modélisé par son ellipse extérieure $(x_c, y_c, a, b, \theta)$ :
1. Délimitation d'une boîte englobante locale de rayon $R = \lfloor 3.0 \times a \rfloor + 4$.
2. Pour chaque pixel $(x, y)$ de la boîte :
   * Coordonnées unitaires canoniques $(u_x, u_y) = \text{toUnitCircle}(x, y)$.
   * Distance radiale normée $\rho = \sqrt{u_x^2 + u_y^2}$.
   * Facteur d'interpolation linéaire normalisé :
     $$t = \text{clamp}\left(\frac{FZ_1 - \rho}{FZ_1 - FZ_0},\; 0.0,\; 1.0\right) \quad \text{avec } FZ_0 = 2.3,\; FZ_1 = 3.0$$
   * Modulation continue par interpolation cubique *Smoothstep* :
     $$s(t) = 3t^2 - 2t^3$$
   * Accumulation du masque de protection :
     $$z_w(x, y) = \max\big(z_w(x, y),\; s(t)\big)$$
3. Application du bouclier de protection :
   $$\text{factor}_{\text{protected}}(x, y) = 1.0 - z_w(x, y) \cdot \big(1.0 - \text{factor}(x, y)\big)$$

### 2.6. Façade `AlphaRefiner`
Exécute la séquence complète et produit le contrat immuable `AlphaRefinementMap`.

---

## 3. Évolution des Modèles Existants (Traçabilité des Ronds-Points)

Pour transmettre fidèlement les ronds-points substitués sans duplication de code :
1. **`Roundabout` :** Ajout de `int cellId` au record, avec constructeur de commodité pour préserver la rétrocompatibilité des tests existants.
2. **`HermiteSplineConnector` :** Mémorisation et exposition de la liste ordonnée des ronds-points effectivement substitués lors du raccordement tangentiel.
3. **`SmoothVectorContour` :** Ajout du champ `List<Roundabout> substitutedRoundabouts` avec constructeur rétrocompatible.

---

## 4. Stratégie de Validation & Critères d'Acceptation

1. **`AlphaRefinementModelTest` :** Validation unitaire des invariants des records `AlphaRefinementConfig` et `AlphaRefinementMap`.
2. **`AllowedRegionBuilderTest` :** Vérification de l'union binaire ($M_{\text{fill}} \lor R_c \lor \text{îlots}$) et du bouchage effectif des micro-trous intérieurs de surface $< 2000\text{ px}$.
3. **`SoftThresholdFilterTest` :** Vérification de la régularité du flou gaussien séparable, de la symétrie et de la monotonicité de la transition raide $FK = 2.0$.
4. **`RoundaboutExemptionModulatorTest` :** Vérification que $z_w = 1.0 \implies \text{factor} = 1.0$ sur l'ellipse intérieure ($\rho \le 2.3$), décroissance $C^1$ sur $[2.3 .. 3.0]$ et $z_w = 0.0$ au-delà ($\rho \ge 3.0$).
5. **`AlphaRefinerTest` :** Vérification unitaire de l'orchestration globale de la façade avec des mocks/stubs contrôlés.
6. **`Sprint8IntegrationTest` (CA01) :**
   * Exécution de bout en bout Sprints 1 $\to$ 8 sur le cas étalon CA01.
   * Élimination constatée de ~28 893 pixels blancs résiduels ($\text{alpha} > 0.5 \land \text{factor} < 0.5$).
   * Temps d'exécution de la passe d'affinage spectral $\le 150\text{ ms}$.
   * Taux de réussite global de la suite de tests : 100%.
