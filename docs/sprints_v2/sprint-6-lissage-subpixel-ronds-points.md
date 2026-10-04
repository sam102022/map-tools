# Sprint 6 (V2) — Géométrie Sub-Pixel, Lissage Robuste LQR & Modélisation des Ronds-points

**Statut :** ✅ Validé & Terminé (04/10/2026)  
**Package cible :** `com.sam102022.photoshop.v2.contour`  
**Documents associés :**
* Spécification détaillée : [`docs/superpowers/specs/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts-design.md`](../superpowers/specs/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts-design.md)
* Plan d'implémentation : [`docs/superpowers/plans/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts.md`](../superpowers/plans/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts.md)
* Spécification générale : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Prototypes Python étalons : [`maps/python/snap_cells_prototype_v5.py`](../../maps/python/snap_cells_prototype_v5.py) et [`maps/python/roundabouts.py`](../../maps/python/roundabouts.py)

---

## 🎯 1. Objectifs du Sprint

Transformer le masque binaire consolidé (`ConsolidatedMask`) en un contour vectoriel continu lissé de haute fidélité (**`SmoothVectorContour`**) :
1. **Élimination de la pixellisation :** Extraction sub-pixel continue par *Marching Squares* (isovaleur 0.5) et rééchantillonnage curviligne uniforme ($\text{step} = 1.0\text{ px}$).
2. **Fin du rabotage des virages sans renflements de carrefours :** Lissage par **Régression Quadratique Locale Robuste (LQR)** avec M-estimateur de Cauchy/Tukey éliminant les rues transversales comme des valeurs aberrantes.
3. **Préservation des angles vifs :** Détection multi-échelles des coins et fondu progressif (*Hermite Smoothstep* cubique entre $35$ et $95\text{ px}$).
4. **Modélisation géométrique des ronds-points :** Ajustement direct d'ellipses (Halir-Flusser 100% Java pur), sondage radial à travers l'anneau routier et raccordement tangentiel $C^1$ par **splines cubiques d'Hermite**.

---

## 📦 2. Livrables & Composants Clés

### Composants & Algorithmes (ADR-008 & ADR-011)
* **`SubpixelContourExtractor` :** Comblement des trous intérieurs résiduels, Marching Squares 2D à isovaleur 0.5 sur grille interpolée, sélection de la boucle extérieure principale et rééchantillonnage curviligne à pas uniforme ($\text{step} = 1.0\text{ px}$).
* **`CornerDetector` :** Pré-filtrage gaussien périodique ($\sigma = 4.0\text{ px}$), calcul des déviations angulaires sur fenêtre sécante large ($L = 80\text{ px}$) et détection des sommets d'angles vifs ($\theta \ge 38^\circ$).
* **`RobustLqrSmoother` :** Découpage du contour en segments entre coins, soustraction de la corde directrice $P_0 \to P_1$, calcul des 5 moments gaussiens polynomiaux ($\sigma = 22\text{ px}$, fenêtre $k = 77\text{ px}$), résolution locale du système $3 \times 3$ par Cramer et actualisation itérative des poids de Cauchy/Tukey ($sc = 3.0$, 6 itérations).
* **`CornerPreservationBlender` :** Modulation continue du lissage par interpolation cubique d'Hermite (*Smoothstep* $3\alpha^2 - 2\alpha^3$) entre $R_0 = 35\text{ px}$ (arête brute à 100%) et $R_1 = 95\text{ px}$ (lissage plein effet).
* **`AlgebraicEllipseFitter` :** Ajustement direct d'ellipse aux moindres carrés en **100% Java standard** (méthode de Halir & Flusser sous contrainte $4AC - B^2 = 1$) avec décentrage barycentrique, résolution du système propre $3 \times 3$ et conversion en paramètres euclidiens $(x_c, y_c, a, b, \theta)$.
* **`RoundaboutDetector` :** Détection des îlots centraux ($40 \le \text{surface} \le 9000$, $\text{solidité} \ge 0.90$), sondage radial de 240 rayons sur la chaussée fermée $R_{\text{closed}}$, filtrage sur le mode bas (percentile 35) et ajustement itératif robuste de l'anneau extérieur.
* **`HermiteSplineConnector` :** Localisation des points de transition $i_A$ et $i_B$, calcul des tangentes unitaires de voie et d'ellipse, sélection de l'arc externe et raccordement $C^1$ par splines cubiques d'Hermite avec garde-fou anti-boucle (déviation $\le 0.7 L + 2$).
* **`ContourSmoothingEngine` :** Façade d'orchestration globale assemblant la chaîne séquentielle complète.

### Modèles de Données & Records Immuables (ADR-004)
* **`SmoothVectorContour` :** Contour sub-pixel ordonné, indices de coins, dimensions et fenêtre de découpe `CropWindow`.
* **`EllipseModel` :** Paramètres euclidiens d'ellipse $(x_c, y_c, a, b, \theta)$ et conversions vers/depuis le repère unitaire.
* **`Roundabout` :** Synthèse d'un giratoire (centroïde, ellipse extérieure, surface de l'îlot, taux d'inliers).
* **`ContourSmoothingConfig` :** Hyperparamètres immuables de lissage et de détection.

---

## 🔄 3. Contrat d'Entrée / Sortie Enrichi

Pour permettre la détection topologique et le sondage radial des ronds-points tout en traitant la géométrie consolidée :

```text
Entrées : 
  - ConsolidatedMask Mc (Sprint 5 : masque consolidé dans la CropWindow)
  - RoadMask (Sprint 2 : masque des routes fermées pour le sondage radial)
  - CellLabelMap (Sprint 3 : matrice des labels pour identifier les îlots candidats)
  - CropWindow (Sprint 3 : repère local ROI et offset d'origine)
  - ContourSmoothingConfig (hyperparamètres)

Sortie : 
  - Record immuable SmoothVectorContour
```

```java
public record SmoothVectorContour(
        List<PixelPoint> points,
        List<Integer> cornerIndices,
        int width,
        int height,
        CropWindow cropWindow
) {
    public SmoothVectorContour {
        points = List.copyOf(points);
        cornerIndices = List.copyOf(cornerIndices);
    }
}
```

---

## 🧪 4. Critères de Validation & Tests (Test Pivot `CA01`)

* **Validation unitaire des composants :**
  * Extraction sub-pixel exacte à $\pm 0.01\text{ px}$ sur formes de référence (disque, rectangle).
  * Ajustement d'ellipse Java pur avec erreur relative $< 1\%$ sur nuage de points bruités de test.
  * Robustesse LQR démontrée : rejet complet d'une encoche transversale étroite de 5 px sans affaissement de l'axe principal.
  * Continuité $C^1$ vérifiée sans boucle sur les transitions d'Hermite.
* **Test d'intégration pivot sur le Territoire CA01 ($3810 \times 2130\text{ px}$) :**
  * **Coins préservés :** Détection de **10 à 16 angles vifs authentiques** (arêtes vives des carrefours urbains).
  * **Ronds-points :** Détection et substitution de l'arc externe sur **exactement 1 rond-point** (le giratoire sud-est).
  * **Concordance avec l'étalon Python V5 (`contour_smooth.npy`) :**
    * Distance de Hausdorff maximale $\le 2.5\text{ px}$ ;
    * Écart quadratique moyen (RMS) $\le 0.8\text{ px}$.
  * **Performance :** Temps d'exécution total $\le 600\text{ ms}$ en Java standard.
