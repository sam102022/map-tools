# Spécification Technique V2 — Sprint 6 : Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points

**Date :** 04 octobre 2026  
**Auteur :** Équipe Photoshop Map Snapping  
**Statut :** Spécification Validée  
**Document cible :** `docs/superpowers/specs/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts-design.md`  
**Conformité ADR :** ADR-001 (100% Java standard sans lib native), ADR-002, ADR-004 (Records immuables), ADR-006 (Spécification préalable), ADR-007, ADR-008 (SRP), ADR-009, ADR-011 (Complexité cognitive ≤ 15), ADR-012 (Imports explicites), ADR-014 (Javadoc intégrale en FR).

---

## 1. Contexte & Enjeux Métier

Dans l'architecture V2 du détourage cartographique ([docs/SPRINTS_V2.md](../../SPRINTS_V2.md) et [docs/SPEC_V2_ALGORITHME.md](../../SPEC_V2_ALGORITHME.md)), le **Sprint 6** intervient immédiatement après la consolidation matricielle des frontières géodésiques (`ConsolidatedMask` du Sprint 5) et avant la rastérisation finale par supersampling vectoriel (`RenderResult` du Sprint 7).

### Les 3 défis géométriques majeurs résolus par le Sprint 6
1. **Élimination définitive de l'effet d'escalier (Marching Squares sub-pixel) :**  
   Les contours matriciels bruts présentent une pixellisation discrète. Le Sprint 6 extrait un contour vectoriel continu sub-pixel à isovaleur 0.5 le long de la ligne de niveau réelle, puis le rééchantillonne à pas uniforme ($\text{step} = 1.0\text{ px}$).
2. **Fin du rabotage des virages sans absorber les carrefours (LQR Robuste) :**  
   Le lissage gaussien linéaire classique tirait systématiquement les courbes vers l'intérieur (rabotage des virages) et créait des renflements au droit des carrefours. La **Régression Quadratique Locale Robuste (LQR)** ajuste localement un polynôme d'ordre 2 avec M-estimateur de Cauchy/Tukey : elle épouse fidèlement la courbure authentique des voies et traite les départs de rues adjacentes comme des anomalies statistiques (*outliers*), les effaçant sans déformer l'axe principal.
3. **Modélisation géométrique élégante des ronds-points (Ellipses & Hermite $C^1$) :**  
   Les carrefours giratoires ne sont plus tronqués ni comblés grossièrement : l'îlot central et l'anneau externe de circulation sont détectés et ajustés sous forme d'ellipses directes. L'arc extérieur est substitué dans le contour et raccordé aux tronçons de route amont et aval par des **splines cubiques d'Hermite garantissant une continuité tangentielle $C^1$ sans boucles**.

---

## 2. Architecture & Modèle de Données

Le package cible est **`com.sam102022.photoshop.v2.contour`** (avec un sous-package optionnel `com.sam102022.photoshop.v2.contour.math` pour les solveurs matriciels purs Java).

### 2.1 Modèle de Contour Lissé (`SmoothVectorContour`)
Le contrat d'échange officiel vers le Sprint 7 encapsule le contour vectoriel, les indices de coins détectés, les dimensions de la zone de calcul et la fenêtre de découpe d'origine :

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.List;

/**
 * Modèle immuable du contour vectoriel lissé sub-pixel.
 * Les points sont exprimés dans le repère local de la fenêtre de recadrage (CropWindow).
 *
 * @param points Coordonnées sub-pixels ordonnées formant la boucle fermée extérieure.
 * @param cornerIndices Indices des sommets identifiés comme angles vifs préservés.
 * @param width Largeur de la fenêtre locale de calcul.
 * @param height Hauteur de la fenêtre locale de calcul.
 * @param cropWindow Fenêtre de découpe englobante permettant le repositionnement dans l'image globale.
 */
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

### 2.2 Modèle Géométrique d'Ellipse (`EllipseModel`)
Paramétrage standard d'une ellipse euclidienne en 2D :

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;

/**
 * Modèle géométrique d'une ellipse plane.
 *
 * @param xc Abscisse du centre géométrique.
 * @param yc Ordonnée du centre géométrique.
 * @param a Demi-grand axe (a >= b).
 * @param b Demi-petit axe.
 * @param theta Angle d'orientation du demi-grand axe en radians [-pi, +pi].
 */
