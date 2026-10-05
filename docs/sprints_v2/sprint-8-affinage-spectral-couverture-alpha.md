# Sprint 8 (V2) — Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel

**Statut :** ✅ Validé (05/10/2026)  
**Package cible :** `com.sam102022.photoshop.v2.refine`  
**Documents associés :**
* Spécification de conception : [`docs/superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md`](../superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md)
* Sommaire et planification V2 : [`docs/SPRINTS_V2.md`](../SPRINTS_V2.md)
* Script Python étalon : [`maps/python/snap_cells_prototype_v7.py`](../../maps/python/snap_cells_prototype_v7.py) (Étape 9)
* Architecture Decision Records associés :
  * [`ADR-001`](../adr/ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md) : Socle 100% Java standard sans dépendances natives.
  * [`ADR-002`](../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md) : Modèle de masques matriciels hybrides.
  * [`ADR-004`](../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md) : Immutabilité de la configuration via records.
  * [`ADR-008`](../adr/ADR-008-une-seule-responsabilite-par-classe.md) : Une seule responsabilité par classe (SRP).
  * [`ADR-009`](../adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md) : Logs structurés en français.
  * [`ADR-011`](../adr/ADR-011-limitation-complexite-cognitive.md) : Complexité cognitive plafonnée à 15 par méthode.
  * [`ADR-014`](../adr/ADR-014-javadoc-obligatoire-classes-methodes.md) : Documentation Javadoc exhaustive en français.

---

## 🎯 1. Objectifs du Sprint

L'application directe d'un polygone géométrique vectoriel sur une carte raster peut générer deux types d'imperfections :
1. **Pixels résiduels indésirables :** Présence de franges de pixels blancs ou clairs situés immédiatement hors de la chaussée le long des limites de parcelles.
2. **Érosion des ronds-points :** Un filtrage morphologique ou spectral brutal risquerait d'éroder ou de déformer les anneaux giratoires géométriquement parfaits modélisés au Sprint 6.

Le **Sprint 8** introduit le module d'affinage spectral (`AlphaRefiner`) directement adapté de l'étape 9 de `snap_cells_prototype_v7.py` :
1. **Définition de l'espace autorisé (`AllowedMask`) :** Union des cellules retenues remplies ($M_{\text{fill}}$), du réseau routier fermé ($R_c$) et des îlots des giratoires substitués, avec comblement automatique des micro-trous de chaussée ($< 2000\text{ px}$).
2. **Filtrage doux et seuillage raide :** Flou gaussien séparable ($\sigma = FS = 1.4\text{ px}$) et fonction de transfert à contraste renforcé ($FK = 2.0$) pour créer une décroissance continue et nette sans marches d'escalier.
3. **Protection géométrique des ronds-points :** Neutralisation du masque d'atténuation au voisinage des ellipses giratoires par une fonction *Smoothstep* cubique ($3t^2 - 2t^3$) entre $FZ_0 = 2.3$ et $FZ_1 = 3.0$ en rayons normés.

---

## 📦 2. Livrables & Composants Clés

```text
com.sam102022.photoshop.v2.refine
├── AllowedRegionBuilder.java           (Construction du masque autorisé et comblement des micro-trous)
├── SoftThresholdFilter.java            (Flou gaussien séparable 2D FS=1.4 px et seuillage saturé FK=2.0)
├── RoundaboutExemptionModulator.java   (Génération de la zone tampon elliptique zw via Smoothstep)
├── AlphaRefiner.java                   (Façade orchestrant la chaîne d'affinage)
├── AlphaRefinementConfig.java          (Record immuable des hyperparamètres d'affinage)
└── AlphaRefinementMap.java             (Record immuable encapsulant la matrice locale factor [0..1])
```

