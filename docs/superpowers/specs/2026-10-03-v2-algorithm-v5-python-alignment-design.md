# Spécification de Design V2 — Alignement Algorithmique sur le Prototype Python V5

**Date :** 03 octobre 2026  
**Auteur :** Sam (Gemini CLI)  
**Statut :** Validé / En cours  
**Référence d'implémentation étalon :** `maps/python/snap_cells_prototype_v5.py`  
**Conformité ADR :** [ADR-001](../../adr/ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md), [ADR-002](../../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md), [ADR-004](../../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md), [ADR-007](../../adr/ADR-007-developpement-par-sprints.md), [ADR-008](../../adr/ADR-008-une-seule-responsabilite-par-classe.md), [ADR-011](../../adr/ADR-011-limitation-complexite-cognitive.md), [ADR-014](../../adr/ADR-014-javadoc-obligatoire-classes-methodes.md).

---

## 1. Contexte & Rétrospective du Prototype V5

Le prototype Python de calage par cellules topologiques a évolué à travers plusieurs itérations (`v2`, `v3`, `v4`, `v5`). L'analyse comparative entre `snap_cells_prototype_v4.py` et `snap_cells_prototype_v5.py` a mis en lumière pourquoi la version V5 produit un résultat nettement supérieur ("marche beaucoup mieux") :

1. **Fin du rabotage des courbes :** Le lissage gaussien linéaire classique (ou avec soustraction de corde droite de la V4) tirait systématiquement les contours vers le centre de courbure, "rabotant" les virages réels des routes.
2. **Élimination des amorces de rues perpendiculaires :** Les intersections et débuts de ruelles créaient des protubérances ou des encoches parasites le long de la voie principale.
3. **Modélisation géométrique des ronds-points :** Les îlots de ronds-points ne sont plus comblés de façon grossière par fermeture morphologique aveugle, mais modélisés par des ellipses tangentes raccordées de façon $C^1$ par des splines cubiques d'Hermite.
4. **Anti-aliasing par couverture géométrique exacte :** Remplacement définitif du flou gaussien du masque binaire par un supersampling vectoriel $\times 4$ ($SS=4$) avec filtrage boîte (*box filter*).

---

## 2. Découpage Révisé du Pipeline en 7 Sprints

Pour préserver le principe de responsabilité unique (SRP, ADR-008) et limiter la complexité cognitive (ADR-011), le pipeline de développement est articulé en 7 sprints incrémentaux :

```text
SPRINT 1 (Terminé)  : Géométrie & Projection (WebMercator -> PolygonMask P)
SPRINT 2 (Spécifié) : Détection des Routes & Fermeture Topologique (RoadMask R + Rclosed)
SPRINT 3            : Segmentation en Cellules (4-connexité) & CellGraph
SPRINT 4            : Moteur de Vote Topologique & Parcelles Ouvertes (CellSelection)
SPRINT 5            : Reconstruction des Frontières Routières & Expansion Géodésique (ConsolidatedMask Mc)
SPRINT 6            : Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points Hermite (SmoothVectorContour)
SPRINT 7            : Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 (RenderResult)
```

---

## 3. Spécification Mathématique du Lissage LQR Robuste (`robust_lqr`)

### 3.1 Motivation
Sur un segment de contour reliant deux angles vifs $P_0$ et $P_1$, on soustrait la corde linéaire :
$$base(t) = P_0 + t (P_1 - P_0) \quad \text{pour } t \in [0, 1]$$
Le résidu $res(t) = seg(t) - base(t)$ est soumis à une **Régression Quadratique Locale Robuste**.

