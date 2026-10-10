# Spécification Technique : Découpage de Zone Interne Multi-Couleurs et Exclusion de la Chaussée Routière

- **Date :** 2026-09-28
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé (Révisé avec support multi-couleurs)
- **Langage / Environnement :** Java 21, Maven, Swing, Java2D (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Objectif

L'application permet d'extraire une portion de carte Google Maps à partir d'un calque vert et d'aligner automatiquement son contour extérieur sur le réseau routier environnant (mode "Territoire").

Dans un cas d'usage plus fin, un territoire global est subdivisé en plusieurs parcelles ou zones (par exemple la "Zone 1", "Zone 2", etc.). Sur une capture Google Maps de type "Plan avec territoires et zones", ces zones internes sont délimitées par :
- Les axes routiers traversant ou bordant le secteur ;
- Des tracés ou démarcations internes sombres ;
- La frontière extérieure du territoire d'origine.

Pour identifier et isoler une zone spécifique, l'utilisateur trace sur le calque d'entrée (`limites.jpg`) un **cadre fermé de couleur distinctive** (ex: rouge pour la zone 1, bleu pour la zone 2, magenta pour la zone 3, etc.). Plusieurs annotations de couleurs différentes peuvent coexister sur la même image de calque.

L'objectif de cette évolution est d'introduire le **découpage ciblé de zone interne paramétrable par couleur** avec les exigences majeures suivantes :
1. **Sélecteur polychrome configurable (`SelectorColor`) :** La couleur d'annotation n'est pas limitée au rouge : l'architecture supporte un jeu étendu de couleurs d'annotation (`RED`, `BLUE`, `MAGENTA`, `CYAN`) ainsi qu'un mode `AUTO`.
2. **Dissociation stricte des annotations et du fond de carte :** Les pixels d'annotation colorés ne doivent en aucun cas être confondus avec des axes routiers (autoroutes orange, départementales jaunes) ni avec le calque de territoire vert ou les démarcations sombres.
3. **Gestion des cadres multiples & désambiguïsation :** Si plusieurs cadres de couleurs différentes sont présents, l'utilisateur peut cibler explicitement la couleur voulue via `--zone-color <couleur>`. En mode `AUTO`, si un seul cadre coloré est détecté, il est sélectionné automatiquement ; si plusieurs cadres distincts coexistent sans choix explicite, une erreur claire liste les couleurs détectées et invite l'utilisateur à préciser son choix.
4. **Exclusion absolue de la chaussée routière :** Le masque final s'arrête précisément au **bord intérieur** de la chaussée. Les axes routiers délimitant la zone restent transparents (taux d'inclusion routière mathématiquement nul $= 0{,}0\%$), y compris après simplification vectorielle et rastérisation sub-pixel.
5. **Anti-aliasing sub-pixel sécurisé :** Le rendu vectoriel utilise `PolygonBuilder` pour générer un `CoverageMask` continu [0..255] assurant une transition douce et sans effet d'escalier.
6. **Modes d'opération explicites :** Support des modes `--mode auto|territory|zone` (par défaut `auto`).
7. **Support avec ou sans masque de territoire :** Fonctionnement autonome (territoire déduit du vert) ou guidé par un masque de territoire préexistant (`--territory-mask`).

---

## 2. Architecture Globale & Découpage Modulaire (SRP)

