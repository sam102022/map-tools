# V2 formelle de l’algorithme

Je propose de figer la V2 autour d'un principe : **le polygone fourni par le JSON définit l'intention du territoire ; les routes définissent les frontières possibles ; les cellules définissent la topologie ; le mode `TERRITORY` ou `ZONE` définit ensuite comment reconstruire la frontière finale.**

Cette V2 remplace donc définitivement le BFS à `snapDistance`, `fillSmallHoles` et les heuristiques de proximité.

---

# 1. Définition du problème

## Entrées

Le moteur reçoit :

```text
MapContext
 ├── image carte
 ├── largeur W
 ├── hauteur H
 ├── zoom Z
 └── centre / paramètres de projection

TerritoryGeometry
 ├── Polygon
 ├── MultiPolygon éventuel
 └── trous éventuels

ProcessingMode
 ├── TERRITORY
 └── ZONE

ProcessingOptions
 ├── seuils de décision
 ├── paramètres de route
 └── paramètres de rendu
```

La géométrie du territoire provenant du JSON devient la **source géométrique principale**.

L'image annotée n'est donc plus nécessaire pour déterminer le contour si le JSON est disponible.

---

# 2. Principe général

Le pipeline V2 est :

```text
             JSON
              │
              ▼
       Géométrie géographique
              │
              ▼
       Projection Web Mercator
              │
              ▼
       Polygone pixel P
              │
              │
Carte ────────┤
              ▼
        Détection routes
              │
              ▼
          RoadMask R + Rclosed
              │
              ▼
      Segmentation cellules (4-connexité)
              │
              ▼
          CellGraph G (Cells + RoadInterfaces)
              │
              ▼
       Vote par cellule
              │
              ▼
       CellSelection S (INSIDE / OUTSIDE / PARTIAL)
              │
       ┌──────┴──────┐
       ▼             ▼
   TERRITORY        ZONE
       │             │
       ▼             ▼
 Boundary roads   Zone boundaries
       │             │
       ▼             ▼
 Expansion       Internal roads
 géodésique MCP  / neutralisation (médiane)
       │             │
       └──────┬──────┘
              ▼
     ConsolidatedMask Mc (binaire 0/1)
              │
              ▼
  Extraction contour sub-pixel (Marching Squares 0.5)
              │
              ▼
  Détection des coins (L=80 px, >38°) + fondu smoothstep
              │
              ▼
  Régression Quadratique Locale Robuste (robust_lqr)
              │
              ▼
  Modélisation ronds-points (ellipses + splines d'Hermite C1)
              │
              ▼
      SmoothVectorContour
              │
              ▼
  Rastérisation vectorielle 4x (SS=4) + box filter
              │
              ▼
       CoverageMask continu [0.0, 1.0]
              │
              ▼
   Assemblage RGBA (clipped.png, mask.png, overlay.png)
```

---

# 3. Étape 0 — normalisation des entrées

Toutes les coordonnées géographiques sont converties dans un repère pixel commun.

On définit :

```java
record MapContext(
    int width,
    int height,
    int zoom,
    GeoCoordinate center
) {}
```

et :

```java
record PixelPoint(
    double x,
    double y
) {}
```

La projection est :

```text
GeoCoordinate
      ↓
Web Mercator monde
      ↓
coordonnées pixel
      ↓
coordonnées locales de l'image
```

### Règle

Toutes les étapes suivantes travaillent **exclusivement en coordonnées pixel**.

Cela évite de mélanger :

```text
lat/lng
pixels
zoom
dimensions image
```

dans les algorithmes.

---

# 4. Étape 1 — rasterisation du polygone

Le polygone géographique est projeté :

```text
Pgeo
 ↓
Ppixel
```

puis rasterisé :

```text
Ppixel
 ↓
PolygonMask
```

On obtient :

```text
P[x,y] ∈ {0,1}
```

avec :

```text
P = 1 → pixel couvert par le polygone
P = 0 → pixel extérieur
```

## Important

La rasterisation doit supporter :

- `Polygon`
- `MultiPolygon`
- `outerRing`
- `innerRing` / trous.

---

# 5. Étape 2 — création du `RoadMask`

On construit :

```text
R[x,y] ∈ [0,1]
```

où :

```text
0 = pas de route
1 = route certaine
```

et éventuellement des valeurs intermédiaires :

```text
0.25
0.50
0.75
```

pour les pixels anti-aliasés.

---

# 6. Détection des routes

La V2 prévoit trois stratégies.

```text
RoadDetector
 ├── STYLE
 ├── OSM
 └── HYBRID
```

## STYLE

Extraction depuis la carte de segmentation.

C'est le mode privilégié lorsque l'image de segmentation est disponible.

## OSM

Utilisation de la géométrie des routes.

OSM fournit alors :

```text
axes routiers
type
géométrie
```

mais pas nécessairement la largeur exacte affichée par Google.

## HYBRID

Fusion :

```text
Google RoadMask
       +
OSM topology
       ↓
RoadModel
```

C'est le mode de référence pour une version robuste.

---

# 7. Étape 3 — fermeture du masque routier

Le `RoadMask` brut peut contenir :

```text
anti-aliasing
petites interruptions
1 pixel de coupure
```

On applique uniquement une fermeture très faible :

```text
RoadMask
   ↓
closing(radius = 1 px)
   ↓
Rclosed
```

### Règle

Cette opération ne doit pas servir à "réparer" arbitrairement les routes.

Son seul rôle est de garantir que les frontières routières sont topologiquement fermées.

---

# 8. Étape 4 — segmentation en cellules

On considère :

```text
¬Rclosed
```

et on calcule ses composantes connexes en **4-connexité**.

```text
R = route

┌──────────────┐
│   Cell 1     │
│              │
└──────┐ ┌─────┘
       │R│
┌──────┘ └─────┐
│   Cell 2     │
│              │
└──────────────┘
```

On obtient :

```text
label[x,y]
```

où :

```text
0       = route
1..N    = cellule
```

---

# 9. Pourquoi 4-connexité

Il faut impérativement utiliser :

```text
4-neighbors
```

et non :

```text
8-neighbors
```

Sinon deux blocs qui se touchent uniquement par un coin pourraient devenir une seule cellule.

Exemple :

```text
┌─────┐
│  A  │
└─────┘
      ┌─────┐
      │  B  │
      └─────┘
```

Avec 8-connexité :

```text
A + B
```

pourraient être connectés artificiellement.

Avec 4-connexité :

```text
A ≠ B
```

---

# 10. Construction du `Cell`

Chaque composante devient :

```java
record Cell(
    int id,
    long area,
    Bounds bounds,
    PixelPoint centroid,
    double polygonCoverage,
    CellState state
) {}
```

avec :

```java
enum CellState {
    OUTSIDE,
    INSIDE,
    PARTIAL,
    AMBIGUOUS
}
```

---

# 11. Étape 5 — statistiques de chaque cellule

Pour chaque cellule `c` :

```text
area(c)
```

et :

```text
coverage(c)
=
nombre pixels cellule ∩ P
--------------------------------
nombre pixels cellule
```

Donc :

```text
coverage ∈ [0,1]
```

Exemple :

```text
Cell 42
area       = 82 451 px
covered    = 74 203 px

coverage   = 0.900
```

---

# 12. Étape 6 — décision des cellules

On définit deux seuils :

```java
record CellSelectionPolicy(
    double insideThreshold,
    double outsideThreshold
) {}
```

Par exemple :

```text
outsideThreshold = 0.05
insideThreshold  = 0.60
```

Mais ces valeurs deviennent des **paramètres de politique**, pas des constantes algorithmiques.

---

# 13. Règle de décision

```text
coverage < outsideThreshold
        ↓
     OUTSIDE

coverage >= insideThreshold
        ↓
     INSIDE

entre les deux
        ↓
     PARTIAL
```

Formellement :

```text
C < T_out
    → OUTSIDE

C ≥ T_in
    → INSIDE

T_out ≤ C < T_in
    → PARTIAL
```

---

# 14. Conserver la couverture exacte

Il est important de ne jamais perdre :

```text
coverage = 0.48
```

simplement parce que la cellule devient :

```text
PARTIAL
```

Le moteur doit conserver la valeur.

Elle sera utilisée pour :

- diagnostics ;
- GUI ;
- confiance ;
- traitement des grandes cellules ;
- décision utilisateur.

---

# 15. Étape 7 — gestion des cellules partielles

C'est ici que la V2 améliore fortement le prototype.

Une cellule partielle n'est pas systématiquement :

```text
OUTSIDE
```

ni systématiquement :

```text
INSIDE
```

Elle est classée selon son contexte.

---

## Cas A — petite cellule

Si :

```text
area < A_small
```

et qu'elle est partielle :

```text
PARTIAL
```

elle peut être traitée comme ambiguë.

---

## Cas B — grande cellule

Si :

```text
area >= A_large
```

alors la cellule peut représenter :

```text
champ
parc
zone ouverte
```

où aucune route ne permet de définir naturellement la frontière.

Dans ce cas :

```text
Cell ∩ P
```

est conservé.

---

# 16. Cellules ouvertes vers l'extérieur

Il faut également identifier les cellules qui touchent le bord de l'image.

```text
┌─────────────────────────────┐
│ Cell 1 ──────────────────── │
│                             │
│       routes                │
│                             │
│ Cell 2                      │
└─────────────────────────────┘
```

Une cellule touchant le bord n'est pas un bloc urbain fermé.

On lui attribue :

```java
boolean touchesImageBorder;
```

Cela sera utile pour éviter des décisions erronées sur les zones ouvertes.

---

# 17. Étape 8 — construction du `CellGraph`

C'est une nouveauté essentielle de la V2.

On construit :

```text
Cell A
   │
 Road 1
   │
Cell B
```

Donc :

```java
record CellAdjacency(
    int cellA,
    int cellB,
    int roadComponent
) {}
```

Le graphe contient :

```text
Cells
+
Road boundaries
```

---

# 18. Composantes routières

Le masque routier doit également être segmenté en composantes :

```text
RoadMask
   ↓
Road components
```

Une composante routière représente un tronçon topologique.

On peut alors obtenir :

```text
Cell 12
   │
   ├── Road 3 ── Cell 15
   │
   └── Road 8 ── Cell 22
```

---

# 19. Règle fondamentale du graphe

Une route est caractérisée par les cellules qu'elle sépare.

Exemple :

```text
Cell A ─── Road 5 ─── Cell B
```

Cela devient :

```java
RoadBoundary(
    roadId = 5,
    leftCell = A,
    rightCell = B
)
```

Cette information est beaucoup plus puissante que la simple couleur des pixels.

---

# 20. Étape 9 — sélection initiale du territoire

Après le vote :

```text
INSIDE
PARTIAL
OUTSIDE
```

on crée :

```java
CellSelection
```

avec :

```text
selectedCells
partialCells
ambiguousCells
```

---

# 21. Règle TERRITORY

Pour `TERRITORY` :

```text
une cellule INSIDE
        ↓
son territoire inclut
les routes qui la bordent
```

mais uniquement selon la topologie.

---

# 22. Détermination des routes frontières

Pour chaque route :

```text
Road R
Cell A
Cell B
```

on applique :

```text
A selected
B selected
        ↓
route interne
        ↓
candidate

A selected
B outside
        ↓
route frontière
        ↓
candidate

A outside
B outside
        ↓
route extérieure
        ↓
excluded
```

