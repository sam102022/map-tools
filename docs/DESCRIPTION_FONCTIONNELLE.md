# Description Fonctionnelle Complète — Projet Photoshop (Map Snapping)

---

## 🎯 1. Vision & Problématique Métier

### Le Contexte
Dans les opérations de cartographie, d'aménagement ou de sectorisation commerciale, les opérateurs délimitent des territoires et des zones d'action à l'aide d'outils de dessin rapide (brosses semi-transparentes, annotations manuelles, tracés grossiers) superposés à Google Maps.

### Le Problème
Ces tracés manuels sont imprécis :
* Ils mordent sur les habitations ou s'arrêtent au milieu des chaussées.
* Ils coupent les carrefours et les ronds-points de façon anguleuse et inesthétique.
* Détourer manuellement ces cartes au lasso ou à la plume sous un logiciel comme Adobe Photoshop prend **plusieurs dizaines de minutes par carte**, avec un résultat variable et fastidieux à maintenir.

### L'Objectif du Projet
Le projet **Photoshop Map Snapping** automatise intégralement ce détourage en **"aimantant" automatiquement les tracés approximatifs sur le réseau routier réel** pour produire un découpage chirurgical, net et anti-aliasé, au pixel près, en quelques secondes et de manière 100% autonome (socle Java pur, sans dépendance native comme OpenCV).

---

## 📥 2. Les Données en Entrée (Inputs)