### 3.2 Formulation
Pour chaque coordonnée $j \in \{x, y\}$ le long du segment à l'abscisse discrète $t \in [0, N-1]$ :
* On cherche les coefficients du polynôme local $\hat{y}_j(u) = c_0 + c_1 u + c_2 u^2$ sur la fenêtre $u \in [-k, +k]$ avec $k = \lfloor 3.5 \cdot \sigma \rfloor$ et $\sigma = 22.0\text{ px}$.
* Le noyau gaussien est $g(u) = \exp\left(-\frac{1}{2} \left(\frac{u}{\sigma}\right)^2\right)$.
* Les noyaux polynomiaux sont $K_p(u) = g(u) \cdot (-u)^p$ pour $p \in \{0, 1, 2, 3, 4\}$.
* Les moments matriciels locaux $M_p(t) = (w * K_p)(t)$ et vecteurs seconds membres $B_{j, p}(t) = ((w \cdot y_j) * K_p)(t)$ sont évalués par convolution 1D (avec fenêtres unilatérales naturelles aux extrémités du segment sans besoin de padding artificiel).
* On résout en chaque point le système linéaire $3 \times 3$ :
  $$\begin{pmatrix} M_0(t) & M_1(t) & M_2(t) \\ M_1(t) & M_2(t) & M_3(t) \\ M_2(t) & M_3(t) & M_4(t) \end{pmatrix} \begin{pmatrix} c_0 \\ c_1 \\ c_2 \end{pmatrix} = \begin{pmatrix} B_{j, 0}(t) \\ B_{j, 1}(t) \\ B_{j, 2}(t) \end{pmatrix}$$
* La valeur lissée est donnée par le terme constant $\hat{y}_j(t) = c_0$.

### 3.3 Repondération Robuste (M-Estimateur de Cauchy/Tukey)
À chaque itération ($1..6$, initialement $w(t) = 1$) :
* On calcule la déviation euclidienne :
  $$dev(t) = \sqrt{(y_x(t) - \hat{y}_x(t))^2 + (y_y(t) - \hat{y}_y(t))^2}$$
* On recalcule le poids de Cauchy :
  $$w(t) = \frac{1}{\left(1 + \left(\frac{dev(t)}{sc}\right)^2\right)^2} \quad \text{avec } sc = 3.0\text{ px}$$
* **Conséquence :** Les départs de voies transversales présentent un écart $dev(t) \gg 3.0\text{ px}$ et voient leur poids $w(t) \to 0$, ce qui les ignore complètement dans le lissage de l'axe principal !

---

## 4. Détection des Coins & Fondu Progressif (*Smoothstep*)

### 4.1 Détection des Angles Vifs
1. Un pré-lissage gaussien périodique léger ($\sigma_{\text{pre}} = 4.0\text{ px}$) est appliqué au contour fermé rééchantillonné.
2. Deux vecteurs tangents sur fenêtre large $L = 80\text{ px}$ mesurent l'orientation générale de part et d'autre :
   $$v_1 = c(s) - c(s - L), \quad v_2 = c(s + L) - c(s)$$
3. L'angle de déviation est calculé via le produit vectoriel et scalaire :
   $$\theta(s) = \left| \text{atan2}(v_1 \times v_2, v_1 \cdot v_2) \right| \times \frac{180}{\pi}$$
4. Un point est un coin si $\theta(s) \ge 38.0^\circ$ et constitue un maximum local strict sur $[-L, +L]$.

### 4.2 Fondu Hermite (*Smoothstep*)
Pour chaque point du contour, soit $d$ la distance curviligne minimale au coin le plus proche :
* Si $d \le R_0 = 35\text{ px}$ : le contour brut $c(s)$ est conservé intact (angle vif parfait).
* Si $d \ge R_1 = 95\text{ px}$ : le contour lissé LQR $out\_c(s)$ est appliqué à 100%.
* Entre $R_0$ et $R_1$ : interpolation douce cubique :
  $$\alpha = \frac{d - R_0}{R_1 - R_0}, \quad S(\alpha) = 3\alpha^2 - 2\alpha^3$$
  $$c_{\text{final}}(s) = c(s) + S(\alpha) \cdot (out\_c(s) - c(s))$$

---

## 5. Modélisation Géométrique des Ronds-points

