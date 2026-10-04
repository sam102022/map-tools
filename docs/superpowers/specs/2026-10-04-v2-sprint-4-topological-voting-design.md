# Spécification Technique V2 — Sprint 4 : Moteur de Vote Topologique & Résolution des Parcelles

**Date :** 04 octobre 2026  
**Auteur :** Équipe Photoshop Map Snapping  
**Statut :** Validé  
**Document cible :** `docs/superpowers/specs/2026-10-04-v2-sprint-4-topological-voting-design.md`  
**Conformité ADR :** ADR-001, ADR-002, ADR-004, ADR-006, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014.

---

## 1. Contexte & Objectifs

Dans l'architecture V2 du découpage cartographique ([docs/SPRINTS_V2.md](../../SPRINTS_V2.md) et [docs/SPEC_V2_ALGORITHME.md](../../SPEC_V2_ALGORITHME.md)), le **Sprint 4** assure la passerelle entre la topologie discrète des îlots urbains (produite au Sprint 3) et la reconstruction géodésique des frontières routières (opérée au Sprint 5).

Chaque cellule urbaine $C$ issue de la segmentation en 4-connexité doit être évaluée vis-à-vis du polygone d'intention $P$ afin d'attribuer sans ambiguïté son statut :
* **`INSIDE`** : cellule absorbée à 100 % dans le territoire ;
* **`OUTSIDE`** : cellule rejetée à 100 % (extérieure au territoire) ;
* **`PARTIAL`** : cellule frontière ouverte (champ, forêt, bordure de commune sans barrière routière continue) dont seule la portion $C \cap P$ est conservée.

Le Sprint 4 génère le **masque d'amorçage matriciel $T$** indispensable au Sprint 5 pour propager l'expansion géodésique dans l'épaisseur des voies limitrophes.

---

## 2. Clarifications Architecturales & Levée des Ambiguïtés

L'évaluation préalable de la documentation a mis en évidence quatre points critiques qui sont formellement tranchés ici :

1. **Complétude du contrat d'entrée (I/O) :**
   Le graphe `CellGraph` et les records `Cell` ne stockant que des métadonnées géométriques et scalaires (`area`, `bounds`, `centroid`), ils ne contiennent pas les masques de pixels des cellules. L'entrée de calcul du vote doit donc obligatoirement recevoir la matrice d'étiquettes **`CellLabelMap`** (fournie par `CellLabelingResult` du Sprint 3) pour déterminer l'appartenance pixel par pixel.
2. **Repère de coordonnées unifié (Local ROI) :**
   Le vote s'effectue dans le repère local de la boîte englobante rognée (`CropWindow`), identique à celui de `CellLabelMap` ($1505 \times 1783\text{ px}$ sur CA01). Le polygone d'intention global $P$ (`PolygonMask`, Sprint 1) est préalablement découpé sur cette même fenêtre via `BoundingBoxCropper.crop(polygonMask.mask(), cropWindow)` pour donner le sous-masque $P_c$.
3. **Fourniture directe du masque d'amorçage $T$ (`retainedMask`) :**
   Pour éviter que le Sprint 5 ne doive réassembler manuellement les pixels des cellules retenues, le record de sortie `CellSelection` fournit directement le masque binaire consolidé $T$ :
   $$T = \left(\bigcup_{C \in \text{INSIDE}} C\right) \cup \left(\bigcup_{C \in \text{PARTIAL}} (C \cap P_c)\right)$$
4. **Gestion uniforme des cellules de bordure et des grandes parcelles ouvertes :**
   Conformément au prototype étalon Python (`snap_cells_prototype_v5.py`), aucune heuristique empirique complexe n'est requise pour les cellules touchant le bord de l'image. Toute cellule dont le ratio de couverture vérifie $\text{LO} < \text{coverage} < \text{HI}$ est traitée de manière déterministe comme `PARTIAL` par intersection matricielle stricte $C \cap P_c$.

---

## 3. Modèle de Données & Contrats Immuables

Le package cible est **`com.sam102022.photoshop.v2.vote`**.

### 3.1 `CellState` (Enum)
Qualifie la décision topologique pour une cellule :
```java
public enum CellState {
    INSIDE,
    OUTSIDE,
    PARTIAL
}
```

