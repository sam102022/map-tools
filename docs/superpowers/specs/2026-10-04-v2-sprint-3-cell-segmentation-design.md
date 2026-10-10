# Spécification Technique V2 — Sprint 3 : Segmentation en Cellules (4-connexité) & CellGraph

**Date :** 04 octobre 2026  
**Auteur :** Équipe Photoshop Map Snapping  
**Statut :** Validé  
**Document cible :** `docs/superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md`  
**Conformité ADR :** ADR-001, ADR-002, ADR-004, ADR-006, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014.

---

## 1. Contexte & Objectifs

Dans l'architecture V2 du découpage topologique ([docs/SPRINTS_V2.md](../../SPRINTS_V2.md) et [docs/SPEC_V2_ALGORITHME.md](../../SPEC_V2_ALGORITHME.md)), le **Sprint 3** transforme le masque continu des routes imperméables (`RoadMask.closed` produit au Sprint 2) en une topologie discrète de parcelles urbaines (**cellules**) et en un graphe relationnel explicite (**`CellGraph`**).

### Objectifs Principaux
1. **Recadrage Déterministe sur la Zone d'Intérêt (`BoundingBoxCropper`) :**
   Isoler la fenêtre de calcul englobant le polygone d'intention augmentée d'une marge de sécurité ($mg = 90\text{ px}$), évitant le traitement de milliers de cellules inutiles situées en dehors du secteur d'intérêt.
2. **Segmentation 4-connexe Stricte (`CellLabeler`) :**
   Identifier chaque composante connexe disjointe de l'espace complémentaire des routes ($\neg R_{closed}$) sans introduire de ponts diagonaux artificiels à travers les coins des carrefours.
3. **Modélisation Topologique des Interfaces Routières (`RoadInterface` & `RoadInterfaceExtractor`) :**
   Désambiguïser les tronçons de chaussée en reliant chaque portion de route locale aux deux cellules adjacentes qu'elle sépare ($A \longleftrightarrow \text{RoadInterface} \longleftrightarrow B$).
4. **Construction du Graphe Topologique Immuable (`CellGraph`) :**
   Fournir le modèle d'adjacence complet pour le moteur de vote du Sprint 4 et l'expansion géodésique du Sprint 5.

---

## 2. Modèle de Données & Contrats Immuables

Le package cible est **`com.sam102022.photoshop.v2.cell`**.

### 2.1 `BoundingBox` & `CropWindow`
Représente la boîte englobante géométrique et la fenêtre de découpe matricielle associée :
```java
public record CropWindow(
        int x0,
        int y0,
        int width,
        int height
) {
    public boolean contains(int x, int y) { ... }
}
```

### 2.2 `CellLabelMap`
Matrice compacte de stockage des labels entiers sur la fenêtre de calcul :
```java
public record CellLabelMap(
        int width,
        int height,
        int[] labels,
        int cellCount
) {
    public int getLabel(int x, int y) { ... }
    public boolean isRoad(int x, int y) { ... }
}
```
* **Convention de valeur :**
  * `0` : Pixel routier / barrière infranchissable.
  * `1..N` : Identifiant unique de la cellule urbaine.

### 2.3 `Cell`
Record immuable encapsulant les propriétés géométriques et statistiques d'une cellule :
```java
public record Cell(
        int id,
        long area,
        int minX,
        int minY,
        int maxX,
        int maxY,
        PixelPoint centroid
) {
}
```

