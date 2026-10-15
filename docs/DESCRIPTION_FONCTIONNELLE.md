# Description Fonctionnelle Complète — Projet Photoshop (Map Snapping)

---

## 🎯 1. Vision & Problématique Métier

### Le Contexte
Dans les opérations de cartographie, d'aménagement ou de sectorisation commerciale, les opérateurs délimitent des territoires et des zones d'action sur Google Maps en traçant des polygones approximatifs.

### Le Problème
Ces tracés sont imprécis :
* Ils mordent sur les habitations ou s'arrêtent au milieu des chaussées.
* Ils coupent les carrefours et les ronds-points de façon anguleuse et inesthétique.
* Détourer ces cartes à la main au lasso ou à la plume sous Adobe Photoshop prend **plusieurs dizaines de minutes par carte**, avec un résultat variable et fastidieux à maintenir.

### L'Objectif du Projet
Le projet **Photoshop Map Snapping** automatise ce détourage en **« aimantant » les tracés approximatifs sur le réseau routier réel** : le contour suit le bord des chaussées, au sub-pixel près, avec un bord anti-crénelé, en quelques secondes et de manière autonome (Java 21 pur, sans dépendance native).

---

## 📥 2. Les Données en Entrée (Inputs)

Les entrées sont produites par le script de capture `maps/capture_territoires.js` (Node.js + navigateur automatisé), qui ouvre la carte Google Maps du projet, sélectionne chaque territoire et enregistre, dans `maps/captures_maps/Territoire <code>/`, plusieurs calques à la même échelle et au même cadrage :

| Fichier d'entrée | Rôle fonctionnel |
| :--- | :--- |
| **Fond de carte à découper** (`05_style_contraste_sans_rien.png`) — **obligatoire** | L'image cartographique qui sera découpée (style épuré, sans textes ni icônes). Les routes y sont aussi reconnues par leur couleur si la capture « Routes seules » est absente. |
| **Tracé du territoire** (`01_plan_avec_territoires.json`) | Le polygone du territoire (coordonnées géographiques) et le cadrage de la carte (centre, zoom, taille). Pris par défaut à côté de la carte. |
| **Plan des zones** (`02_plan_avec_zones.png`) — mode zones | La carte avec les zones tracées en couleur : 1 rouge, 2 verte, 3 mauve, 4 orange, 5 cyan, 6 noir. Pris par défaut à côté de la carte. |
| **Routes seules** (`06_routes_seules.png`) — recommandé | Capture Google des seules routes, en blanc sur fond noir. Utilisée automatiquement si elle est présente : elle donne le réseau routier et le bord exact des chaussées. Le fond doit être entièrement noir (bâtiments, terrain, eau, voies ferrées masqués ou noirs) : tout ce qui est clair est lu comme de la route. |
| **Données OpenStreetMap** (`osm_roads.json`) — facultatif | Axes routiers OpenStreetMap, utilisables comme autre source du réseau routier (`--road-source osm`). |

Les autres captures (`01_plan_avec_territoires.png`, `03_style_contraste.png`, `04_style_contraste_sans_routes.png`) servent au contrôle visuel.

---

## 🔄 3. Les Deux Modes de Fonctionnement Métier

```text
                  [ TERRITOIRE ]
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

### 1. Le Mode Territoire (par défaut)
* **Objectif :** détourer la silhouette complète du territoire sur le fond de carte.
* **Règle métier :** le territoire englobe toute la chaussée des voies qui le bordent, ainsi que les ronds-points traversés par son tracé. Le bord **extérieur** des routes marque la frontière avec le reste du monde.

### 2. Le Mode Zones (`--mode zone`)
* **Objectif :** détourer les zones internes tracées en couleur dans `02_plan_avec_zones.png`, chacune séparément.
* **Règle métier :** les routes qui bordent une zone ne lui appartiennent pas. Le détourage s'arrête au **bord intérieur** de la chaussée ; les rues intérieures à la zone restent incluses. Les bords suivent le tracé en grands segments rectilignes, sans les décrochements des débouchés de rues ni des entrées de parking.

---

## ⚙️ 4. Pipeline Fonctionnel de Traitement

### 4.1 Territoire

```text
[Carte 05 + Tracé JSON + Routes seules 06]
       │
       ▼
 1. Projection du tracé et recadrage autour du territoire
       ▼
 2. Réseau routier et découpage en îlots (cellules)
       ▼
 3. Vote : îlots retenus selon leur recouvrement par le tracé
       ▼
 4. Consolidation : chaussées frontières, poches le long des boulevards, giratoires frontaliers
       ▼
 5. Contour vectoriel lissé, ronds-points en arcs d'ellipse
       ▼
 6. Calage sur le bord exact des chaussées et redressement des bords
       ▼
 7. Rendu anti-crénelé (supersampling ×4) et finitions autour des giratoires
       ▼
