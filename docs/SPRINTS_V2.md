# Planification des Sprints V2 — Découpage par Cellules Topologiques

Ce document constitue le **sommaire directeur** et le guide d'orchestration pour l'implémentation de la V2 de l'algorithme de détourage cartographique, conformément à l'[ADR-007](adr/ADR-007-developpement-par-sprints.md), à la spécification technique [SPEC_V2_ALGORITHME.md](SPEC_V2_ALGORITHME.md) et aux avancées de la référence Python V7 (`maps/python/snap_cells_prototype_v7.py`).

Chaque sprint fait l'objet d'un fichier de spécification détaillé dédié dans le répertoire [`docs/sprints_v2/`](sprints_v2/).

---

## 🗺️ Vue d'Ensemble du Pipeline V2

```text
SPRINT 1 (Géométrie & Projection)
        │
        ▼
   PolygonMask P
        │
SPRINT 2 (Détection des Routes & Topologie)
        │
        ▼
 RoadMask R + Rclosed
        │
SPRINT 3 (Cellules & CellGraph)
        │
        ▼
 Cells + RoadInterfaces + Adjacencies
        │
SPRINT 4 (Vote Topologique)
        │
        ▼
   CellSelection (INSIDE / OUTSIDE / PARTIAL)
        │
SPRINT 5 (Reconstruction des Frontières Routières & Expansion Géodésique)
        │
        ├── BoundaryRoadExtractor
        ├── RoadDistanceTransform (hw)
        ├── GeodesicRoadExpander (MCP / Dijkstra borné)
        └── ResidualHoleResolver
        │
        ▼
 ConsolidatedMask Mc (binaire 0/1)
        │
SPRINT 6 (Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points)
        │
        ├── SubpixelContourExtractor (Marching Squares 0.5 + Resampling 1px)
        ├── CornerDetector (Angles vifs > 38° + Smoothstep blend)
        ├── RobustLqrSmoother (Régression quadratique locale + M-estimateur Cauchy)
        ├── RoundaboutDetector (Ellipses îlots + sondage radial bord externe)
        └── HermiteSplineConnector (Raccordement C1 sans rupture de courbure)
        │
        ▼
 SmoothVectorContour brut
        │
SPRINT 7 (Raffinement du Lissage Vectoriel — Tronçons Droits Multi-Échelles)
        │
        ├── StraightSegmentSmoother (Double LQR σ=22 et 2.7σ + détection de rectitude)
        └── Transition C1 par convolution boîte (k=10, fenêtre 21)
        │
        ▼
 SmoothVectorContour affiné
        │
SPRINT 8 (Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel)
        │
        ├── AllowedRegionBuilder (Mfill | Rc | giratoires + micro-trous < 2000 px)
        ├── SoftThresholdFilter (Flou gaussien FS=1.4 px + seuil raide FK=2.0)
        └── RoundaboutExemptionModulator (Protection elliptique ronds-points zw [2.3..3.0])
        │
        ▼
 AlphaRefinementMap
        │
SPRINT 9 (Rendu Sub-Pixel Supersampling SS=4, Export RGBA & CLI V2)
        │
        ├── V2Config (Configuration unifiée des hyperparamètres S1 -> S8)
        ├── SupersampleRenderer (Rastérisation SS=4 sur ROI + box filter + modulation d'affinage)
        ├── ImageClipper (Assemblage RGBA 32-bit clipped, mask 8-bit, overlay rouge)
        ├── V2Pipeline (Orchestrateur global de bout en bout Sprints 1 -> 9)
        └── V2CliRunner (CLI --v2 avec hyperparamètres documentés)
        │
        ▼
 RGBA + diagnostics + CLI
```

---

## 📑 Sommaire des Sprints Détaillés

| Sprint | Thématique | Statut | Package cible | Documentation dédiée |
| :--- | :--- | :--- | :--- | :--- |
| **Sprint 1** | Socle Géométrique, Projection Web Mercator & Rasterisation | ✅ Validé | `v2.geometry` | [sprint-1-geometrie-projection.md](sprints_v2/sprint-1-geometrie-projection.md) |
| **Sprint 2** | Détection Colorimétrique des Routes & Fermeture Topologique | ✅ Validé | `v2.road` | [sprint-2-detection-routes.md](sprints_v2/sprint-2-detection-routes.md) |
| **Sprint 3** | Segmentation en Cellules (4-connexité) & `CellGraph` | ✅ Validé | `v2.cell` | [sprint-3-cellules-cellgraph.md](sprints_v2/sprint-3-cellules-cellgraph.md) |
| **Sprint 4** | Moteur de Vote Topologique & Résolution des Parcelles | ✅ Validé | `v2.vote` | [sprint-4-vote-topologique.md](sprints_v2/sprint-4-vote-topologique.md) |
| **Sprint 5** | Reconstruction des Frontières Routières & Expansion Géodésique | ✅ Validé | `v2.expansion` | [sprint-5-expansion-geodesique.md](sprints_v2/sprint-5-expansion-geodesique.md) |
| **Sprint 6** | Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points | ✅ Validé | `v2.contour` | [sprint-6-lissage-subpixel-ronds-points.md](sprints_v2/sprint-6-lissage-subpixel-ronds-points.md) |
| **Sprint 7** | Lissage Adaptatif Multi-Échelle des Tronçons Droits (LQR Élargi) | ✅ Validé | `v2.contour` | [sprint-7-lissage-troncons-droits.md](sprints_v2/sprint-7-lissage-troncons-droits.md) |
| **Sprint 8** | Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel | ✅ Validé | `v2.refine` | [sprint-8-affinage-spectral-couverture-alpha.md](sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md) |
| **Sprint 9** | Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 | ⏳ À venir | `v2.render` / `v2.pipeline` / `cli` | [sprint-9-rendu-supersampling-cli.md](sprints_v2/sprint-9-rendu-supersampling-cli.md) |

