# Sprint 5 (V2) — Reconstruction des Frontières Routières & Expansion Géodésique Matricielle

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.expansion`  
**Documents associés :**
* Spécification : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)

---

## 🎯 1. Objectifs du Sprint

Passer de la sélection de cellules (`CellSelection`), du graphe (`CellGraph`) et des routes frontières (`BoundaryRoads`) à un masque binaire consolidé **`ConsolidatedMask`**, en intégrant les routes frontières jusqu'à leur bordure extérieure réelle via une transformée de distance euclidienne (EDT) et une propagation géodésique bornée (MCP / Dijkstra), puis en comblant les îlots fermés compacts résiduels.

$$\text{RoadMask} \longrightarrow \text{RoadInterface} \longrightarrow \text{BoundaryRoad} \longrightarrow \text{Distance Transform (hw)} \longrightarrow \text{Geodesic Expansion} \longrightarrow \text{ConsolidatedMask}$$

---

## 📦 2. Livrables & Composants Clés

* **`BoundaryRoadExtractor` :** Extraction et classification des interfaces routières reliant une cellule `INSIDE` à une cellule `OUTSIDE` (ou séparant deux zones contiguës en mode `ZONE`).
* **`RoadDistanceTransform` :** Calcul de la carte de demi-largeur locale $hw(x, y)$ sur la chaussée par transformée de distance euclidienne (EDT) et filtrage du maximum local ($size = 41$).
* **`GeodesicRoadExpander` (MCP / Dijkstra) :** Propagation contrainte à la surface routière depuis le bord des cellules retenues avec coût géométrique borné :
  $$d_G \le 2 \times hw_{\max} + \varepsilon \quad (\text{avec } \varepsilon = 4\text{ px})$$
* **`MorphologicalConsolidator` :** Union $U = T \cup ext$, ouverture morphologique par élément structurant circulaire (disque de rayon $\rho = 5\text{ px}$) pour régulariser les indentations de la propagation.
* **`ResidualHoleResolver` :** Détection et comblement des îlots compacts résiduels fermés ($\text{surface} \le 15\,000\text{ px}$).
* **`ConsolidatedMask` :** Masque binaire étanche consolidé sur la boîte englobante locale.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - CellSelection (Sprint 4)
  - CellGraph (Sprint 3)
  - RoadMask (Sprint 2)
  - Mode d'opération (TERRITORY ou ZONE)

Sortie : 
  - Record immuable ConsolidatedMask
```

```java
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        int offsetX,
        int offsetY
) {
}
```

---

## 🚦 4. Différenciation des Modes `TERRITORY` vs `ZONE`

* **Mode `TERRITORY` (Détourage d'un territoire complet) :**
  Une interface reliant `INSIDE ── ROAD ── OUTSIDE` est absorbée **intégralement jusqu'au bord extérieur de la chaussée**.
* **Mode `ZONE` (Découpage de zones internes contiguës) :**
  Une interface reliant `ZONE A ── ROAD ── ZONE B` fait l'objet d'un **partage équitable le long de l'axe médian** de la chaussée pour éviter tout recouvrement illégal.

---

## 🧪 5. Critères de Validation & Tests

* **Intégration rigoureuse des routes de bordure :** Les `BoundaryRoads` sont couvertes jusqu'à leur lisière extérieure réelle.
* **Étanchéité :** Aucune fuite vers les cellules ou voies extérieures `OUTSIDE`.
* **Comblement :** Résolution complète des trous et cours intérieurs fermés ($\le 15\,000\text{ px}$).