[clipped.png + mask.png + overlay.png]
```

**Étape 1 — Projection et recadrage.** Le polygone du JSON est projeté en pixels (Web Mercator) puis rastérisé. Le calcul se limite à un rectangle englobant élargi d'une marge (`--crop-margin`, 90 px par défaut).

**Étape 2 — Réseau routier et îlots.** Les routes viennent de la capture « Routes seules » si elle existe (lignes de marquage refermées, chemins piétons et autoroutes écartés), sinon de la couleur des routes sur la carte, ou d'OpenStreetMap sur demande. Tout ce qui n'est pas route est découpé en îlots (cellules) délimités par les chaussées.

**Étape 3 — Vote topologique.** Chaque îlot est retenu, rejeté ou partiellement retenu selon la part couverte par le tracé (seuil haut 0,80). Dans un îlot partiel, seules les lamelles assez épaisses sont gardées (40 px par défaut, `--min-partial-thickness`), pour ne pas suivre un tracé qui déborde un peu de la route sur un champ.

**Étape 4 — Consolidation.**
* La chaussée des routes qui bordent les îlots retenus est ajoutée jusqu'à son bord extérieur (expansion géodésique bornée, puis ouverture morphologique qui retire les dentelures), sans jamais retirer d'îlot retenu.
* Le long des boulevards, les poches entre le territoire et la chaussée (bretelles, îlots, terre-pleins) sont comblées et les deux chaussées d'un boulevard longé sont incluses (`--pocket-radius`).
* Les ronds-points traversés par le tracé sont inclus en entier.

**Étape 5 — Contour vectoriel.** Le bord du masque est extrait au sub-pixel puis lissé par régression locale robuste, en préservant les angles vifs. Les ronds-points sont remplacés par des arcs d'ellipse raccordés aux routes par des courbes d'Hermite (continuité tangentielle).
    
**Étape 6 — Calage et redressement.**
* Le contour est accroché au bord exact des chaussées, lu dans la capture « Routes seules » (désactivable par `--no-road-snap`).
* Les petites boucles où le contour se recoupe sont supprimées.
* Les bosses le long des bords rectilignes, au débouché des rues secondaires, sont remplacées par une ligne droite, sauf si cela couperait un rond-point ou un pâté de maisons.
* Près d'un sommet du tracé, une amorce de rue qui dépasse d'un carrefour à angle franc est coupée à la jonction des deux bords, et l'embouchure d'une rue latérale sur le bord d'une route est remplacée par une ligne droite.
* Le contour est réduit à des grands segments rectilignes, effet « lasso polygonal » (`--polygon-tolerance`).

**Étape 7 — Rendu.**
* Le contour est rastérisé sur une grille quatre fois plus fine (`--ss`), ce qui donne à chaque pixel de bord une opacité exacte entre 0 et 255.
* Là où le tracé fait un zigzag dans un îlot retenu, le contour lissé enjambe le creux et le terrain de l'îlot sous le contour reste visible (pas d'encoche transparente).
* Autour des ronds-points inclus, les petits coins de terrain blanc restés au-delà de la chaussée sont rendus transparents, et les petites encoches taillées dans l'anneau sont comblées d'après la capture « Routes seules ».

### 4.2 Zones

1. **Extraction des zones :** pour chaque couleur présente dans `02_plan_avec_zones.png`, le trait est isolé (tolérance de couleur, couleur la plus proche en cas d'ambiguïté), les petits traits parasites sont écartés et le trait est refermé si besoin. Chaque région fermée donne une partie de la zone.
2. **Intérieur de la zone :** vote des îlots comme pour le territoire, sans les chaussées frontières. Les rues intérieures sont conservées, et les giratoires traversés par le tracé sont exclus.
3. **Bords rectilignes :** chaque côté du tracé est reporté parallèlement au bord intérieur de la chaussée, ce qui donne des bords droits malgré les débouchés de rues et les entrées de parking.
4. **Contour et rendu :** lissage, polygone à grands segments (tolérance 5 px par défaut), rendu anti-crénelé. Autour des ronds-points, le bord est lu directement dans la capture « Routes seules ».

---

## 📤 5. Livrables Générés (Outputs)

### Territoire (dans `--out-dir`, par défaut le dossier courant)
1. **`clipped.png` — image détourée :** la carte découpée en RGBA, extérieur transparent, bord anti-crénelé, prête à être superposée sur n'importe quel fond.
2. **`mask.png` — masque d'opacité :** niveaux de gris (blanc = conservé, noir = exclu, dégradé sur le bord), utilisable comme masque de fusion dans Photoshop ou un SIG.
3. **`overlay.png` — contrôle :** la carte avec le contour en rouge, pour vérifier le calage d'un coup d'œil.

### Zones (dans `--out-dir`)
Pour chaque zone présente : **`zoneN_<couleur>_mask.png`** (masque d'opacité) et **`zoneN_<couleur>_rendu.png`** (carte détourée), par exemple `zone1_rouge_mask.png` et `zone1_rouge_rendu.png`.

---

## 💻 6. Utilisation (ligne de commande)

Seule l'option `--map` est obligatoire ; les autres fichiers sont pris à côté de la carte. Sans argument, l'aide s'affiche.

**Territoire :**
```bash
java -jar photoshop-1.0.jar \
  --map "maps/captures_maps/Territoire CA02/05_style_contraste_sans_rien.png" \
  --out-dir "maps/captures_maps/Territoire CA02/sortie"
