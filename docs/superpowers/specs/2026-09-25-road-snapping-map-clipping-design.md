# Spécification Technique : Détourage de Carte Google Maps Suivant les Axes Routiers

- **Date :** 2026-09-25
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé
- **Langage / Environnement :** Java 21, Maven, Swing, Java2D (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Objectif

L'objectif est de réaliser un programme Java permettant d'extraire / détourer une portion d'une capture Google Maps à partir d'un calque grossier vert (dessiné à la main ou généré approximativement). 

Le détourage ne doit pas se limiter au contour brut du calque vert : il doit s'aimanter / s'aligner précisément (snapping) sur les **axes routiers environnants** (autoroutes, départementales, voies urbaines) qui ceinturent ou bordent la zone ciblée.

Le système doit produire :
1. Une image PNG finale avec fond transparent (`clipped_output.png`) ne contenant que la zone détourée.
2. Une image du masque affiné aligné sur les routes (`mask_output.png`).

L'application doit fonctionner en **ligne de commande (CLI)** pour l'automatisation, et proposer une **interface graphique (GUI Swing)** optionnelle via l'option `--gui`.

---

## 2. Architecture Globale & Découpage Modulaire

Le projet est structuré au sein du package `com.sam102022.photoshop` :

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   ├── BinaryMask.java          # Grille booléenne 2D pour manipulations matricielles rapides
│   │   └── SnappingConfig.java      # Configuration (seuils de couleur, tolérance, lissage, etc.)
│   ├── detection/
│   │   ├── GreenMaskExtractor.java  # Détection robuste de la composante verte dans le calque
│   │   └── RoadDetector.java        # Détection des routes Google Maps (couleurs & contours Sobel)
│   └── segmentation/
│       ├── MorphologyOps.java       # Érosion, dilatation, fermeture, composantes connexes
│       └── RoadSnappingEngine.java  # Moteur d'expansion/rétraction contrainte par barrières routières
├── io/
│   ├── ImageLoader.java             # Chargement BufferedImage et vérification de conformité des dimensions
│   └── ImageExporter.java           # Export du PNG avec transparence ARGB et export du masque affiné
├── cli/
│   └── CliRunner.java               # Parseur d'arguments CLI et orchestration du mode console
├── gui/
│   └── MainWindow.java              # Fenêtre Swing avec prévisualisation avant/après et curseurs
└── Main.java                        # Point d'entrée principal (aiguillage CLI / GUI)
```

---

## 3. Algorithme Détaillé

### 3.1. Extraction du Masque Vert (`GreenMaskExtractor`)
- Détection des pixels du calque grossier ayant une dominante verte :
  - Soit canal Alpha > 0 et couleur de composante verte nettement supérieure aux composantes rouge et bleue ($G > R + 25$ et $G > B + 25$).
  - Soit analyse de teinte HSV ($H \in [70^\circ, 170^\circ]$ avec Saturation $> 0.2$ et Valeur $> 0.2$).
- Nettoyage du masque : suppression du bruit isolé par une ouverture morphologique (érosion 1px suivie d'une dilatation 1px).

### 3.2. Détection des Axes Routiers Google Maps (`RoadDetector`)
Analyse de chaque pixel de la carte Google Maps pour classifier les types de routes :
1. **Autoroutes & Voies rapides :** teintes orange/saumonées ($R \in [220, 255]$, $G \in [140, 215]$, $B \in [60, 150]$ avec $R > G$).
2. **Routes principales & avenues :** teintes jaunes/crèmes ($R \in [230, 255]$, $G \in [210, 255]$, $B \in [110, 200]$ avec $R \approx G > B$).
3. **Rues secondaires & urbaines :** teintes blanc cassé / gris clair ($R, G, B > 230$ avec contraste local).
4. **Détection de contours (Gradient Sobel) :** pour capter les bordures des routes blanches qui tranchent avec le fond cartographique grisâtre/beige.
5. **Génération de la Barrière Routière :**
   - Combinaison des détections de couleur et de gradient dans une matrice binaire `roadBarrier`.
   - **Fermeture morphologique (Closing) :** dilatation de rayon 2 à 3 pixels suivie d'une érosion identique pour ponter les brèches causées par les étiquettes textuelles (noms de rues), passages piétons et ponts.

### 3.3. Snapping et Détourage Morphologique (`RoadSnappingEngine`)
1. **Extraction de la graine intérieure ($Seed$) :**
   - Érosion prononcée du masque vert grossier pour obtenir un noyau central certain, situé strictement à l'intérieur de la zone d'intérêt et éloigné des bords approximatifs.
2. **Expansion contrainte (Propagation par file d'attente / Geodesic Dilation) :**
   - Depuis le noyau, une propagation BFS (Breadth-First Search) s'étend vers l'extérieur.
   - La propagation est bloquée dès qu'un pixel appartient à `roadBarrier`.
   - La distance maximale de propagation est bornée par `snapDistance` (par défaut 40px) par rapport au masque initial pour éviter d'inonder la carte entière en cas de route manquante.
3. **Rétraction des zones hors-barrières :**
   - Tout pixel du masque initial vert qui se trouve séparé du noyau par une route continue est exclu.
4. **Lissage des contours (Anti-Aliasing) :**
   - Application d'un masque de fondu progressif (alpha gradient de 1 à 2 pixels sur le contour) pour éviter les effets d'escalier lors du découpage de l'image.

### 3.4. Export et Masquage (`ImageExporter`)
- Création d'une nouvelle `BufferedImage` de type `TYPE_INT_ARGB`.
- Pour chaque pixel $(x, y)$ :
  - Si le masque final est à 1 : couleur d'origine de la carte avec alpha calculé (alpha = 255 à l'intérieur, alpha progressif sur le bord).
  - Si le masque final est à 0 : alpha = 0 (pixel totalement transparent).
- Export PNG via `ImageIO.write()`.

---

## 4. Spécification des Interfaces

### 4.1. Ligne de commande (CLI)
Exemple d'exécution :
```bash
java -jar photoshop.jar --map path/to/google_map.png --mask path/to/rough_mask.png --output clipped.png --mask-out refined_mask.png
```

Paramètres :
- `--map <path>` : Fichier image de la carte (obligatoire hors `--gui`).
- `--mask <path>` : Fichier image du calque vert (obligatoire hors `--gui`).
- `--output <path>` : Chemin de l'image détourée finale PNG (défaut : `clipped_output.png`).
- `--mask-out <path>` : Chemin du masque affiné sauvegardé (défaut : `mask_output.png`).
- `--snap-distance <int>` : Portée maximale d'ajustement en pixels (défaut : `40`).
- `--road-sensitivity <float>` : Facteur de sensibilité de détection des routes de 0.5 à 2.0 (défaut : `1.0`).
- `--smooth <int>` : Rayon de lissage des contours en pixels (défaut : `1`).
- `--gui` : Démarre l'interface graphique interactive Swing.
- `--help` / `-h` : Affiche l'aide complète et quitte.

### 4.2. Interface Graphique (GUI Swing)
- Fenêtre principale `MainWindow` avec :
  - Panneau supérieur : Boutons `Parcourir Carte...` et `Parcourir Calque...` avec labels affichant les fichiers chargés et leurs dimensions.
  - Panneau central : Affichage côte à côte ou onglets (Carte originale + Calque vert en surimpression / Image détourée résultante sur fond damier de transparence).
  - Panneau de commande : Sliders pour `Sensibilité routes`, `Distance de calage`, `Lissage`, bouton `Détourer`, bouton `Exporter`.
  - Barre de statut en bas : messages de progression, temps de calcul et erreurs éventuelles.

---

## 5. Gestion des Erreurs et Robustesse

| Cas d'erreur                                 | Comportement CLI                                                  | Comportement GUI                                            |
|----------------------------------------------|-------------------------------------------------------------------|-------------------------------------------------------------|
| Fichier introuvable ou illisible             | Affiche un message d'erreur sur `stderr` et exit(1)               | Pop-up d'alerte `JOptionPane`                               |
| Dimensions différentes entre carte et masque | Message explicite indiquant les deux résolutions et exit(1)       | Pop-up d'erreur invitant à fournir deux images concordantes |
| Aucun pixel vert détecté dans le masque      | Avertissement console, masque vide produit, exit(1)               | Notification dans la barre de statut                        |
| Réseau routier discontinu / non détecté      | Le snapping s'arrête à `snapDistance`, préserve la forme générale | Avertissement visuel sur la carte                           |

---

## 6. Plan de Test (JUnit 5)

Dépendances à ajouter dans `pom.xml` : `org.junit.jupiter:junit-jupiter:5.10.2`.

1. **`BinaryMaskTest` :** Vérification des opérations matricielles (dimensions, get/set, clonage, érosion et dilatation de base).
2. **`GreenMaskExtractorTest` :**
   - Image avec fond blanc et rectangle vert pur -> extraction parfaite.
   - Image avec différentes teintes de vert (kaki, émeraude, vert clair translucide) -> bonne détection.
   - Image sans composante verte -> masque vide (`count == 0`).
3. **`RoadDetectorTest` :**
   - Détection d'une ligne orange (autoroute) et d'une ligne jaune (route principale).
   - Test de la fermeture morphologique : vérification qu'une coupure de 2 pixels dans une route est bien rebouchée.
4. **`RoadSnappingEngineTest` :**
   - Test d'alignement synthétique : une boîte de 100x100 bordée par des routes avec un calque grossier difforme (dépassant de 10px à gauche, en retrait de 10px à droite). Vérification que le masque résultant s'aligne exactement sur le cadre routier.
5. **`CliRunnerTest` :**
   - Gestion des arguments `--help`, validation des arguments manquants.
   - Exécution d'un scénario complet de bout en bout produisant les deux fichiers PNG attendus.
