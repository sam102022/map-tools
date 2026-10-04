# Sprint 6 (V2) — Géométrie Sub-Pixel, Lissage Robuste LQR & Modélisation des Ronds-points

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.contour`  
**Documents associés :**
* Spécification : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Prototype Python étalon : [`maps/python/snap_cells_prototype_v5.py`](../../maps/python/snap_cells_prototype_v5.py) et [`maps/python/roundabouts.py`](../../maps/python/roundabouts.py)

---

## 🎯 1. Objectifs du Sprint

Transformer le masque binaire consolidé (`ConsolidatedMask`) en un contour vectoriel continu lissé de haute fidélité (**`SmoothVectorContour`**), en conservant les angles vifs authentiques, en éliminant les encoches d'intersections sans raboter les virages grâce à la régression quadratique locale robuste (LQR), et en substituant les ronds-points par des arcs d'ellipses parfaits raccordés par des splines cubiques d'Hermite.

---

## 📦 2. Livrables & Composants Clés

* **`SubpixelContourExtractor` :** Extraction de contour sub-pixel continu par *Marching Squares* à isovaleur 0.5 sur le masque consolidé avec trous comblés, extraction de l'anneau extérieur principal et rééchantillonnage curviligne équidistant ($\text{step} = 1.0\text{ px}$).
* **`CornerDetector` :** Détection des angles vifs et virages en épingle par déviation tangentielle sur fenêtre large ($L = 80\text{ px}$, seuil $\theta \ge 38^\circ$), figeant les sommets de coins et appliquant un fondu progressif (*Hermite Smoothstep* $3\alpha^2 - 2\alpha^3$ entre $R_0 = 35$ et $R_1 = 95\text{ px}$).
* **`RobustLqrSmoother` :** Régression quadratique locale robuste (LQR) le long des segments de contour :
  * Ajustement local d'un polynôme d'ordre 2 ($\hat{y}(u) = c_0 + c_1 u + c_2 u^2$) pondéré par un noyau gaussien ($\sigma = 22\text{ px}$) ;
  * Repondération itérative par M-estimateur de Cauchy/Tukey ($w = \frac{1}{(1 + (dev/sc)^2)^2}$, $sc=3.0$, 6 itérations) traitant les départs de rues transversales comme des valeurs aberrantes ;
  * Préservation intégrale des courbures authentiques des voies sans phénomène de rabotage ou d'écrasement ;
  * Prise en charge naturelle des fenêtres unilatérales aux extrémités sans artefacts de bord.
* **`RoundaboutDetector` :** Identification et modélisation géométrique des ronds-points :
  * Détection des îlots centraux ($40 \le \text{surface} \le 9000$, $\text{solidité} \ge 0.90$) et ajustement d'ellipse algébrique directe ;
  * Sondage radial (240 rayons) à travers l'anneau routier pour identifier le bord externe de la chaussée et ajustement robuste de l'ellipse extérieure sur le mode bas ;
  * Filtrage des ronds-points traversés par le contour du territoire ($0.75 \le \rho \le 1.3$, recouvrement $> 20\%$).
* **`HermiteSplineConnector` :** Découpage du contour aux points de contact $i_A$ et $i_B$, calcul des tangentes unitaires de chaussée et d'ellipse, sélection de l'arc extérieur et transition continue $C^1$ sans boucle via des splines cubiques d'Hermite.

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée : 
  - ConsolidatedMask Mc (Sprint 5)

Sortie : 
  - Record immuable SmoothVectorContour
```

```java
public record SmoothVectorContour(
        List<PixelPoint> points,
        List<Integer> cornerIndices,
        int width,
        int height
) {
}
```

---

## 🧪 4. Critères de Validation & Tests

* **Élimination des marches d'escalier :** Le contour obtenu est sub-pixel et continu.
* **Respect des angles de carrefours :** Préservation exacte des angles vifs sans arrondissement excessif.
* **Absence de rabotage :** Suivi fidèle des grandes courbes de routes sans aplatissement intérieur.
* **Raccordement $C^1$ des ronds-points :** Jonction visuelle parfaite des ronds-points sans cassure anguleuse ni boucle indésirable.
