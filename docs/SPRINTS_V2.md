# Planification des Sprints V2 — Découpage par Cellules Topologiques

Ce document définit le découpage opérationnel en 7 sprints incrémentaux pour l'implémentation de la V2 de l'algorithme de détourage cartographique, conformément à l'[ADR-007](adr/ADR-007-developpement-par-sprints.md), à la spécification technique [SPEC_V2_ALGORITHME.md](SPEC_V2_ALGORITHME.md) et aux avancées de la référence Python V5 (`maps/python/snap_cells_prototype_v5.py`).

---

## 🗺️ Vue d'Ensemble du Pipeline V2

```text
SPRINT 1 (Géométrie & Projection)
        │
        ▼
   PolygonMask P
        │
SPRINT 2 (Détection des Routes & Topologie)
        │
        ▼
 RoadMask R + Rclosed
        │
SPRINT 3 (Cellules & CellGraph)
        │
        ▼
 Cells + RoadInterfaces + Adjacencies
        │
SPRINT 4 (Vote Topologique)
        │
        ▼
   CellSelection (INSIDE / OUTSIDE / PARTIAL)
        │
SPRINT 5 (Reconstruction des Frontières Routières & Expansion Géodésique)
        │
        ├── BoundaryRoadExtractor
        ├── RoadDistanceTransform (hw)
        ├── GeodesicRoadExpander (MCP / Dijkstra borné)
        └── ResidualHoleResolver
        │
        ▼
 ConsolidatedMask Mc (binaire 0/1)
        │
SPRINT 6 (Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points)
        │
        ├── SubpixelContourExtractor (Marching Squares 0.5 + Resampling 1px)
        ├── CornerDetector (Angles vifs > 38° + Smoothstep blend)
        ├── RobustLqrSmoother (Régression quadratique locale + M-estimateur Cauchy)
        ├── RoundaboutDetector (Ellipses îlots + sondage radial bord externe)
        └── HermiteSplineConnector (Raccordement C1 sans rupture de courbure)
        │
        ▼
 SmoothVectorContour
        │
SPRINT 7 (Rendu Sub-Pixel Supersampling SS=4 & CLI V2)
        │
        ├── SupersampleRenderer (Rastérisation vectorielle 4x + box filter -> CoverageMask)
        ├── ImageClipper (Assemblage RGBA 32-bit clipped, mask, overlay)
        └── V2CliRunner (CLI --v2 avec hyperparamètres documentés)
        │
        ▼
 RGBA + diagnostics + CLI
```

---

## 📦 Sprint 1 : Socle Géométrique, Projection Web Mercator & Rasterisation
* **Objectif :** Transformer la géométrie géographique contenue dans le JSON (`outerRings`, `innerRings`, `center`, `zoom`) en un masque binaire pixel `PolygonMask` sur la grille de l'image.
* **Package cible :** `com.sam102022.photoshop.v2.geometry`
* **Livrables :**
  * `GeoCoordinate(lat, lng)`, `PixelPoint(x, y)`, `MapContext(width, height, zoom, center)`
  * `WebMercatorProjection` : formules mathématiques de projection EPSG:3857 vers les coordonnées pixels locales de l'image.
  * `JsonTerritoryLoader` : parseur Jackson/JSON des métadonnées cartographiques et polygones.
  * `PolygonRasterizer` : rasterisation sub-pixel / binaire du polygone projeté (`P[x, y] ∈ {0, 1}`) avec support des trous et multipolygones.
* **Critère de validation :** Tests unitaires validant la conversion GPS ➔ Pixel avec les coordonnées de `01_plan_avec_territoires.json`, et conformité du masque `P` avec l'emprise réelle. *(Sprint terminé et validé)*.

---

