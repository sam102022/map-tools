# Sprint 2 (V2) — Détection Colorimétrique des Routes & Fermeture Topologique

**Statut :** ✅ Validé (03/10/2026)  
**Package cible :** `com.sam102022.photoshop.v2.road`  
**Documents associés :**
* Spécification : [`docs/superpowers/specs/2026-10-03-v2-sprint-2-road-detection-design.md`](../superpowers/specs/2026-10-03-v2-sprint-2-road-detection-design.md)
* Plan d'implémentation : [`docs/superpowers/plans/2026-10-03-v2-sprint-2-road-detection.md`](../superpowers/plans/2026-10-03-v2-sprint-2-road-detection.md)

---

## 🎯 1. Objectifs du Sprint

Générer le masque binaire officiel `RoadMask` à partir du style de carte Google Maps contrasté (`05_style_contraste_sans_rien.png`) ou du flux vectoriel OSM, avec garantie de fermeture topologique étanche et respect strict de la géométrie routière réelle (*ne jamais inventer une route et ne jamais supprimer une route réelle sans justification*).

---

## 📦 2. Livrables & Composants Clés

* **`RoadMask(int width, int height, BinaryMask raw, BinaryMask closed)` :** Contrat de domaine immuable encapsulant le masque brut et le masque après fermeture topologique minimale.
* **`RoadDetector` :** Interface abstraite standardisant la détection raster de routes `detect(BufferedImage image)`.
* **`RoadDetectorStyle` :** Détecteur colorimétrique basé sur les règles de deltas chromatiques RVB bleu-gris issues de `script.py` :
  $$(B - R \ge 12) \land (B - G \ge 2) \land (B - G \le 22) \land (R < 228)$$
* **`RoadMaskCleaner` :** Nettoyeur morphologique assurant :
  1. Une fermeture minimale par croix 4-connexe ($r=1\text{ px}$) pour colmater les micro-coupures d'anti-aliasing ;
  2. Une ouverture $2 \times 2$ pour éliminer le micro-bruit et les artefacts isolés.
* **`RoadDetectorOsm` :** Rasteriseur vectoriel OSM autonome (parseur JSON récursif interne sans dépendance externe, projection via `WebMercatorProjection` et conversion d'échelle métrique locale).

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - Image cartographique de style contrasté (ex: 05_style_contraste_sans_rien.png)
  - Ou données vectorielles OSM

Sortie : 
  - Record immuable RoadMask (raw, closed)
```

```java
public record RoadMask(
        int width,
        int height,
        BinaryMask raw,
        BinaryMask closed
) {
}
```

---

## 🧪 4. Critères de Validation & Tests

* **Test d'intégration pivot CA01 (`Sprint2IntegrationTest`) :**
  * Comparaison pixel à pixel avec l'image témoin de référence `maps/road.png` ;
  * Taux de concordance mesuré : **99,9850%** (seuil exigé $\ge 99,5\%$) ;
  * Préservation intégrale des axes secondaires ;
  * Temps d'exécution : **~329 ms** (critère $\le 2000\text{ ms}$).
* **Suite de tests :** 15/15 tests réussis (100% de succès dans `com.sam102022.photoshop.v2.road`).
