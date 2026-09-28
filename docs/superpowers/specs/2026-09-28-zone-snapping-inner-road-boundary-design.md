# Spécification Technique : Découpage de Zone Interne et Exclusion de la Chaussée Routière

- **Date :** 2026-09-28
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé (Révisé suite à revue technique)
- **Langage / Environnement :** Java 21, Maven, Swing, Java2D (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Objectif

L'application permet d'extraire une portion de carte Google Maps à partir d'un calque vert et d'aligner automatiquement son contour extérieur sur le réseau routier environnant (mode "Territoire").

Dans un cas d'usage plus fin, un territoire global est lui-même subdivisé en plusieurs parcelles ou zones (par exemple la "Zone 1" d'un territoire communal ou intercommunal). Sur une capture Google Maps de type "Plan avec territoires et zones", ces zones internes sont délimitées par :
- Les axes routiers traversant ou bordant le secteur ;
- Des tracés ou démarcations internes sombres ;
- La frontière extérieure du territoire d'origine.

Pour découper une zone interne donnée, l'utilisateur trace grossièrement un cadre de couleur distinctive (rouge) sur le calque d'entrée (`limites.jpg`).

L'objectif de cette évolution est d'introduire le **découpage ciblé de zone interne** avec les exigences majeures suivantes :
1. **Rôle de sélecteur du cadre rouge :** Le tracé rouge n'est pas un contour géométrique rigide à déplacer pixel par pixel, mais un sélecteur de région d'intérêt permettant d'isoler la zone ciblée parmi les différentes zones du territoire.
2. **Exclusion absolue de la chaussée routière :** Le masque final doit s'arrêter précisément au **bord intérieur** de la chaussée. Les axes routiers délimitant la zone ne doivent en aucun cas être englobés : la surface de la route reste transparente / découpée, y compris après simplification vectorielle et rastérisation sub-pixel.
3. **Calage mixte & étanchéité :** Le masque épouse à la fois le bord intérieur des routes, les lignes sombres de démarcation internes et la frontière externe du territoire.
4. **Anti-aliasing sub-pixel sécurisé :** Le rendu vectoriel utilise `PolygonBuilder` pour générer un `CoverageMask` continu [0..255] assurant une transition douce et sans effet d'escalier, sans jamais réactiver un pixel de route.
5. **Mode d'exécution explicite :** Support des modes `--mode auto|territory|zone` (par défaut `auto`), avec diagnostic immédiat et compréhensible en cas d'absence de cadre valide.
6. **Support avec ou sans masque de territoire :** Le traitement peut être exécuté de façon autonome (le territoire étant déduit du vert de l'image de calque) ou guidé par un masque de territoire préexistant (`--territory-mask`).

---

## 2. Architecture Globale & Découpage Modulaire (SRP)

Conformément à l'**ADR-008 (Une seule responsabilité par classe)**, les fonctionnalités de découpage de zone sont encapsulées dans de nouveaux composants dédiés, préservant ainsi l'intégrité du moteur de territoire existant (`RoadSnappingEngine`).

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── detection/
│   │   ├── RedFrameExtractor.java        # NOUVEAU : Détection du sélecteur rouge, fermeture contrôlée et intérieur
│   │   ├── DarkDemarcationDetector.java  # NOUVEAU : Détection mesurable des lignes sombres de démarcation
│   │   ├── GreenMaskExtractor.java       # Existant : Détection du calque vert du territoire
│   │   ├── RoadCandidateDetector.java    # Existant : Détection des pixels candidats routiers
│   │   └── RoadDetector.java             # Existant : Façade de détection routière
│   ├── segmentation/
│   │   ├── ZoneBarrierConsolidator.java  # NOUVEAU : Consolidation étanche des barrières infranchissables
│   │   ├── ZoneSegmentationEngine.java   # NOUVEAU : Sélection déterministe de graine et BFS haute performance
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
│       ├── OperationMode.java            # NOUVEAU : Énumération AUTO, TERRITORY, ZONE
│       └── SnappingConfig.java           # Évolution : Ajout du mode d'opération
├── cli/
│   └── CliRunner.java                    # Évolution : Options --mode, --territory-mask et messages clairs
├── gui/
│   ├── FileSelectionPanel.java           # Évolution : Sélecteur optionnel de masque de territoire
│   └── MainWindow.java                   # Évolution : Prise en charge du mode zone
```

---

## 3. Algorithmes Détaillés

### 3.1. Détection et Réparation du Sélecteur Rouge (`RedFrameExtractor`)

#### A. Signature Chromatique
Un pixel $(R, G, B)$ de l'image de calque est classé comme rouge si :
$$R \ge 150 \quad \text{et} \quad R > G + 40 \quad \text{et} \quad R > B + 40$$
En coordonnées HSB (Hue, Saturation, Brightness) :
- Saturation $S \ge 0.35$ (rejette les teintes beiges, grises ou désaturées).
- Luminosité $B \ge 0.20$ (rejette les ombres sombres).

#### B. Réparation des Brèches et Fermeture Limitée
Un tracé au pinceau ou à main levée peut présenter des micro-coupures.
1. **Fermeture morphologique bornée :**
   Une fermeture morphologique (`MorphologyOps.close`) est appliquée avec un rayon maximal paramétrable $R_{\text{gap}} = 5$ pixels.
2. **Contrôle d'étanchéité par inondation extérieure :**
   - Une grille binaire est initialisée.
   - Les pixels accessibles depuis les 4 bords de l'image sans franchir le tracé rouge fermé sont inondés (BFS 4-connectivité depuis $(0, 0)$, $(W-1, 0)$, $(0, H-1)$, $(W-1, H-1)$).
   - Les pixels non atteints par l'extérieur constituent le domaine intérieur candidat.
3. **Validation de boucle fermée exploitable :**
   - Si l'inondation extérieure atteint l'ensemble de l'image (aucun pixel intérieur résiduel), le cadre est ouvert avec une brèche $> 2 \times R_{\text{gap}}$.
   - **Décision stricte :** Dans ce cas, l'algorithme ne se rabat **pas** sur la boîte englobante pour deviner l'intérieur. Si le mode actif est `ZONE` ou si un cadre rouge discontinu a été détecté en mode `AUTO`, une exception descriptive est levée :
     `IllegalStateException: "Le tracé rouge présente une brèche non colmatable (> 10 pixels). Veuillez fermer le tracé du cadre rouge entourant la zone."`
4. **Masque intérieur réel :**
   Le masque `interiorMask` retourné correspond strictement aux pixels situés à l'intérieur de la boucle rouge fermée (hors tracé rouge lui-même), avec une aire minimale de 500 pixels.

---

### 3.2. Détection Mesurable des Démarcations Sombres (`DarkDemarcationDetector`)

Les captures Google Maps avec option "Afficher zones" comportent des tracés de démarcation sombres dessinés en surimpression sur `limites.jpg`.

Pour distinguer ces délimitations des bâtiments, textes isolés ou ombres :
1. **Alignement spatial rigoureux :** `carte` et `limites` partagent exactement le même repère pixel $(x, y)$.
2. **Formule de luminance standard UIT-R BT.601 :**
   $$Y(R, G, B) = 0.299\,R + 0.587\,G + 0.114\,B$$
3. **Critères de détection d'une démarcation cartographique :**
   Un pixel $(x, y)$ est un candidat de démarcation sombre si :
   - Il appartient à la zone d'influence du territoire ($G_{\text{limites}} > R_{\text{limites}} + 15$ ou présent dans `territoryMask`).
   - Luminance dans le calque : $Y(\text{limites}) \le 125$.
   - Contraste avec la carte brute : $Y(\text{carte}) - Y(\text{limites}) \ge 45$ (le calque a assombri ce pixel par un tracé de ligne).
   - Non-rouge : le pixel n'appartient pas au sélecteur rouge ($R < G + 30$).
4. **Filtrage des artéfacts et textes isolés :**
   Les chiffres de numérotation de zone ou artefacts ponctuels sont filtrés :
   - Élimination des composantes connexes sombres de taille $< 15$ pixels.
   - Les pixels restants sont consolidés dans un masque binaire `darkDemarcations`.

---

### 3.3. Consolidation Étanche des Barrières (`ZoneBarrierConsolidator`)

Le masque global des barrières infranchissables `barriers` combine :
1. **Axes routiers (`roadBarriers`) :**
   Issus de `RoadCandidateDetector.detect(carte)`. Chaque pixel classé comme route forme un obstacle infranchissable.
2. **Démarcations internes (`darkDemarcations`) :**
   Issues de `DarkDemarcationDetector`.
3. **Frontière du territoire (`territoryBarriers`) :**
   - Si `territoryMask` est injecté : $\text{barrière} = \neg \text{territoryMask}$.
   - Sinon : le territoire est extrait par `GreenMaskExtractor.extract(limites)`, et $\text{barrière} = \neg \text{territoireVert}$.
4. **Colmatage des diagonales (Étanchéité 4-connectivité vs 8-connectivité) :**
   Pour éviter qu'une inondation en 4-connectivité ne passe au travers de deux pixels de barrière disposés en diagonale (coin à coin) :
   - Application d'une fermeture morphologique de rayon 1 :
     $$\text{consolidatedBarriers} = \text{MorphologyOps.close}(\text{barriers}, 1)$$
   - Ainsi, aucune fuite diagonale à 1 pixel n'est possible.

---

### 3.4. Sélection Déterministe de la Graine et Inondation Haute Performance (`ZoneSegmentationEngine`)

#### A. Domaine de Recherche de la Graine ($S$)
Le domaine admissible pour planter la graine est défini par :
$$S = \text{interiorMask} \cap \text{territoryMask} \setminus \text{consolidatedBarriers}$$

Si $S = \emptyset$ (aucun pixel libre à l'intérieur du cadre dans le territoire), l'algorithme lève immédiatement une exception explicite :
`IllegalStateException: "Impossible d'initialiser la zone : l'intérieur du cadre rouge ne contient aucun pixel libre dans le territoire (surface entièrement occupée par des barrières ou hors territoire)."`

#### B. Transformée de Distance et Traitement Déterministe des Égalités
1. Pour chaque pixel $(x, y) \in S$, on calcule sa distance de Manhattan ou euclidienne minimale aux frontières de $S$ (pixels hors de $S$).
2. Soit $D_{\max} = \max_{(x, y) \in S} \text{dist}(x, y)$.
3. Ensemble des candidats optimaux :
   $$C = \{ (x, y) \in S \mid \text{dist}(x, y) = D_{\max} \}$$
4. **Départage déterministe (sans ambiguïté) :**
   - On calcule le centroïde $(\bar{x}, \bar{y})$ de l'ensemble $S$.
   - On sélectionne dans $C$ le point minimisant la distance euclidienne au centroïde :
     $$(x_0, y_0) = \arg\min_{(x, y) \in C} \left( (x - \bar{x})^2 + (y - \bar{y})^2 \right)$$
   - En cas d'égalité résiduelle absolue, sélection par ordre lexicographique strict : plus petit $y$, puis plus petit $x$.

#### C. Inondation BFS Haute Performance (Zéro Allocation d'Objets)
Pour éviter la surcharge mémoire et le GC churn d'une file contenant des millions d'objets `java.awt.Point` sur une image $3441 \times 2409$ (8,3 millions de pixels) :
- Utilisation d'un buffer linéaire plat `int[] queue = new int[W * H]` indexé par $idx = y \times W + x$.
- Pointeur de lecture `head` et pointeur d'écriture `tail` primitifs.
- Grille binaire `visited` (instance de `BinaryMask`).
- **Confinement strict :** Un voisin $(nx, ny)$ n'est ajouté dans la file que si :
  1. $0 \le nx < W$ et $0 \le ny < H$.
  2. $\text{interiorMask}(nx, ny) == \text{true}$ (strictement à l'intérieur du cadre rouge).
  3. $\text{consolidatedBarriers}(nx, ny) == \text{false}$ (pixel non bloquant / hors route).
  4. $\text{visited}(nx, ny) == \text{false}$.
- Le masque `zoneBinary` résultant est formé par les pixels visités.

---

### 3.5. Vectorisation Sub-Pixel et Garantie Absolue d'Exclusion des Routes

1. **Extraction de Contour :**
   `ContourExtractor.extractLargestContour(zoneBinary)` extrait le polygone fermé ordonné.
2. **Simplification RDP Contrôlée :**
   `ContourSimplifier.simplify(contour, 0.8)` élimine les crénelages orthogonaux sans dévier de plus de 0,8 pixel.
3. **Rastérisation Continue :**
   `PolygonBuilder.rasterizePixelCenterContour(W, H, simplifiedContour, config.antialiasing())` produit un `CoverageMask` continu $[0..255]$.
4. **Garantie Absolue d'Exclusion Routière (Post-Rastérisation) :**
   Pour neutraliser tout débordement introduit par l'interpolation polygonale ou la préservation du noyau :
   - Le noyau intérieur préservé est d'abord assaini :
     $$\text{safeCore} = \text{MorphologyOps.erode}(\text{zoneBinary}, 1) \setminus \text{consolidatedBarriers}$$
   - La couverture intermédiaire est unie avec ce noyau assaini :
     $$\text{mergedCoverage} = \text{coverage}.\max(\text{CoverageMask.fromBinaryMask}(\text{safeCore}))$$
   - **Masquage d'exclusion strict final :**
     Pour chaque pixel $(x, y)$ où $\text{roadBarriers}(x, y) == \text{true}$ ou $\text{territoryMask}(x, y) == \text{false}$ :
     $$\text{finalCoverage}.\text{set}(x, y, 0)$$
   **Théorème opérationnel :** Aucun pixel appartenant à un axe routier candidat ou situé hors du territoire ne peut avoir une valeur $> 0$ dans le masque final. Le taux de pixels routiers dans la zone est mathématiquement nul ($0{,}0\%$).

---

## 4. Interfaces Utilisateur, CLI & Paramétrage

### 4.1. Énumération `OperationMode` & Configuration

Création de `com.sam102022.photoshop.core.model.OperationMode` :
```java
public enum OperationMode {
    AUTO,      // Détecte automatiquement la présence d'un cadre rouge fermé
    TERRITORY, // Force le mode Territoire global (ignore les éventuels tracés rouges)
    ZONE       // Force le mode Zone (lève une exception si aucun cadre rouge valide n'est présent)
}
```

Évolution du record `SnappingConfig` :
- Champ `OperationMode mode` (valeur par défaut dans `defaults()` : `OperationMode.AUTO`).
- Méthode `withMode(OperationMode mode)`.

### 4.2. Options Ligne de Commande (`CliRunner`)

Nouvelles options CLI :
- `--mode <auto|territory|zone>` : spécifie explicitement le mode opératoire (insensible à la casse, valeur par défaut `auto`).
- `--territory-mask <path>` (alias `-tm`) : chemin vers une image de masque de territoire global existant.

**Règles de parsing de `--territory-mask` :**
- Support des formats PNG, JPG, BMP.
- Si l'image comporte un canal Alpha : pixel actif si $\text{alpha} \ge 128$ et (si RGB) luminance $\ge 128$.
- Si l'image est sans canal Alpha (ex: `TYPE_BYTE_GRAY` ou RGB) : pixel actif si luminance $\ge 128$.
- Rejet immédiat si les dimensions différent de l'image de carte principale.

**Comportement de l'aiguillage :**
1. Si `--mode zone` :
   - Recherche d'un cadre rouge fermé via `RedFrameExtractor`.
   - Si absent ou non fermé : arrêt avec erreur `IllegalArgumentException: "Mode 'zone' exigé mais aucun cadre rouge fermé valide n'a été trouvé dans le calque."`.
2. Si `--mode territory` :
   - Exécution du moteur standard `RoadSnappingEngine` (le tracé rouge est ignoré).
3. Si `--mode auto` :
   - Analyse du calque. Si un cadre rouge fermé d'aire $\ge 500$ pixels est détecté $\rightarrow$ bascule transparente vers le mode **Zone**.
   - Sinon $\rightarrow$ exécution du mode historique **Territoire**.

---

## 5. Gestion des Erreurs et Cas Limites

| Cas Limite | Comportement Attendu |
| :--- | :--- |
| **Cadre rouge avec brèche $\le 10$ px** | Colmaté automatiquement par fermeture morphologique ($R_{\text{gap}} = 5$). Traitement nominal. |
| **Cadre rouge avec brèche $> 10$ px** | Détection d'inondation extérieure totale $\rightarrow$ `IllegalStateException` explicite demandant la fermeture du tracé. |
| **Mode `--mode zone` sans cadre** | `IllegalArgumentException` avec code de retour CLI non-zéro (1) et message d'aide. |
| **Cadre rouge hors territoire** | Détection de $S = \emptyset \rightarrow$ `IllegalStateException` indiquant qu'aucun pixel de zone n'est dans le territoire. |
| **Dimensions d'images discordantes** | `IllegalArgumentException` spécifiant les résolutions respectives de la carte, du calque et du masque. |
| **Barrières en diagonale 8-connexes** | Colmatées par la fermeture rayon 1 $\rightarrow$ fuite impossible en 4-connectivité. |
| **Route tangente au bord vectoriel** | Mise à zéro absolue post-rastérisation $\rightarrow$ garantie de 0 pixel de route inclus. |

---

## 6. Stratégie de Validation & Tests (TDD)

### 6.1. Tests Unitaires Dédiés
1. **`RedFrameExtractorTest` :**
   - Détection sur tracé rouge fermé nominal.
   - Réparation d'une brèche fine ($\le 5$ pixels) avec succès.
   - Échec contrôlé (`IllegalStateException`) sur cadre largement ouvert (brèche $> 10$ pixels).
   - Rejet des teintes orangées, saumon et fonds de carte cartographiques ordinaires.
   - Extraction exacte du masque intérieur `interiorMask`.
2. **`DarkDemarcationDetectorTest` :**
   - Détection des lignes sombres avec fort différentiel de luminance ($Y_C - Y_L \ge 45$).
   - Filtrage des composantes trop petites ($< 15$ pixels).
3. **`ZoneBarrierConsolidatorTest` :**
   - Union étanche des routes, démarcations et de l'extérieur du territoire.
   - Neutralisation des passages diagonaux 8-connexes.
4. **`ZoneSegmentationEngineTest` :**
   - Sélection déterministe de la graine sur un cas de symétrie (test du départage par centroïde puis lexicographique).
   - Inondation d'une zone bordée de routes : vérification que les routes restent strictement à zéro.
   - Garantie que la simplification vectorielle ne réactive aucun pixel routier.
   - Vérification de l'anti-aliasing sub-pixel [1..254] sur les bordures non-routières.
5. **`CliRunnerTest` :**
   - Validation des arguments `--mode auto`, `--mode zone`, `--mode territory`.
   - Échec explicite si `--mode zone` sans cadre rouge.
   - Chargement et seuillage correct de `--territory-mask` (niveaux de gris, ARGB).

### 6.2. Test d'Intégration Réel (`sample 02`) dans `IntegrationCliTest`
Un test grandeur nature est intégré dans `IntegrationCliTest` avec les images de `src/main/resources/sample 02/` :
- Entrées : `carte à découper.jpg`, `limites.jpg` (avec cadre rouge tracé autour de la zone 1), `mask territoire.jpg`.
- Référence de comparaison : `mask zone 1.jpg`.
- **Métrique 1 — IoU Binaire (Seuil 128) :**
  $$\text{IoU} = \frac{|M_{\text{généré}} \cap M_{\text{référence}}|}{|M_{\text{généré}} \cup M_{\text{référence}}|}$$
  Exigence : $\text{IoU} \ge 0.90$.
- **Métrique 2 — Taux d'Exclusion Routière Stricte :**
  $$\text{TauxRoute} = \frac{|\{ (x, y) \mid \text{mask}(x, y) \ge 128 \land \text{roadCandidates}(x, y) = \text{true} \}|}{|\{ (x, y) \mid \text{mask}(x, y) \ge 128 \}|}$$
  Exigence : $\text{TauxRoute} = 0.000$ (aucun pixel de route englobé).
- **Métrique 3 — Continuité Sub-Pixel :**
  Présence confirmée de pixels de couverture partielle [1..254] sur le contour extérieur pour valider l'anti-aliasing.

### 6.3. Non-Régression
- Exécution de l'intégralité de la suite de tests du projet via `mvn test`.
- Taux de succès requis : **100%**.
- Respect strict des standards : complexité cognitive $\le 15$, Javadoc complète en français sur toutes les méthodes, zéro FQCN.