### 2.1. `AllowedRegionBuilder`
* Fusion binaire sur la boîte englobante : $M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \text{labels ronds-points}$.
* Étiquetage 4-connexe des composantes de fond du complémentaire $\neg M_{\text{allowed}}$ fermées par `fill_holes`.
* Comblement des trous dont l'aire est inférieure au seuil `roadHoleMaxArea` ($2000\text{ px}$).

### 2.2. `SoftThresholdFilter`
* Convolution gaussienne 2D séparable (filtre horizontal puis vertical) à $\sigma = 1.4\text{ px}$ avec gestion miroir/réplication aux bords.
* Transformation point par point :
  $$\text{factor}(x, y) = \text{clamp}\left((\text{allowed}_s(x, y) - 0.5) \times FK + 0.5, 0.0, 1.0\right) \quad \text{avec } FK = 2.0$$

### 2.3. `RoundaboutExemptionModulator`
* Pour chaque ellipse $(x_e, y_e, a, b, \theta)$ de giratoire substitué :
  - Délimitation de la boîte englobante locale (rayon $3a + 4$) ;
  - Projection dans le repère canonique de l'ellipse : $\rho = \|(u_x, u_y)\| = \left\|\left(\frac{\Delta x \cos\theta + \Delta y \sin\theta}{a}, \frac{-\Delta x \sin\theta + \Delta y \cos\theta}{b}\right)\right\|$ ;
  - Interpolation cubique : $t = \text{clamp}\left(\frac{FZ_1 - \rho}{FZ_1 - FZ_0}, 0.0, 1.0\right)$, $s(t) = 3t^2 - 2t^3$ ;
  - Accumulation maximale : $z_w(x, y) = \max(z_w(x, y), s(t))$.
* Application du bouclier de protection :
  $$\text{factor}_{\text{protected}}(x, y) = \text{factor}(x, y) + z_w(x, y) \cdot (1.0 - \text{factor}(x, y))$$

### 2.4. `AlphaRefinementConfig` (Record immuable)
* `gaussianBlurSigma` : Écart-type du flou (défaut : `1.4 px`).
* `contrastStiffness` : Raideur de coupure (défaut : `2.0`).
* `roundaboutBufferInner` : Distance normée début de fondu (défaut : `2.3`).
* `roundaboutBufferOuter` : Distance normée fin de fondu (défaut : `3.0`).
* `roadHoleMaxArea` : Seuil de remplissage des îlots enclavés (défaut : `2000 px`).

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée :
  - ConsolidatedMask (Sprint 5)
  - RoadMask (Sprint 2)
  - CellLabelMap (Sprint 3)
  - List<Roundabout> substitués (Sprint 6)
  - AlphaRefinementConfig

Sortie :
  - Record immuable AlphaRefinementMap (matrice flottante float[][] factor de taille W_crop x H_crop)
```

---

## 🧪 4. Critères de Validation & Tests

1. **Test unitaire `SoftThresholdFilterTest` :**
   - Application sur un demi-plan binaire $\to$ transition douce et monotone entre 0.0 et 1.0 sur une bande de 2 à 3 pixels.
2. **Test unitaire `RoundaboutExemptionModulatorTest` :**
   - À l'intérieur de l'anneau ($\rho \le 2.3$) $\to z_w = 1.0 \implies \text{factor}_{\text{protected}} = 1.0$ sans aucune dégradation.
   - Au-delà de la zone tampon ($\rho \ge 3.0$) $\to z_w = 0.0 \implies \text{factor}_{\text{protected}} = \text{factor}$.
3. **Test d'intégration pivot `Sprint8IntegrationTest` sur CA01 :**
   - Sanctuarisation totale du rond-point substitué ($\text{factor} = 1.0f$).
   - Neutralisation effective des pixels de fuite résiduels en débordement de chaussée ($\text{factor} < 0.5$).
   - Temps d'exécution total du Sprint 8 $\le 1000\text{ ms}$ en Java pur (~330 ms mesurés sur grille $1505 \times 1783$).
   - Taux de réussite global de la suite de tests : 100% (136/136 tests passants).
