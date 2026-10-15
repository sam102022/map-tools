# Plan d'Implémentation V2 — Sprint 3 : Segmentation en Cellules (4-connexité) & CellGraph

**Date :** 04 octobre 2026  
**Spécification de référence :** `docs/superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md`  
**Conformité ADR :** ADR-001, ADR-002, ADR-004, ADR-006, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014.

---

## Architecture des Fichiers à Créer

```text
src/main/java/com/sam102022/photoshop/v2/cell/
├── CropWindow.java                     # Fenêtre de recadrage géométrique
├── BoundingBoxCropper.java             # Calculateur de ROI et découpeur de masque
├── Cell.java                           # Record de cellule unitaire (id, area, bounds, centroid)
├── CellLabelMap.java                   # Matrice compacte int[] des étiquettes
├── CellLabeler.java                    # Étiqueteur 4-connexe CCL (Two-pass Union-Find)
├── RoadInterface.java                  # Record d'interface routière A-B
├── RoadInterfaceExtractor.java         # Extracteur Voronoi/BFS des tronçons de route
└── CellGraph.java                      # Modèle de graphe topologique immuable

src/test/java/com/sam102022/photoshop/v2/cell/
├── BoundingBoxCropperTest.java         # Tests unitaires de recadrage
├── CellLabelerTest.java                # Tests unitaires de 4-connexité et étanchéité
├── RoadInterfaceExtractorTest.java     # Tests d'extraction d'interfaces routières
├── CellGraphTest.java                  # Tests d'adjacence et de navigation
└── Sprint3IntegrationTest.java         # Test d'intégration pivot sur CA01 (130 cellules)
```

---

## Tâches Séquentielles d'Implémentation

### Task 1: Contrats de Domaine Immuables (`CropWindow`, `Cell`, `CellLabelMap`, `RoadInterface`, `CellGraph`)
* Créer les records `CropWindow`, `Cell`, `CellLabelMap`, `RoadInterface` et `CellGraph` dans `com.sam102022.photoshop.v2.cell`.
* Vérifier l'immutabilité et les préconditions défensives.

### Task 2: Découpeur de Boîte Englobante (`BoundingBoxCropper`)
* Développer `BoundingBoxCropper` pour calculer les coordonnées $x_0, y_0, x_1, y_1$ avec marge $mg = 90\text{ px}$.
* Développer `BoundingBoxCropperTest` pour valider les calculs de bornes et la découpe d'un sous-masque `BinaryMask`.

### Task 3: Étiqueteur en 4-Connexité Stricte (`CellLabeler`)
* Développer l'algorithme Two-Pass avec Union-Find (Disjoint-Set avec path compression et union by rank).
* Garantir l'étanchéité 4-connexe stricte : aucune liaison diagonale entre cellules.
* Produire la `CellLabelMap` et la `List<Cell>` avec centroïdes et surfaces exactes.
* Développer `CellLabelerTest` avec cas nominaux, tests de diagonales et validation défensive.

### Task 4: Extracteur d'Interfaces Routières (`RoadInterfaceExtractor`) & Graphe (`CellGraph`)
* Développer la propagation Voronoi/BFS dans la surface routière pour relier les cellules voisines adjacentes.
* Créer les instances `RoadInterface(id, cellA, cellB, mask, area)` avec $cellA < cellB$.
* Construire le `CellGraph` avec tables d'indexation par cellule.
* Développer `RoadInterfaceExtractorTest` et `CellGraphTest`.

### Task 5: Test d'Intégration Pivot CA01 (`Sprint3IntegrationTest`)
* Charger l'image témoin des routes `maps/road.png` (ou la sortie Sprint 2 sur `05_style_contraste_sans_rien.png`) et le polygone CA01 `01_plan_avec_territoires.json`.
* Exécuter le pipeline complet : `BoundingBoxCropper` $\rightarrow$ `CellLabeler` $\rightarrow$ `RoadInterfaceExtractor` $\rightarrow$ `CellGraph`.
* Vérifier le nombre exact de cellules : **130 cellules**.
* Vérifier la complétude du graphe et le temps d'exécution ($\le 300\text{ ms}$).

### Task 6: Mise à Jour de `JOURNAL.md`, `SPRINTS_V2.md` et Validation Finale
* Documenter la réussite du Sprint 3 dans `JOURNAL.md` et `docs/SPRINTS_V2.md`.
* Valider la non-régression de l'ensemble de la suite V2 (`mvn test -Dtest="com.sam102022.photoshop.v2.**.*Test"`).
