# Spécification Technique : Découpage de Zone Interne et Exclusion de la Chaussée Routière

- **Date :** 2026-09-28
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé
- **Langage / Environnement :** Java 21, Maven, Swing, Java2D (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Objectif

L'application permet d'extraire une portion de carte Google Maps à partir d'un calque vert et d'aligner automatiquement son contour extérieur sur le réseau routier environnant (mode "Territoire").

Dans un cas d'usage plus fin, un territoire global est lui-même subdivisé en plusieurs parcelles ou zones (par exemple la "Zone 1" d'un territoire communal ou intercommunal). Sur une capture Google Maps de type "Plan avec territoires et zones", ces zones internes sont délimitées par :
- Les axes routiers traversant ou bordant le secteur ;
- Des tracés ou démarcations internes ;
- La frontière extérieure du territoire d'origine.

Pour découper une zone interne donnée, l'utilisateur trace grossièrement un cadre fermé de couleur distinctive (rouge) sur le calque d'entrée (`limites.jpg`).

L'objectif de cette évolution est d'introduire le **découpage ciblé de zone interne** avec les exigences majeures suivantes :
1. **Rôle de sélecteur du cadre rouge :** Le tracé rouge n'est pas un contour géométrique rigide à déplacer pixel par pixel, mais un sélecteur de région d'intérêt permettant d'identifier la zone ciblée parmi les différentes zones du territoire.
2. **Exclusion stricte de la chaussée routière :** Le masque final doit s'arrêter précisément au **bord intérieur** de la chaussée. Les axes routiers délimitant la zone ne doivent pas être englobés (la surface de la route reste transparente / découpée).
3. **Calage mixte & étanchéité :** Le masque épouse à la fois le bord intérieur des routes, les éventuelles lignes sombres de démarcation internes et la frontière externe du territoire.
4. **Anti-aliasing sub-pixel :** Le rendu vectoriel utilise `PolygonBuilder` pour générer un `CoverageMask` continu [0..255] assurant une transition douce et sans effet d'escalier.
5. **Support avec ou sans masque de territoire :** Le traitement peut être exécuté de façon autonome (le territoire étant déduit du vert de l'image de calque) ou guidé par un masque de territoire préexistant (`--territory-mask`).

---

## 2. Architecture Globale & Découpage Modulaire (SRP)

Conformément à l'**ADR-008 (Une seule responsabilité par classe)**, les fonctionnalités de découpage de zone sont encapsulées dans de nouveaux composants dédiés, préservant ainsi l'intégrité du moteur de territoire existant (`RoadSnappingEngine`).

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── detection/
│   │   ├── RedFrameExtractor.java        # NOUVEAU : Détection du sélecteur rouge et extraction de son intérieur
│   │   ├── GreenMaskExtractor.java       # Existant : Détection du calque vert du territoire
│   │   ├── RoadCandidateDetector.java    # Existant : Détection des pixels candidats routiers
│   │   └── RoadDetector.java             # Existant : Façade de détection routière
│   ├── segmentation/
│   │   ├── ZoneBarrierConsolidator.java  # NOUVEAU : Construction du masque des barrières infranchissables
│   │   ├── ZoneSegmentationEngine.java   # NOUVEAU : Orchestration de l'inondation géodésique et du masque de zone
│   │   ├── MorphologyOps.java            # Existant : Opérations morphologiques (érosion, dilatation, fermeture)
│   │   └── RoadSnappingEngine.java       # Existant : Recalage géodésique de territoire global
│   ├── geometry/
│   │   ├── ContourExtractor.java         # Existant : Extraction du contour polygonal fermé
│   │   ├── ContourSimplifier.java        # Existant : Simplification Douglas-Peucker
│   │   ├── PolygonBuilder.java           # Existant : Rastérisation sub-pixel anti-aliasée
│   │   └── RoadSnapper.java              # Existant : Aimantation vectorielle
│   └── model/
│       ├── BinaryMask.java               # Existant : Grille booléenne 2D
│       ├── CoverageMask.java             # Existant : Grille d'opacité continue [0..255]
│       └── SnappingConfig.java           # Existant : Record immuable de configuration
├── cli/
│   └── CliRunner.java                    # Évolution : Prise en charge de --territory-mask et aiguillage auto
├── gui/
│   ├── FileSelectionPanel.java           # Évolution : Sélecteur optionnel de masque de territoire
│   └── MainWindow.java                   # Évolution : Prise en charge du mode zone
```

### Responsabilités des Nouveaux Composants

- **`RedFrameExtractor`** :
  - Identifie les pixels correspondant au tracé rouge selon une signature chromatique robuste en espace RGB et HSB.
  - Construit le masque binaire de l'intérieur plein du cadre rouge via une inondation depuis l'extérieur (ou calcul de composante fermée).
  - Fournit la boîte englobante de sécurité (*bounding box*) du tracé rouge.

- **`ZoneBarrierConsolidator`** :
  - Fusionne les différentes sources d'obstacles bloquants :
    1. Les axes routiers candidats détectés par `RoadCandidateDetector` (chaussée infranchissable).
    2. Les lignes sombres de démarcation cartographiques détectées dans `limites.jpg`.
    3. L'extérieur du territoire (pixels inactifs du masque de territoire ou de l'extraction verte).
  - Applique un colmatage morphologique fin (rayon 1) pour interdire toute fuite diagonale à 1 pixel.

- **`ZoneSegmentationEngine`** :
  - Détermine un point de graine (*seed*) optimal à l'intérieur du cadre rouge et dans le territoire.
  - Exécute une propagation par file d'attente (BFS 4-connectivité) arrêtée par les barrières.
  - Extrait le contour vectoriel de la composante connexe obtenue via `ContourExtractor`.
  - Simplifie le contour avec `ContourSimplifier`.
  - Rastérise la couverture continue anti-aliasée via `PolygonBuilder`.

---

## 3. Algorithmes Détaillés

### 3.1. Détection et extraction du sélecteur rouge (`RedFrameExtractor`)

La signature du cadre rouge doit être insensible aux légères variations de compression JPEG tout en rejetant les éléments cartographiques beiges, orangés ou gris :

1. **Critère chromatique :**
   Un pixel $(R, G, B)$ est classé comme rouge si :
   $$R \ge 150 \quad \text{et} \quad R > G + 40 \quad \text{et} \quad R > B + 40$$
   De plus, en coordonnées HSB, la saturation $S$ doit être $\ge 0.35$ et la luminosité $B \ge 0.20$.
2. **Fermeture morphologique :**
   Si le tracé au pinceau ou au stylet comporte des micro-discontinuités, une opération de fermeture ou dilatation de rayon 1 unifie le tracé.
3. **Extraction de l'intérieur du cadre :**
   - Une grille binaire de dimensions $W \times H$ est initialisée.
   - Les pixels extérieurs au cadre rouge sont inondés à partir des 4 bords de l'image (marquage des pixels accessibles depuis l'extérieur sans traverser le rouge).
   - L'intérieur du cadre correspond à tous les pixels non atteints par cette inondation extérieure et qui ne sont pas eux-mêmes le trait rouge.
   - Si le cadre n'est pas totalement étanche sur les bords de l'image, la boîte englobante $[x_{\min}, x_{\max}, y_{\min}, y_{\max}]$ du tracé rouge sert de borne géométrique stricte.

### 3.2. Consolidation des barrières bloquantes (`ZoneBarrierConsolidator`)

Pour empêcher la zone de déborder sur la chaussée routière ou sur les parcelles voisines :

1. **Barrière routière :**
   Les pixels candidats routiers issus de `RoadCandidateDetector.detect(carte)` forment la première barrière. Comme ces pixels représentent la chaussée (jaune, orange, rouge autoroutier, axes majeurs), les interdire garantit que la chaussée reste hors du masque.
2. **Barrière des tracés sombres internes :**
   Dans l'image `limites.jpg`, les délimitations séparant les zones présentent une luminance significativement inférieure au fond de carte :
   $$\text{luminance} = \frac{R + G + B}{3} < 130 \quad \text{avec} \quad \text{luminance}_{\text{carte}} - \text{luminance}_{\text{limites}} > 45$$
   Ces pixels sont injectés dans les barrières infranchissables.
3. **Barrière extérieure du territoire :**
   - Si un `territoryMask` est fourni : les pixels où $\text{territoryMask}(x, y) == 0$ sont marqués comme barrières.
   - Sinon : le territoire est extrait du calque vert via `GreenMaskExtractor.extract(limites)`. L'extérieur de ce masque devient la barrière.
4. **Colmatage morphologique :**
   Une fermeture binaire de rayon 1 (`MorphologyOps.close(barriers, 1)`) élimine les diagonales isolées (connectivité 8) pour empêcher toute fuite de l'inondation en connectivité 4.

### 3.3. Inondation géodésique contrainte (Flood-Fill BFS)

1. **Recherche de la graine (*seed*) :**
   - On recherche les pixels candidats qui sont à la fois :
     - À l'intérieur du cadre rouge ;
     - À l'intérieur du territoire ;
     - Non marqués comme barrière.
   - La graine retenue $(x_0, y_0)$ est le pixel maximisant la distance minimale aux barrières (centre géodésique de la zone).
2. **Propagation :**
   - Une file FIFO (`ArrayDeque<Point>`) est initialisée avec la graine $(x_0, y_0)$.
   - Un masque binaire `visited` marque les pixels explorés.
   - À chaque itération, les 4 voisins directs (haut, bas, gauche, droite) sont ajoutés si :
     - Ils sont dans les limites de l'image et dans la boîte englobante élargie du cadre rouge ;
     - Ils ne sont pas des barrières ;
     - Ils n'ont pas encore été visités.
3. **Résultat :**
   L'ensemble des pixels visités forme le masque binaire brut de la zone (`zoneBinary`).

### 3.4. Vectorisation et Rendu Sub-Pixel

1. **Extraction de contour :**
   `ContourExtractor.extractLargestContour(zoneBinary)` extrait la séquence ordonnée des sommets du polygone fermant la zone.
2. **Simplification RDP :**
   `ContourSimplifier.simplify(contour, 0.8)` élimine les micro-marches de l'inondation raster pour produire des segments fluides.
3. **Rastérisation continue anti-aliasée :**
   `PolygonBuilder.rasterizePixelCenterContour(W, H, simplifiedContour, antialiasing)` génère le `CoverageMask` avec fractions d'opacité [0..255] le long de la bordure.
4. **Préservation du noyau intérieur :**
   Pour éviter tout trou résiduel, une union maximale est effectuée avec le noyau érodé de la composante binaire :
   $$\text{finalCoverage} = \text{coverage}.\max(\text{CoverageMask.fromBinaryMask}(\text{zoneBinary.erode}(1)))$$

---

## 4. Interfaces Utilisateur & Ligne de Commande

### 4.1. Ligne de commande CLI (`CliRunner`)

Nouvel argument optionnel :
- `--territory-mask <path>` (alias `-tm`) : chemin vers un fichier image contenant le masque binaire ou ARGB du territoire complet.

**Comportement d'aiguillage automatique :**
1. Chargement de l'image de calque fournie via `--mask`.
2. Détection du rouge via `RedFrameExtractor.hasRedFrame(maskImage)` :
   - **Si un cadre rouge est détecté ($\ge 50$ pixels rouges) :**
     Activation automatique du mode **Zone Interne**.
     Logs :
     ```
     -> Détection d'un cadre rouge de sélection de zone...
        N pixels rouges identifiés.
     -> Détection des axes routiers Google Maps...
     -> Consolidation des barrières bloquantes...
     -> Inondation géodésique de la zone (exclusion stricte de la chaussée)...
     -> Vectorisation sub-pixel du contour...
     -> Export de l'image détourée et du masque...
     ```
   - **Sinon :**
     Maintien du comportement historique (mode **Territoire global** via `RoadSnappingEngine`).

### 4.2. Interface Graphique Swing (`MainWindow`)

- Détection visuelle automatique de la présence d'un cadre rouge lors du chargement de l'image de calque.
- Affichage d'un badge ou statut dans la barre d'état : `"Mode détecté : Découpage de Zone (bord intérieur routes)"`.
- Ajout d'une ligne optionnelle dans `FileSelectionPanel` pour spécifier le masque de territoire si l'utilisateur souhaite le contraindre explicitement.

---

## 5. Gestion des Erreurs et Cas Limites

1. **Cadre rouge absent alors qu'une découpe de zone est attendue :**
   Le système conserve le traitement du territoire complet sans lever d'erreur bloquante, avec avertissement dans les logs.
2. **Cadre rouge situé entièrement hors du territoire :**
   Si la graine calculée ne trouve aucun pixel de territoire à l'intérieur du cadre rouge, une `IllegalStateException` explicite est levée : `"Aucune zone du territoire ne se trouve à l'intérieur du cadre rouge fourni."`.
3. **Dimensions discordantes :**
   Si la carte, le calque de limites ou le masque de territoire ont des résolutions différentes, une `IllegalArgumentException` est immédiatement levée avec indication des dimensions respectives.
4. **Cadre rouge présentant une large brèche :**
   La boîte englobante $[x_{\min}, x_{\max}, y_{\min}, y_{\max}]$ du cadre rouge empêche toute fuite incontrôlée de l'inondation vers les autres zones du territoire.

---

## 6. Stratégie de Validation & Tests (TDD)

### 6.1. Tests Unitaires Dédiés
- `RedFrameExtractorTest` :
  - Détection sur un rectangle rouge net.
  - Détection sur un tracé à main levée rugueux ou discontinu.
  - Rejet des images sans tracé rouge (fonds cartographiques ordinaires, routes orangées).
  - Calcul exact de la boîte englobante et du masque intérieur.
- `ZoneBarrierConsolidatorTest` :
  - Consolidation correcte des barrières routières et de la frontière du territoire.
  - Élimination des brèches diagonales par morphologie.
- `ZoneSegmentationEngineTest` :
  - Inondation d'un carré fermé bordé de routes : la chaussée n'est jamais franchie.
  - Vérification de l'anti-aliasing sub-pixel sur la bordure.
  - Absence de régression dimensionnelle ou de décalage de coordonnées.

### 6.2. Test d'Intégration Réel (Sample 02)
- Test complet dans `IntegrationCliTest` s'appuyant sur les fichiers de `src/main/resources/sample 02/` :
  - Carte : `carte à découper.jpg`
  - Calque de limites avec cadre rouge : tracé sur `limites.jpg` autour de la zone 1
  - Référence attendue : `mask zone 1.jpg`
  - **Critère de succès IoU (Intersection over Union) :** $\text{IoU} \ge 0.92$ par rapport au masque de référence.
  - **Critère d'exclusion des routes :** Pourcentage de pixels de route inclus dans la zone $< 0.5\%$.

### 6.3. Suite Complète
- Exécution de l'intégralité des 83 tests existants : taux de succès impératif de 100%.
- Respect strict des ADR-001 à ADR-014 (Java 21 standard, Javadoc complète en français, complexité cognitive $\le 15$, zéro FQCN).