## 🛣️ Sprint 2 : Détection Colorimétrique des Routes & Fermeture Topologique
* **Objectif :** Générer le `RoadMask` binaire à partir du style de carte Google (`05_style_contraste_sans_rien.png`) ou du flux OSM, avec garantie de fermeture topologique et respect strict de la géométrie routière réelle (*ne jamais inventer une route et ne jamais supprimer une route réelle sans justification*).
* **Package cible :** `com.sam102022.photoshop.v2.road`
* **Livrables :**
  * `RoadDetectorStyle` : extraction colorimétrique robuste des axes routiers (règles delta RVB bleu-gris issues de `script.py` : `(b-r) >= 12`, `2 <= (b-g) <= 22`, `r < 228`).
  * `RoadDetectorOsm` : rasterisation des segments vectoriels OSM avec largeur théorique.
  * `RoadMaskCleaner` : nettoyage minimal du masque routier afin de supprimer uniquement les artefacts ponctuels identifiés (nettoyage très conservateur pour ne supprimer aucune petite route réelle), puis fermeture topologique minimale (rayon 1 px) pour colmater les discontinuités dues à l'anti-aliasing (`Rclosed`).
* **Critère de validation :** Comparaison pixel par pixel du `RoadMask` généré avec l'image témoin `maps/road.png` générée par le prototype Python et vérification de la préservation intégrale des axes secondaires. *(Sprint terminé et validé)*.

---

## 🧱 Sprint 3 : Segmentation en Cellules (4-connexité) & `CellGraph`
* **Objectif :** Découper l'espace complémentaire des routes (`¬Rclosed`) en îlots urbains/parcelles disjoints et construire la topologie d'adjacence enrichie par les interfaces routières locales.
* **Package cible :** `com.sam102022.photoshop.v2.cell`
* **Livrables :**
  * `CellLabeler` : composantes connexes en **4-connexité stricte** sur `¬Rclosed` (chiffre 0 = route, $1..N$ = cellules).
  * `Cell(id, area, bounds, centroid)`
  * `RoadInterface` : identification et extraction des portions localisées de chaussée reliant deux cellules adjacentes.
  * `CellGraph` : modélisation structurelle explicite des relations de voisinage traversant le réseau routier (`Cell A ── RoadInterface R ── Cell B`) permettant d'identifier précisément les routes frontières lors des étapes d'expansion.
* **Critère de validation :** Détection d'un ensemble déterministe de cellules sur le territoire CA01 et vérification de leur cohérence topologique (confirmation de l'étanchéité 4-connexe sans ponts diagonaux, indexation exacte des interfaces routières adjacentes).

---

## 🗳️ Sprint 4 : Moteur de Vote Topologique & Résolution des Parcelles Ouvertes
* **Objectif :** Classifier chaque cellule selon son taux d'inclusion dans le polygone d'intention $P$ et traiter les cas limites (champs ouverts, bord de carte).
* **Package cible :** `com.sam102022.photoshop.v2.vote`
* **Livrables :**
  * `CellCoverageCalculator` : calcul vectorisé ou matriciel de $\text{coverage}(C) = \frac{\text{Surface}(C \cap P)}{\text{Surface}(C)}$.
  * `CellClassifier` : application de la politique `CellSelectionPolicy` :
    * `INSIDE` si $\ge 0.60$ (conservée à 100%).
    * `OUTSIDE` si $\le 0.05$ (rejetée à 100%).
    * `PARTIAL` entre 0.05 et 0.60.
  * `PartialCellResolver` : conservation de $C \cap P$ pour les grandes cellules ouvertes (parcs, forêts, champs sans route de clôture) et détection des cellules touchant les bords de l'image.
  * `CellSelection` : synthèse du vote (IDs des cellules `inside`, `outside`, `partial` et masques associés).
* **Critère de validation :** Décision déterministe et cohérente sur l'ensemble des cellules de référence (`CA01`), avec conservation appropriée des cellules fermées et découpe géométrique nette des cellules partielles ouvertes.

---

## 🌊 Sprint 5 : Reconstruction des Frontières Routières & Expansion Géodésique Matricielle
* **Objectif :** Passer de `CellSelection` + `CellGraph` + `BoundaryRoads` à un masque binaire consolidé `ConsolidatedMask`, en intégrant les routes frontières jusqu'à leur bordure extérieure réelle via une transformée de distance et une propagation géodésique bornée, puis comblement des îlots compacts résiduels.
* **Principe d'enchaînement :**
  $$\text{RoadMask} \longrightarrow \text{RoadInterface} \longrightarrow \text{BoundaryRoad} \longrightarrow \text{Distance Transform (hw)} \longrightarrow \text{Geodesic Expansion} \longrightarrow \text{ConsolidatedMask}$$