```

**Zones :**
```bash
java -jar photoshop-1.0.jar \
  --map "maps/captures_maps/Territoire CA02/05_style_contraste_sans_rien.png" \
  --mode zone \
  --out-dir "maps/captures_maps/Territoire CA02/sortie_zones"
```

Options utiles :

| Option | Effet |
| :--- | :--- |
| `--json <chemin>` | Tracé du territoire (défaut : `01_plan_avec_territoires.json` à côté de la carte). |
| `--zones <chemin>` / `--zone 1,3` | Plan des zones, et zones à traiter (numéros ou couleurs ; défaut : toutes). |
| `--google-roads <png>` / `--no-google-roads` | Capture « Routes seules » explicite, ou détection des routes par la couleur de la carte. |
| `--road-source osm`, `--osm-roads <json>`, `--road <png>` | Autres sources du réseau routier. |
| `--output`, `--mask-out`, `--overlay-out` | Chemins explicites des livrables du territoire. |
| `--polygon-tolerance <px>` | Écart maximal du polygone à grands segments (1 px pour les territoires, 5 px pour les zones ; 0 = désactivé). |
| `--pocket-radius <px>` | Comblement des poches le long des boulevards (25 px ; 0 = désactivé). |
| `--min-partial-thickness <px>` | Épaisseur minimale des parties d'îlots conservées (40 px). |
| `--no-road-snap` | Désactive le calage sur le bord exact des chaussées. |
| `--ss <n>`, `--crop-margin <px>`, `--eps <px>` | Supersampling, marge de recadrage, marge d'expansion. |

Prévoir une mémoire Java suffisante pour les grandes captures (par exemple `-Xmx8g` pour 5 700 × 3 200 px).

---

## ⭐ 7. Résumé des Points Forts

* **Indépendance totale :** 100 % Java 21 standard, aucun binaire OpenCV, GDAL ou Python à installer.
* **Précision :** contour calé sur le bord des chaussées dessiné par Google, au sub-pixel, avec un bord anti-crénelé.
* **Robustesse géométrique :** les îlots retenus ne sont jamais rognés ; les ronds-points sont inclus en entier (territoires) ou exclus proprement (zones).
* **Esthétique :** bords en grands segments rectilignes, bosses et décrochements supprimés, ronds-points arrondis.
* **Rapidité :** quelques secondes à quelques dizaines de secondes par carte, calculs lourds parallélisés et limités au voisinage utile.
