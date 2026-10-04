# Sprint 4 (V2) — Moteur de Vote Topologique & Résolution des Parcelles Ouvertes

**Statut :** ⏳ Prêt pour implémentation  
**Package cible :** `com.sam102022.photoshop.v2.vote`  
**Documents associés :**
* Spécification Technique : [`docs/superpowers/specs/2026-10-04-v2-sprint-4-topological-voting-design.md`](../superpowers/specs/2026-10-04-v2-sprint-4-topological-voting-design.md)
* Plan d'Implémentation TDD : [`docs/superpowers/plans/2026-10-04-v2-sprint-4-topological-voting.md`](../superpowers/plans/2026-10-04-v2-sprint-4-topological-voting.md)
* Spécification Générale : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)

---

## 🎯 1. Objectifs du Sprint

Classifier chaque cellule urbaine issue du découpage en 4-connexité du Sprint 3 (`CellLabelMap` & `CellGraph`) selon son taux d'inclusion dans le polygone d'intention $P$ et générer le **masque d'amorçage matriciel $T$** (`retainedMask`) indispensable au Sprint 5 pour propager l'expansion géodésique dans les voies limitrophes.

---

## 📦 2. Livrables & Composants Clés

* **`CellState` :** Enumération des trois états possibles : `INSIDE`, `OUTSIDE`, `PARTIAL`.
* **`CellSelectionPolicy` :** Record immuable encapsulant les seuils paramétrables de décision (par défaut $\text{inside} = 0.60$ et $\text{partial} = 0.05$).
* **`CellDecision` :** Record diagnostique immuable par cellule (`cellId`, `cellArea`, `intersectionArea`, `coverage`, `state`).
* **`CellCoverageCalculator` :** Calcul matriciel en un seul passage $\mathcal{O}(W \times H)$ du ratio d'inclusion de chaque cellule :
  $$\text{coverage}(C) = \frac{\text{Surface}(C \cap P_c)}{\text{Surface}(C)}$$
* **`CellClassifier` :** Application de la politique de sélection :
  * **`INSIDE`** si $\text{coverage}(C) \ge 0.60$ (cellule conservée à 100 %) ;
  * **`OUTSIDE`** si $\text{coverage}(C) \le 0.05$ (cellule rejetée à 100 %) ;
  * **`PARTIAL`** entre $0.05$ et $0.60$ (cellule frontière à découpage mixte : conservation stricte de $C \cap P_c$).
* **`PartialCellResolver` :** Traitement des parcelles ouvertes / bordures de carte par intersection binaire stricte $C \cap P_c$ et assemblage du masque d'amorçage $T$.
* **`CellSelection` :** Record immuable de sortie synthétisant les identifiants par statut, les décisions individuelles, le masque d'amorçage complet $T$ (`retainedMask`) et les masques partiels dédiés.
* **`TopologicalVoteEngine` :** Façade orchestratrice unifiée pour l'exécution du vote topologique.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - CellLabelMap + List<Cell> (Sprint 3)
  - BinaryMask croppedPolygonMask (Pc = PolygonMask découpé sur CropWindow)
  - CellSelectionPolicy (Optionnel, défaut 0.60 / 0.05)

Sortie : 
  - Record immuable CellSelection
```

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

* **Masque d'amorçage $T$ (`retainedMask`) :** Fournit directement à la géodésie du Sprint 5 le masque binaire local :
  $$T = \left(\bigcup_{C \in \text{INSIDE}} C\right) \cup \left(\bigcup_{C \in \text{PARTIAL}} (C \cap P_c)\right)$$

---

## 🧪 4. Critères de Validation & Tests

* **Validation déterministe sur CA01 (`Sprint4IntegrationTest`) :**
  * Traitement des 130 cellules du territoire de référence (concordance 100% avec l'étalon Python `snap_cells_prototype_v5.py`) :
    * **21 cellules** `INSIDE` ;
    * **6 cellules** `PARTIAL` ;
    * **103 cellules** `OUTSIDE` ;
  * Somme exacte : $21 + 6 + 103 = 130$ cellules ;
  * Conservation stricte de l'intersection pour les 6 cellules ouvertes sans fuite extérieure ;
  * Dimensions du masque $T$ conformes à la zone d'intérêt ($1505 \times 1783\text{ px}$) ;
  * Budget de performance : exécution complète en moins de **50 ms**.