* **Package cible :** `com.sam102022.photoshop.v2.expansion`
* **Livrables :**
  * `BoundingBoxCropper` : recadrage du calcul sur la boîte englobante du polygone d'intention augmentée d'une marge de sécurité ($mg = 90\text{ px}$) pour optimiser les performances de calcul sur les grandes images ($3810 \times 2130$).
  * `BoundaryRoadExtractor` : extraction et classification des interfaces routières reliant une cellule `INSIDE` à une cellule `OUTSIDE` (ou séparant deux zones contiguës en mode `ZONE`).
  * `RoadDistanceTransform` : calcul de la carte de demi-largeur locale $hw(x, y)$ sur la chaussée par transformée de distance euclidienne (EDT) et filtrage du maximum local ($size = 41$).
  * `GeodesicRoadExpander` (MCP / Dijkstra) : propagation contrainte à la surface routière depuis le bord des cellules retenues avec coût géométrique borné par $d_G \le 2 \times hw_{\max} + \varepsilon$ (avec $\varepsilon = 4\text{ px}$).
  * `MorphologicalConsolidator` : union $U = T \cup ext$, ouverture morphologique par élément structurant circulaire (disque de rayon $\rho = 5\text{ px}$) pour régulariser les indentations de la propagation.
  * `ResidualHoleResolver` : détection et comblement des îlots compacts résiduels fermés ($\text{surface} \le 15\,000\text{ px}$).
* **Critère de validation :**
  * Intégration rigoureuse des `BoundaryRoads` jusqu'au bord extérieur de la chaussée ;
  * Les routes séparant deux zones internes respectent la règle de partage médian en mode `ZONE` ;
  * Comblement des trous intérieurs et absence totale de fuite vers les cellules ou routes extérieures (`OUTSIDE`) ;
  * Génération d'un masque binaire étanche `ConsolidatedMask`.

---

## 📐 Sprint 6 : Géométrie Sub-Pixel, Lissage Robuste LQR & Modélisation des Ronds-points
* **Objectif :** Transformer le masque binaire consolidé (`ConsolidatedMask`) en un contour vectoriel continu lissé de haute fidélité (`SmoothVectorContour`), en conservant les angles vifs réels, en éliminant les encoches d'intersections sans raboter les virages grâce à la régression quadratique locale robuste, et en substituant les ronds-points par des arcs d'ellipses parfaits raccordés par des splines cubiques d'Hermite.
* **Package cible :** `com.sam102022.photoshop.v2.contour`
* **Livrables :**
  * `SubpixelContourExtractor` : extraction de contour sub-pixel continu par Marching Squares à isovaleur 0.5 (`find_contours`) sur le masque binaire avec trous comblés (`Mfill`), extraction de l'anneau extérieur principal et rééchantillonnage curviligne équidistant ($\text{step} = 1.0\text{ px}$).
  * `CornerDetector` : détection des angles vifs et virages en épingle par déviation tangentielle sur fenêtre large ($L = 80\text{ px}$, seuil $\theta \ge 38^\circ$), figeant les sommets de coins et appliquant un fondu progressif (*Hermite Smoothstep* $3\alpha^2 - 2\alpha^3$ entre $R_0 = 35$ et $R_1 = 95\text{ px}$).
  * `RobustLqrSmoother` : régression quadratique locale (LQR) robuste le long des segments de contour :
    * Ajustement local d'un polynôme d'ordre 2 ($\hat{y}(u) = c_0 + c_1 u + c_2 u^2$) pondéré par un noyau gaussien ($\sigma = 22\text{ px}$) ;
    * Repondération itérative par M-estimateur de Cauchy/Tukey ($w = \frac{1}{(1 + (dev/sc)^2)^2}$, $sc=3.0$, 6 itérations) traitant les départs de rues transversales comme des valeurs aberrantes ;
    * Préservation intégrale des courbures authentiques des voies sans phénomène de rabotage ou d'écrasement ;
    * Prise en charge naturelle des fenêtres unilatérales aux extrémités sans artefacts de bord.
  * `RoundaboutDetector` : identification et modélisation géométrique des ronds-points :
    * Détection des îlots centraux ($40 \le \text{surface} \le 9000$, $\text{solidité} \ge 0.90$) et ajustement d'ellipse algébrique directe ;
    * Sondage radial (240 rayons) à travers l'anneau routier pour identifier le bord externe de la chaussée et ajustement robuste de l'ellipse extérieure sur le mode bas ;
    * Filtrage des ronds-points traversés par le contour du territoire ($0.75 \le \rho \le 1.3$, recouvrement $> 20\%$).
  * `HermiteSplineConnector` : découpage du contour aux points de contact $i_A$ et $i_B$, calcul des tangentes unitaires de chaussée et d'ellipse, sélection de l'arc extérieur et transition continue $C^1$ sans boucle via des splines cubiques d'Hermite.