Conformément à l'**ADR-008 (Une seule responsabilité par classe)**, les fonctionnalités de découpage de zone sont encapsulées dans des composants dédiés :

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   ├── OperationMode.java                 # NOUVEAU : AUTO, TERRITORY, ZONE
│   │   ├── SelectorColor.java                 # NOUVEAU : AUTO, RED, BLUE, MAGENTA, CYAN (avec prédicats chromatiques)
│   │   ├── BinaryMask.java                    # Existant : Grille booléenne 2D
│   │   ├── CoverageMask.java                  # Existant : Grille d'opacité continue [0..255]
│   │   └── SnappingConfig.java                # MODIFIÉ : Ajout de OperationMode et SelectorColor
│   ├── detection/
│   │   ├── ColorRegionSelectorExtractor.java  # NOUVEAU : Détection du cadre selon sa signature chromatique et intérieur fermé
│   │   ├── DarkDemarcationDetector.java       # NOUVEAU : Détection mesurable des lignes sombres de démarcation
│   │   ├── GreenMaskExtractor.java            # Existant : Détection du calque vert du territoire
│   │   ├── RoadCandidateDetector.java         # Existant : Détection des pixels candidats routiers
│   │   └── RoadDetector.java                  # Existant : Façade de détection routière
│   ├── segmentation/
│   │   ├── ZoneBarrierConsolidator.java       # NOUVEAU : Consolidation étanche des barrières infranchissables
│   │   ├── ZoneSegmentationEngine.java        # NOUVEAU : Sélection déterministe de graine, BFS haute performance et exclusion stricte
│   │   ├── MorphologyOps.java                 # Existant : Opérations morphologiques (érosion, dilatation, fermeture)
│   │   └── RoadSnappingEngine.java            # Existant : Recalage géodésique de territoire global
│   └── geometry/
│       ├── ContourExtractor.java              # Existant : Extraction du contour polygonal fermé
│       ├── ContourSimplifier.java             # Existant : Simplification Douglas-Peucker
│       ├── PolygonBuilder.java                # Existant : Rastérisation sub-pixel anti-aliasée
│       └── RoadSnapper.java                   # Existant : Aimantation vectorielle
└── cli/
    └── CliRunner.java                         # MODIFIÉ : Options --mode, --zone-color, --territory-mask et messages clairs
