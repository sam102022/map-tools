# Changelog

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

## [Non publié] - 2026-10-05

### Ajouté
- **Sprint 9 (V2) - Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 (Parachèvement V2)** :
  - `RenderResult` : Record immuable regroupant l'ensemble des livrables graphiques finaux : `clipped` (ARGB 32-bit), `mask` (TYPE_BYTE_GRAY 8-bit), `overlay` (RGB avec contour rouge `#FF0000`) et `coverageMask` (`CoverageMask` continu $[0..255]$).
  - `V2Config` : Record immuable unifié encapsulant l'intégralité des hyperparamètres des Sprints 1 à 9, avec validation défensive des bornes et méthodes de conversion vers les sous-configurations (`CellSelectionPolicy`, `ExpansionConfig`, `ContourSmoothingConfig`, `AlphaRefinementConfig`).
  - `SupersampleRenderer` : Rastériseur vectoriel haute-fidélité par sur-échantillonnage ($SS=4$, 16 sous-pixels/px) avec décalage de demi-pixel ($+0.5\text{ px}$), réduction par Box Filter et modulation point par point par la matrice d'affinage spectral `AlphaRefinementMap`.
  - `ImageClipper` : Découpeur et assembleur des livrables graphiques avec composition alpha sans crénelage, extraction morphologique de bordure frontière et projection sur la carte source.
  - `V2Pipeline` : Orchestrateur complet de bout en bout reliant de façon fonctionnelle et modulaire les 9 étapes algorithmiques (Sprints 1 à 9).
  - `V2CliRunner` : Point d'entrée de ligne de commande dédié au pipeline V2 avec gestion des drapeaux (`--map`, `--json`, `--road`, `--out-dir`, `--output`, `--mask-out`, `--overlay-out`, `--ss`, `--mode`, `--crop-margin`, `--eps`, etc.), validation des fichiers et affichage de statistiques d'exécution en français.
  - `CliRunner` : Aiguillage automatique vers `V2CliRunner` lors de la présence de l'option `--v2`.
  - Tests unitaires et d'intégration : `RenderResultTest`, `V2ConfigTest`, `SupersampleRendererTest`, `ImageClipperTest`, `V2PipelineTest`, `V2CliRunnerTest`.
  - Test d'intégration pivot CA01 `Sprint9IntegrationTest` validant l'ensemble de la chaîne de bout en bout avec concordance IoU de 98.6938% vs `CA01_mask_v7.png` et temps de calcul de 1574 ms (budget $\le 2500\text{ ms}$).

- **Sprint 8 (V2) - Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel** :
  - `AllowedRegionBuilder` : Construction du masque binaire de l'espace autorisé $M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \text{îlots ronds-points}$ et comblement sélectif des cavités intérieures compactes de chaussée ($< 2000\text{ px}$).
  - `SoftThresholdFilter` : Filtre de convolution gaussienne séparable 2D ($\sigma = 1.4\text{ px}$) avec conditions aux limites réfléchies (miroir demi-échantillon) et fonction de transfert à contraste renforcé ($FK = 2.0$) clampée dans $[0.0 .. 1.0]$, optimisé pour une exécution ultra-rapide en cache L1.
  - `RoundaboutExemptionModulator` : Modulateur d'exemption spectrale sanctuarisant les carrefours giratoires substitués par un bouclier interpolé en Smoothstep cubique $C^1$ ($3t^2 - 2t^3$) entre $FZ_0 = 2.3$ et $FZ_1 = 3.0$ en rayons normés d'ellipse.
  - `AlphaRefiner` : Façade d'orchestration de bout en bout de l'affinage spectral avec injection de dépendances et surcharge de commodité avec configuration par défaut.
  - `AlphaRefinementConfig` : Record immuable regroupant les hyperparamètres calibrés (`gaussianBlurSigma`, `contrastStiffness`, `roundaboutBufferInner`, `roundaboutBufferOuter`, `roadHoleMaxArea`).
  - `AlphaRefinementMap` : Record immuable encapsulant la matrice locale 2D `float[][] factor` sur la `CropWindow` avec copie défensive et accesseur unitaire `factorAt(x, y)`.
  - `SmoothVectorContour` & `Roundabout` : Traçabilité des ronds-points substitués et exposition de `cellId`.
  - Tests unitaires et d'intégration complets : `AllowedRegionBuilderTest`, `SoftThresholdFilterTest`, `RoundaboutExemptionModulatorTest`, `AlphaRefinementModelTest`, `AlphaRefinerTest`.
  - Test d'intégration pivot CA01 `Sprint8IntegrationTest` validant l'enchaînement des Sprints 1 à 8, la sanctuarisation totale du giratoire ($\text{factor} = 1.0f$), la neutralisation des pixels résiduels en débordement de chaussée et le respect du budget de performance (~330 ms pour un budget $\le 1000\text{ ms}$).