* **Critère de validation :**
  * Élimination complète des marches d'escalier du raster ;
  * Préservation exacte des angles de carrefours sans arrondissement excessif ;
  * Suivi fidèle des grandes courbes de routes sans aplatissement intérieur ;
  * Jonction visuelle parfaite des ronds-points sans cassure anguleuse ni boucle indésirable.

---

## 🎨 Sprint 7 : Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Intégration CLI
* **Objectif :** Produire les livrables graphiques finaux avec couverture continue par suréchantillonnage vectoriel ($SS=4$) et rendre le pipeline V2 accessible en ligne de commande avec des critères de validation mesurables.
* **Package cible :** `com.sam102022.photoshop.v2.render` / `com.sam102022.photoshop.cli`
* **Livrables :**
  * `SupersampleRenderer` : rastérisation vectorielle haute résolution sur une grille agrandie $\times 4$ ($SS=4$) avec compensation de décalage de demi-pixel ($+0.5\text{ px}$, passage des centres aux bords de pixels), puis sous-échantillonnage par boîte (*box filter*) produisant un masque de couverture continue $\alpha \in [0.0, 1.0]$.
  * `ImageClipper` : assemblage RGBA 32-bit de l'image détourée finale (`clipped.png`), du masque alpha de découpe (`mask.png`) et de l'image de diagnostic avec tracé du contour extérieur rouge (`overlay.png`).
  * `V2CliRunner` : interface CLI de production supportant le drapeau `--v2` et l'ensemble des hyperparamètres documentés (`--hi`, `--lo`, `--eps`, `--rho`, `--sig`, `--corner`, `--r0`, `--l`, `--zr`).
* **Critère de validation final :**
  * Pipeline complet exécuté en $\le 5$ secondes sur `Territoire CA01` ;
  * Aucune fuite vers les cellules `OUTSIDE` ;
  * Les `BoundaryRoads` sont correctement intégrées jusqu'au bord externe ;
  * Les routes séparant deux zones respectent le partage médian en mode `ZONE` ;
  * Les ronds-points et carrefours présentent une courbure continue et harmonieuse ;
  * Rendu visuellement et métriquement équivalent à la sortie étalon `CA01_clipped_v5.png` ;
  * Critères quantitatifs mesurables : IoU V2 vs référence Python V5 $\ge 0.99$, écart de surface $< 0.5\%$.

---

## 🔒 Compléments de Spécification — Contrats et Zones d'Ombre V2

### 1. Définition Opérationnelle de `RoadInterface`

#### Problème
Le `RoadMask` complet peut constituer une unique composante connexe, notamment lorsque plusieurs routes se rejoignent au niveau des carrefours.
Il ne faut donc pas définir l'élément de liaison comme une composante connexe globale du `RoadMask`.

#### Définition V2
On introduit la notion de **`RoadInterface`**.
Une `RoadInterface` représente une portion localisée de chaussée constituant la frontière entre deux cellules adjacentes.

```text
             RoadInterface
                  ↓
Cell A ████████████████ Cell B
       ← chaussée →
```

Elle est déterminée à partir de la relation topologique locale :
$$\text{Cell A} \longleftrightarrow \text{RoadInterface R} \longleftrightarrow \text{Cell B}$$
et non à partir d'une simple composante connexe globale du réseau routier.