---

# 23. Cas particulier du bord de l'image

Si :

```text
A selected
B = outside/image
```

la route peut être une frontière extérieure.

Elle est donc candidate.

---

# 24. TERRITORY — expansion géodésique

Une fois les routes frontières identifiées :

```text
Selected Cells
       ↓
Boundary roads
       ↓
RoadMask
```

on détermine jusqu'où inclure chaque route.

On calcule :

```text
hw(x,y)
```

= demi-largeur locale estimée de la route.

Puis :

```text
distanceGeodesic(p)
```

depuis les cellules sélectionnées ou la frontière intérieure.

---

# 25. Condition d'inclusion

Un pixel routier `p` est inclus si :

```text
dG(p) ≤ 2 × hw(p) + ε
```

Mais cette formule doit être comprise comme une **borne de propagation**, pas comme la définition de la frontière.

La frontière finale doit être déterminée par la surface routière réellement détectée.

---

# 26. Amélioration V2 : arrêter la propagation par topologie

Le Dijkstra ne doit pas être autorisé à traverser arbitrairement :

```text
road intersection
```

pour partir dans une autre rue.

Il doit rester attaché à la composante routière identifiée comme frontière.

Donc :

```text
BoundaryRoadComponent
       ↓
propagation
       ↓
surface de cette composante
```

et non :

```text
n'importe quel pixel route voisin
```

---

# 27. Résultat TERRITORY

On obtient :

```text
T =
SelectedCells
+
BoundaryRoadSurface
```

mais :

```text
T ∩ NonBoundaryRoads = false
```

sauf si ces routes sont des routes internes explicitement absorbées.

---

# 28. Règle ZONE

`ZONE` est fondamentalement différente.

Pour deux cellules :

```text
Cell A = Zone 1
Cell B = Zone 2
```

et une route entre les deux :

```text
Zone 1
██████
═══════ Route
       Zone 2
```

la route reste :

```text
NEUTRAL
```

---

# 29. Route interne d'une même zone

Si :

```text
Cell A = Zone 1
Cell B = Zone 1
```

alors :

```text
Road(A,B)
```

est une route interne.

Elle peut être absorbée selon la politique :

```java
InternalRoadPolicy
```

---

# 30. Formalisation des routes ZONE

Pour chaque route :

```text
zone(A)
zone(B)
```

on applique :

```text
A = Z
B = Z
    ↓
INTERNAL

A = Z
B ≠ Z
    ↓
BOUNDARY / EXCLUDED

A ≠ Z
B ≠ Z
    ↓
OUTSIDE
```

C'est cette règle qui garantit l'absence de chevauchement.

---

# 31. Règle de non-chevauchement

Pour deux zones :

```text
Z1
Z2
```

on impose :

```text
Z1 ∩ Z2 = ∅
```

sur les pixels de territoire.

Et :

```text
BoundaryRoad(Z1,Z2)
```

reste neutre.

C'est une **invariante de l'algorithme**, pas seulement un résultat espéré.

---

# 32. Petites rues internes

Une petite rue interne peut être absorbée uniquement si :

```text
zone(leftCell) == zone(rightCell)
```

et éventuellement :

```text
roadWidth <= maxInternalRoadWidth
```

Donc :

```text
même zone
+
route fine
       ↓
absorber
```

mais :

```text
zones différentes
       ↓
ne jamais absorber
```

---

# 33. Ronds-points et modélisation géométrique

La V2 ne traite plus les ronds-points comme un simple comblement morphologique aveugle (`area < 15000`), mais comme des **primitives géométriques explicites (ellipses tangentes)** intégrées harmonieusement au contour vectoriel via des **splines cubiques d'Hermite**.

### A. Détection et ajustement d'ellipse sur l'îlot central
1. **Filtrage des candidats :** Dans les cellules non routières issues de la segmentation, on recherche les îlots fermés compacts vérifiant :
   $$40 \le \text{Surface} \le 9\,000\text{ px} \quad \text{et} \quad \text{Solidité} \ge 0.90$$
2. **Ajustement d'ellipse algébrique :** Un modèle d'ellipse direct (`EllipseModel`) est ajusté sur les points de contour de l'îlot central, produisant les paramètres $(x_c, y_c, a, b, \theta)$. Les candidats trop aplatis ($b / a < 0.55$) ou aux résidus excessifs ($RMS > 1.0$) sont rejetés.

### B. Sondage radial et détection du bord extérieur de l'anneau
De l'îlot central $(x_c, y_c)$, on projette $n_{\text{ang}} = 240$ rayons équidistants à travers la chaussée jusqu'à $90\text{ px}$ :
* Pour chaque angle, on identifie le passage dans la route puis la sortie de la chaussée vers l'extérieur.
* Les rayons traversant des branches de route perpendiculaires rayonnantes génèrent des distances anormalement élevées : un ajustement robuste itératif (mode bas, percentile 35) isole les points de l'anneau circulaire pur.
* Une ellipse extérieure $(x_e, y_e, a_e, b_e, \theta_e)$ est ajustée avec une tolérance stricte sur le rayon intérieur $iR = \sqrt{\text{Surface}/\pi}$ ($1.3 \cdot iR \le \min(a_e, b_e) \le \max(a_e, b_e) \le 4.5 \cdot iR + 8$).

---

# 34. Raccordement $C^1$ par Splines Cubiques d'Hermite

Lorsqu'un rond-point est validé, il est substitué dans le contour vectoriel de manière continue :

```text
               Tronçon de route A
               ───────────────────►
                                   \  Spline Hermite C1 (dA -> tA)
                                    \
                                ┌────┴────────────┐
                                │   Arc externe   │
                                │   de l'ellipse  │
                                │  du rond-point  │
                                └────┬────────────┘
                                    /
                                   /  Spline Hermite C1 (tB -> dB)
               ◄───────────────────
               Tronçon de route B
```

1. **Filtrage topologique :**
   * Le rond-point doit être longé par le contour extérieur ($0.75 \le \rho \le 1.3$) et son disque intérieur doit recouvrir au moins $20\%$ du territoire consolidé ($M_{fill}$).
2. **Découpage entrée/sortie :**
   * On identifie sur le contour ordonné les points d'entrée $i_A$ et de sortie $i_B$ délimitant l'arc à remplacer.
3. **Sélection de l'arc extérieur :**
   * Entre les deux sens de rotation possibles ($\text{sgn} \in \{+1, -1\}$), on sélectionne l'arc d'ellipse externe qui englobe le territoire sans s'auto-croiser.
4. **Continuité tangentielle d'Hermite :**
   * Les tangentes unitaires de la chaussée ($d_A, d_B$) et les tangentes de l'arc d'ellipse ($t_A, t_B$) sont calculées.
   * Deux transitions cubiques d'Hermite sont interpolées :
     $$H(t) = (2t^3 - 3t^2 + 1)P_0 + (t^3 - 2t^2 + t)T_0 L + (-2t^3 + 3t^2)P_1 + (t^3 - t^2)T_1 L$$
   * Un garde-fou géométrique prévient toute formation de boucle : si la déviation maximale dépasse $0.7 L + 2$, la transition retombe sur un segment linéaire sécurisé.