### 5.1 Détection d'Anneau Routier
1. **Îlot intérieur :** Cellule non routière de surface $40 \le \text{area} \le 9000\text{ px}$ et de compacité $\ge 0.90$.
2. **Ajustement d'ellipse intérieur :** Ajustement direct (`DirectEllipseFitter`) produisant $(x_c, y_c, a, b, \theta)$.
3. **Sondage radial :** Projection de 240 rayons depuis $(x_c, y_c)$ vers l'extérieur pour identifier le passage chaussée $\to$ extérieur.
4. **Ellipse extérieure :** Ajustement robuste sur le 35e percentile des rayons (filtrant les axes entrants/sortants) avec validation des rayons $1.3 \cdot iR \le \min(a_e, b_e) \le 4.5 \cdot iR + 8$.

### 5.2 Raccordement $C^1$ par Splines Cubiques d'Hermite
Pour chaque rond-point traversé par le contour ($0.75 \le \rho \le 1.3$, recouvrement $> 20\%$) :
1. Détection des points de tangence $i_A$ et $i_B$.
2. Sélection de l'arc d'ellipse externe longeant le territoire.
3. Interpolation par splines cubiques d'Hermite :
   $$H(t) = (2t^3 - 3t^2 + 1)P_0 + (t^3 - 2t^2 + t)T_0 L + (-2t^3 + 3t^2)P_1 + (t^3 - t^2)T_1 L$$
   reliant la tangente de route ($d_A$) à la tangente de l'arc d'ellipse ($t_A$) à l'entrée, et de l'ellipse ($t_B$) à la route ($d_B$) à la sortie.
4. Remplacement continu du tronçon par l'assemblage : Spline d'entrée + Arc d'ellipse + Spline de sortie.

---

## 6. Rendu Sub-Pixel par Supersampling Vectoriel ($SS=4$)

Au lieu d'un flou matriciel qui épaissit les contours :
1. Le polygone vectoriel continu (`SmoothVectorContour`) est rasterisé sur une grille 4 fois plus grande ($4W \times 4H$) avec un décalage géométrique de $+0.5\text{ px}$.
2. Une réduction par filtre boîte (*box filter*) ramène chaque bloc $4 \times 4$ à un pixel nominal $[0..255]$ ou $[0.0, 1.0]$.
3. L'opacité obtenue est rigoureusement égale à la fraction de pixel recouverte par le territoire.

---

## 7. Contrats Java 21 Associés (Records)

```java
package com.sam102022.photoshop.v2.contour;

/**
 * Contrat de données immuable issu du Sprint 5 représentant le masque matriciel consolidé.
 */
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        int offsetX,
        int offsetY
) {}

/**
 * Contrat de données vectoriel issu du Sprint 6 représentant le contour sub-pixel lissé.
 */
public record SmoothVectorContour(
        List<PixelPoint> points,
        List<Integer> cornerIndices,
        int width,
        int height
) {}

/**
 * Livrable de production issu du Sprint 7.
 */
public record RenderResult(
        BufferedImage clipped,
        BufferedImage mask,
        BufferedImage overlay
) {}
```

---

## 8. Critères d'Acceptation & Validation

1. **Test Étalon CA01 :**
   * Exécution de bout en bout du pipeline sur `Territoire CA01` ($3810 \times 2130\text{ px}$) en moins de 5 secondes.
   * IoU géométrique $\ge 0.99$ par rapport à l'image témoin de référence `CA01_clipped_v5.png`.
   * Déviation de surface $< 0.5\%$.
2. **Préservation des angles :** Les carrefours anguleux détectés conservent un angle franc sans émoussement excessif.
3. **Continuité des virages :** Les longues courbes de chaussées ne présentent aucun aplatissement intérieur ni encoches de rues adjacentes.
4. **Fluidité des ronds-points :** Les ronds-points traversés présentent une continuité $C^1$ sans boucles ni discontinuités anguleuses.