public record EllipseModel(
        double xc,
        double yc,
        double a,
        double b,
        double theta
) {
    public EllipseModel {
        if (a < b) {
            throw new IllegalArgumentException("Le demi-grand axe 'a' doit être >= au demi-petit axe 'b'.");
        }
    }

    /**
     * Convertit un point du repère local en coordonnées normalisées du cercle unité.
     */
    public PixelPoint toUnitCircle(PixelPoint p) {
        double dx = p.x() - xc;
        double dy = p.y() - yc;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double u = (dx * cos + dy * sin) / a;
        double v = (-dx * sin + dy * cos) / b;
        return new PixelPoint(u, v);
    }

    /**
     * Reconvertit un point du cercle unité normalisé vers le repère euclidien local.
     */
    public PixelPoint fromUnitCircle(double u, double v) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double xLocal = u * a;
        double yLocal = v * b;
        double x = xc + xLocal * cos - yLocal * sin;
        double y = yc + xLocal * sin + yLocal * cos;
        return new PixelPoint(x, y);
    }
}
```

### 2.3 Modèle de Rond-point Détecté (`Roundabout`)

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;

/**
 * Modèle immuable d'un carrefour giratoire détecté.
 *
 * @param center Centroïde de l'îlot central.
 * @param exteriorEllipse Ellipse ajustée sur le bord extérieur de la chaussée.
 * @param islandArea Surface en pixels de l'îlot central d'origine.
 * @param inlierRatio Taux de concordance des rayons lors de l'ajustement de l'anneau.
 */
public record Roundabout(
        PixelPoint center,
        EllipseModel exteriorEllipse,
        double islandArea,
        double inlierRatio
) {
}
```

### 2.4 Configuration Immuable du Lissage (`ContourSmoothingConfig`)

```java
package com.sam102022.photoshop.v2.contour;

/**
 * Configuration immuable des hyperparamètres de lissage vectoriel V2.
 * Les valeurs par défaut correspondent rigoureusement aux étalons Python V5.
 */
public record ContourSmoothingConfig(
        double resampleStep,     // 1.0 px
        int cornerWindowL,       // 80 px
        double cornerThreshold,  // 38.0 degrés
        double preSmoothSigma,   // 4.0 px
        double lqrSigma,         // 22.0 px
        double lqrScale,         // 3.0 px (seuil Cauchy/Tukey)
        int lqrIterations,       // 6 itérations
        double blendR0,          // 35.0 px (zone d'arête vive 100% brute)
        double blendR1,          // 95.0 px (fin du fondu Hermite smoothstep)
        double roundaboutZr      // 2.0 (zone d'influence relative du rond-point)
) {
    public static ContourSmoothingConfig defaultConfig() {
        return new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0
        );
    }
}
```

---

## 3. Pipeline Algorithmique Détaillé & Implémentation Pur Java

Le traitement complet est orchestré par **`ContourSmoothingEngine`** suivant la séquence séquentielle suivante :

```text
ConsolidatedMask (Mfill)
         │
         ▼ (Étape 1 : Marching Squares 0.5 + Resampling 1px)
SubpixelContourExtractor
         │
         ▼ (Étape 2 : L=80, Seuil 38°, Pre-smooth 4.0px)
CornerDetector  ───►  Liste des indices de coins immuables
         │
         ▼ (Étape 3 : Découpage par segments + LQR M-estimateur Cauchy 6 iters)
RobustLqrSmoother
         │
         ▼ (Étape 4 : Fondu cubique Smoothstep R0=35 / R1=95)
CornerPreservationBlender
         │
         ▼ (Étape 5 : Détection îlots + Ellipse directe + Sondage 240 rayons)
RoundaboutDetector
         │
         ▼ (Étape 6 : Raccordement d'arcs d'ellipses + Tangentes + Hermite C1)
HermiteSplineConnector
         │
         ▼
SmoothVectorContour
```

---

### Étape 1 : Extraction de Contour Sub-Pixel & Rééchantillonnage Curviligne (`SubpixelContourExtractor`)