5. **Résultat :** Remplacement chirurgical du segment par :
   $$\text{Hermite}(A \to A_p) \cup \text{Arc d'ellipse}(A_p \to B_p) \cup \text{Hermite}(B_p \to B)$$

---

# 35. Cas des grandes zones sans routes

Exemple :

```text
┌──────────────────────────┐
│                          │
│          PARC            │
│                          │
│                          │
└──────────────────────────┘
```

Il n'existe aucune frontière routière interne.

Dans ce cas :

```text
cell ∩ polygon
```

reste la solution géométriquement correcte.

La V2 ne doit pas inventer une route inexistante.

---

# 36. Étape 10 — Construction du masque binaire consolidé

À l'issue de l'expansion géodésique et du comblement des trous :

```text
ConsolidatedMask[x,y] ∈ {0,1}
```

avec :

```text
1 = territoire consolidé (cellules sélectionnées + chaussée frontières jusqu'au bord externe)
0 = extérieur
```

Ce masque est calculé dans la boîte englobante locale avec marge de sécurité ($mg = 90\text{ px}$) pour optimiser les performances mémoires et CPU sur les cartes haute résolution.

---

# 37. Étape 11 — Extraction de contour sub-pixel et préservation des coins

Pour éliminer définitivement l'effet d'escalier inhérent à la grille discrète de pixels sans introduire de déformations arbitraires :

1. **Extraction sub-pixel par Marching Squares :**
   * On applique l'algorithme des Marching Squares à isovaleur $0.5$ (`skimage.measure.find_contours`) sur le masque binaire avec trous résiduels comblés (`Mfill`).
   * On extrait le contour fermé principal extérieur $c(t) = (y(t), x(t))$.
2. **Rééchantillonnage régulier :**
   * Le contour est rééchantillonné à pas constant uniforme ($\text{step} = 1.0\text{ px}$) le long de son abscisse curviligne $s$, garantissant une densité homogène de points pour les convolutions ultérieures.
3. **Détection des angles vifs et virages serrés :**
   * L'orientation globale de la voie est évaluée sur une fenêtre large $L = 80\text{ px}$ après un très léger pré-lissage gaussien ($\sigma = 4.0\text{ px}$) afin de ne pas être faussée par le bruit local.
   * L'angle de déviation directionnelle est mesuré :
     $$\theta(i) = |\text{atan2}(v_1 \times v_2, v_1 \cdot v_2)| \quad \text{avec } v_1 = c(i) - c(i-L) \text{ et } v_2 = c(i+L) - c(i)$$
   * Tout point dont l'angle dépasse le seuil $\theta_{\text{corner}} = 38^\circ$ et constitue un maximum local sur $[-L, +L]$ est étiqueté comme **coin immuable**.
4. **Fondu progressif (*Smoothstep*) :**
   * À proximité immédiate d'un coin ($d \le R_0 = 35\text{ px}$), le tracé brut d'origine est préservé à 100% pour garder l'arête vive du carrefour.
   * Entre $R_0 = 35\text{ px}$ et $R_1 = 95\text{ px}$, une interpolation douce cubique (*Hermite Smoothstep*) assure la transition :
     $$\alpha(d) = \text{clamp}\left(\frac{d - R_0}{R_1 - R_0}, 0, 1\right), \quad S(\alpha) = 3\alpha^2 - 2\alpha^3$$
     $$C_{\text{final}}(t) = C_{\text{brut}}(t) + S(\alpha) \cdot (C_{\text{lissé}}(t) - C_{\text{brut}}(t))$$

---

# 38. Étape 12 — Lissage par Régression Quadratique Locale Robuste (`robust_lqr`)

Le simple lissage gaussien linéaire présentait un défaut critique : il tirait systématiquement les courbes vers l'intérieur (rabotage des virages) et absorbait les excroissances des rues latérales.

La V2 introduit la **Régression Quadratique Locale Robuste** (`robust_lqr`) :

```text
Segment entre deux coins
         │
         ▼
Soustraction de la corde de base (P0 -> P1)
         │
         ▼
Régression polynomiale d'ordre 2 locale
  poids gaussiens (sigma = 22 px)
  + M-estimateur de Cauchy/Tukey (iters = 6)
         │
         ▼
Courbe authentique fidèlement suivie
Rues latérales transversales éliminées comme outliers !
```

### Formulation Mathématique
Sur chaque segment entre coins, pour le résidu par rapport à la droite joignant les extrémités :
* En chaque point, on ajuste le polynôme $\hat{y}(u) = c_0 + c_1 u + c_2 u^2$ sur la fenêtre $[-k, +k]$ avec $k = \lfloor 3.5 \cdot \sigma \rfloor$ et $\sigma = 22\text{ px}$.
* Les noyaux gaussiens sont $K_p(u) = g(u) \cdot (-u)^p$ avec $g(u) = \exp(-0.5 (u/\sigma)^2)$.
* Pour chaque itération ($1..6$) :
  * Calcul de la matrice $3 \times 3$ locale $A(t)$ par convolution du poids robuste $w$ avec les noyaux $K_p$.
  * Résolution des coordonnées lissées $\hat{y}(t) = A^{-1}(t) B(t)$.
  * Mise à jour du poids robuste de Cauchy/Tukey :
    $$w(t) = \frac{1}{\left(1 + \left(\frac{\|y(t) - \hat{y}(t)\|}{sc}\right)^2\right)^2} \quad \text{avec } sc = 3.0$$
* Grâce à cette repondération, toute bosse étroite (amorce d'une rue transversale perpendiculaire) génère un fort écart $\|y - \hat{y}\| \gg 3.0$ et son poids s'annule, lissant parfaitement la bordure de la route principale sans la déformer.

---

# 39. Étape 13 — Rendu Sub-Pixel par Supersampling Vectoriel ($SS=4$)

Au lieu d'appliquer un flou gaussien matriciel artificiel qui dilaterait arbitrairement le territoire :

1. **Rastérisation suréchantillonnée $\times 4$ ($SS=4$) :**
   * Le contour vectoriel final lissé (`SmoothVectorContour`) est rasterisé sur une image monochrome 4 fois plus grande ($4W \times 4H$).
   * Un décalage géométrique strict de $+0.5\text{ px}$ est appliqué pour faire correspondre le repère discret de bordure aux centres des pixels sub-pixels.
2. **Réduction par filtre boîte (*Box Filter*) :**
   * L'image agrandie est ramenée à la dimension nominale $(W, H)$ par moyenne locale de boîte.
   * On obtient une couche d'opacité continue $\alpha(x, y) \in [0.0, 1.0]$ correspondant rigoureusement à la **fraction de surface du pixel** recouverte par le polygone vectoriel.

---

# 40. Étape 14 — Rendu et Assemblage Final RGBA

Pour chaque pixel de la carte d'origine :

```text
RGB = image originale RGB
Alpha = round(alpha(x, y) * 255)
```

Résultat :

```text
RGBA 32-bit (clipped.png)
Masque 8-bit niveaux de gris (mask.png)
Overlay de contrôle avec contour rouge vif (overlay.png)
```

---

# 41. Les sorties de la V2

Je recommande :

```text
*_clipped.png
```

Image originale avec alpha.

```text
*_mask.png
```

Masque final.

```text
*_cells.png
```

Visualisation des cellules.

```text
*_roads.png
```

Masque routier.

```text
*_confidence.png
```

Heatmap de confiance.

```text
*_diagnostic.png
```

Vue combinée.

```text
*_metadata.json
```

Statistiques et paramètres.

---

# 42. Le diagnostic devient une partie du moteur

Le moteur doit produire :

```java
record ProcessingDiagnostics(
    int totalCells,
    int insideCells,
    int partialCells,
    int ambiguousCells,
    int roadComponents,
    int boundaryRoadComponents,
    long processingTimeMs
) {}
```

Ce n'est pas uniquement destiné au développeur.

Le GUI pourra afficher :

```text
Cellules :       130
Sélectionnées :   21
Partielles :       6
Ambiguës :         2
Routes :           48
Temps :          4.2 s
```

---

# 43. Gestion manuelle des cellules

La V2 introduit explicitement :

```java
enum CellOverride {
    AUTO,
    INCLUDE,
    EXCLUDE
}
```

Ainsi :

```text
AUTO
```

utilise l'algorithme.

```text
INCLUDE
```

force l'inclusion.

```text
EXCLUDE
```

force l'exclusion.

---

# 44. L'override ne doit pas modifier la géométrie

Si l'utilisateur clique :

```text
Cell 37 → INCLUDE
```

on relance :

```text
cell selection
        ↓
boundary detection
        ↓
territory reconstruction
```

mais on ne modifie jamais manuellement les pixels.

C'est important pour garder une géométrie cohérente.

---

# 45. Algorithme complet formalisé

On peut maintenant résumer la V2 ainsi :

```text
INPUT
  │
  ├── Map image
  ├── Map metadata
  └── Territory geometry
          │
          ▼
[1] Projection
          │
          ▼
[2] Polygon rasterization
          │
          ▼
[3] Road detection
          │
          ▼
[4] Road topology cleanup
          │
          ▼
[5] Cell segmentation
          │
          ▼
[6] Cell statistics
          │
          ▼
[7] Cell classification
          │
          ▼
[8] CellGraph construction
          │
          ▼
[9] Manual overrides
          │
          ▼
    ┌─────┴─────┐
    │           │
TERRITORY      ZONE
    │           │
    ▼           ▼
Boundary      Boundary
roads         roads
    │           │
    ▼           ▼
Geodesic      Internal-road
expansion     handling
    │           │
    └─────┬─────┘
          ▼
[10] Consolidated binary mask (Mc)
          │
          ▼
[11] Sub-pixel contour (Marching Squares 0.5) & Curvilinear resampling (1 px)
          │
          ▼
[12] Corner detection (L=80 px, >38°) & Hermite smoothstep blend (R0=35, R1=95)
          │
          ▼
[13] Robust Local Quadratic Regression (robust_lqr : sigma=22, sc=3.0, iters=6)
          │
          ▼
[14] Roundabouts geometric replacement (ellipses + C1 Hermite cubic splines)
          │
          ▼
[15] SmoothVectorContour
          │
          ▼
[16] Supersampling vectoriel 4x (SS=4) & box filter -> CoverageMask [0.0, 1.0]
          │
          ▼
[17] RGBA rendering (clipped.png, mask.png, overlay.png)
          │
          ▼
OUTPUT
```

---

# 46. Invariants à garantir

La V2 doit être conçue autour de plusieurs invariants.

### Invariant 1 — déterminisme

Même entrée :

```text
→ même sortie
```

Aucun traitement dépendant de l'ordre des pixels ou d'un parcours non déterministe.

---

### Invariant 2 — non-chevauchement ZONE

```text
∀ Z1 ≠ Z2 :
Z1 ∩ Z2 = ∅
```

---

### Invariant 3 — frontières routières

En `TERRITORY`, lorsqu'une route constitue la frontière :

```text
boundary ⊂ road boundary
```

---

### Invariant 4 — aucune invention de route

Une zone ouverte sans route ne doit pas être artificiellement "accrochée" à une route éloignée.

---

### Invariant 5 — les overrides sont déterministes

```text
INCLUDE
```

doit toujours produire le même résultat.

---

### Invariant 6 — rendu indépendant de la décision

Modifier :

```text
alpha blur
```

ne doit jamais modifier :

```text
selected cells
```

---

# 47. Paramètres V2

Le modèle de configuration V2 est immuable et modulaire :

```java
record ProcessingOptions(
    CellSelectionPolicy cellSelection,
    RoadDetectionOptions roadDetection,
    RoadExpansionOptions roadExpansion,
    ContourSmoothingOptions contourSmoothing,
    RoundaboutOptions roundabouts,
    InternalRoadPolicy internalRoads,
    RenderingOptions rendering
) {}
```

Et surtout :

**pas de `snapDistance` arbitraire.**

---

# 48. Paramètres de cellules

```java
record CellSelectionPolicy(
    double outsideThreshold,
    double insideThreshold,
    long smallCellArea,
    long largeCellArea
) {}
```

Les seuils de référence validés par le prototype V5 :

```text
outside = 0.05
inside  = 0.60
```

---

# 49. Paramètres de route et d'expansion

```java
record RoadExpansionOptions(
    double epsilon,       // eps = 4 px
    int morphoRadius,     // rho = 5 px
    long maxIslandHole    // 15 000 px
) {}
```

---

# 50. Paramètres de géométrie de contour, lissage LQR et rendu

```java
record ContourSmoothingOptions(
    double sigma,              // sigma = 22.0 px
    int iterations,            // iters = 6
    double cauchyScale,        // sc = 3.0 px
    double cornerThresholdDeg, // corner = 38.0°
    int cornerWindowL,         // L = 80 px
    double r0,                 // R0 = 35.0 px
    double r1                  // R1 = 95.0 px
) {}

record RoundaboutOptions(
    long minIslandArea,        // 40 px
    long maxIslandArea,        // 9000 px
    double minSolidity,        // 0.90
    int rayCount,              // 240
    double minOverlapRatio     // 0.20
) {}

record RenderingOptions(
    int supersamplingScale     // SS = 4
) {}
```

---

# 51. Ce que devient l'ancien algorithme

Il ne faut pas simplement supprimer le code.

Je le conserverais comme :

```text
LEGACY
```

et éventuellement comme fallback.

Architecture :

```text
Algorithm
├── CellBasedAlgorithm   ← V2
└── LegacyBfsAlgorithm   ← ancien
```

Cela permet de comparer les deux pendant la phase de validation.

---

# 52. Stratégie de validation de la V2

Pour chaque jeu de données :

```text
ancien résultat
nouveau résultat
référence attendue
```

Puis mesurer :

```text
IoU
boundary distance
area difference
```

Le but n'est pas nécessairement de reproduire l'ancien algorithme, mais de vérifier que les différences sont **expliquées**.

---

# 53. Les trois niveaux de vérité

La V2 introduit implicitement trois sources de confiance :

```text
Niveau 1
Géométrie JSON
     ↓
intention du territoire

Niveau 2
RoadMask
     ↓
géométrie physique de la carte

Niveau 3
CellGraph
     ↓
topologie
```

La décision finale est basée sur leur combinaison.

---

# 54. Architecture conceptuelle finale

Le projet peut maintenant être résumé par cette formule :

```text
               TERRITOIRE
                   │
                   ▼
             Géométrie P
                   │
                   │
             ┌─────┴─────┐
             │           │
          ROUTES       POLYGONE
             │           │
             ▼           ▼
          RoadMask    PolygonMask
             │           │
             └─────┬─────┘
                   ▼
                 CELLS
                   │
                   ▼
               CELL GRAPH
                   │
                   ▼
             DECISION ENGINE
                   │
            ┌──────┴──────┐
            ▼             ▼
        TERRITORY        ZONE
            │             │
            ▼             ▼
       OUTER ROADS    INTERNAL ROADS
            │             │
            └──────┬──────┘
                   ▼
              FINAL MASK
                   │
                   ▼
                 ALPHA
                   │
                   ▼
                 PNG
```

## Conclusion

Pour moi, **cette V2 est la bonne spécification algorithmique de référence** pour passer au développement Java.

Le changement le plus important par rapport au prototype n'est pas une optimisation de code : c'est l'introduction du **`CellGraph`**. C'est lui qui permet de transformer les règles actuellement exprimées sous forme d'heuristiques (`15000 px`, ouverture morphologique, propagation qui s'arrête à `2 × largeur`) en **règles topologiques explicites**.

Je ferais donc le développement dans cet ordre :

**`Projection → RoadMask → CCL → CellGraph → CellDecision → TERRITORY/ZONE → ConsolidatedMask → SubpixelContour (Marching Squares 0.5) → RobustLQR / Roundabouts (Hermite C1) → Supersampling (SS=4) → RenderResult (PNG RGBA)`**

et **je ne commencerais ni par Swing, ni par la vectorisation SVG, ni par l'optimisation**. Le premier objectif serait d'obtenir un moteur CLI Java 21 capable de reproduire proprement le cas CA01 avec un `CellGraph` inspectable et des tests unitaires à chaque étape.

---

# 55. Revue technique et diagnostic du passage au moteur Java

## 1. Diagnostic global

| Domaine | État actuel | Avis |
|---|---|---|
| Concept métier | 🟢 Clair | Très bon |
| Approche par cellules | 🟢 Prometteuse | À conserver |
| Ancien BFS `snapDistance` | 🔴 Fragile | À supprimer |
| Polygone vectoriel JSON | 🟢 Excellent | Doit devenir la source principale |
| Détection des routes par couleur | 🟠 Fragile | Bon fallback, pas vérité absolue |
| CCL des cellules | 🟢 Très bonne base | À conserver |
| Vote par cellule | 🟢 Très bonne idée | À formaliser |
| TERRITORY | 🟠 Prototype fonctionnel | À renforcer |
| ZONE | 🔴 Pas réellement implémenté | Gros chantier |
| Ronds-points | 🟠 Heuristique | À remplacer par une règle topologique |
| Anti-aliasing | 🟠 Correct visuellement | Peut être amélioré |
| Vectorisation | 🔴 Pas nécessaire pour le MVP | À repousser |
| GUI | 🟠 À concevoir autour des cellules | Important |
| Performance | 🟢 Très prometteuse | Java devrait être largement suffisant |
| Architecture | 🔴 À construire | Le Python est un prototype, pas une architecture |

La spécification actuelle décrit encore un pipeline basé sur extraction colorimétrique → BFS → RDP → Java2D. fileciteturn0file0L93-L114

Le prototype, lui, a déjà changé de paradigme : **polygone vectoriel → routes → cellules → vote → expansion géodésique**. C'est cette deuxième architecture qu'il faut officialiser.

---

# 2. Première décision importante : abandonner le "gribouillis" comme source de vérité

C'est probablement la modification architecturale la plus importante.

La spécification dit actuellement :

> extraction de la teinte verte pour identifier le territoire.

Mais le prototype démontre que le JSON contient déjà le territoire sous forme de polygone géographique.

Le code fait :

```python
ring = meta["territory"]["polygons"][0]["outerRings"][0]

pts = [
    (
        merc(p["lat"], p["lng"])[0] - cx + W/2,
        merc(p["lat"], p["lng"])[1] - cy + H/2
    )
    for p in ring
]
```

Donc le système possède déjà quelque chose de beaucoup plus fiable qu'une analyse d'image.

### Je définirais les sources ainsi

```text
                 JSON territoire
                       │
                       ▼
              Polygon géographique
                       │
                       ▼
                Projection pixel
                       │
                       ▼
             Masque de référence P
```

L'image `01_plan_avec_territoires.png` devient alors :

**un input visuel secondaire / fallback / diagnostic**, pas la source de vérité.

C'est une différence fondamentale.

---

# 3. Architecture cible

Je partirais sur une architecture Java 21 de ce type :

```text
application
│
├── cli
│
├── gui
│
└── usecase
      │
      ▼
domain
│
├── model
│   ├── MapContext
│   ├── GeoPolygon
│   ├── PixelPolygon
│   ├── RoadMask
│   ├── Cell
│   ├── CellSelection
│   ├── TerritoryMask
│   └── ZoneMask
│
├── segmentation
│   ├── CellSegmenter
│   ├── CellStatistics
│   └── CellSelector
│
├── routing
│   ├── RoadDetector
│   ├── RoadWidthEstimator
│   └── GeodesicExpansion
│
├── territory
│   └── TerritoryProcessor
│
├── zone
│   └── ZoneProcessor
│
└── rendering
    ├── AlphaRenderer
    ├── PngRenderer
    └── DiagnosticRenderer

infrastructure
│
├── image
│   ├── PngImageReader
│   ├── PixelBuffer
│   └── ImageWriter
│
├── json
│   └── TerritoryJsonReader
│
└── map
    └── MercatorProjection
```

L'objectif est d'éviter un énorme :

```java
MapProcessor.process(...)
```

qui finirait par contenir 1 500 lignes de traitement d'image.

---

# 4. Le modèle de données doit être explicite

Je créerais notamment :

```java
public record MapContext(
        int width,
        int height,
        int zoom,
        GeoCoordinate center
) {}
```

Puis :

```java
public record PixelPoint(
        double x,
        double y
) {}
```

et :

```java
public record GeoCoordinate(
        double latitude,
        double longitude
) {}
```

Le passage géographique → pixel doit être isolé :

```java
public interface MapProjection {

    PixelPoint project(
        GeoCoordinate coordinate,
        MapContext context
    );
}
```

Cela permettra notamment de tester indépendamment la projection Web Mercator.

---

# 5. La projection Web Mercator est critique

Le prototype utilise :

```python
s = 256 * 2**z
```

puis calcule `x/y`.

C'est correct comme principe, mais cette partie mérite un composant dédié en Java.

### Attention

Il faut tester :

- latitude proche des limites ;
- longitude ±180° ;
- zooms différents ;
- coordonnées situées autour du centre ;
- largeur/hauteur différentes ;
- éventuel `devicePixelRatio` ;
- cartes capturées avec dimensions CSS différentes des dimensions physiques.

Un test d'intégration essentiel serait :

```text
JSON polygon
     ↓
projection
     ↓
pixel polygon
     ↓
comparaison avec capture
```

avec une tolérance de quelques pixels.

---

# 6. Le vrai cœur du projet : les cellules

C'est ici que votre nouvelle approche devient intéressante.

Le prototype fait :

```python
cells, nc = ndi.label(~Rc)
```

puis :

```python
cov = ndi.sum(Pc, cells, idx) / area
```

C'est conceptuellement très bon.

On obtient :

```text
              ROUTE
────────────────────────────────

        Cellule 1
        ┌──────────────┐
        │              │
        │              │
────────┘              └────────
        │              │
        │ Cellule 2    │
        │              │
        └──────────────┘
```

Chaque cellule devient une **unité métier**.

C'est beaucoup plus robuste qu'un contour pixel.

---

# 7. Je modifierais toutefois le modèle de vote

Le prototype :

```python
HI = 0.6
LO = 0.05
```

puis :

```python
sel[1:] = cov >= HI
part[1:] = (cov > LO) & (cov < HI)
```

C'est fonctionnel, mais ces valeurs ne doivent pas devenir des constantes magiques.

Je créerais :

```java
public record CellDecision(
        CellState state,
        double coverage,
        long area
) {}
```

avec :

```java
enum CellState {
    OUTSIDE,
    INSIDE,
    PARTIAL,
    AMBIGUOUS
}
```

Et surtout :

```java
public record CellSelectionPolicy(
        double insideThreshold,
        double partialThreshold
) {}
```

---

# 8. Encore mieux : introduire une notion de confiance

Une cellule avec :

```text
coverage = 0.99
```

est évidente.

Une cellule avec :

```text
coverage = 0.51
```

ne l'est pas.

Le moteur devrait donc conserver :

```java
public record CellDecision(
        int id,
        double coverage,
        CellState state,
        double confidence
) {}
```

Par exemple :

```text
0.00 ───────────── 0.30 ───── 0.70 ───────────── 1.00
       extérieur        ambiguë                 intérieur
```

Le GUI pourra ensuite afficher les cellules ambiguës.

C'est beaucoup plus intéressant que de simplement produire une image finale.

---

# 9. Je déconseille de garder `LO = 0.05` comme règle générale

C'est l'un des points faibles du prototype.

Actuellement :

```python
T |= part[cells] & Pc
```

signifie :

> si une cellule possède seulement 5 % de couverture, on garde la partie du polygone qui la recouvre.

Cela peut être pertinent pour un grand champ ouvert.

Mais cela peut aussi produire des formes incohérentes.

Je préférerais distinguer les cellules selon leur **topologie et leur taille**.

Par exemple :

```text
petite cellule
     ↓
vote strict

grande cellule
     ↓
analyse partielle

cellule touchant le bord de l'image
     ↓
cas particulier

cellule entourée de routes
     ↓
bloc fermé

cellule ambiguë
     ↓
GUI / décision secondaire
```

---

# 10. Le problème principal restant : les routes

Le prototype fait :

```python
road = ((b-r)>=12) & ...
```

C'est efficace sur `05_style_contraste_sans_rien.png`.

Mais ce n'est pas encore un moteur de détection robuste.

La spécification dit d'ailleurs que la détection repose actuellement soit sur OSM, soit sur les contrastes de Google Maps. fileciteturn0file0L97-L99

### Je ferais trois niveaux

```text
                  RoadDetector
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
      Map Style      OSM        Hybrid
```

### Niveau 1 — Map Style

```java
ColorRoadDetector
```

Très rapide.

### Niveau 2 — OSM

```java
OsmRoadRasterizer
```

Pour disposer de la topologie.

### Niveau 3 — hybride

C'est probablement le meilleur mode production :

```text
Google road mask
       +
OSM geometry
       ↓
fusion / validation
       ↓
RoadMask
```

---

# 11. Attention à une distinction fondamentale

Il faut distinguer :

### Axe routier

```text
      ─────────────
           │
           │
```

de :

### Surface routière

```text
════════════════════
════════════════════
```

Votre algorithme a besoin de la **surface routière**, pas seulement des axes.

C'est précisément pour cela que la proposition de largeur locale est importante.

---

# 12. La largeur locale actuelle est encore trop heuristique

Le prototype :

```python
hw = ndi.distance_transform_edt(Rc)
hwm = ndi.maximum_filter(hw, size=41) * Rc
```

puis :

```python
gd <= 2*hwm + EPS
```

fonctionne, mais :

```text
41
4
```

sont encore des paramètres empiriques.

Il faudrait remplacer progressivement :

```text
magic numbers
```

par :

```text
RoadWidthModel
```

par exemple :

```java
public interface RoadWidthModel {

    double halfWidth(
        int x,
        int y,
        RoadMask roadMask
    );
}
```

---

# 13. Le Dijkstra est une bonne solution, mais attention aux performances

Le prototype utilise :

```python
MCP_Geometric
```

pour faire la propagation.

En Java pur, je ne partirais pas immédiatement sur un Dijkstra générique avec objets Java par pixel.

Sur :

```text
3810 × 2130
≈ 8,1 millions de pixels
```

il faut absolument éviter :

```java
Node
Edge
PriorityQueue<Node>
```

pour chaque pixel.

Cela provoquerait énormément d'allocations.

---

# 14. Structure mémoire Java recommandée

Utiliser des tableaux primitifs :

```java
byte[] road;
int[] cellLabels;
int[] distance;
byte[] territory;
float[] alpha;
```

ou éventuellement :

```java
boolean[]
int[]
float[]
```

selon les besoins.

Pour 8 millions de pixels, c'est parfaitement gérable.

Éviter :

```java
Pixel[][]

List<Pixel>

Cell[][]

Map<Pixel, ...>
```

dans le hot path.

---

# 15. Même remarque pour les cellules

La CCL doit être faite avec :

```java
int[] labels
```

et une pile :

```java
int[] stack
```

exactement comme l'idée de la spécification initiale.

Le code fourni dans la spécification est déjà proche de la bonne approche mémoire. fileciteturn0file0L67-L90

Je recommande même un **run-length / scanline CCL** si les performances deviennent critiques, mais ce n'est pas nécessaire pour le MVP.

---

# 16. TERRITORY : architecture que je recommande

Le mode territoire devrait être :

```text
Polygon P
   │
   ▼
Road mask R
   │
   ▼
Cell segmentation
   │
   ▼
Cell selection
   │
   ▼
Selected cells T
   │
   ▼
Boundary roads
   │
   ▼
Geodesic expansion
   │
   ▼
Outer road boundary
   │
   ▼
Territory mask
```

La grande idée est :

> **Le territoire ne doit pas chercher une route. Il doit identifier les cellules puis déterminer quelles routes font frontière.**

C'est beaucoup plus déterministe.

---

# 17. Je changerais aussi l'algorithme d'expansion

Actuellement :

```python
seeds = Rc & ndi.binary_dilation(T, iterations=2)
```

puis propagation dans les routes.

Le concept est bon.

Mais je préférerais explicitement identifier :

```text
SelectedCell
     ↓
boundary pixels
     ↓
adjacent road components
```

Puis :

```text
RoadComponent
     ↓
quelle cellule(s) touche-t-elle ?
```

On peut alors savoir :

```text
route entre deux cellules sélectionnées
→ incluse

route entre cellule sélectionnée et extérieur
→ incluse en TERRITORY

route entre deux cellules non sélectionnées
→ exclue
```

Cela apporte une compréhension topologique beaucoup plus forte.

---

# 18. ZONE : c'est actuellement le plus gros trou fonctionnel

La spécification définit bien la règle :

> la route séparant deux zones doit rester neutre.

fileciteturn0file0L55-L63

Mais le prototype fourni ne l'implémente pas réellement.

Il traite uniquement :

```python
T
ext
Mc
```

pour un territoire.

Il faut donc construire un vrai moteur de zones.

---

# 19. Architecture ZONE

Supposons :

```text
          ROUTE
════════════════════════
       │          │
       │ Zone A   │ Zone B
       │          │
════════════════════════
          ROUTE
```

On doit avoir :

```text
Zone A mask
██████████
██████████
──────────  ← route exclue
           
Zone B mask
           
──────────
```

Donc :

```java
ZoneMask processZone(
    ZonePolygon zone,
    RoadMask roads,
    CellGraph graph
)
```

avec une règle explicite :

```text
road between zone A and zone B
→ neutral
```

---

# 20. Je recommande de construire un `CellGraph`

C'est probablement la prochaine évolution architecturale la plus intéressante.

Au lieu de seulement avoir :

```java
int[] cellLabels
```

construire conceptuellement :

```text
Cell 1 ── Road 5 ── Cell 2
  │
  └──── Road 8 ──── Cell 7
```

avec :

```java
record Cell(
    int id,
    long area,
    double coverage,
    Bounds bounds
) {}
```

et :

```java
record CellAdjacency(
    int cellA,
    int cellB,
    int roadComponent
) {}
```

On obtient alors un véritable graphe topologique.

---

# 21. Cela simplifiera énormément les zones

Exemple :

```text
Cell A
  │
Road 1
  │
Cell B
```

Si :

```text
A = Zone 1
B = Zone 2
```

alors :

```text
Road 1 = frontière neutre
```

Mais :

```text
A = Zone 1
B = Zone 1
```

alors :

```text
Road 1 = rue interne
```

Et la règle métier devient triviale :

```java
if (sameZone(cellA, cellB)) {
    includeInternalRoad();
} else {
    excludeBoundaryRoad();
}
```

C'est beaucoup plus robuste qu'une fermeture morphologique aveugle.

---

# 22. Le `binary_opening` est actuellement dangereux

Le prototype fait :

```python
Mc = T | ndi.binary_opening(
    np.pad(U, pad),
    structure=disk(RHO)
)[pad:-pad,pad:-pad]
```

avec :

```python
RHO = 5
```

Cela fonctionne probablement sur CA01.

Mais une opération morphologique peut modifier la topologie.

Elle peut :

- supprimer une rue fine ;
- couper un passage ;
- déformer une intersection ;
- supprimer une petite zone ;
- modifier une frontière volontairement anguleuse.

Je ne mettrais donc pas cette opération au cœur du moteur géométrique.

Elle doit plutôt être :

```text
PostProcessor
```

et optionnelle.

---

# 23. Même critique pour `fill_small_holes`

La spécification actuelle utilise :

```text
surface <= 15 000 px
```

pour les îlots. fileciteturn0file0L97-L99

Et le prototype reprend cette logique :

```python
if comp.sum() <= 15000:
    Mc |= comp
```

C'est précisément le type de règle que je chercherais à éliminer.

Un rond-point n'est pas défini par :

```text
aire < 15000
```

mais par sa **topologie**.

---

# 24. Nouvelle règle pour les ronds-points

Je proposerais :

```text
Îlot non sélectionné
        │
        ▼
Est-il entièrement entouré par
une composante routière sélectionnée ?
        │
     ┌──┴──┐
    oui    non
     │      │
     ▼      ▼
  candidat  bloc normal
     │
     ▼
compact + voisinage cohérent ?
     │
     ▼
  inclusion
```

Encore mieux si OSM est disponible :

```text
OSM roundabout geometry
        ↓
identification explicite
```

L'aire maximale devient alors seulement un garde-fou.

---

# 25. L'anti-aliasing : du flou gaussien au supersampling vectoriel (SS=4)

Dans les premiers prototypes :

```python
alpha = ndi.gaussian_filter(
    Mc.astype(np.float32),
    0.8
)
```

Ce n'était pas réellement un calcul de couverture géométrique, mais un simple **flou du masque binaire** qui déformait l'épaisseur des voies et dilatait arbitrairement le territoire.

Le prototype V5 résout ce défi de façon éclatante via le **supersampling vectoriel $\times 4$** :

```text
SmoothVectorContour
        ↓
Rastérisation sub-pixel sur grille 4x (SS=4)
        ↓
Filtre boîte (box filter / moyenne de bloc)
        ↓
Couverture continue exacte α ∈ [0.0, 1.0]
```

Chaque pixel de bordure reçoit ainsi sa véritable fraction surfacique d'inclusion géométrique, garantissant un fondu sub-pixel parfait sans dilatation artificielle du masque.

---

# 26. La vectorisation sub-pixel et le lissage LQR : le saut qualitatif de la V5

L'approche raster pure (morphologie + flou) présentait une limite structurelle :
- Les marches d'escalier de la grille discrète restaient visibles ;
- Les carrefours étaient arrondis ou formaient des pointes disgracieuses ;
- Le lissage gaussien linéaire standard tirait systématiquement les courbes vers l'intérieur (rabotage des virages).

Le prototype V5 a démontré que la chaîne vectorielle fine était indispensable pour obtenir un résultat de qualité cartographique professionnelle :

```text
ConsolidatedMask (binaire)
        │
        ▼
Marching Squares à niveau 0.5 (skimage.measure.find_contours)
        │
        ▼
Rééchantillonnage curviligne uniforme (step = 1.0 px)
        │
        ▼
Détection des angles vifs (> 38°) + Fondu progressif smoothstep (R0=35, R1=95)
        │
        ▼
Régression Quadratique Locale Robuste (robust_lqr : suit les courbes sans rabotage, filtre les carrefours)
        │
        ▼
Modélisation des ronds-points (ellipses directes + splines cubiques d'Hermite C1)
        │
        ▼
SmoothVectorContour
        │
        ▼
Supersampling SS=4 -> Alpha [0..255]
```

Cette chaîne géométrique est désormais formalisée au sein du **Sprint 6**, préservant la netteté des carrefours et la fluidité des grands axes sans aucune complexité superflue côté rendu.

---

# 27. Le format de sortie est bien choisi

Les deux sorties :

```text
*_clipped.png
*_mask.png
```

sont pertinentes.

La spécification les définit clairement. fileciteturn0file0L118-L128

J'ajouterais néanmoins :

```text
*_diagnostic.png
*_cells.png
*_roads.png
*_confidence.png
*_metadata.json
```

Le `metadata.json` pourrait contenir :

```json
{
  "algorithmVersion": "2.0",
  "mode": "TERRITORY",
  "width": 3810,
  "height": 2130,
  "zoom": 17,
  "cells": 130,
  "selectedCells": 21,
  "partialCells": 6,
  "ambiguousCells": 2,
  "processingTimeMs": 4210
}
```

Très utile en production.

---

# 28. Le GUI devrait être centré sur les cellules

La spécification prévoit des sliders pour `snapDistance`. fileciteturn0file0L151-L157

Je supprimerais ce concept.

Le GUI devrait plutôt afficher :

```text
┌──────────────────────────────────────────┐
│              CARTE                       │
│                                          │
│   ┌─────────┐      ┌─────────────┐       │
│   │ CELL 12 │      │   CELL 13   │       │
│   │   94%   │      │     52%     │       │
│   └─────────┘      └─────────────┘       │
│                                          │
└──────────────────────────────────────────┘
```

Et permettre :

### Clic gauche

```text
INCLUDE
```

### Clic droit

```text
EXCLUDE
```

### éventuellement

```text
AUTO
```

Le moteur garde :

```java
CellOverride {
    AUTO,
    INCLUDE,
    EXCLUDE
}
```

C'est une excellente sécurité pour les cas ambigus.

---

# 29. Je mettrais une vraie notion de "preview"

Pipeline GUI :

```text
Chargement
   ↓
Projection
   ↓
Road detection
   ↓
Cells
   ↓
Preview
   ↓
Utilisateur valide/modifie
   ↓
Finalisation
   ↓
PNG
```

Le calcul des cellules doit être indépendant du rendu final.

Cela permettra de recalculer uniquement ce qui change.

---

# 30. Performance : le projet est parfaitement compatible avec Java pur

Pour :

```text
3810 × 2130
≈ 8,1 Mpx
```

je suis favorable à votre contrainte :

> Java 21 sans OpenCV.

La spécification vise déjà explicitement un socle Java pur. fileciteturn0file0L159-L163

Il faut cependant respecter trois règles :

### 1. tableaux primitifs

```java
byte[]
int[]
float[]
```

### 2. pas d'objet par pixel

### 3. éviter les copies d'images inutiles

Par exemple, éviter :

```java
BufferedImage → int[][] → Pixel[][] → ...
```

---

# 31. Pipeline mémoire recommandé

```text
BufferedImage
      ↓
int[] RGB
      ↓
byte[] roadMask
      ↓
int[] cellLabels
      ↓
float[] coverage/confidence
      ↓
byte[] territoryMask
      ↓
byte[] alpha
      ↓
BufferedImage output
```

On peut libérer les buffers intermédiaires dès qu'ils ne sont plus nécessaires.

---

# 32. Je recommande aussi un système de "ROI"

Le prototype le fait déjà :

```python
mg = 90
```

et recadre autour du territoire.

C'est une bonne optimisation.

Mais :

```text
90 px
```

ne devrait pas être codé en dur.

Je ferais :

```java
record ProcessingRegion(
    int x,
    int y,
    int width,
    int height
) {}
```

avec une marge configurable :

```java
ProcessingOptions.roiMargin()
```

---

# 33. Gestion des trous et multipolygones

Le prototype ne traite que :

```python
polygons[0]["outerRings"][0]
```

C'est un vrai problème.

Il faut prévoir :

```text
Polygon
 ├── outer ring
 ├── hole
 ├── hole
 └── ...
```

et potentiellement :

```text
MultiPolygon
 ├── Polygon A
 └── Polygon B
```

La spécification mentionne explicitement que les polygones multi-anneaux / à trous n'ont pas encore été testés.

Donc je considère ce point comme **fonctionnellement non couvert**.

---

# 34. Le masque du polygone doit être multi-anneaux dès le départ

Ne faites surtout pas :

```java
drawPolygon(...)
```

uniquement.

Utilisez une abstraction :

```java
PolygonMask rasterize(GeoPolygon polygon)
```

avec :

```text
outer ring → +1
hole       → -1
```

ou directement une `Path2D` avec une règle de winding appropriée.

---

# 35. Le pipeline final que je recommande

Je partirais sur ceci :

```text
                    INPUT
                      │
          ┌───────────┴───────────┐
          │                       │
     Map JSON                 Map image
          │                       │
          ▼                       ▼
    Geo Polygon              Road detector
          │                       │
          ▼                       ▼
   Mercator projection        RoadMask
          │                       │
          └──────────┬────────────┘
                     ▼
              Cell segmentation
                     │
                     ▼
                CellGraph
                     │
                     ▼
             Cell classification
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
     TERRITORY                 ZONE
          │                     │
          ▼                     ▼
  boundary roads          zone boundaries
          │                     │
          ▼                     ▼
 geodesic expansion      internal-road rules
          │                     │
          └──────────┬──────────┘
                     ▼
                Final Mask
                     │
                     ▼
              Alpha renderer
                     │
          ┌──────────┼──────────┐
          ▼          ▼          ▼
       clipped     mask     diagnostics
         PNG        PNG
```

---

# 36. Ce que je supprimerais de la spécification actuelle

Je supprimerais ou marquerais comme **legacy** :

### ❌ Extraction verte comme source principale

La donnée JSON est supérieure.

### ❌ `snapDistance`

Il appartient à l'ancien algorithme.

### ❌ BFS "chercher la route la plus proche"

Il faut raisonner en cellules.

### ❌ `fillSmallHoles <= 15000`

À remplacer par une règle topologique.

### ❌ RDP dans le pipeline principal

Pas nécessaire pour produire un PNG.

### ❌ Sliders `snapDistance`

Le GUI doit manipuler les cellules et éventuellement quelques paramètres avancés.

---

# 37. Ce que je conserverais

### ✅ Java 21

Très bon choix.

### ✅ Aucun OpenCV

Possible et cohérent.

### ✅ PNG RGBA

Très bon.

### ✅ masque alpha séparé

Très utile.

### ✅ images de diagnostic

Indispensables.

### ✅ mode CLI

Très pertinent pour automatiser.

### ✅ GUI Swing

Acceptable pour un outil interne.

### ✅ architecture `TERRITORY` / `ZONE`

Très bonne séparation métier.

---

# 38. Les tests à mettre en place

Il faut absolument créer un jeu de tests de référence.

### Niveau 1 — projection

```text
lat/lng → pixel
```

### Niveau 2 — rasterisation

```text
polygon → mask
```

### Niveau 3 — routes

```text
image → road mask
```

### Niveau 4 — cellules

```text
road mask → cell graph
```

### Niveau 5 — vote

```text
polygon + cells → selected cells
```

### Niveau 6 — TERRITORY

```text
selected cells → outer boundary
```

### Niveau 7 — ZONE

```text
zones → non-overlap + road exclusion
```

### Niveau 8 — rendering

```text
mask → alpha → PNG
```

---

# 39. Tests de non-régression particulièrement importants

Je constituerais un dossier :

```text
test-data/
  CA01/
  CA02/
  CA03/
  rond-point/
  carrefour/
  grande-route/
  petites-rues/
  zone-adjacente/
  trou/
  multipolygon/
  bord-image/
```

Et pour chaque cas :

```text
input/
expected/
diagnostic/
```

On pourra alors faire :

```bash
mvn test
```

et comparer les masques.

---

# 40. Ajouter une métrique de comparaison

Au lieu de vérifier uniquement :

```text
ça ressemble bien
```

il faut mesurer.

Par exemple :

```text
IoU
Boundary distance
Pixel difference
Area difference
```

Si un résultat de référence existe.

Exemple :

```text
IoU             >= 99 %
Boundary error  <= 1 px
Area deviation  <= 0.5 %
```

Les valeurs exactes devront être déterminées avec vos données de référence, mais le principe est important.

---

# 41. Une amélioration importante : séparer "détection" et "décision"

C'est probablement le meilleur principe d'architecture du projet.

### Détection

```text
Qu'est-ce qu'il y a sur la carte ?
```

→ routes  
→ cellules  
→ géométrie

### Décision

```text
Qu'est-ce qui appartient au territoire ?
```

→ vote  
→ règles métier

### Reconstruction

```text
Où doit passer exactement la frontière ?
```

→ route boundary

### Rendering

```text
Comment produire l'image ?
```

→ alpha / PNG

Cela donne :

```text
Detection
    ↓
Topology
    ↓
Business decision
    ↓
Geometry
    ↓
Rendering
```

Et chaque étage devient testable indépendamment.

---

# 42. Mon architecture Java finale

Je viserais quelque chose comme :

```text
com.photoshop.mapsnapping
│
├── application
│   ├── MapSnappingService
│   └── ProcessingRequest
│
├── domain
│   ├── geometry
│   ├── map
│   ├── road
│   ├── cell
│   ├── territory
│   └── zone
│
├── processing
│   ├── projection
│   ├── raster
│   ├── segmentation
│   ├── routing
│   ├── geodesic
│   └── alpha
│
├── infrastructure
│   ├── json
│   ├── png
│   └── filesystem
│
├── cli
│
└── gui
```

Et surtout, éviter que `domain` dépende de :

```text
BufferedImage
ImageIO
Swing
Jackson
```

Le domaine doit manipuler ses propres structures.

---

# 43. API principale

Je partirais sur une API extrêmement simple :

```java
public interface MapSnappingEngine {

    ProcessingResult process(
        ProcessingRequest request
    );
}
```

avec :

```java
public record ProcessingRequest(
        MapInput map,
        PolygonInput polygon,
        ProcessingMode mode,
        ProcessingOptions options
) {}
```

et :

```java
public enum ProcessingMode {
    TERRITORY,
    ZONE
}
```

---

# 44. Résultat

```java
public record ProcessingResult(
        AlphaMask mask,
        BufferedImage clippedImage,
        ProcessingDiagnostics diagnostics
) {}
```

avec :

```java
public record ProcessingDiagnostics(
        int cellCount,
        int selectedCellCount,
        int partialCellCount,
        int ambiguousCellCount,
        long processingTimeMs
) {}
```

Cela permet au CLI **et** au GUI de partager exactement le même moteur.

---

# 45. Roadmap que je recommande

Je ne chercherais surtout pas à tout implémenter d'un coup.

### Phase 1 — moteur de base

```text
JSON
 ↓
projection
 ↓
road mask
 ↓
cells
 ↓
vote
 ↓
ZONE simple
 ↓
PNG
```

Objectif : reproduire CA01.

---

### Phase 2 — TERRITORY

```text
CellGraph
 ↓
boundary road detection
 ↓
geodesic expansion
 ↓
outer road boundary
```

---

### Phase 3 — robustesse

```text
rond-points
carrefours
petites rues
grandes cellules
bord image
```

---

### Phase 4 — ZONE complète

```text
multi-zones
routes neutres
routes internes
non-overlap
```

---

### Phase 5 — GUI

```text
visualisation cellules
clic include/exclude
heatmap confiance
preview
export
```

---

### Phase 6 — optimisation

Seulement à ce moment :

```text
profiling
memory
parallelisation
fast CCL
fast EDT
```

---

# 46. Priorité des risques

Voici comment je classerais les risques actuels :

| Risque | Importance |
|---|---:|
| Détection routes dépendante d'un style | 🔴 Très élevée |
| ZONE non implémenté | 🔴 Très élevée |
| Multi-polygones / trous | 🔴 Élevée |
| Heuristique `15000 px` | 🔴 Élevée |
| Seuils `HI/LO` | 🟠 Élevée |
| Largeur route `41/4` | 🟠 Élevée |
| Dijkstra Java naïf | 🟠 Élevée |
| Anti-aliasing par Gaussian blur | 🟠 Moyenne |
| Vectorisation RDP | 🟡 Faible |
| GUI | 🟡 Moyenne |
| CLI | 🟢 Faible |

---

# 47. Verdict technique

**Je garderais l'idée générale du projet, mais je réécrirais la spécification autour de l'approche par cellules.**

Le changement de paradigme est :

```text
ANCIEN

gribouillis
    ↓
BFS
    ↓
route proche
    ↓
contour
```

vers :

```text
NOUVEAU

polygone vectoriel
       +
carte routière
       ↓
    cellules
       ↓
  vote topologique
       ↓
 frontière routière
       ↓
règle TERRITORY / ZONE
       ↓
   masque final
```

C'est beaucoup plus propre.

Et surtout, cela permet d'avoir quelque chose que l'ancien algorithme n'avait pas : **un modèle explicable de la décision**.

Quand le résultat est mauvais, le GUI pourra dire :

> « Cellule 37 : couverture 47 %, décision ambiguë »

au lieu de :

> « Le BFS s'est arrêté 12 pixels plus tôt. »

C'est un changement majeur en termes de maintenabilité.

---

## Ma recommandation pour la suite

Je ne commencerais **pas** immédiatement par coder toutes les classes Java.

Je ferais d'abord une **V2 formelle de l'algorithme**, avec notamment :

1. définition précise de `RoadMask` ;
2. définition de `CellGraph` ;
3. algorithme exact de sélection des cellules ;
4. algorithme exact `TERRITORY`;
5. algorithme exact `ZONE`;
6. gestion des ronds-points/carrefours ;
7. gestion des trous et multipolygones ;
8. calcul de confiance ;
9. contrat des sorties ;
10. stratégie de tests.

Ensuite seulement, on pourra transformer cela en **architecture Java 21 complète avec les classes, interfaces, records, algorithmes et tests JUnit 5**, sans reproduire les heuristiques fragiles du prototype.

Le prototype actuel constitue déjà une très bonne preuve de faisabilité : il utilise effectivement la projection du polygone JSON, la segmentation en composantes du non-route, le vote par couverture et l'expansion géodésique bornée. fileciteturn0file1L9-L17 fileciteturn0file1L39-L59

**En résumé : je classerais aujourd'hui le projet comme "POC algorithmique prometteur → architecture de production à formaliser", et non comme "prototype à simplement traduire de Python vers Java".** C'est précisément cette distinction qui évitera de figer les heuristiques actuelles dans le futur moteur Java.",{},{"_156":734,"_163":1224,"_54":1225,"_56":1225,"_167":1226,"_169":170,"_171":14,"_172":173,"_174":1227,"_176":177},"a669e4ba-0af8-4c96-bd5e-c5c6ebd3b0c6",[717],{"_678":679,"_174":1241},1790955398.398705,{"_673":1239,"_167":1240},{"_1228":1229,"_1230":1231,"_1232":1233,"_1234":1235,"_1236":1237,"_192":193,"_194":14,"_195":747,"_197":198,"_199":748,"_201":748,"_187":109,"_202":109,"_66":67,"_203":14,"_204":1222,"_1238":34,"_205":10},"reasoning_status","reasoning_ended","finished_duration_sec",10,"reasoning_start_time",1790955388.0482585,"reasoning_end_time",1790955398.3648345,"reasoning_recap_type","collapse","hide_inline_actions","reasoning_recap","Réfléchi pendant 10s",{},{"_156":1222,"_163":1245,"_54":1246,"_56":1246,"_167":1247,"_169":170,"_171":14,"_172":173,"_174":1248,"_176":177},"d33900b6-dc10-45e3-9df2-bfe6ea9d611f",[734],{"_678":679,"_174":1270},1790955398.393054,{"_673":1260,"_1260":1261,"_1262":1263},{"_1249":1250,"_1228":1251,"_1252":1253,"_1254":1255,"_1232":1233,"_192":193,"_194":14,"_195":747,"_197":198,"_199":748,"_201":748,"_187":109,"_202":109,"_66":67,"_203":14,"_204":1243,"_1238":34,"_205":10},"inline_cot_expandable_content",{"_1257":1258},"is_reasoning","tool_summary_type","container","tool_icons",[1256],"code","source_message_ids",[1259,1243],"d34efa3f-2219-48ae-8f4c-653901b35a7b","thoughts",[1264],"source_analysis_msg_id","da4fb99b-13a7-4cdd-bc14-772990eabf87",{"_1265":1266,"_167":694,"_1267":1268,"_1269":34},"summary","Révision du script Python complet","chunks",[],"finished",{},{"_156":1243,"_163":1273,"_54":1274,"_56":1274,"_167":1275,"_169":170,"_172":173,"_174":1276,"_176":177},[1222],{"_678":1283,"_1040":1284,"_174":1285},1790955398.055009,{"_673":674,"_675":1281},{"_192":193,"_199":748,"_201":748,"_204":1277,"_1278":-5,"_1279":-5,"_1228":1251,"_1232":1233,"_205":10,"_1280":34},"eedd1b1b-bd71-4dd3-9735-d65f1d2d5947","reasoning_titles","reasoning_title","is_redacted",[1282],"The output of this plugin was redacted.","tool","container.exec",{},{"_156":1259,"_163":1289,"_54":1290,"_56":1290,"_167":1291,"_169":170,"_171":14,"_172":173,"_174":1292,"_176":1284},"9436e092-ac21-4889-940e-6b037c172301",[1243],{"_678":679,"_174":1305},1790955394.237752,{"_673":1256,"_1302":1303,"_674":1304},{"_180":1293,"_182":34,"_183":1294,"_206":1295,"_187":109,"_1238":34,"_1296":34,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_204":1297,"_1278":1298,"_1279":1266,"_1228":1251,"_1254":1299,"_1232":1233,"_205":10,"_203":14},{"_50":669,"_670":1300},[],[],"disable_turn_actions","c8b5b4d0-28ca-4573-ae12-c9c22f54ecbf",[1266],[1256],[1301],200012,"language","unknown","bash -lc sed -n '1,220p' /mnt/data/ab8935ea-f39c-4bc7-8df2-49ef924a5594.py",{},{"_156":1287,"_163":1309,"_54":1310,"_56":1311,"_167":1312,"_169":170,"_171":14,"_172":173,"_174":1313,"_176":177,"_178":1314},"be315877-6bde-4bfc-bd07-413a5a46212b",[1259],{"_678":679,"_174":1326},1790955391.290523,1790955392.742038,{"_673":674,"_675":1325},{"_183":1315,"_185":1316,"_187":109,"_1317":34,"_1238":34,"_1296":34,"_1232":1233,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_203":14,"_204":1307,"_205":10},"commentary",[1318],[],"is_thinking_preamble_message",{"_1167":1319,"_1168":1320,"_1169":1170,"_174":1321},235,265,{"_50":1039,"_1040":1088,"_156":1089,"_224":1043,"_674":1124,"_1172":1322},{"_1174":687,"_1175":687,"_1176":1191,"_1178":1094,"_1179":-5,"_1180":1323,"_1046":-5,"_1059":1060,"_1047":1091,"_1069":-5,"_1070":-5,"_1071":-5,"_1072":-5,"_1073":-5,"_1074":-5,"_1075":-5,"_1076":-5,"_1182":-5,"_1183":-5,"_1184":-5,"_53":-5,"_1185":-5},[1127,1324],110,[694],{},{"_156":1307,"_163":1330,"_54":1331,"_56":1331,"_167":1332,"_169":170,"_171":14,"_172":173,"_174":1333,"_176":177},"fb1fecd6-668b-47e5-8cd6-11e2ef208dda",[1287],{"_678":679,"_174":1346},1790955391.285497,{"_673":1260,"_1260":1341,"_1262":1342},{"_1249":1334,"_1228":1251,"_1252":1335,"_1254":1336,"_1232":1233,"_192":193,"_194":14,"_195":747,"_197":198,"_199":748,"_201":748,"_187":109,"_202":109,"_66":67,"_203":14,"_204":1337,"_1238":34,"_205":10},{"_1257":1339},"files",[1338],"7b944a1d-6b58-41cd-8969-d77bf25226d6","api_tool",[1340],"e7671dd1-b875-4085-92b1-a3f91906b1dd",[1343],"2d8ca33d-ffce-4b95-8ac3-a00e9fb26c3a",{"_1265":1344,"_167":694,"_1267":1345,"_1269":34},"Examiné le fichier Markdown fourni",[],{},{"_156":1328,"_163":1350,"_54":1351,"_56":1351,"_167":1352,"_169":170,"_172":173,"_174":1353,"_176":177},"7e6cb2c9-53a8-4cab-badd-88886c270123",[1307],{"_678":1283,"_1040":1355,"_174":1356},1790955390.273572,{"_673":674,"_675":1354},{"_1232":1233,"_192":193,"_199":748,"_201":748,"_204":1348,"_691":34,"_205":10,"_1280":34},[1282],"functions.exec",{},{"_156":1348,"_163":1360,"_54":1361,"_56":1362,"_167":1363,"_169":170,"_171":14,"_172":173,"_174":1364,"_176":1355,"_178":1314},"77378b12-3d9d-4021-a56b-cd34880d8c5b",[1328],{"_678":679,"_174":1370},1790955390.02265,1790955390.194591,{"_673":674,"_675":1369},{"_180":1365,"_182":34,"_183":1366,"_206":1367,"_187":109,"_1232":1233,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_204":1358,"_691":34,"_205":10,"_203":14},{"_50":669,"_670":1368},[],[],[1301],[694],{},{"_156":1358,"_163":1374,"_54":1375,"_56":1375,"_167":1376,"_169":170,"_172":173,"_174":1377,"_176":177},"26978cd3-edd6-48c1-8900-e09dc0b6376c",[1348],{"_678":1283,"_1040":1355,"_174":1380},1790955389.883796,{"_673":674,"_675":1379},{"_1279":1378,"_1232":1233,"_192":193,"_199":748,"_201":748,"_204":1372,"_691":34,"_205":10,"_1280":34},"Examen du fichier Markdown fourni",[1282],{},{"_156":1372,"_163":1383,"_54":1384,"_56":1384,"_167":1385,"_169":170,"_172":173,"_174":1386,"_176":177,"_178":1314},[1358],{"_678":1283,"_1040":1388,"_174":1389},1790955389.77326,{"_673":674,"_675":1387},{"_691":34,"_1279":1378,"_1232":1233,"_192":193,"_199":748,"_201":748,"_204":1340,"_205":10,"_1280":34},[1282],"api_tool.call_tool",{},{"_156":1340,"_163":1393,"_54":1394,"_56":1394,"_167":1395,"_169":170,"_171":14,"_172":173,"_174":1396,"_176":1388},"bb156b8b-5819-4b23-9e90-3b7f6ffb18b7",[1372],{"_678":679,"_174":1399},1790955389.660975,{"_673":674,"_675":1398},{"_1278":1397,"_1279":1378,"_1232":1233,"_192":193,"_199":748,"_201":748,"_204":1391,"_205":10,"_1280":34},[1378],[1282],{},{"_156":1391,"_163":1403,"_54":1404,"_56":1405,"_167":1406,"_169":170,"_171":14,"_172":173,"_174":1407,"_176":1355,"_178":1314},"fe0f6f71-88e7-48ec-93d3-76614c3acae3",[1340],{"_678":679,"_174":1413},1790955388.775512,1790955389.020169,{"_673":674,"_675":1412},{"_180":1408,"_182":34,"_183":1409,"_206":1410,"_187":109,"_1232":1233,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_204":1401,"_691":34,"_205":10,"_203":14},{"_50":669,"_670":1411},[],[],[1301],[694],{},{"_156":1401,"_163":1417,"_54":1418,"_56":1419,"_167":1420,"_169":170,"_171":14,"_172":173,"_174":1421,"_176":177,"_178":1314},"560e8d41-6ca1-45f3-8bad-ec2f0df2bd22",[1391],{"_678":679,"_174":1425},1790955388.339228,1790955388.609151,{"_673":674,"_675":1424},{"_183":1422,"_185":1423,"_187":109,"_1317":34,"_1238":34,"_1296":34,"_1232":1233,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_203":14,"_204":1415,"_205":10},[],[],[694],{},{"_156":1415,"_163":1429,"_54":1430,"_56":1430,"_167":1431,"_169":170,"_171":34,"_172":687,"_174":1432,"_176":177},"2e1e48c4-c168-4803-ac49-bee78ea25a29",[1401],{"_678":695,"_174":1434},1790955388.295203,{"_673":674,"_675":1433},{"_689":690,"_691":34,"_692":14,"_187":109,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_204":1427,"_205":10,"_203":14},[694],{},{"_156":1427,"_163":1438,"_54":1439,"_56":1439,"_167":1440,"_169":170,"_172":173,"_174":1441,"_176":177},"d00246bf-5b91-4792-ad7a-ac9857f42f8d",[1415],{"_678":695,"_174":1443},1790955388.294729,{"_673":674,"_675":1442},{"_691":34,"_187":109,"_192":193,"_195":747,"_197":198,"_199":748,"_201":748,"_202":109,"_66":67,"_204":1436,"_205":10,"_203":14},[694],{},{"_156":1436,"_163":1447,"_54":1448,"_56":1449,"_167":1450,"_169":170,"_172":173,"_174":1451,"_176":177},"b9a3481b-c0cb-4a3a-bc7b-18f317144bd8",[1427],{"_678":731,"_174":1455},1790955389.872,1790955456.7865,{"_673":674,"_675":1453},{"_1452":34,"_195":747,"_726":-5,"_199":748,"_201":748,"_205":10,"_203":14},"followups_v2_followup_clicked",[1454],"Fais-moi une revue technique complète du projet",{},{"_156":1445,"_163":1459,"_54":1460,"_56":1461,"_167":1462,"_169":170,"_171":34,"_172":173,"_174":1463,"_176":177,"_178":179},"86c161ad-4e2c-4bcb-9aa1-b941c0dd687b",[1436],{"_678":679,"_174":1487},1790955361.530767,1790955364.554653,{"_673":674,"_675":1485},{"_180":1464,"_182":34,"_183":1465,"_206":1466,"_185":1467,"_187":109,"_190":1468,"_192":193,"_194":34,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_203":34,"_204":1457,"_205":10},{"_50":669,"_670":1484},[1480],[1471,1472],[],[],"6a947d1b-c5a9-4eed-bed8-677a55cb0518","dc0b3624-36d4-4eba-af5a-36af57706dee",{"_212":1149,"_214":1476,"_216":1477,"_47":-5,"_50":1039,"_1040":1088,"_156":1089,"_224":1043,"_1044":1152,"_1046":-5,"_1047":1091,"_1049":-5,"_1050":1478,"_1052":-5,"_1053":-5,"_1054":-5,"_1055":1479,"_1057":-5,"_1058":-5,"_1059":1060},{"_50":211,"_212":1473,"_214":1474,"_216":1475,"_47":1473,"_218":1454},"revue technique complète du projet",379,413,288,318,{"_1069":-5,"_1070":-5,"_1071":-5,"_1072":-5,"_1073":-5,"_1074":-5,"_1075":-5,"_1076":-5},{"_1061":687,"_1062":1094,"_1064":687,"_1065":1155,"_1067":1120},{"_1167":1476,"_1168":1477,"_1169":1170,"_174":1481},{"_50":1039,"_1040":1088,"_156":1089,"_224":1043,"_674":1152,"_1172":1482},{"_1174":687,"_1175":687,"_1176":1191,"_1178":1094,"_1179":-5,"_1180":1483,"_1046":-5,"_1059":1060,"_1047":1091,"_1069":-5,"_1070":-5,"_1071":-5,"_1072":-5,"_1073":-5,"_1074":-5,"_1075":-5,"_1076":-5,"_1182":-5,"_1183":-5,"_1184":-5,"_53":-5,"_1185":-5},[1155,1120],[672],[1486],"J’ai bien le document de spécifications ainsi que le prototype Python. Le point important est que le prototype confirme déjà l’approche **polygone vectoriel → cellules → vote → expansion géodésique**, mais il reste plusieurs choix à solidifier avant de porter cela proprement en Java 21. fileciteturn0file0L93-L114

