# Sprint 4 (V2) — Moteur de Vote Topologique & Résolution des Parcelles Ouvertes

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.vote`  
**Documents associés :**
* Spécification : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)

---

## 🎯 1. Objectifs du Sprint

Classifier chaque cellule urbaine issue du `CellGraph` selon son taux d'inclusion dans le polygone d'intention $P$ et traiter les cas limites (champs ouverts, forêts, parcs sans clôture routière, cellules touchant les bords de l'image).

---

## 📦 2. Livrables & Composants Clés

* **`CellCoverageCalculator` :** Calcul matriciel ou vectorisé du ratio de couverture de chaque cellule :
  $$\text{coverage}(C) = \frac{\text{Surface}(C \cap P)}{\text{Surface}(C)}$$
* **`CellClassifier` :** Application de la politique de sélection `CellSelectionPolicy` :
  * **`INSIDE`** si $\text{coverage}(C) \ge 0.60$ (cellule conservée à 100%) ;
  * **`OUTSIDE`** si $\text{coverage}(C) \le 0.05$ (cellule rejetée à 100%) ;
  * **`PARTIAL`** entre $0.05$ et $0.60$ (cellule frontière à découpage mixte).
* **`PartialCellResolver` :** Traitement géométrique des grandes cellules ouvertes (conservation stricte de l'intersection $C \cap P$) et gestion des cellules de bordure de carte.
* **`CellSelection` :** Modèle de synthèse immuable regroupant les identifiants de cellules par catégorie et les masques des cellules partielles.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - PolygonMask P (Sprint 1)
  - CellGraph G (Sprint 3)

Sortie : 
  - Record immuable CellSelection
```

```java
public record CellSelection(
        Set<Integer> inside,
        Set<Integer> outside,
        Set<Integer> partial,
        Map<Integer, BinaryMask> partialMasks
) {
}
```

* **Principe d'isolation :** L'usage d'ensembles d'identifiants (`Set<Integer>`) évite tout couplage fort avec les instances mémoire de `Cell` et garantit une manipulation légère lors de la phase de reconstruction frontière.

---

## 🧪 4. Critères de Validation & Tests

* **Décision déterministe sur CA01 :**
  * Classification exacte des 130 cellules du territoire de référence (environ 21 cellules pleines `INSIDE` et 6 cellules partielles) ;
  * Découpe nette des grandes parcelles ouvertes sans débordement extérieur ;
  * Aucun faux-positif en zone extérieure lointaine (`OUTSIDE`).