Le pipeline repose sur la combinaison de plusieurs calques cartographiques synchronisés (capturés à l'échelle identique) :

| Fichier d'entrée | Rôle fonctionnel |
| :--- | :--- |
| **Fond de carte à découper** (`05_style_contraste_sans_rien.png` ou `03_style_contraste.png`) | L'image cartographique réelle finale qui sera découpée (de préférence un style épuré, sans textes ni icônes parasites pour garantir un résultat graphique pur). |
| **Calque d'annotation territoire** (`01_plan_avec_territoires.png`) | Une image contenant le "gribouillage" vert initial fait à main levée, indiquant approximativement où se trouve le territoire. |
| **Calque de zonage** (`02_plan_avec_zones.png`) *(optionnel)* | Une image annotée avec des cadres ou contours de couleur vive (Rouge, Magenta, etc.) délimitant des sous-secteurs internes. |
| **Données OpenStreetMap** (`osm_roads.json`) *(recommandé)* | Les coordonnées vectorielles des axes routiers réels extraits sur la zone géographique, servant de vérité terrain topologique. |

---

## 🔄 3. Les Deux Modes de Fonctionnement Métier

Le projet gère deux besoins distincts avec des règles géométriques opposées :

```text
                  [ TERRITOIRE GLOBAL ]
                  Englobe la chaussée jusqu'au bord extérieur
                        ┌────────────────────────┐
                        │   Route périphérique   │
 ┌──────────────────────┼───┬────────────────┬───┼──────────────────────┐
 │                      │   │   CHAMPS /     │   │                      │
 │    EXTÉRIEUR CARTE   │   │   BÂTIMENTS    │   │    EXTÉRIEUR CARTE   │
 │                      │   │                │   │                      │
 └──────────────────────┼───┴────────────────┴───┼──────────────────────┘
                        │        ZONE A      │  Zone B
                        │ S'arrête au pied   │ S'arrête au pied
                        │ de la chaussée     │ de la chaussée
                        └────────────────────┴────────
                               [ ZONES INTERNES ]
```

### 1. Le Mode Territoire (`TERRITORY`)
* **Objectif :** Découper la silhouette globale du territoire complet sur le fond de carte.
* **Comportement du magnétisme :** **Calage au bord extérieur (`OUTER`)**.
* **Règle métier :** Le territoire doit englober l'intégralité de la chaussée des voies qui le bordent ainsi que les ronds-points. L'extérieur de la route marque la frontière avec le reste du monde.

### 2. Le Mode Zone (`ZONE`)
* **Objectif :** Découper des sous-parcelles internes adjacentes au sein d'un même territoire (ex: Zone 1, Zone 2...).
* **Comportement du magnétisme :** **Calage au bord intérieur (`INNER`)**.
* **Règle métier :** Les routes qui séparent deux zones ne doivent appartenir ni à l'une ni à l'autre : elles constituent une **barrière étanche neutre**. Le détourage de chaque zone s'arrête strictement au pied de la chaussée (la route est exclue).

---

## ⚙️ 4. Pipeline Fonctionnel de Traitement (Étape par Étape)

Le moteur exécute les phases suivantes de façon transparente :

```text
[Entrées : Carte + Calque Vert + OSM]
       │
       ▼
 1. Extraction Colorimétrique (Identification du noyau brut)
       │
       ▼
 2. Construction du Réseau Routier & Solidification des Ronds-points
       │
       ▼
 3. Recalage Géodésique Positif (Expansion BFS vers la frontière extérieure)
       │
       ▼
 4. Vectorisation & Lissage Intelligent (Élimination de l'effet d'escalier)
       │
       ▼
 5. Rastérisation Sub-Pixel (Anti-Aliasing) & Préservation du Cœur
       │
       ▼
[Sorties : Image transparente PNG + Masque d'opacité]
```

### Étape 1 : Extraction du Masque Brut
* L'algorithme analyse l'image d'annotation pour extraire la teinte cible (vert pour le territoire, rouge/magenta pour les zones) grâce à un filtre tolérant aux variations de compression JPEG/PNG.
* Une fermeture morphologique comble les éventuels trous de brosse laissés par l'utilisateur.

### Étape 2 : Détection & Solidification des Routes
* Si un fichier `osm_roads.json` est fourni, les axes sont rasterisés avec précision. Sinon, le moteur analyse les contrastes de gris/bleu de Google Maps pour identifier les chaussées.
* **Traitement spécifique des ronds-points :** Un algorithme d'analyse topologique (`fillSmallHoles`) détecte automatiquement les îlots centraux des ronds-points (anneaux fermés de surface $\le 15\,000\text{ px}$) et les comble. Le rond-point devient un bloc plein, garantissant qu'il sera englobé avec une courbure harmonieuse et naturelle.

### Étape 3 : Recalage par Expansion Géodésique Positive
* Le territoire d'origine n'est jamais rogné arbitrairement.
* Une onde de propagation (parcours en largeur BFS) étend le masque vert vers l'extérieur :
  * Elle traverse les espaces vierges jusqu'à une distance limite paramétrable (`snapDistance`) pour aller chercher la route la plus proche.
  * Dès qu'elle atteint la chaussée, elle l'absorbe.
  * **L'effet barrière :** L'onde a l'interdiction de ressortir de la route. Elle s'arrête net sur la bordure extérieure de l'axe routier.

### Étape 4 : Lissage Vectoriel sans Perte de Forme
* Un contour haute précision est extrait autour du masque consolidé.
* L'algorithme de Ramer-Douglas-Peucker élimine les micro-marches d'escalier du format pixel, redressant parfaitement les grandes lignes droites tout en préservant l'arrondi des courbes et des carrefours.

### Étape 5 : Rendu Sub-Pixel (Anti-Aliasing)
* Le polygone est converti en masque de couverture continue (`CoverageMask`) où chaque pixel de bordure reçoit une valeur de transparence douce de 0 à 255 via le moteur Java2D (`KEY_ANTIALIASING`).
* Le cœur intérieur du territoire est fusionné avec le résultat pour garantir qu'aucun artefact transparent n'apparaît au centre de l'image.

---

## 📤 5. Livrables Générés (Outputs)

Pour chaque traitement, l'application produit un ensemble de fichiers prêts pour la production et le contrôle qualité :

1. **L'Image Détourée Haute Fidélité** (`*_clipped.png`) :
   * L'image source originale découpée, au format RGBA 32-bit.
   * L'arrière-plan extérieur est 100% transparent.
   * La bordure présente un fondu sub-pixel doux, prêt à être superposé sur n'importe quel support ou fond coloré sans effet de crénelage ("pixel art").
2. **Le Masque Alpha de Découpe** (`*_mask.png`) :
   * Image en niveaux de gris représentant la couche d'opacité pure (blanc = conservé, noir = masqué, dégradé de gris sur la bordure).
   * Utilisable directement comme masque de fusion sous Photoshop, Illustrator ou dans un moteur SIG.
3. **Images de Diagnostic & Contrôle** :
   * `territory_mask_detected.png` : Visualisation du tracé brut initial capturé.
   * `roads_detected.png` : Empreinte exacte des routes utilisées pour le magnétisme.

---

## 💻 6. Modes d'Exploitation

L'outil propose une double modalité d'utilisation adaptée aussi bien aux développeurs/serveurs qu'aux utilisateurs finaux :

### A. Mode CLI (Headless / Ligne de Commande)
Idéal pour les scripts automatisés (Node.js, scripts batch, pipelines de production) :
```bash
java -jar photoshop-1.0.jar \
  --map "carte_sans_rien.png" \
  --mask "plan_avec_territoires.png" \
  --osm-roads "osm_roads.json" \
  --mode territory \
  --output "resultat_detoure.png" \
  --mask-out "masque_alpha.png"
```

### B. Mode GUI (Interface Graphique Interactive Swing)
Permet à un opérateur d'ajuster visuellement les paramètres :
* **Panneau de fichiers :** Sélection par glisser-déposer de la carte et des masques.
* **Contrôles interactifs :** Sliders pour ajuster la distance d'aimantation (`snapDistance`), le rayon de fermeture morphologique et l'activation de l'anti-aliasing.
* **Visualiseur multi-couches :** Affichage superposé en temps réel du détourage, du masque d'opacité ou de la carte d'origine avec zoom et déplacement à la souris.

---

## ⭐ 7. Résumé des Points Forts

* **Indépendance Totale :** 100% Java 21 standard (aucun binaire OpenCV, GDAL ou Python à installer).
* **Robustesse Géométrique :** L'approche géodésique positive élimine les risques d'inversion de normales, de croisements de vecteurs aux intersections et de disparition du territoire.
* **Esthétisme Professionnel :** Solidification native des ronds-points pour un rendu arrondi harmonieux, complété par un anti-aliasing sub-pixel.