Je peux maintenant partir de ces éléments pour faire une **revue technique complète du projet** : incohérences entre la spécification et le prototype, algorithme cible, architecture Java 21, gestion `TERRITORY`/`ZONE`, performances sur 3810×2130, et plan d'implémentation étape par étape.",{},{"_156":1457,"_163":1490,"_54":1491,"_56":1491,"_167":1492,"_169":170,"_172":173,"_174":1493,"_176":177},[1445],{"_678":1283,"_1040":1338,"_174":1495},1790955361.523852,{"_673":674,"_675":1494},{"_691":14,"_192":193,"_199":1470,"_201":1470,"_204":1063,"_205":10,"_1280":34},[1282],{},{"_156":1063,"_163":1498,"_54":1499,"_56":1499,"_167":1500,"_169":170,"_172":173,"_174":1501,"_176":177},[1457],{"_678":1283,"_1040":1338,"_174":1503},1790955361.510392,{"_673":674,"_675":1502},{"_691":14,"_192":193,"_199":1470,"_201":1470,"_204":1094,"_205":10,"_1280":34},[1282],{},{"_156":1094,"_163":1507,"_54":1508,"_56":1508,"_167":1509,"_169":170,"_172":173,"_174":1510,"_176":177},"c5659e7b-a56e-4a4b-bde1-476622d44106",[1063],{"_678":1283,"_1040":1338,"_174":1512},1790955361.49009,{"_673":674,"_675":1511},{"_691":14,"_192":193,"_199":1470,"_201":1470,"_204":1505,"_205":10,"_1280":34},[1282],{},{"_156":1505,"_163":1516,"_54":1517,"_56":1517,"_167":1518,"_169":170,"_172":687,"_174":1519,"_176":177},"21fcbdeb-fd70-4124-b6d1-b1b6856f1bc4",[1094],{"_678":1283,"_1040":1338,"_174":1521},1790955361.478378,{"_673":674,"_675":1520},{"_192":193,"_199":1470,"_201":1470,"_204":1514,"_205":10,"_1280":34},[1282],{},{"_156":1514,"_163":1525,"_54":1526,"_56":1526,"_167":1527,"_169":170,"_172":173,"_174":1528,"_176":177},"913c088e-ca19-49e9-87ae-890b9ad9bb93",[1505],{"_678":695,"_174":1530},1790955361.467048,{"_673":674,"_675":1529},{"_691":34,"_192":193,"_199":1470,"_201":1470,"_204":1523,"_205":10,"_203":14},[694],{},{"_156":1523,"_163":1534,"_54":1535,"_56":1535,"_167":1536,"_169":170,"_171":34,"_172":687,"_174":1537,"_176":177},"c7374ff3-1608-441e-8402-688928a52443",[1514],{"_678":695,"_174":1539},1790955361.460988,{"_673":674,"_675":1538},{"_689":690,"_691":34,"_692":14,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1532,"_205":10,"_203":14},[694],{},{"_156":1532,"_163":1543,"_54":1544,"_56":1544,"_167":1545,"_169":170,"_172":173,"_174":1546,"_176":177},"b5fb6765-3843-417e-ad3f-e1e596f090db",[1523],{"_678":695,"_174":1548},1790955361.455306,{"_673":674,"_675":1547},{"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1541,"_205":10,"_203":14},[694],{},{"_156":1541,"_163":1552,"_54":1553,"_56":1553,"_167":1554,"_169":170,"_172":173,"_174":1555,"_176":177},"540b5748-68bc-4c1f-be33-efbb2f72769a",[1532],{"_678":695,"_174":1557},1790955361.440616,{"_673":674,"_675":1556},{"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1550,"_205":10,"_203":14},[694],{},{"_156":1550,"_163":1561,"_54":1562,"_56":1562,"_167":1563,"_169":170,"_171":34,"_172":687,"_174":1564,"_176":177},"b2d50ab0-5ed6-4142-a43b-016f9db99987",[1541],{"_678":695,"_174":1567},1790955361.429132,{"_673":674,"_675":1566},{"_689":1565,"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1559,"_205":10,"_203":14},"attachment_caas_paths_v1",[694],{},{"_156":1559,"_163":1571,"_54":1572,"_56":1572,"_167":1573,"_169":170,"_172":173,"_174":1574,"_176":177},"5ff7daec-0c06-42a8-ac6b-8dfd80ea3b23",[1550],{"_678":679,"_174":1577},1790955361.421461,{"_673":1575,"_1576":694},{"_691":34,"_192":193,"_199":1470,"_201":1470,"_204":1569,"_205":10,"_1280":34},"model_editable_context","model_set_context",{},{"_156":1569,"_163":1581,"_54":1582,"_56":1582,"_167":1583,"_169":170,"_171":34,"_172":687,"_174":1584,"_176":177},"5c7cd350-80ac-4dff-8ae1-e85ee0ab6d9b",[1559],{"_678":695,"_174":1586},1790955361.409219,{"_673":674,"_675":1585},{"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1579,"_205":10,"_203":14},[694],{},{"_156":1579,"_163":1590,"_54":1591,"_56":1591,"_167":1592,"_169":170,"_171":34,"_172":687,"_174":1593,"_176":177},"535529d9-87b1-4e00-8320-ad547bfc8707",[1569],{"_678":695,"_174":1595},1790955361.395303,{"_673":674,"_675":1594},{"_692":34,"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1588,"_205":10,"_203":14},[694],{},{"_156":1588,"_163":1599,"_54":1600,"_56":1600,"_167":1601,"_169":170,"_171":34,"_172":687,"_174":1602,"_176":177},"6baf4eb7-bf72-40aa-972b-cd892a73fda1",[1579],{"_678":695,"_174":1604},1790955361.37715,{"_673":674,"_675":1603},{"_713":34,"_691":34,"_187":109,"_192":193,"_195":1469,"_197":198,"_199":1470,"_201":1470,"_202":109,"_66":67,"_204":1597,"_205":10,"_203":14},[694],{},{"_156":1597,"_163":1608,"_54":1609,"_56":1610,"_167":1611,"_169":170,"_172":173,"_174":1612,"_176":177},"client-created-root",[1588],{"_678":731,"_174":1631},1790955362.608,1790955365.101539,{"_673":674,"_675":1629},{"_1613":1614,"_724":1615,"_195":1469,"_726":-5,"_199":1470,"_201":1470,"_205":10,"_203":14},"attachments",[1619,1620],{"_727":1616,"_1617":1618},[],"render_format","markdown",{"_156":1089,"_1621":1627,"_1040":1088,"_1623":1628,"_224":1625,"_1047":1091,"_1626":14},{"_156":1042,"_1621":1622,"_1040":1041,"_1623":1624,"_224":1625,"_1047":1048,"_1626":14},"size",4182,"mime_type","text/x-python","local","is_big_paste",10426,"text/markdown",[1630],"Voici les spécifications d'un projet perfectible. Voici le contexte et les échanges avec une IA