### 2.4 `RoadInterface`
Record immuable représentant la portion de chaussée bordant deux cellules adjacentes :
```java
public record RoadInterface(
        int id,
        int cellA,
        int cellB,
        BinaryMask mask,
        long area
) {
}
```
* **Invariants :**
  * `cellA < cellB` (ordonnancement strict pour garantir l'unicité de la relation non-orientée).
  * `cellA > 0 && cellB > 0`
  * `mask ⊆ RoadMask`

### 2.5 `CellGraph`
Structure de graphe immuable :
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

## 3. Algorithmes Détaillés

### 3.1 Recadrage Déterministe (`BoundingBoxCropper`)
* À partir des sommets du polygone d'intention $(x_i, y_i) \in P$ et d'une marge $mg = 90\text{ px}$ :
  $$x_0 = \max\left(0, \lfloor\min(x_i)\rfloor - mg\right), \quad x_1 = \min\left(W, \lceil\max(x_i)\rceil + mg\right)$$
  $$y_0 = \max\left(0, \lfloor\min(y_i)\rfloor - mg\right), \quad y_1 = \min\left(H, \lceil\max(y_i)\rceil + mg\right)$$
* La sous-matrice $R_c$ est extraite : $R_c = \text{RoadMask.closed}[y_0..y_1, x_0..x_1]$.
* Si aucun polygone n'est spécifié, le recadrage prend par défaut l'intégralité de l'image ($x_0 = 0, y_0 = 0, \text{width} = W, \text{height} = H$).

### 3.2 Étiquetage en 4-Connexité Stricte (`CellLabeler`)
L'étiquetage en composantes connexes (CCL) opère sur le complémentaire $\neg R_c$ :
1. **Pass 1 (Scan ligne par ligne avec Union-Find) :**
   * Pour chaque pixel $(x, y)$ où $\neg R_c(x, y) = \text{true}$ :
     * Examiner les voisins directs déjà visités en 4-voisinage : Nord $(x, y - 1)$ et Ouest $(x - 1, y)$.
     * Si aucun voisin n'est étiqueté $\rightarrow$ attribuer un nouveau label temporaire $L$.
     * Si un seul voisin est étiqueté $\rightarrow$ propager son label.
     * Si les deux voisins portent des labels différents $L_1 \neq L_2 \rightarrow$ propager $\min(L_1, L_2)$ et consigner l'équivalence dans une structure *Union-Find* avec compression de chemin et union par rang.
2. **Pass 2 (Résolution des équivalences & calcul cumulatif) :**
   * Résoudre la racine représentative de chaque label dans l'Union-Find et réindexer consécutivement les labels en $1..N$.
   * Au cours de la réécriture du tableau `int[] labels`, accumuler pour chaque cellule :
     * `area` : nombre total de pixels ;
     * `minX, minY, maxX, maxY` : boîte englobante locale ;
     * $\sum x$ et $\sum y$ : calcul du centre de gravité $\text{centroid} = (\frac{\sum x}{\text{area}}, \frac{\sum y}{\text{area}})$.

### 3.3 Extraction des Interfaces Routières (`RoadInterfaceExtractor`)
Pour attribuer les pixels de la chaussée aux cellules adjacentes :
1. **Initialisation Multi-Sources :**
   * Chaque pixel de chaussée immédiatement contigu à une cellule $C$ en 4-voisinage est marqué comme touchant $C$.
2. **Propagation Voronoi / BFS dans la route :**
   * Un algorithme de type parcours en largeur (BFS) propage les étiquettes de cellules vers l'intérieur de la surface routière jusqu'à une distance bornée par la demi-largeur maximale ($d \le 20\text{ px}$).
   * Lorsque deux fronts provenant de deux cellules distinctes $A$ et $B$ ($A < B$) se rejoignent, les pixels de chaussée intermédiaires sont assignés à la paire $\{A, B\}$.
3. **Agrégation des Interfaces :**
   * Pour chaque paire $\{A, B\}$ connectée par une portion de chaussée, créer une `RoadInterface(id, A, B, mask, area)`.
   * Les pixels de chaussée sans vis-à-vis (bord externe du territoire ou impasse) sont classés comme bord libre de cellule unilatérale.

---

## 4. Stratégie de Test et Critères d'Acceptation

### 4.1 Tests Unitaires TDD
* **`CellLabelerTest` :**
  * Grille synthétique avec 2 cellules séparées par une bande de route horizontale de 1 px $\rightarrow$ détection exacte de 2 cellules étanches.
  * Vérification de la non-connexion diagonale : deux pixels non-route se touchant uniquement par le coin doivent rester 2 cellules distinctes (preuve de la 4-connexité).
  * Rejet des masques invalides / null.
* **`BoundingBoxCropperTest` :**
  * Calcul de la boîte englobante augmentée de $90\text{ px}$ bornée aux dimensions de l'image.
  * Découpe exacte d'un `BinaryMask`.
* **`RoadInterfaceExtractorTest` :**
  * Deux cellules carrées séparées par une route de 4 px de large $\rightarrow$ extraction d'une unique `RoadInterface` reliant les deux cellules.
* **`CellGraphTest` :**
  * Navigation dans le graphe : recherche de voisins, recherche d'interface par paire de cellules.

### 4.2 Test d'Intégration Pivot CA01 (`Sprint3IntegrationTest`)
* Exécution complète à partir du `RoadMask` étalon et du polygone CA01 :
  * Nombre de cellules sur la zone rognée : **130 cellules** (concordance déterministe avec le résultat Python `ndi.label(~Rc)`).
  * Vérification qu'aucune cellule ne possède d'identifiant $\le 0$.
  * Temps d'exécution de la segmentation et de la construction du graphe : $\le 300\text{ ms}$.

---

## 5. Revue de Spécification (Self-Review)

- [x] **Placeholder scan :** Aucun TODO, TBD ou paramètre indéfini.
- [x] **Internal consistency :** Harmonisé avec `SPRINTS_V2.md` et les ADRs (ADR-001, ADR-004, ADR-008, ADR-011, ADR-014).
- [x] **Scope check :** Délimité au Sprint 3 (Cropping, CCL 4-connexe, CellGraph, RoadInterface).
- [x] **Ambiguity check :** Formules de recadrage, convention de labels et algorithme de propagation entièrement explicités.
