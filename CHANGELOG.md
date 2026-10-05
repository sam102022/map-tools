# Changelog

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

## [Non publié] - 2026-10-05

### Ajouté
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
