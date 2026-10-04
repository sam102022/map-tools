# Sprint 5 (V2) — Reconstruction des Frontières Routières & Expansion Géodésique Matricielle

**Statut :** ✅ Validé  
**Package cible :** `com.sam102022.photoshop.v2.expansion`  
**Documents associés :**
* Spécification Technique : [`docs/superpowers/specs/2026-10-04-v2-sprint-5-geodesic-expansion-design.md`](../superpowers/specs/2026-10-04-v2-sprint-5-geodesic-expansion-design.md)
* Plan d'Implémentation TDD : [`docs/superpowers/plans/2026-10-04-v2-sprint-5-geodesic-expansion.md`](../superpowers/plans/2026-10-04-v2-sprint-5-geodesic-expansion.md)
* Spécification Générale V2 : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Prototype Python Étalon : [`maps/python/snap_cells_prototype_v5.py`](../../maps/python/snap_cells_prototype_v5.py)

---

## 🎯 1. Objectifs du Sprint

Passer de la sélection discrète des cellules retenues ($T$, issu de `CellSelection` du Sprint 4) et du masque local de voirie ($R_c$, issu de `RoadMask` du Sprint 2) à un masque binaire consolidé, plein et étanche **`ConsolidatedMask`** ($M_c$) :
1. **Couvrir l'épaisseur de la chaussée limitrophe** jusqu'à sa bordure extérieure réelle via une transformée de distance euclidienne (EDT) et une propagation géodésique 8-connexe (MCP / Dijkstra) strictement bornée par la demi-largeur locale :
   $$d_G(p) \le 2 \times hw_{\max}(p) + \varepsilon \quad (\varepsilon = 4.0\text{ px})$$
2. **Régulariser les bords par ouverture morphologique** circulaire ($\rho = 5\text{ px}$) tout en **sanctuarisant les cellules intérieures $T$** :
   $$M_c^{\text{morpho}} = T \cup \text{open}(T \cup ext, \text{disk}(\rho))$$
3. **Combler sélectivement les cavités et îlots compacts intérieurs** ($\text{surface} \le 15\,000\text{ px}$) par inondation inverse du fond infini.

$$\text{RoadMask } R_c \longrightarrow \text{EDT } hw \longrightarrow \text{Max Filter } hw_{\max} \longrightarrow \text{Dijkstra Borné } ext \longrightarrow \text{Morpho (Sanctuaire } T) \longrightarrow \text{Comblement } \le 15\,000 \longrightarrow \text{ConsolidatedMask } M_c$$

---

## 📦 2. Livrables & Composants Clés

* **`EuclideanDistanceTransform` :** Calcul linéaire $O(N)$ en Java pur (algorithme de Meijster, Roerdink & Hesselink 2000 en 2 passes 1D séparables) de la distance euclidienne minimale de chaque pixel routier au bord de la chaussée $hw(x, y)$.
* **`LocalMaxFilter` :** Filtre glissant séparable horizontal/vertical en $O(N)$ (file monotone 1D de Lemire) calculant le maximum local $hw_{\max}(x, y)$ sur une fenêtre carrée $41 \times 41$ ($k = 20\text{ px}$).
* **`BoundedGeodesicExpander` :** Propagation géodésique 8-connexe Dijkstra (métrique cardinale $1.0$, diagonale $\sqrt{2}$) amorcée depuis $R_c \cap \text{dilate}(T, 2)$ et bornée par $2 \times hw_{\max} + \varepsilon$, garantissant l'étanchéité stricte aux carrefours extérieurs.
* **`MorphologicalConsolidator` :** Union $U = T \cup ext$, padding de sécurité $\rho + 2 = 7\text{ px}$, ouverture par élément structurant circulaire euclidien de rayon $\rho = 5\text{ px}$, et union préservant $T$ intact.
* **`ResidualHoleResolver` :** Détection des cavités intérieures fermées par inondation BFS inverse depuis les bordures de la ROI, et comblement sélectif des îlots compacts $\le 15\,000\text{ px}$.
* **`BoundaryRoadPartitioner` :** Attribution équitable des voies limitrophes partagées en mode `ZONE` le long de la ligne médiane (Voronoi géodésique à double front sans recouvrement).
* **`RoadBoundaryConsolidator` :** Orchestrateur de haut niveau assemblant la chaîne complète du Sprint 5.
* **`ConsolidatedMask` :** Contrat de domaine immuable fournissant le masque binaire local et sa boîte englobante de positionnement global (`CropWindow`).

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - RetainedMask T (binaire local, issu de CellSelection du Sprint 4)
  - CroppedRoadMask Rc (binaire local, issu de RoadMask + CropWindow du Sprint 2 & 3)
  - CropWindow cropWindow (dimensions et offsets du Sprint 3)
  - ExpansionConfig config (mode TERRITORY ou ZONE, hyperparamètres eps=4, size=41, rho=5, hole=15000)

Sortie : 
  - Record immuable ConsolidatedMask (M_c[x, y] ∈ {0, 1})
```

```java
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        CropWindow cropWindow
) {
    public int offsetX() { return cropWindow.x0(); }
    public int offsetY() { return cropWindow.y0(); }
}
```

---

## 🚦 4. Différenciation des Modes `TERRITORY` vs `ZONE`

* **Mode `TERRITORY` (Détourage d'un territoire complet) :**
  La chaussée mitoyenne $INSIDE \longleftrightarrow OUTSIDE$ est absorbée **intégralement jusqu'au bord extérieur de la route**. La borne de distance géodésique bloque la propagation dès la traversée de la voie, sans franchir les limites cadastrales extérieures.
* **Mode `ZONE` (Découpage de zones internes contiguës) :**
  Une interface reliant $ZONE_A \longleftrightarrow ROAD \longleftrightarrow ZONE_B$ fait l'objet d'un **partage équitable le long de la ligne de crête médiane** via un Voronoi géodésique à double front, garantissant l'invariance stricte de non-recouvrement ($ZONE_A \cap ZONE_B = \emptyset$).

---

## 🧪 5. Critères de Validation & Tests sur CA01

* **Concordance pixel étalon sur CA01 :**
  * Fixture de référence : `src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json` et `maps/road.png` ;
  * Masque témoin Python étalon : `maps/python/CA01_mask_v5.png` ;
  * Surface $M_c$ attendue : **1 215 383 px** (taux de concordance $\ge 99{,}5\%$) ;
  * Comblement des îlots compacts : Résolution intégrale des 3 îlots résiduels intérieurs ($\le 15\,000\text{ px}$).
* **Étanchéité topologique absolue :**
  * Débordement extérieur nul sur les parcelles classées `OUTSIDE` ;
  * Aucune fuite dans les rues perpendiculaires au-delà de la borne $2 \times hw_{\max} + \varepsilon$.
* **Budget de performance en Java pur (ADR-001) :**
  * Temps de calcul complet sur la ROI $1505 \times 1783$ ($2{,}68$ Mpx) : **$\le 1200\text{ ms}$** (seuil maximal admissible : $1500\text{ ms}$).
* **Suite de tests :** 100% de succès sur les tests unitaires et le test d'intégration pivot `Sprint5IntegrationTest`.