1. **Comblement des trous intérieurs (`binaryFillHoles`) :**  
   Avant d'extraire la ligne de contour, le masque consolidé $M_c$ subit un comblement systématique des trous intérieurs résiduels pour garantir l'unicité de la frontière externe principale :
   $$M_{\text{fill}} = \text{fillHoles}(M_c)$$
2. **Marching Squares à isovaleur 0.5 :**  
   * On matérialise une grille de cellules carrées $2 \times 2$ formée par les centres des pixels avec une marge de 1 pixel (`pad = 1`).
   * Chaque cellule est indexée par un code binaire 4 bits correspondant aux 4 sommets ($v_0, v_1, v_2, v_3$).
   * L'intersection exacte de l'isovaleur 0.5 sur chaque arête est interpolée linéairement :
     $$t = \frac{0.5 - v_a}{v_b - v_a}$$
   * Les segments générés sont reliés pour former les anneaux fermés. On sélectionne **la plus longue boucle fermée continue** représentant le pourtour extérieur principal.
   * On soustrait l'offset de marge $-1.0\text{ px}$.
3. **Rééchantillonnage curviligne équidistant ($\text{step} = 1.0\text{ px}$) :**  
   * Calcul des distances euclidiennes cumulées le long du contour :
     $$d_0 = 0, \quad d_k = d_{k-1} + \sqrt{(x_k - x_{k-1})^2 + (y_k - y_{k-1})^2}$$
   * Longueur totale de l'arc : $D = d_N$.
   * Nombre de points équidistants réguliers : $M = \lfloor D / \text{step} \rfloor$.
   * Génération des abscisses cibles $s_j = j \cdot \frac{D}{M}$ ($0 \le j < M$) et interpolation linéaire unitaire sur $(x, y)$.

---

### Étape 2 : Détection des Coins et Angles Vifs (`CornerDetector`)

Pour distinguer un véritable carrefour d'une bosse parasite de chaussée :
1. **Pré-filtrage gaussien périodique léger ($\sigma_{\text{pre}} = 4.0\text{ px}$) :**  
   Application d'un noyau gaussien périodique sur le contour fermé pour éliminer le bruit haute fréquence de discrétisation sans déplacer les sommets macroscopiques :
   $$c_{\text{pre}}(i) = \frac{\sum_{k=-K}^K c((i+k) \pmod M) \cdot \exp(-0.5 (k/\sigma_{\text{pre}})^2)}{\sum_{k=-K}^K \exp(-0.5 (k/\sigma_{\text{pre}})^2)} \quad (K = \lfloor 4 \sigma_{\text{pre}} \rfloor)$$
2. **Vecteurs tangents sur fenêtre large ($L = 80\text{ px}$) :**  
   On évalue les vecteurs sécants amont et aval :
   $$v_1 = c_{\text{pre}}(i) - c_{\text{pre}}((i - L + M) \pmod M)$$
   $$v_2 = c_{\text{pre}}((i + L) \pmod M) - c_{\text{pre}}(i)$$
3. **Déviation angulaire :**  
   $$\theta(i) = \left| \text{atan2}(v_{1,x} v_{2,y} - v_{1,y} v_{2,x}, \; v_{1,x} v_{2,x} + v_{1,y} v_{2,y}) \right| \times \frac{180}{\pi}$$
4. **Sélection des sommets immuables :**  
   Un point $i$ est classé comme coin si :
   $$\theta(i) \ge \theta_{\text{corner}} \quad (38^\circ) \quad \text{et} \quad \theta(i) \ge \max_{k \in [-L, +L]} \theta((i+k) \pmod M)$$

---

### Étape 3 : Lissage Robuste LQR par Régression Polynomiale d'Ordre 2 (`RobustLqrSmoother`)

Le contour fermé est partitionné en $K$ segments délimités par les indices de coins ordonnés $[c_0, c_1, \dots, c_{K-1}]$ (si $K=0$, on traite la boucle complète comme un segment périodique unique).

Pour chaque segment $S = [P_0, \dots, P_{n-1}]$ de longueur $n$ :
1. **Garde-fou sur les segments courts :**  
   Si $n < 2.5 \times \sigma_{\text{lqr}}$ (soit $n < 55\text{ px}$), le segment est conservé dans son tracé brut sans modification pour éviter l'écrasement des détails étroits.