## Avis sur l'algorithme actuel, puis une approche plus robuste

### 1. Pourquoi le détourage actuel est insatisfaisant

Le défaut de fond est que le **point de départ est un gribouillis imprécis** et que le BFS le fait grossir à l'aveugle jusqu'à `snapDistance`. L'algorithme ne décide donc jamais où se trouve la frontière, il laisse simplement le tracé s'étendre.

- **Aucune notion de "bloc".** Le BFS grossit dans toutes les directions. Si le trait s'arrête au milieu d'un pâté de maisons, il peut s'arrêter dans le vide si la route est trop loin, ou sauter la mauvaise route si une autre est plus proche. Le résultat dépend de `snapDistance`, qui est impossible à régler une fois pour toutes.
- **Le masque vert est mal extrait.** Une brosse semi-transparente se mélange au fond, donc un seuil colorimétrique est instable (JPEG, zones sombres, parcs verts). Or vous possédez la carte propre au même cadrage : la différence entre les deux images donne le masque exact.
- **`fillSmallHoles` ≤ 15 000 px est un hack.** Il bouche aussi les petits pâtés de maisons entre deux routes, qui ne sont pas des ronds-points.
- **Les routes sont mal localisées.** Les axes OSM n'ont pas de largeur, donc il faut deviner une épaisseur qui ne colle pas à celle dessinée par Google (largeur variable selon le zoom et le type de route, léger décalage de projection). Le bord extérieur de la chaussée est alors faux de quelques pixels, ce qui est précisément ce que vous cherchez à éviter.
- **RDP sur un contour pixel** donne des lignes droites mais des courbes en facettes, et le réglage de `epsilon` est un compromis permanent.
- **Le mode ZONE n'a pas de règle pour les petites rues internes**, qui seraient des trous dans la zone.

### 2. Nouvelle approche : segmentation en cellules + vote

Au lieu de faire grossir un masque, on **découpe d'abord la carte en blocs délimités par les routes** (les faces du graphe routier), puis on décide pour chaque bloc s'il est dedans ou dehors. La frontière tombe alors par construction sur un bord de route, sans paramètre de distance.

```
Carte "segmentation" ──► masque 