#### Contrat Java
```java
public record RoadInterface(
        int id,
        int cellA,
        int cellB,
        BinaryMask mask,
        long area
) {
}
```
Avec les invariants stricts :
* `cellA != cellB`
* `mask ⊆ RoadMask`

Une même composante routière globale peut donc contenir plusieurs `RoadInterface`.

#### Conséquence Structurelle
Le `CellGraph` devient :
```text
Cell A
   │
   │ RoadInterface #12
   │
Cell B
   │
   │ RoadInterface #13
   │
Cell C
```
et non `Cell A ─── RoadComponent globale ─── Cell B`.
C'est cette distinction fondamentale qui permet de traiter proprement les carrefours et les ronds-points.

#### Définition de la Frontière de Cellule au Niveau Pixel & Construction de `RoadInterface`
Une `RoadInterface` est constituée des pixels routiers qui sont topologiquement accessibles depuis les frontières immédiates de Cell A et Cell B :
```text
Non-road
    │
    ├── Cell A
    │
    └── Cell B
```
Procédure déterministe d'attribution des pixels routiers :
```text
RoadMask
   │
   ▼
Cell labels (1..N)
   │
   ▼
Pour chaque pixel route r :
   rechercher les cellules voisines
   │
   ├── {A, B} ──► assigné à l'interface A-B
   ├── {A}    ──► bord de route de A (sans vis-à-vis immédiat)
   └── {}     ──► route interne / carrefour / artefact
```
Cette approche est infiniment plus robuste qu'un simple `connectedComponents(RoadMask)` pour délimiter les tronçons de chaussée.

---

### 2. Contrats I/O Explicites Entre les Sprints