2. **Soustraction de la corde de base :**  
   On extrait la droite reliant le premier point $P_0$ au dernier point $P_{n-1}$ :
   $$\text{base}(t) = P_0 + \frac{t}{n - 1} (P_{n-1} - P_0) \quad (t \in [0, n-1])$$
   On calcule le signal résiduel 2D : $y(t) = S(t) - \text{base}(t)$.
3. **Régression Quadratique Locale Robuste (LQR) :**  
   * Demi-fenêtre d'ajustement : $k = \lfloor 3.5 \times \sigma_{\text{lqr}} \rfloor = 77\text{ px}$.
   * Pour $u \in [-k, +k]$, calcul du poids gaussien $g(u) = \exp(-0.5 (u/\sigma_{\text{lqr}})^2)$ et des 5 noyaux polynomiaux :
     $$K_p(u) = g(u) \cdot (-u)^p \quad (p \in \{0, 1, 2, 3, 4\})$$
   * Initialisation : poids $w(t) = 1.0$ et estimation initiale $\hat{y}(t) = y(t)$ pour tout $t \in [0, n-1]$.
   * **Boucle itérative (6 itérations) :**
     * Calcul des 5 convolutions locales pondérées :
       $$M_p(t) = \sum_{u=-k}^k w(t+u) \cdot K_p(u)$$
     * Pour chaque coordonnée $j \in \{x, y\}$ :
       $$B_{j, p}(t) = \sum_{u=-k}^k w(t+u) \cdot y_j(t+u) \cdot K_p(u) \quad (p \in \{0, 1, 2\})$$
     * En chaque point $t$, construction de la matrice symétrique $3 \times 3$ :
       $$A(t) = \begin{pmatrix} M_0(t) & M_1(t) & M_2(t) \\ M_1(t) & M_2(t) & M_3(t) \\ M_2(t) & M_3(t) & M_4(t) \end{pmatrix} + \lambda I \quad (\text{avec } \lambda = 10^{-3} M_0(t) + 10^{-9})$$
     * Résolution du système linéaire $3 \times 3$ par **formule explicite de Cramer** (aucun appel externe) pour déterminer le coefficient d'ordre 0 (valeur lissée au centre de la fenêtre) :
       $$\hat{y}_j(t) = c_0 = \frac{\det(A_{0 \leftarrow B_j})}{\det(A)}$$
     * Calcul de l'écart géométrique : $\text{dev}(t) = \sqrt{(y_x(t) - \hat{y}_x(t))^2 + (y_y(t) - \hat{y}_y(t))^2}$.
     * Mise à jour robuste de Cauchy/Tukey pour l'itération suivante :
       $$w(t) = \frac{1}{\left(1 + \left(\frac{\text{dev}(t)}{sc}\right)^2\right)^2} \quad (sc = 3.0)$$
4. **Reconstruction du segment :**  
   $$S_{\text{lissé}}(t) = \text{base}(t) + \hat{y}(t)$$

---

### Étape 4 : Préservation des Coins & Fondu Progressif Hermite (`CornerPreservationBlender`)

Pour garantir que les angles des carrefours restent parfaitement aigus :
1. Pour chaque point $i \in [0, M-1]$ du contour, on détermine la distance curviligne minimale au coin le plus proche :
   $$d_{\text{corner}}(i) = \min_{c \in \text{corners}} \min(|i - c|, M - |i - c|)$$
2. Application de l'interpolation douce cubique (*Smoothstep*) entre $R_0 = 35\text{ px}$ et $R_1 = 95\text{ px}$ :
   $$\alpha = \text{clamp}\left(\frac{d_{\text{corner}}(i) - R_0}{R_1 - R_0}, 0.0, 1.0\right)$$
   $$S(\alpha) = 3\alpha^2 - 2\alpha^3$$
