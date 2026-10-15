# Sprint 3 (V2) — Segmentation en Cellules (4-connexité) & CellGraph

**Statut :** ✅ Validé (04/10/2026)  
**Package cible :** `com.sam102022.photoshop.v2.cell`  
**Documents associés :**
* Spécification : [`docs/superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md`](../superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md)
* Plan d'implémentation : [`docs/superpowers/plans/2026-10-04-v2-sprint-3-cell-segmentation.md`](../superpowers/plans/2026-10-04-v2-sprint-3-cell-segmentation.md)

---

## 🎯 1. Objectifs du Sprint

Découper l'espace complémentaire des routes imperméables ($\neg R_{closed}$) en îlots urbains/parcelles disjoints (**cellules**) et construire la topologie d'adjacence enrichie par les interfaces routières locales (**`CellGraph`**).

---

## 📦 2. Livrables & Composants Clés

* **`CropWindow(int x0, int y0, int width, int height)` :** Record immuable modélisant la fenêtre de recadrage rectangulaire (ROI).
* **`BoundingBoxCropper` :** Calculateur de boîte englobante du polygone avec marge de sécurité $mg = 90\text{ px}$ et découpeur de masques matriciels.
* **`CellLabelMap(int width, int height, int[] labels, int cellCount)` :** Matrice compacte des étiquettes (0 = route, $1..N$ = cellules urbaines).
* **`Cell(int id, long area, int minX, int minY, int maxX, int maxY, PixelPoint centroid)` :** Modèle immuable de cellule urbaine.
* **`CellLabeler` :** Étiqueteur de composantes connexes (CCL) en **4-connexité stricte** à balayage Two-Pass et structure d'équivalences *Union-Find* (compression de chemin et union par rang).
* **`RoadInterface(int id, int cellA, int cellB, BinaryMask mask, long area)` :** Record immuable représentant la portion de chaussée séparant deux cellules adjacentes ($cellA < cellB$).
* **`RoadInterfaceExtractor` :** Détecteur d'interfaces par analyse directe sur routes fines ($1\text{ px}$) et propagation Voronoi multi-sources (BFS) sur routes larges.
* **`CellGraph(List<Cell> cells, List<RoadInterface> roadInterfaces)` :** Graphe topologique immuable avec indexation par cellule et navigation de voisinage.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - RoadMask (fermé topologiquement)
  - Polygone d'intention (ou liste de sommets pour le recadrage 90px)

Sortie : 
  - Record immuable CellGraph
```

```java
public record CellGraph(
        List<Cell> cells,
        List<RoadInterface> roadInterfaces,
        Map<Integer, Cell> cellById,
        Map<Integer, List<RoadInterface>> interfacesByCell
) {
    public Optional<Cell> findCell(int id) { ... }
    public List<RoadInterface> getInterfaces(int cellId) { ... }
    public Optional<RoadInterface> findInterface(int cellA, int cellB) { ... }
    public Set<Integer> getNeighbors(int cellId) { ... }
}
```

---

## 🧪 4. Critères de Validation & Tests

* **Étanchéité 4-connexe stricte :** Preuve formelle qu'aucun pont diagonal ne fusionne artificiellement deux cellules se touchant par le coin.
* **Test d'intégration pivot CA01 (`Sprint3IntegrationTest`) :**
  * Boîte englobante rognée : $1505 \times 1783\text{ px}$ ;
  * Nombre de cellules : **exactement 130 cellules** (concordance déterministe à 100% avec `ndi.label(~Rc)`) ;
  * Nombre d'interfaces routières : **269 interfaces** (concordance exacte avec l'analyse d'adjacence Voronoi) ;
  * Temps de calcul : **~800 ms** (critère $\le 1000\text{ ms}$).
* **Suite de tests :** 21/21 tests réussis (100% de succès dans `com.sam102022.photoshop.v2.cell`).