### 3.2 `CellSelectionPolicy` (Record)
Encapsule les seuils de décision paramétrables ([ADR-004](../../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md)) :
```java
public record CellSelectionPolicy(
        double insideThreshold,
        double partialThreshold
) {
    public static final double DEFAULT_INSIDE_THRESHOLD = 0.60;
    public static final double DEFAULT_PARTIAL_THRESHOLD = 0.05;

    public static CellSelectionPolicy defaultPolicy() {
        return new CellSelectionPolicy(DEFAULT_INSIDE_THRESHOLD, DEFAULT_PARTIAL_THRESHOLD);
    }
}
```
* **Invariants :**
  $$0.0 \le \text{partialThreshold} < \text{insideThreshold} \le 1.0$$

### 3.3 `CellDecision` (Record)
Détail diagnostique immuable du vote pour une cellule donnée :
```java
public record CellDecision(
        int cellId,
        long cellArea,
        long intersectionArea,
        double coverage,
        CellState state
) {
}
```
* **Invariants :**
  * `cellId > 0`
  * `cellArea > 0`
  * `0 <= intersectionArea <= cellArea`
  * `0.0 <= coverage <= 1.0` avec $\text{coverage} = \frac{\text{intersectionArea}}{\text{cellArea}}$

### 3.4 `CellSelection` (Record)
Contrat de sortie officiel du Sprint 4 vers le Sprint 5 :
```java
public record CellSelection(
        Set<Integer> insideCellIds,
        Set<Integer> outsideCellIds,
        Set<Integer> partialCellIds,
        Map<Integer, CellDecision> decisions,
        BinaryMask retainedMask,
        Map<Integer, BinaryMask> partialCellMasks
) {
    public boolean isRetained(int cellId) { ... }
    public Optional<CellDecision> findDecision(int cellId) { ... }
}
```
* **Propriétés & Invariants :**
  * Ensembles d'identifiants disjoints : $\text{inside} \cap \text{outside} = \emptyset$, $\text{inside} \cap \text{partial} = \emptyset$, $\text{outside} \cap \text{partial} = \emptyset$.
  * Union exhaustive : $\text{inside} \cup \text{outside} \cup \text{partial} = \text{l'ensemble des cellules}$.
  * `retainedMask` : dimensions strictement égales à celles de la fenêtre de calcul.
  * `partialCellMasks` : contient le sous-masque binaire $C \cap P_c$ pour chaque identifiant de cellule partielle.

---

## 4. Architecture des Composants & Algorithmes

### 4.1 `CellCoverageCalculator`
* **Responsabilité unique :** Calculer l'intersection spatiale $C \cap P_c$ et le ratio de couverture pour chaque cellule de `CellLabelMap`.
* **Algorithme d'accumulation en un seul passage (Single-Pass O(N)) :**
  1. Instancier un tableau d'entiers `long[] intersectionCounts = new long[cellCount + 1]`.
  2. Balayer la grille locale $(x, y) \in [0..W[ \times [0..H[$ :
     * Récupérer le label $L = \text{labelMap.getLabel}(x, y)$.
     * Si $L > 0$ et que le masque du polygone $P_c(x, y)$ est actif (`true`) :
       $$\text{intersectionCounts}[L]++$$
  3. Pour chaque cellule $i \in [1..\text{cellCount}]$ :
     $$\text{area} = \text{cells.get}(i - 1).\text{area}()$$
     $$\text{intersection} = \text{intersectionCounts}[i]$$
     $$\text{coverage} = \frac{\text{intersection}}{\text{area}}$$
* **Complexité :** Temporelle $\mathcal{O}(W \times H)$, Spatiale $\mathcal{O}(\text{cellCount})$.

### 4.2 `CellClassifier`
* **Responsabilité unique :** Assigner le statut `CellState` à partir de la politique `CellSelectionPolicy` :
  $$\text{state}(C) = \begin{cases} 
  \mathbf{INSIDE} & \text{si } \text{coverage}(C) \ge \text{insideThreshold} \\
  \mathbf{PARTIAL} & \text{si } \text{partialThreshold} < \text{coverage}(C) < \text{insideThreshold} \\
  \mathbf{OUTSIDE} & \text{si } \text{coverage}(C) \le \text{partialThreshold}
  \end{cases}$$
* Génère pour chaque cellule un record `CellDecision`.

### 4.3 `PartialCellResolver`
* **Responsabilité unique :** Isoler le masque binaire de chaque cellule partielle $C \cap P_c$ et construire le masque matriciel global retenu $T$.
* **Algorithme :**
  1. Allouer un masque binaire `retainedMask` de taille $W \times H$.
  2. Créer une map de sous-masques `Map<Integer, BinaryMask> partialMasks`.
  3. Pour chaque cellule `INSIDE`, marquer tous ses pixels à `true` dans `retainedMask`.
  4. Pour chaque cellule `PARTIAL`, marquer à `true` dans `retainedMask` et dans son `partialMask` dédié uniquement les pixels $(x, y)$ où $\text{labelMap}(x, y) == C \land P_c(x, y) == \text{true}$.
* **Optimisation par passage unique :**
  L'assemblage de `retainedMask` et des masques partiels peut être réalisé en un seul balayage $(x, y)$ :
  * Si $\text{insideCellIds.contains}(L) \implies \text{retainedMask.set}(x, y, \text{true})$ ;
  * Si $\text{partialCellIds.contains}(L) \land P_c(x, y) \implies \text{retainedMask.set}(x, y, \text{true})$ et enregistrement dans le masque partiel de la cellule $L$.

### 4.4 `TopologicalVoteEngine`
* **Responsabilité unique :** Coordinateur de façade du vote topologique.
* **Signature :**
  ```java
  public CellSelection execute(
          CellLabelMap labelMap,
          List<Cell> cells,
          BinaryMask croppedPolygon,
          CellSelectionPolicy policy
  )
  ```
  Et sa surcharge avec `CellSelectionPolicy.defaultPolicy()`.

---

## 5. Stratégie de Test et Critères d'Acceptation

### 5.1 Tests Unitaires TDD
* **`CellSelectionPolicyTest` :**
  * Validation des seuils valides ($0.05, 0.60$).
  * Rejet des seuils invalides (négatifs, $> 1.0$, ou $\text{partial} \ge \text{inside}$).
* **`CellCoverageCalculatorTest` :**
  * Grille synthétique $10 \times 10$ avec 2 cellules :
    * Cellule 1 ($50\text{ px}$), $50\text{ px}$ recouverts $\implies \text{coverage} = 1.0$.
    * Cellule 2 ($50\text{ px}$), $10\text{ px}$ recouverts $\implies \text{coverage} = 0.20$.
  * Précision des comptages et des centroïdes.
* **`CellClassifierTest` :**
  * Classification exacte selon les seuils : $0.70 \rightarrow \text{INSIDE}$, $0.30 \rightarrow \text{PARTIAL}$, $0.02 \rightarrow \text{OUTSIDE}$.
  * Cas limites aux bornes ($0.60$ et $0.05$).
* **`PartialCellResolverTest` :**
  * Vérification de l'étanchéité du masque $T$ : les pixels hors intersection d'une cellule partielle restent à `false`.
* **`CellSelectionTest` :**
  * Immutabilité des collections et méthodes utilitaires (`isRetained`, `findDecision`).

### 5.2 Test d'Intégration Pivot CA01 (`Sprint4IntegrationTest`)
* Exécution complète sur le territoire étalon CA01 :
  * 130 cellules en entrée (Sprint 3).
  * Masque de routes rogné ($1505 \times 1783\text{ px}$).
  * Masque de polygone rogné $P_c$ ($1505 \times 1783\text{ px}$).
* **Critères déterministes stricts (concordance 100% avec l'étalon Python) :**
  * **Nombre de cellules `INSIDE` :** exactement **21 cellules** ;
  * **Nombre de cellules `PARTIAL` :** exactement **6 cellules** ;
  * **Nombre de cellules `OUTSIDE` :** exactement **103 cellules** ;
  * **Somme totale :** $21 + 6 + 103 = 130$ cellules.
* **Surface du masque retenu $T$ :** Concordance exacte au pixel près avec le prototype Python.
* **Budget de performance :** Exécution du vote et de la génération du masque retenu en moins de **50 ms**.

---

## 6. Revue de Spécification (Self-Review)

- [x] **Placeholder scan :** Aucun TODO, TBD ou paramètre indéfini.
- [x] **Internal consistency :** Harmonisé avec `SPRINTS_V2.md`, `CellGraph`, `CellLabelMap` et les ADRs.
- [x] **Scope check :** Délimité strictement au vote topologique et à la résolution des parcelles (Sprint 4).
- [x] **Ambiguity check :** Formules mathématiques, contrats d'entrée/sortie et structures de données explicitées.
