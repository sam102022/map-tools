# Sprint 1 (V2) — Socle Géométrique, Projection Web Mercator & Rasterisation

**Statut :** ✅ Validé (02/10/2026)  
**Package cible :** `com.sam102022.photoshop.v2.geometry`  
**Documents associés :**
* Spécification : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Plan d'implémentation : [`docs/superpowers/plans/2026-10-02-v2-sprint-1-geometry-projection.md`](../superpowers/plans/2026-10-02-v2-sprint-1-geometry-projection.md)

---

## 🎯 1. Objectifs du Sprint

Transformer la géométrie géographique vectorielle contenue dans les fichiers d'entrée JSON (`outerRings`, `innerRings`, coordonnées GPS, `center`, `zoom`) en un masque binaire pixel `PolygonMask` sur la grille matricielle de l'image cartographique ($P[x, y] \in \{0, 1\}$).

---

## 📦 2. Livrables & Composants Clés

* **`GeoCoordinate(double lat, double lng)` :** Modèle immuable de coordonnées géodésiques WGS84.
* **`PixelPoint(double x, double y)` :** Modèle immuable de point dans le repère pixel local de l'image.
* **`MapContext(int width, int height, int zoom, GeoCoordinate center)` :** Contexte de prise de vue cartographique.
* **`TerritoryGeometry(List<List<GeoCoordinate>> outerRings, List<List<GeoCoordinate>> innerRings)` :** Géométrie vectorielle d'un territoire avec support des îlots et polygones à trous.
* **`WebMercatorProjection` :** Formules mathématiques de projection conforme EPSG:3857 projetant les coordonnées GPS vers les coordonnées pixels absolues puis relatives au centre de l'image.
* **`JsonTerritoryLoader` :** Parseur autonome JSON (Jackson) extrayant les métadonnées de la carte et les polygones du territoire.
* **`PolygonRasterizer` :** Rasteriseur Java2D haute précision projetant les anneaux vectoriels en un masque binaire `PolygonMask`.
* **`PolygonMask(int width, int height, BinaryMask mask)` :** Contrat officiel immuable représentant le polygone d'intention $P$.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - Fichier JSON de métadonnées et polygone (ex: 01_plan_avec_territoires.json)

Sortie : 
  - Record immuable PolygonMask (P[x, y] ∈ {0, 1})
```

```java
public record PolygonMask(
        int width,
        int height,
        BinaryMask mask
) {
}
```

---

## 🧪 4. Critères de Validation & Tests

* **Validation mathématique :** Vérification de la conformité EPSG:3857 de la projection Web Mercator sur des coordonnées étalons.
* **Test d'intégration pivot CA01 (`Sprint1IntegrationTest`) :**
  * Chargement du fichier réel `01_plan_avec_territoires.json` ;
  * Projection conforme à l'emprise sur l'image $3810 \times 2130$ ;
  * Surface rasterisée du polygone $P$ cohérente avec la vérité terrain (~1 184 000 px).
* **Suite de tests :** 15/15 tests réussis (100% de succès dans `com.sam102022.photoshop.v2.geometry`).