```

---

## 3. Algorithmes Détaillés

### 3.1. Signatures Chromatiques et Modèle `SelectorColor`

Pour éviter toute ambiguïté avec le fond cartographique Google Maps :
- Le vert du territoire ($G > R + 20 \land G > B + 20$) est systématiquement rejeté de toute sélection d'annotation.
- Les routes jaunes/oranges ($R > 200 \land G \in [130..210] \land B < 120$) sont protégées et rejetées.
- Les saturations faibles ($S < 0.35$ en espace HSB) et faibles luminosités ($B < 0.20$) sont rejetées pour éliminer gris, beiges, blancs et noirs.

Chaque couleur de l'énumération `SelectorColor` dispose d'un prédicat chromatique strict :
1. **`RED` ("rouge") :**
   $$R \ge 150 \quad \text{et} \quad R > G + 40 \quad \text{et} \quad R > B + 40 \quad \text{et} \quad S \ge 0.35$$
2. **`BLUE` ("bleu") :**
   $$B \ge 150 \quad \text{et} \quad B > R + 40 \quad \text{et} \quad B > G + 20 \quad \text{et} \quad S \ge 0.35$$
3. **`MAGENTA` ("magenta" / violet) :**
   $$R \ge 140 \quad \text{et} \quad B \ge 140 \quad \text{et} \quad R > G + 40 \quad \text{et} \quad B > G + 40 \quad \text{et} \quad S \ge 0.35$$
4. **`CYAN` ("cyan" / turquoise) :**
   $$G \ge 140 \quad \text{et} \quad B \ge 140 \quad \text{et} \quad G > R + 40 \quad \text{et} \quad B > R + 40 \quad \text{et} \quad S \ge 0.35$$
5. **`AUTO` :**
   Analyse les pixels de l'image et détecte dynamiquement les couleurs d'annotations présentes ayant au moins 50 pixels significatifs.

---

### 3.2. Extraction et Réparation du Sélecteur (`ColorRegionSelectorExtractor`)

1. **Inventaire des couleurs présentes :**
   `public List<SelectorColor> detectPresentColors(BufferedImage image)` :
   Scanne l'image et retourne la liste ordonnée des couleurs reconnues comptant au moins 50 pixels.
2. **Résolution de la couleur cible :**
   - Si une couleur explicite est demandée (ex: `RED`) : vérifie sa présence ($\ge 50$ px). Si absente, lève `IllegalArgumentException: "La couleur spécifiée (ROUGE) n'a pas été détectée dans l'image de calque."`.
   - Si `AUTO` est actif :
     - Si aucune couleur reconnue n'est détectée : retourne `null` (le système reste en mode Territoire en mode AUTO).
     - Si exactement **une** couleur est détectée : cette couleur est retenue comme cible.
     - Si **plusieurs** couleurs sont détectées simultanément (ex: tracé rouge pour zone 1 ET tracé bleu pour zone 2) :
       Lève une `IllegalStateException: "Plusieurs cadres de couleurs distinctes ont été détectés dans le calque : [ROUGE, BLEU]. Veuillez préciser la zone à découper via l'option --zone-color <rouge|bleu|...>."`.
3. **Masque brut et réparation de brèche bornée :**
   - Un masque binaire est instancié pour la couleur cible retenue.
   - Fermeture morphologique bornée (`MorphologyOps.close`) de rayon $R_{\text{gap}} = 5$ pixels pour combler d'éventuelles coupures de brosse $\le 10$ pixels.
4. **Validation de l'intérieur fermé :**
   - Inondation extérieure (BFS 4-connectivité depuis les 4 coins et bords de l'image).
   - Les pixels non atteints par l'extérieur forment l'intérieur candidat.
   - Si l'inondation extérieure atteint toute l'image (aucune zone fermée isolée), l'algorithme conclut à une brèche $> 10$ pixels et lève :
     `IllegalStateException: "Le tracé d'annotation de couleur " + color + " présente une brèche non colmatable (> 10 pixels). Veuillez fermer le contour."`.
   - Le masque `interiorMask` retourné correspond à la surface intérieure close (aire minimale requise de 500 pixels).

---

### 3.3. Détection Mesurable des Démarcations Sombres (`DarkDemarcationDetector`)

Les délimitations cartographiques sombres Google Maps sont distinguées des tracés d'annotation colorés :
1. **Luminance pondérée standard (UIT-R BT.601) :**
   $$Y(R, G, B) = 0.299\,R + 0.587\,G + 0.114\,B$$
2. **Critères d'une démarcation cartographique :**
   - Appartient au territoire ($G_{\text{limites}} > R_{\text{limites}} + 15$ ou présent dans `territoryMask`).
   - Luminance sombre dans `limites` : $Y(\text{limites}) \le 125$.
   - Contraste marqué par rapport à `carte` : $Y(\text{carte}) - Y(\text{limites}) \ge 45$.
   - **Protection contre les annotations colorées :** Le pixel n'appartient à aucune signature de `SelectorColor` (saturation faible ou teinte non saturée dans les primaires).
3. **Filtrage des artéfacts et chiffres isolés :**
   Élimination des composantes connexes sombres $< 15$ pixels.

---

### 3.4. Consolidation Étanche des Barrières (`ZoneBarrierConsolidator`)

Le masque des obstacles infranchissables `barriers` combine :
1. **Axes routiers (`roadBarriers`) :**
   Issus de `RoadCandidateDetector.detect(carte)`. Chaque pixel classé comme route forme un mur bloquant.
2. **Démarcations internes (`darkDemarcations`) :**
   Issues de `DarkDemarcationDetector`.
3. **Frontière du territoire (`territoryBarriers`) :**
   - Si `territoryMask` est injecté : $\text{barrière} = \neg \text{territoryMask}$.
   - Sinon : le territoire est extrait par `GreenMaskExtractor.extract(limites)`, et $\text{barrière} = \neg \text{territoireVert}$.
4. **Colmatage des diagonales 8-connexes :**
   $$\text{consolidatedBarriers} = \text{MorphologyOps.close}(\text{barriers}, 1)$$
   Garantit l'étanchéité absolue de l'inondation en 4-connectivité.

---

### 3.5. Sélection Déterministe de la Graine et Inondation Haute Performance (`ZoneSegmentationEngine`)

1. **Domaine admissible de la graine ($S$) :**
   $$S = \text{interiorMask} \cap \text{territoryMask} \setminus \text{consolidatedBarriers}$$
   Si $S = \emptyset$, lève immédiatement une `IllegalStateException` explicite indiquant l'absence de pixel libre dans le territoire pour la couleur spécifiée.
2. **Transformée de distance et départage déterministe :**
   - Calcul de la distance euclidienne minimale aux frontières de $S$.
   - En cas d'égalités multiples sur la distance maximale $D_{\max}$, sélection du point le plus proche du centroïde géométrique $(\bar{x}, \bar{y})$ de $S$.
   - En cas d'égalité résiduelle, arbitrage par ordre lexicographique strict (plus petit $y$, puis plus petit $x$).
3. **Inondation BFS Haute Performance (Zéro Allocation d'Objets) :**
   - File linéaire primitive sur tableau plat `int[] queue = new int[W * H]` indexé par $y \times W + x$.
   - Pointeur de lecture `head` et pointeur d'écriture `tail` primitifs.
   - Confinement strict : seuls les pixels où $\text{interiorMask} == \text{true} \land \text{consolidatedBarriers} == \text{false} \land \neg \text{visited}$ sont explorés.

---

### 3.6. Vectorisation Sub-Pixel et Garantie Absolue d'Exclusion des Routes

1. **Extraction de Contour :** `ContourExtractor.extractLargestContour(zoneBinary)`.
2. **Simplification RDP :** `ContourSimplifier.simplify(contour, 0.8)`.
3. **Rastérisation Sub-Pixel :** `PolygonBuilder.rasterizePixelCenterContour(W, H, simplifiedContour, config.antialiasing())`.
4. **Préservation Sécurisée du Noyau :**
   $$\text{safeCore} = \text{MorphologyOps.erode}(\text{zoneBinary}, 1) \setminus \text{consolidatedBarriers}$$
   $$\text{mergedCoverage} = \text{coverage}.\max(\text{CoverageMask.fromBinaryMask}(\text{safeCore}))$$
5. **Garantie Absolue d'Exclusion Routière (Post-Rastérisation) :**
   Pour chaque pixel $(x, y)$ où $\text{roadBarriers}(x, y) == \text{true}$ ou $\text{territoryMask}(x, y) == \text{false}$ :
   $$\text{finalCoverage}.\text{set}(x, y, 0)$$
   **Théorème opérationnel :** Le taux d'inclusion de la chaussée dans la zone finale est strictement égal à $0{,}000\%$.

---

## 4. Interfaces Utilisateur, CLI & Paramétrage

### 4.1. Évolution des Modèles du Domaine

`com.sam102022.photoshop.core.model.OperationMode` :
- `AUTO`, `TERRITORY`, `ZONE`.

`com.sam102022.photoshop.core.model.SelectorColor` :
- `AUTO`, `RED`, `BLUE`, `MAGENTA`, `CYAN`.
- Méthodes `public static SelectorColor fromString(String name)` et `public boolean matches(int r, int g, int b)`.

`com.sam102022.photoshop.core.model.SnappingConfig` :
- Champs `OperationMode mode` et `SelectorColor zoneColor`.
- Constructeur de compatibilité injectant `OperationMode.AUTO` et `SelectorColor.AUTO`.

### 4.2. Options Ligne de Commande (`CliRunner`)

Nouvelles options CLI :
- `--mode <auto|territory|zone>` : mode d'opération (défaut : `auto`).
- `--zone-color <auto|red|blue|magenta|cyan>` (alias `-zc`) : couleur de la zone ciblée (insensible à la casse, alias français supportés : `rouge`, `bleu`, etc. ; défaut : `auto`).
- `--territory-mask <path>` (alias `-tm`) : masque optionnel du territoire global (formats PNG, JPG, BMP).

**Exemple d'invocation CLI :**
```bash
java -jar photoshop-1.0.jar --map carte.jpg --mask limites.jpg --mode zone --zone-color red --territory-mask mask_territoire.jpg --output zone1.png --mask-out mask_zone1.png
```

---

## 5. Gestion des Erreurs et Cas Limites

| Cas Limite | Comportement Attendu |
| :--- | :--- |
| **Cadre de couleur avec brèche $\le 10$ px** | Colmaté automatiquement par fermeture morphologique ($R_{\text{gap}} = 5$). |
| **Cadre de couleur avec brèche $> 10$ px** | Détection d'inondation totale $\rightarrow$ `IllegalStateException` demandant de fermer le contour. |
| **Plusieurs couleurs présentes en mode `AUTO`** | `IllegalStateException` listant les couleurs détectées et invitant à spécifier `--zone-color`. |
| **Couleur demandée absente de l'image** | `IllegalArgumentException` signalant que la couleur n'est pas présente dans le calque. |
| **Mode `--mode zone` sans aucun cadre coloré** | `IllegalArgumentException` avec code d'erreur CLI 1. |
| **Cadre coloré situé hors du territoire** | Détection de $S = \emptyset \rightarrow$ `IllegalStateException` indiquant qu'aucun pixel n'est dans le territoire. |
| **Dimensions d'images discordantes** | `IllegalArgumentException` spécifiant les résolutions respectives. |
| **Route tangente au bord vectoriel** | Forçage à 0 absolu post-rastérisation $\rightarrow$ garantie de 0 pixel de route inclus. |

---

## 6. Stratégie de Validation & Tests (TDD)

### 6.1. Tests Unitaires Dédiés
1. **`SelectorColorTest` :**
   - Vérification des signatures chromatiques `RED`, `BLUE`, `MAGENTA`, `CYAN`.
   - Rejet formel des teintes de route Google Maps et du vert territoire.
   - Parsing tolérant (noms anglais, français, casse variable).
2. **`ColorRegionSelectorExtractorTest` :**
   - Détection sur rectangle rouge, bleu, magenta, cyan.
   - Détection multi-couleurs simultanées sur une même image.
   - Réparation d'une brèche fine ($\le 5$ pixels) avec succès.
   - Échec contrôlé (`IllegalStateException`) sur cadre largement ouvert.
3. **`DarkDemarcationDetectorTest` :**
   - Détection des lignes sombres sans confusion avec les cadres colorés d'annotation.
   - Filtrage des artefacts isolés $< 15$ pixels.
4. **`ZoneBarrierConsolidatorTest` :**
   - Union étanche des routes, démarcations et de l'extérieur du territoire.
   - Colmatage des diagonales 8-connexes.
5. **`ZoneSegmentationEngineTest` :**
   - Sélection déterministe de la graine.
   - Inondation confinée pour différentes couleurs de cadre.
   - Règle de zéro absolu sur les pixels de chaussée.
   - Continuité sub-pixel anti-aliasée [1..254].
6. **`CliRunnerTest` :**
   - Validation des arguments `--mode` et `--zone-color`.
   - Échec explicite si pluralité de couleurs sans choix explicite.

### 6.2. Test d'Intégration Réel (`sample 02`) dans `IntegrationCliTest`
- Entrées : `carte à découper.jpg`, `limites.jpg`, `mask territoire.jpg`.
- Référence de comparaison : `mask zone 1.jpg`.
- **IoU Binaire au seuil 128 :** $\text{IoU} \ge 0.90$.
- **Taux d'Exclusion Routière Stricte :** $\text{TauxRoute} = 0.000$ (aucun pixel de route englobé).
- **Continuité Sub-Pixel :** Présence de pixels fractionnaires sur les frontières lisses.

### 6.3. Non-Régression
- Exécution de 100% de la suite de tests (`mvn test`).
- Respect strict des ADR-001 à ADR-014 (Java 21, SRP, complexité $\le 15$, zéro FQCN, Javadoc FR).