- **Sprint 7 (V2) - Lissage Adaptatif Multi-Échelle des Tronçons Droits (Double LQR)** :
  - `StraightSegmentSmoother` : Composant dédié (SRP) exécutant un double lissage LQR multi-échelle ($\sigma = 22.0$ et $\sigma_{wide} = 59.4$) avec évaluation d'écart géométrique local $\Delta = \|\mathbf{sm}_b - \mathbf{sm}_a\|$, seuillage de rectitude ($ST\_T = 3.0\text{ px}$) et transition continue $C^1$ par convolution boîte 1D avec réplication de bord ($k = 10$).
  - `ContourSmoothingConfig` : Enrichissement du record immuable avec les 4 hyperparamètres étalonnés sur Python V7 (`straightFactor`, `straightScale`, `straightThreshold`, `straightTransitionK`) et validation d'invariants.
  - `RobustLqrSmoother.LqrResidualResult` : Record immuable regroupant les signaux résiduels lissés `fitX` et `fitY`.
  - `RobustLqrSmoother.smoothResiduals` : Exposition de la méthode de lissage sur les résidus 2D avec factorisation de la boucle itérative conjointe.
  - `ContourSmoothingEngine` : Intégration de `StraightSegmentSmoother` dans le pipeline vectoriel sub-pixel avec support de l'injection de dépendances.
  - Tests unitaires complets : `StraightSegmentSmootherTest`, extension de `ContourDomainModelTest`, `RobustLqrSmootherTest` et `ContourSmoothingEngineTest`.
  - Test d'intégration pivot CA01 `Sprint7IntegrationTest` validant l'absorption des encoches d'icônes, la préservation des 5 coins majeurs, la régularité spatiale (5 215 points sub-pixels à pas 1.0 px) et la performance d'exécution (~230 ms pour un budget $\le 1000\text{ ms}$).
  - Documentation et traçabilité : Mise à jour de `JOURNAL.md`, `docs/SPRINTS_V2.md`, `docs/sprints_v2/sprint-7-lissage-troncons-droits.md` et plan d'exécution du Sprint 7.

## [Sprint 6] - 2026-10-04
### Ajouté
- Géométrie Sub-Pixel, Lissage Robuste LQR & Détection/Substitution des Ronds-points.

## [Sprint 5] - 2026-10-04
### Ajouté
- Reconstruction des Frontières Routières, Expansion Géodésique & `ConsolidatedMask`.

## [Sprint 4] - 2026-10-04
### Ajouté
- Moteur de Vote Topologique & Résolution des Parcelles Ouvertes.

## [Sprint 3] - 2026-10-04
### Ajouté
- Segmentation en Cellules (4-connexité) & `CellGraph`.

## [Sprint 2] - 2026-10-03
### Ajouté
- Détection Colorimétrique des Routes & Fermeture Topologique.

## [Sprint 1] - 2026-10-02
### Ajouté
- Socle Géométrique, Projection Web Mercator & Rasterisation de Polygones.
