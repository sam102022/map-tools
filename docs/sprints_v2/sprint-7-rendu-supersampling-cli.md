# Sprint 7 (V2) — Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Intégration CLI

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.render` / `com.sam102022.photoshop.cli`  
**Documents associés :**
* Spécification : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Prototype Python étalon : [`maps/python/snap_cells_prototype_v5.py`](../../maps/python/snap_cells_prototype_v5.py)

---

## 🎯 1. Objectifs du Sprint

Produire les livrables graphiques finaux avec couverture continue par suréchantillonnage vectoriel ($SS=4$) et rendre le pipeline V2 accessible en ligne de commande (CLI) avec des critères de validation mesurables par rapport à l'étalon Python.

---

## 📦 2. Livrables & Composants Clés

* **`SupersampleRenderer` :** Rastérisation vectorielle haute résolution sur une grille agrandie $\times 4$ ($SS=4$) avec compensation de décalage de demi-pixel ($+0.5\text{ px}$, passage des centres aux bords de pixels), puis sous-échantillonnage par boîte (*box filter*) produisant un masque de couverture continue $\alpha \in [0.0, 1.0]$.
* **`ImageClipper` :** Assemblage RGBA 32-bit de l'image détourée finale (`clipped.png`), du masque alpha de découpe (`mask.png`) et de l'image de diagnostic avec tracé du contour extérieur rouge (`overlay.png`).
* **`V2CliRunner` :** Interface CLI de production supportant le drapeau `--v2` et l'ensemble des hyperparamètres documentés (`--hi`, `--lo`, `--eps`, `--rho`, `--sig`, `--corner`, `--r0`, `--l`, `--zr`).
* **`RenderResult` :** Contrat officiel regroupant les trois sorties images générées.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - SmoothVectorContour (Sprint 6)
  - Image cartographique source d'origine (RGBA)

Sortie : 
  - Record immuable RenderResult (clipped, mask, overlay)
```

```java
public record RenderResult(
        BufferedImage clipped,
        BufferedImage mask,
        BufferedImage overlay
) {
}
```

---

## 🧪 4. Critères de Validation & Tests

* **Performance globale :** Pipeline complet V2 exécuté en $\le 5$ secondes sur le `Territoire CA01`.
* **Qualité du rendu :**
  * Aucune fuite vers les cellules `OUTSIDE` ;
  * Les `BoundaryRoads` sont correctement intégrées jusqu'au bord externe ;
  * Les routes séparant deux zones respectent le partage médian en mode `ZONE` ;
  * Rendu visuellement et métriquement équivalent à la sortie étalon `CA01_clipped_v5.png` ;
  * **Critères quantitatifs mesurables :** $\text{IoU V2 vs référence Python V5} \ge 0.99$, écart de surface $< 0.5\%$.