3. Recombinaison :
   $$C_{\text{final}}(i) = C_{\text{brut}}(i) + S(\alpha) \cdot (C_{\text{lissé}}(i) - C_{\text{brut}}(i))$$
   * Si $d \le 35\text{ px}$, $\alpha = 0 \implies C_{\text{final}} = C_{\text{brut}}$ (préservation intégrale de l'arête).
   * Si $d \ge 95\text{ px}$, $\alpha = 1 \implies C_{\text{final}} = C_{\text{lissé}}$ (lissage LQR plein effet).

---

### Étape 5 : Détection et Ajustement d'Ellipse des Ronds-points (`RoundaboutDetector`)

Conformément à l'**ADR-001**, l'ajustement d'ellipse est réalisé en **100% Java pur standard** via l'algorithme direct de **Halir & Flusser (1998)** aux moindres carrés sous contrainte $4AC - B^2 = 1$ :

#### A. Algorithme d'Ajustement d'Ellipse Direct en Java (`AlgebraicEllipseFitter`)
Pour un ensemble de $N \ge 8$ points 2D $(x_i, y_i)$ :
1. Centrage des données pour la stabilité numérique :
   $$\bar{x} = \frac{1}{N} \sum x_i, \quad \bar{y} = \frac{1}{N} \sum y_i, \quad \tilde{x}_i = x_i - \bar{x}, \quad \tilde{y}_i = y_i - \bar{y}$$
2. Matrice de conception partitionnée :
   * $D_1 = [\tilde{x}_i^2, \; \tilde{x}_i \tilde{y}_i, \; \tilde{y}_i^2]$ ($N \times 3$)
   * $D_2 = [\tilde{x}_i, \; \tilde{y}_i, \; 1]$ ($N \times 3$)
3. Matrices de dispersion :
   $$S_1 = D_1^T D_1 \ (3 \times 3), \quad S_2 = D_1^T D_2 \ (3 \times 3), \quad S_3 = D_2^T D_2 \ (3 \times 3)$$
4. Matrice de contrainte :
   $$C_1 = \begin{pmatrix} 0 & 0 & 2 \\ 0 & -1 & 0 \\ 2 & 0 & 0 \end{pmatrix}, \quad C_1^{-1} = \begin{pmatrix} 0 & 0 & 0.5 \\ 0 & -1 & 0 \\ 0.5 & 0 & 0 \end{pmatrix}$$
5. Matrice réduite $3 \times 3$ :
   $$M = C_1^{-1} (S_1 - S_2 S_3^{-1} S_2^T)$$
6. Résolution analytique des valeurs/vecteurs propres de la matrice $3 \times 3$ $M$ :
   On isole le vecteur propre $u_1 = [A, B, C]^T$ associé à l'unique valeur propre positive $\lambda > 0$ (vérifiant $4AC - B^2 = 1$).
7. Complétion : $u_2 = [D, E, F]^T = -S_3^{-1} S_2^T u_1$.
8. Décentrage et extraction des paramètres géométriques $(x_c, y_c, a, b, \theta)$ :
   $$x_c = \bar{x} + \frac{2CD - BE}{B^2 - 4AC}, \quad y_c = \bar{y} + \frac{2AE - BD}{B^2 - 4AC}$$
   $$a = \sqrt{\frac{2(A x_c^2 + C y_c^2 + B x_c y_c - F)}{A + C - \sqrt{(A - C)^2 + B^2}}}, \quad b = \sqrt{\frac{2(A x_c^2 + C y_c^2 + B x_c y_c - F)}{A + C + \sqrt{(A - C)^2 + B^2}}}$$
   $$\theta = \frac{1}{2} \text{atan2}(B, A - C)$$

#### B. Sondage Radial et Ajustement de l'Anneau Externe
1. Pour chaque cellule de `CellLabelMap` vérifiant $40 \le \text{aire} \le 9000$ et $\text{solidité} \ge 0.90$ (non adjacente au bord de l'image) :
   * Ajustement de l'ellipse de l'îlot central. Rejet si $b/a < 0.55$ ou résidu $\text{RMS} > 1.0$.
2. De son centre $(c_x, c_y)$, émission de $n_{\text{ang}} = 240$ rayons équidistants vers la chaussée fermée $R_{\text{closed}}$ (Sprint 2) :
   * Pas radial $d += 0.5\text{ px}$ jusqu'à un rayon max de $90\text{ px}$.
   * Machine à états : traversée de la route (`st=0 -> st=1`), puis détection du bord extérieur (`st=1 -> st=0`).
   * Distance de sortie $ds[i] = d$.
3. Filtrage du **mode bas** (percentile 35) pour éliminer les rayons bifurquant dans les embranchements perpendiculaires de voies :
   $$\text{keep} = \{i \mid ds[i] \le \text{percentile}_{35}(ds) + 1.0\}$$
4. Ajustement robuste de l'ellipse extérieure en 8 itérations avec élimination des résidus $rr \ge \max(1.5, \text{percentile}_{60})$.
5. Validation géométrique par rapport au rayon théorique $iR = \sqrt{\text{aire}/\pi}$ :
   $$1.3 \times iR \le \min(a_e, b_e) \le \max(a_e, b_e) \le 4.5 \times iR + 8 \quad \text{et} \quad \max(a_e, b_e) \le 70\text{ px}$$

---

### Étape 6 : Raccordement $C^1$ par Splines Cubiques d'Hermite (`HermiteSplineConnector`)

Pour chaque rond-point validé :
1. **Test de proximité topologique :**
   * On projette le contour lissé dans le repère unitaire de l'ellipse : $\rho = \sqrt{u^2 + v^2}$.
   * Le rond-point est retenu s'il compte au moins 12 points avec $0.75 \le \rho \le 1.3$ et si son disque central recouvre au moins $20\%$ du territoire consolidé $M_{\text{fill}}$.
2. **Identification des points de contact $i_A$ et $i_B$ :**
   * On isole les indices du contour avec $\rho \le 2.0$.
   * On repère le plus grand saut d'indices le long du contour pour identifier l'entrée $i_A$ (début de l'emprise) et la sortie $i_B$ (fin de l'emprise).
3. **Sélection de l'arc extérieur :**
   * On évalue les deux sens de rotation possibles $\text{sgn} \in \{+1, -1\}$ le long de l'ellipse entre l'angle d'entrée et l'angle de sortie.
   * On sonde un arc légèrement expansé ($1.12 \times \text{arc}$) sur le masque du territoire pour choisir le côté extérieur qui ne traverse pas le cœur de la commune.
4. **Calcul des tangentes unitaires :**
   $$d_A = \text{unit}(C(i_A + 6) - C(i_A - 6)), \quad d_B = \text{unit}(C(i_B + 6) - C(i_B - 6))$$
   $$t_A = \text{unit}(\text{Arc}(3) - \text{Arc}(0)), \quad t_B = \text{unit}(\text{Arc}(\text{fin}) - \text{Arc}(\text{fin} - 3))$$
5. **Interpolation cubique d'Hermite :**
   Pour raccorder le point $P_0$ (tangente $T_0$) au point $P_1$ (tangente $T_1$) sur une corde de longueur $L = \|P_1 - P_0\|$ :
   $$H(t) = (2t^3 - 3t^2 + 1)P_0 + (t^3 - 2t^2 + t)T_0 L + (-2t^3 + 3t^2)P_1 + (t^3 - t^2)T_1 L \quad (t \in [0, 1])$$
   * **Garde-fou anti-boucle :** Si la déviation maximale de la spline par rapport à la corde droite dépasse $0.7 L + 2$, la transition retombe automatiquement sur un segment linéaire sécurisé.
6. **Épissure continue dans le contour :**
   Le tronçon brut entre $i_A$ et $i_B$ est remplacé par la chaîne continue :
   $$\text{Hermite}(A \to \text{Arc}_{\text{début}}) \cup \text{Arc d'ellipse} \cup \text{Hermite}(\text{Arc}_{\text{fin}} \to B)$$

---

## 4. Contrats d'Entrée / Sortie & Relations Inter-Sprints

### 4.1 Entrées
Pour permettre une détection irréprochable des ronds-points tout en traitant la géométrie matricielle consolidée, le composant d'orchestration **`ContourSmoothingEngine`** prend en entrée :
* **`ConsolidatedMask` (Sprint 5) :** Le masque binaire consolidé issu de l'expansion géodésique et du comblement des trous fermés locaux.
* **`RoadMask` (Sprint 2) :** Le masque des routes fermées $R_{\text{closed}}$ utilisé pour le sondage radial des rayons de chaussée.
* **`CellLabelMap` (Sprint 3) :** La matrice des étiquettes des cellules urbaines permettant d'identifier immédiatement les îlots centraux compacts candidats.
* **`CropWindow` (Sprint 3) :** La boîte englobante locale ($x_0, y_0, W, H$).
* **`ContourSmoothingConfig` :** Les hyperparamètres de lissage.

### 4.2 Sortie
* **`SmoothVectorContour` :** Le record immuable contenant la liste ordonnée des points sub-pixels $C(t)$, les indices de coins préservés, les dimensions locales et la `CropWindow` associée pour la restitution finale au Sprint 7.

---

## 5. Découpage en Classes & Responsabilités SRP (ADR-008)

1. **`SubpixelContourExtractor` :** Algorithme Marching Squares 2D à isovaleur 0.5, comblement de trous préalable et rééchantillonnage curviligne équidistant à pas 1.0 px.
2. **`CornerDetector` :** Pré-lissage périodique $\sigma=4.0$, évaluation des vecteurs sécants à $L=80\text{ px}$ et sélection des maxima d'angle $\ge 38^\circ$.
3. **`RobustLqrSmoother` :** Découpage en segments, soustraction de corde de base, moments gaussiens, régularisation et résolution du système $3 \times 3$ par Cramer, repondération de Cauchy/Tukey (6 itérations).
4. **`CornerPreservationBlender` :** Calcul des distances aux coins et modulation continue par Smoothstep cubique d'Hermite ($R_0=35, R_1=95\text{ px}$).
5. **`AlgebraicEllipseFitter` :** Résolution directe aux moindres carrés (Halir-Flusser) en Java standard avec décentrage, calcul de valeurs propres $3 \times 3$ et conversion en paramètres géométriques.
6. **`RoundaboutDetector` :** Filtrage des cellules candidates, sondage radial par 240 rayons sur la chaussée, ajustement robuste de l'anneau externe.
7. **`HermiteSplineConnector` :** Calcul des tangentes unitaires, interpolation cubique d'Hermite $C^1$ avec garde-fou anti-boucle et substitution vectorielle.
8. **`ContourSmoothingEngine` :** Façade d'orchestration globale assemblant la chaîne complète dans le respect de l'ADR-008 et limitant la complexité cognitive à 15 (ADR-011).

---

## 6. Métriques de Validation & Tests d'Acceptation

### A. Tests Unitaires Isolés
* **`MarchingSquaresTest` :** Vérification sur des formes géométriques synthétiques (carré parfait, disque) que l'extraction sub-pixel est exacte à $\pm 0.01\text{ px}$ et qu'aucun artefact en escalier n'apparaît.
* **`AlgebraicEllipseFitterTest` :** Vérification sur un nuage de points bruités générés à partir d'une ellipse connue $(x_c=100, y_c=200, a=40, b=25, \theta=\pi/4)$ : convergence vers les paramètres nominaux avec une erreur $< 1\%$.
* **`RobustLqrSmootherTest` :** Vérification sur un segment circulaire perturbé par une encoche perpendiculaire étroite (largeur 5 px, profondeur 20 px) : le lisseur LQR ignore totalement l'encoche et restitue l'arc de cercle sans aplatissement.
* **`HermiteSplineConnectorTest` :** Vérification de la continuité tangentielle $C^1$ (continuité de la dérivée première) et activation du repli linéaire si une boucle est volontairement provoquée.

### B. Test d'Intégration Pivot `CA01` (`Sprint6IntegrationTest`)
Sur le territoire de test pivot `CA01` ($3810 \times 2130\text{ px}$) :
* **Nombre de coins authentiques détectés :** Entre **10 et 16 coins** (carrefours à angle aigu/droit réels du secteur).
* **Détection des ronds-points :** **Exactement 1 rond-point** détecté et substitué dans le contour (le giratoire du boulevard sud-est).
* **Écart par rapport au contour étalon Python `contour_smooth.npy` :**
  * Distance de Hausdorff maximale $\le 2.5\text{ px}$ ;
  * Écart quadratique moyen (RMS) $\le 0.8\text{ px}$.
* **Temps de calcul total :** $\le 600\text{ ms}$ sur machine standard.