Chaque sprint dispose d'un contrat d'entrée/sortie explicite et immuable sous forme de Java Records (conformément à l'[ADR-004](adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md)), garantissant que chaque étape puisse être développée et testée indépendamment avec des fixtures ou des mocks.

#### Sprint 1 ➔ Sprint 2 / 3 / 4 : `PolygonMask`
```java
public record PolygonMask(
        int width,
        int height,
        BinaryMask mask
) {
}
```
* **Contenu :** $P[x, y] \in \{0, 1\}$
* Représente uniquement la géométrie d'intention issue du JSON.

#### Sprint 2 ➔ Sprint 3 : `RoadMask`
```java
public record RoadMask(
        int width,
        int height,
        BinaryMask raw,
        BinaryMask closed
) {
}
```
* **Champs :**
  * `raw` : masque routier extrait colorimétriquement
  * `closed` : masque après fermeture topologique minimale (rayon 1 px)
* **Invariant :** `closed != nouvelle géométrie routière`. La fermeture sert uniquement à corriger les discontinuités ponctuelles dues au rendu et à l'anti-aliasing sans épaissir artificiellement les voies.

#### Sprint 3 ➔ Sprint 4 : `CellGraph`
```java
public record Cell(
        int id,
        long area,
        Bounds bounds,
        PixelPoint centroid
) {
}

public record CellGraph(
        List<Cell> cells,
        List<RoadInterface> roadInterfaces
) {
}
```
* **Principe de responsabilité :** La cellule stocke uniquement sa géométrie intrinsèque. La couverture par le polygone d'intention $P$ est calculée lors du Sprint 4 par `CellCoverageCalculator` plutôt que pré-calculée dans `Cell` :
  $$\text{Cell} \longrightarrow \text{CellCoverageCalculator} \longrightarrow \text{CellClassifier}$$

#### Sprint 4 ➔ Sprint 5 : `CellSelection`
```java
public record CellSelection(
        Set<Integer> inside,
        Set<Integer> outside,
        Set<Integer> partial,
        Map<Integer, BinaryMask> partialMasks
) {
}
```
* **Justification des identifiants (`Set<Integer>`) :** L'usage des IDs évite tout couplage fort avec l'instance de `Cell` et garantit une sérialisation/manipulation légère et découplée :
  $$\text{CellGraph} \longrightarrow \text{CellSelection} \longrightarrow \text{BoundaryReconstruction}$$

#### Sprint 5 ➔ Sprint 6 : `ConsolidatedMask`
```java
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        int offsetX,
        int offsetY
) {
    public ConsolidatedMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (mask == null) {
            throw new IllegalArgumentException("Le masque consolidé ne peut pas être null.");
        }
    }
}
```
* **Principe d'isolation géométrique :** Le masque consolidé `mask` représente l'union discrète des parcelles et de l'expansion géodésique routière dans la boîte englobante locale (définie par `offsetX`, `offsetY`). Il est purement binaire ($0$ ou $1$).

#### Sprint 6 ➔ Sprint 7 : `SmoothVectorContour`
```java
public record SmoothVectorContour(
        List<PixelPoint> points,
        List<Integer> cornerIndices,
        int width,
        int height
) {
    public SmoothVectorContour {
        if (points == null || points.size() < 3) {
            throw new IllegalArgumentException("Le contour lissé doit contenir au moins 3 points.");
        }
        if (cornerIndices == null) {
            throw new IllegalArgumentException("La liste des indices de coins ne peut pas être null.");
        }
    }
}
```
* **Principe d'isolation vectoriel / rendu :** Le contour vectoriel est une polyligne continue sub-pixel (flottante) régularisée par régression quadratique locale (`robust_lqr`) avec raccordements d'ellipses et splines d'Hermite pour les ronds-points. Aucune notion d'anti-aliasing matriciel ou d'alpha n'est introduite à ce stade.

#### Sprint 7 : `RenderResult`
```java
public record RenderResult(
        BufferedImage clipped,
        BufferedImage mask,
        BufferedImage overlay
) {
    public RenderResult {
        if (clipped == null || mask == null || overlay == null) {
            throw new IllegalArgumentException("Les images de résultat ne peuvent pas être null.");
        }
    }
}
```
* **Sortie finale de production :** `clipped` (RGBA 32-bit), `mask` (niveaux de gris 8-bit avec valeurs d'opacité continues $[0..255]$ issues du supersampling $SS=4$), et `overlay` (RGB avec contour rouge de diagnostic).

---

### 3. Formalisation des Modes `TERRITORY` et `ZONE`

Le Sprint 5 adapte sa stratégie de reconstruction selon le mode d'opération :

```java
public enum OperationMode {
    TERRITORY,
    ZONE
}

public record BoundaryReconstructionOptions(
        OperationMode mode,
        RoadExpansionPolicy expansionPolicy
) {
}
```

#### Mode TERRITORY (Détourage d'un territoire complet)
Une interface routière reliant l'intérieur et l'extérieur :
$$\text{INSIDE} \longleftrightarrow \text{RoadInterface} \longleftrightarrow \text{OUTSIDE}$$
constitue une `BoundaryRoad`.
Elle est absorbée dans le territoire **jusqu'au bord extérieur de la chaussée** :

```text
        OUTSIDE
────────────────────
      ROAD
████████████████████
        INSIDE
████████████████████

Résultat final :
████████████████████
████████████████████
        ↑
  territoire final (englobe la route jusqu'à son bord extérieur)
```
La propagation géodésique contrainte permet d'épouser la largeur réelle de la chaussée sans largeur arbitraire.

#### Mode ZONE (Découpage de zones internes contiguës)
Une route séparant deux zones internes :
$$\text{Zone 1} \longleftrightarrow \text{RoadInterface} \longleftrightarrow \text{Zone 2}$$
ne doit pas être absorbée intégralement par les deux zones (ce qui causerait un recouvrement illégal).

On applique une **règle de partage de la chaussée** :
$$\text{Zone 1} \mid \text{Moitié gauche de la route} \mid \text{Moitié droite de la route} \mid \text{Zone 2}$$
avec une frontière positionnée le long de **l'axe médian** de la chaussée :

```text
       Zone 1
████████████│
            │ ROAD
────────────┼──────────── (axe médian de partage)
            │
            │
            │ Zone 2
```
* **Distinction obligatoire :**
  * `INSIDE ── ROAD ── OUTSIDE` ➔ Absorption intégrale jusqu'au bord extérieur de la chaussée.
  * `ZONE A ── ROAD ── ZONE B` ➔ Partage équitable à mi-chaussée (axe médian).

---

### 4. Fixture d'Intégration Pivot (`CA01_V2_REFERENCE`)

Le cas d'usage réel `Territoire CA01` sert de banc d'essai étalon (Golden Fixture) :
* **Identifiant :** `CA01_V2_REFERENCE`
* **Entrées :**
  * Métadonnées et polygone : `maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json`
  * Image source de contraste : `maps/captures_maps/Territoire CA01/05_style_contraste_sans_rien.png`
* **Paramètres cartographiques réels vérifiés :**
  * Territoire : `CA01`
  * Dimensions réelles : **$3810 \times 2130\text{ px}$**
  * Zoom : **$17$**
  * Coordonnées centre : **$\text{lat}=47.269135$, $\text{lng}=-1.506505$**
* **Dossier de référence :**
  ```text
  src/test/resources/v2/
      fixtures/
          CA01/
              01_plan_avec_territoires.json
              05_style_contraste_sans_rien.png
              expected/
  ```

---

### 5. Matrice de Validation des Tests d'Intégration par Sprint

Cette matrice permet d'isoler immédiatement le sprint responsable en cas de divergence ou de régression :

| Sprint | Entrée | Sortie | Test Pivot & Critère d'Acceptation |
| :--- | :--- | :--- | :--- |
| **S1** | JSON `CA01` | `PolygonMask` | Projection GPS ➔ Pixels conforme à l'emprise ($3810 \times 2130$) *(Validé)* |
| **S2** | Image `CA01` | `RoadMask` | Extraction colorimétrique et fermeture sans perte d'axes secondaires |
| **S3** | `RoadMask` | `CellGraph` | Cellules 4-connexes étanches + `RoadInterfaces` associées |
| **S4** | `PolygonMask` + `CellGraph` | `CellSelection` | Vote déterministe (21 cellules pleines / 6 partielles) |
| **S5** | `CellGraph` + `CellSelection` | `ConsolidatedMask` | Reconstruction des frontières routières (EDT + MCP borné) + îlots résiduels |
| **S6** | `ConsolidatedMask` | `SmoothVectorContour` | Contour sub-pixel, LQR robuste sans rabotage, ronds-points par ellipses & Hermite |
| **S7** | `SmoothVectorContour` + Image source | `RenderResult` | Supersampling vectoriel $\times 4$, découpe RGBA, IoU $\ge 0.99$ vs python V5 |

---

### 6. Architecture V2 Globale

```text
                         JSON
                          │
                          ▼
                 ┌─────────────────┐
                 │    SPRINT 1     │
                 │ WebMercator     │
                 │ PolygonRasterizer│
                 └────────┬────────┘
                          │
                    PolygonMask P
                          │
                          │
Image ──────────► ┌───────▼────────┐
                  │    SPRINT 2    │
                  │ RoadDetector   │
                  │ RoadMaskCleaner│
                  └───────┬────────┘
                          │
                    RoadMask R
                          │
                          ▼
                  ┌───────────────┐
                  │    SPRINT 3   │
                  │ CellLabeler   │
                  │ CellGraph     │
                  │ RoadInterface │
                  └───────┬───────┘
                          │
                  CellGraph + P
                          │
                          ▼
                  ┌───────────────┐
                  │    SPRINT 4   │
                  │ CellCoverage  │
                  │ CellClassifier│
                  └───────┬───────┘
                          │
                   CellSelection
                          │
                          ▼
                  ┌───────────────┐
                  │    SPRINT 5   │
                  │ BoundaryRoad  │
                  │ Geodesic MCP  │
                  │ Morpho Opening│
                  │ HoleResolver  │
                  └───────┬───────┘
                          │
                  ConsolidatedMask Mc
                          │
                          ▼
                  ┌───────────────┐
                  │    SPRINT 6   │
                  │ MarchingSquare│
                  │ CornerDetector│
                  │ RobustLqr     │
                  │ RoundaboutFit │
                  │ HermiteSpline │
                  └───────┬───────┘
                          │
                  SmoothVectorContour
                          │
                          ▼
                  ┌───────────────┐
                  │    SPRINT 7   │
                  │ Supersample4x │
                  │ ImageClipper  │
                  │ V2 CLI        │
                  └───────┬───────┘
                          │
                          ▼
                     RenderResult (PNG RGBA)
```