---

## 🔒 Contrats I/O Explicites Entre Sprints

Chaque sprint s'appuie sur des contrats d'entrée/sortie immuables sous forme de Java Records ([ADR-004](adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md)) :

* **Sprint 1 ➔ Sprint 2 / 3 / 4 : `PolygonMask`**
  Représente le polygone d'intention $P[x, y] \in \{0, 1\}$.
* **Sprint 2 ➔ Sprint 3 : `RoadMask`**
  Encapsule les masques `raw` (brut) et `closed` (fermeture topologique minimale 1 px).
* **Sprint 3 ➔ Sprint 4 : `CellLabelMap` & `CellGraph`**
  Modélise les cellules urbaines, la matrice de labels et les interfaces routières locales les reliant.
* **Sprint 4 ➔ Sprint 5 : `CellSelection`**
  Synthétise les identifiants de cellules retenues (`inside`), rejetées (`outside`) et mixtes (`partial`), ainsi que le masque d'amorçage matriciel local $T$ (`retainedMask`).
* **Sprint 5 ➔ Sprint 6 / 8 : `ConsolidatedMask`**
  Masque binaire étanche consolidé sur la boîte englobante locale après expansion géodésique.
* **Sprint 6 ➔ Sprint 7 : `SmoothVectorContour` & `CropWindow`**
  Contour vectoriel continu sub-pixel lissé par régression LQR locale avec substitution d'arcs giratoires.
* **Sprint 7 ➔ Sprint 9 : `SmoothVectorContour` (affiné)**
  Contour vectoriel sub-pixel dont les longs alignements droits ont été redressés par lissage multi-échelle adaptatif.
* **Sprint 8 ➔ Sprint 9 : `AlphaRefinementMap`**
  Grille flottante locale $[0.0 .. 1.0]$ issue de l'analyse spectrale et du seuillage doux avec bouclier de protection des ronds-points.
* **Sprint 9 : `RenderResult`**
  Ensemble des images de production (`clipped`, `mask`, `overlay`) et du `CoverageMask` continu global.

---

## 🚦 Formalisation des Modes `TERRITORY` et `ZONE`

Le pipeline adapte son comportement lors de la phase de reconstruction frontière (Sprint 5) :
* **Mode `TERRITORY` :** Absorption intégrale de la chaussée jusqu'à son bord extérieur pour les routes reliant `INSIDE` à `OUTSIDE`.
* **Mode `ZONE` :** Partage équitable de la chaussée à mi-largeur (le long de l'axe médian) pour les routes contiguës séparant deux zones internes (`ZONE A` et `ZONE B`).

---

## 🎯 Fixture d'Intégration Pivot (`CA01_V2_REFERENCE`)

Le cas d'usage réel `Territoire CA01` sert de banc d'essai étalon commun à tous les sprints :
* **Localisation des fichiers :** `src/test/resources/v2/fixtures/CA01/`
* **Entrées :** `01_plan_avec_territoires.json` et `05_style_contraste_sans_rien.png`
* **Dimensions réelles vérifiées :** **$3810 \times 2130\text{ px}$** au zoom 17.

---

## 📊 Matrice de Validation Globale V2

| Sprint | Entrée | Sortie | Test Pivot & Critère d'Acceptation |
| :--- | :--- | :--- | :--- |
| **S1** | JSON `CA01` | `PolygonMask` | Projection GPS ➔ Pixels conforme à l'emprise ($3810 \times 2130$) *(Validé)* |
| **S2** | Image `CA01` | `RoadMask` | Extraction colorimétrique et fermeture sans perte d'axes secondaires *(Validé : 99,9850%)* |
| **S3** | `RoadMask` | `CellGraph` | Cellules 4-connexes étanches + `RoadInterfaces` associées *(Validé : 130 cellules, 269 interfaces)* |
| **S4** | `PolygonMask` + `CellGraph` | `CellSelection` | Vote déterministe (21 cellules pleines / 6 partielles) |
| **S5** | `CellGraph` + `CellSelection` | `ConsolidatedMask` | Reconstruction des frontières routières (EDT + MCP borné) + îlots résiduels *(Validé : 1 224 942 px, 99.21% vs python)* |
| **S6** | `ConsolidatedMask` (+ `RoadMask`, `CellLabelMap`) | `SmoothVectorContour` | Contour sub-pixel, LQR robuste sans rabotage, ronds-points par ellipses & Hermite (5 coins majeurs, 1 RP sur CA01, temps 282 ms) *(Validé)* |
| **S7** | `SmoothVectorContour` brut | `SmoothVectorContour` affiné | Redressement des avenues droites, Hausdorff $\le 2.0\text{ px}$, RMS $\le 0.5\text{ px}$ vs `contour_smooth.npy` V7 |
| **S8** | `ConsolidatedMask`, `RoadMask`, Giratoires | `AlphaRefinementMap` | Neutralisation de ~28 893 px blancs en périphérie de route, protection ronds-points zw |
| **S9** | `SmoothVectorContour` + `AlphaRefinementMap` + Image | `RenderResult` | Supersampling vectoriel $\times 4$, découpe RGBA, IoU $\ge 0.99$ vs `CA01_clipped_v7.png` |
