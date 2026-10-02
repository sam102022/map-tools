# Planification des Sprints V2 — Découpage par Cellules Topologiques

Ce document définit le découpage opérationnel en 6 sprints incrémentaux pour l'implémentation de la V2 de l'algorithme de détourage cartographique, conformément à l'[ADR-007](adr/ADR-007-developpement-par-sprints.md) et à la spécification technique [SPEC_V2_ALGORITHME.md](SPEC_V2_ALGORITHME.md).

---

## 🗺️ Vue d'Ensemble du Pipeline V2

```text
Sprint 1 (Géométrie & Projection)   ──►  Polygone pixel P
Sprint 2 (Détection des Routes)     ──►  RoadMask R & Rclosed
Sprint 3 (Cellules & CellGraph)     ──►  Cellules 4-connexes + Adjacences
Sprint 4 (Vote Topologique)         ──►  CellSelection (INSIDE / OUTSIDE / PARTIAL)
Sprint 5 (Expansion Géodésique MCP) ──►  Masque final avec ronds-points englobés
Sprint 6 (Anti-Aliasing & CLI V2)   ──►  Pipeline complet de production RGBA
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
* **Critère de validation :** Tests unitaires validant la conversion GPS ➔ Pixel avec les coordonnées de `01_plan_avec_territoires.json`, et conformité du masque `P` avec l'emprise réelle.

---

## 🛣️ Sprint 2 : Détection Colorimétrique des Routes & Fermeture Topologique
* **Objectif :** Générer le `RoadMask` binaire à partir du style de carte Google (`05_style_contraste_sans_rien.png`) ou du flux OSM, avec garantie de fermeture topologique.
* **Package cible :** `com.sam102022.photoshop.v2.road`
* **Livrables :**
  * `RoadDetectorStyle` : extraction colorimétrique robuste des axes routiers (règles delta RVB bleu-gris issues de `script.py` : `(b-r) >= 12`, `2 <= (b-g) <= 22`, `r < 228`).
  * `RoadDetectorOsm` : rasterisation des segments vectoriels OSM avec largeur théorique.
  * `RoadCleaner` : ouverture morphologique ultra-fine ($2\times2$) pour retirer le bruit/icônes, suivie d'une fermeture topologique minimale (rayon 1 px) pour colmater les coupures d'un pixel (`Rclosed`).
* **Critère de validation :** Comparaison pixel par pixel du `RoadMask` généré avec l'image témoin `maps/road.png` générée par le prototype Python.

---

## 🧱 Sprint 3 : Segmentation en Cellules (4-connexité) & `CellGraph`
* **Objectif :** Découper l'espace complémentaire des routes (`¬Rclosed`) en îlots urbains/parcelles disjoints et construire la topologie d'adjacence.
* **Package cible :** `com.sam102022.photoshop.v2.cell`
* **Livrables :**
  * `CellLabeler` : composantes connexes en **4-connexité stricte** sur `¬Rclosed` (chiffre 0 = route, $1..N$ = cellules).
  * `Cell(id, area, bounds, centroid, polygonCoverage, state)`
  * `CellGraph` & `CellAdjacency` : identification des frontières routières séparant deux cellules voisines (`Cell A ── Road R ── Cell B`).
* **Critère de validation :** Détection d'exactement $N$ cellules fermées sur `Territoire CA01`, confirmation de l'étanchéité 4-connexe (aucun pont diagonal).

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
  * `CellSelection` : synthèse du vote.
* **Critère de validation :** Taux de décision conforme aux 21 cellules retenues et 6 partielles observées sur le prototype Python de référence.

---

## 🌊 Sprint 5 : Expansion Géodésique Bornée (MCP) & Enrobage des Ronds-Points
* **Objectif :** Attacher les routes frontières au territoire en étendant le front jusqu'au bord extérieur réel de la chaussée et combler les ronds-points.
* **Package cible :** `com.sam102022.photoshop.v2.expansion`
* **Livrables :**
  * `BoundaryRoadExtractor` : extraction des composantes routières reliant une cellule `INSIDE` à une cellule `OUTSIDE`.
  * `RoadDistanceTransform` : calcul de la carte de demi-largeur locale $hw(x, y)$ sur la chaussée.
  * `GeodesicRoadExpander` (MCP / Dijkstra) : propagation contrainte à la route depuis le bord des cellules retenues avec coût géométrique borné à $d_G \le 2 \times hw + \varepsilon$.
  * `IslandHoleFiller` : fermeture des trous résiduels fermés $\le 15\,000\text{ px}$ (centres de ronds-points et terre-pleins).
* **Critère de validation :** Alignement parfait de la frontière sur le bord extérieur des routes, ronds-points 100% pleins, absence totale de fuite vers les routes extérieures.

---

## 🎨 Sprint 6 : Rendu Sub-Pixel (Anti-Aliasing), Export RGBA & Intégration CLI
* **Objectif :** Produire les livrables graphiques finaux anti-aliasés et rendre le pipeline V2 accessible en ligne de commande.
* **Package cible :** `com.sam102022.photoshop.v2.render` / `com.sam102022.photoshop.cli`
* **Livrables :**
  * `SubpixelAlphaRenderer` : lissage gaussien localisé sur la frontière du masque ($\sigma \approx 0.8$) pour reproduire l'anti-aliasing sub-pixel natif de Google Maps.
  * `ImageClipper` : assemblage RGBA 32-bit de l'image détourée finale (`clipped.png`), du masque alpha (`mask.png`) et de l'overlay de diagnostic avec contour rouge (`overlay.png`).
  * `V2CliRunner` : commande CLI dédiée (ex: `--v2` ou nouveau binaire) avec paramètres documentés.
* **Critère de validation :** Exécution complète en $\le 5$ secondes sur `Territoire CA01`, inspection visuelle confirmant une découpe strictement identique au résultat de `script.py`.
