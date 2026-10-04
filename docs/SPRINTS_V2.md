# Planification des Sprints V2 — Découpage par Cellules Topologiques

Ce document constitue le **sommaire directeur** et le guide d'orchestration pour l'implémentation de la V2 de l'algorithme de détourage cartographique, conformément à l'[ADR-007](adr/ADR-007-developpement-par-sprints.md), à la spécification technique [SPEC_V2_ALGORITHME.md](SPEC_V2_ALGORITHME.md) et aux avancées de la référence Python V5 (`maps/python/snap_cells_prototype_v5.py`).

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
 SmoothVectorContour
        │
SPRINT 7 (Rendu Sub-Pixel Supersampling SS=4 & CLI V2)
        │
        ├── V2Config (Configuration immuable des hyperparamètres)
        ├── SupersampleRenderer (Rastérisation vectorielle 4x + box filter -> CoverageMask)
        ├── ImageClipper (Assemblage RGBA 32-bit clipped, mask, overlay)
        ├── V2Pipeline (Orchestrateur global Sprints 1 -> 7)
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
| **Sprint 7** | Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 | ⏳ À venir | `v2.render` / `v2.pipeline` / `cli` | [sprint-7-rendu-supersampling-cli.md](sprints_v2/sprint-7-rendu-supersampling-cli.md) |

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
* **Sprint 5 ➔ Sprint 6 : `ConsolidatedMask`**
  Masque binaire étanche consolidé sur la boîte englobante locale après expansion géodésique (fourni avec `RoadMask`, `CellLabelMap` et `CropWindow` pour la modélisation des ronds-points).
* **Sprint 6 ➔ Sprint 7 : `SmoothVectorContour` & `CropWindow`**
  Contour vectoriel continu sub-pixel lissé par régression quadratique locale et ellipses d'îlots, rattaché à son repère ROI.
* **Sprint 7 : `RenderResult`**
  Ensemble des images de production (`clipped`, `mask`, `overlay`) et du `CoverageMask` continu.

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
| **S7** | `SmoothVectorContour` + Image source | `RenderResult` | Supersampling vectoriel $\times 4$, découpe RGBA, IoU $\ge 0.99$ vs python V5 |
