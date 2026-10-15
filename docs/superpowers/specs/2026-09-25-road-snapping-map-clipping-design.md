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

Le programme extrait une portion d’une capture Google Maps à partir d’un masque grossier, généralement dessiné en vert ou généré approximativement.

Le résultat ne doit pas simplement reprendre le bord approximatif du masque. Il doit, lorsque les indices disponibles sont suffisamment fiables, rapprocher son contour des routes qui bordent la zone d’intérêt : autoroutes, routes principales et voies urbaines.

Le programme produit :
1. Une image PNG avec transparence, `clipped_output.png`, contenant la zone détourée.
2. Une image du masque affiné, `mask_output.png`.

Le traitement doit être disponible :
- en ligne de commande (CLI), pour l’automatisation ;
- dans une interface graphique Swing (GUI), activée par `--gui`.

Le masque vert définit la zone générale à conserver. Le détecteur de routes fournit des candidats de recalage près de son contour. Une route détectée ailleurs dans l’image ne doit pas modifier le résultat.

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
│   │   ├── RoadCandidateDetector.java
│   │   └── RoadDetector.java        # Détection des routes Google Maps (couleurs & contours Sobel)
│   ├── geometry/
│   │   ├── ContourExtractor.java
│   │   ├── ContourSimplifier.java
│   │   ├── RoadSnapper.java
│   │   └── PolygonBuilder.java
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

### Responsabilités

- **BinaryMask** : grille binaire utilisée pour représenter et manipuler un masque.
- **SnappingConfig** : paramètres du recalage, du lissage et de la détection.
- **GreenMaskExtractor** : extrait le masque de la zone à conserver.
- **RoadCandidateDetector** : repère les pixels ou régions susceptibles d’appartenir à une route.
- **RoadDetector** : façade conservée pour simplifier les appels du CLI et de la GUI ; elle expose les candidats routiers, pas des barrières destinées à bloquer un BFS.
- **ContourExtractor** : extrait et ordonne les contours du masque.
- **ContourSimplifier** : réduit le nombre de points sans déformer excessivement le contour.
- **RoadSnapper** : recherche des candidats routiers près du contour et déplace les points jugés fiables.
- **PolygonBuilder** : reconstruit un masque binaire à partir des contours recalés.
- **MorphologyOps** : opérations morphologiques ponctuelles, notamment pour nettoyer les masques.
- **RoadSnappingEngine** : orchestre l’extraction du contour, le recalage et la reconstruction.
- **ImageLoader** : charge les images et vérifie leur compatibilité.
- **ImageExporter** : produit le masque final et l’image PNG transparente.
- **CliRunner** et **MainWindow** : interfaces en ligne de commande et graphique.

---

## 3. Algorithme Détaillé

### 3.1. Extraction du Masque Vert (`GreenMaskExtractor`)

L’extracteur identifie la zone à conserver à partir de la couleur du calque.

Pour un calque en couleur, un pixel peut être considéré comme vert si :
- le canal alpha indique qu’il est suffisamment opaque ;
- et le vert domine nettement le rouge et le bleu, par exemple $G > R + 25$ et $G > B + 25$ ;
- ou si son analyse HSV indique une teinte verte, avec une saturation et une luminosité minimales.

Les seuils exacts doivent être réglables ou centralisés pour faciliter leur adaptation aux calques utilisés.

Un nettoyage morphologique léger peut supprimer le bruit isolé. Il doit être utilisé avec prudence : une ouverture trop forte risque d’effacer des bandes étroites ou des détails utiles.

L’extracteur doit aussi accepter les masques binaires ou en niveaux de gris, selon le format d’image chargé. Un pixel transparent ne doit pas être considéré comme actif.

Les composantes séparées ne doivent pas être éliminées silencieusement. Si le traitement ne recale que le plus grand contour, ce comportement doit être explicite et cohérent avec le résultat attendu.

### 3.2. Détection des candidats routiers (`RoadCandidateDetector`)

Le détecteur analyse la carte et produit un masque de candidats routiers. Ce masque sert à guider le recalage du contour.

#### Indices de couleur

Les couleurs orange, saumon, jaune ou crème peuvent indiquer des axes routiers principaux. Des règles initiales peuvent utiliser des plages telles que :
1. **Autoroutes et voies rapides** : pixels orange ou saumonés, par exemple R élevé, G intermédiaire et B plus faible.
2. **Routes principales et avenues** : pixels jaunes ou crème, par exemple R et G élevés, avec B sensiblement plus faible.

Ces plages sont des paramètres de départ, pas des valeurs universelles. La palette dépend du style de carte, du niveau de zoom et de l’image source.

#### Routes blanches ou gris clair

La luminosité seule ne permet pas de distinguer correctement une rue d’un bâtiment, d’un parking, d’un fond clair ou d’une zone pavée. Les pixels blancs ou gris clair ne doivent donc pas être classés comme routes sur leur seule couleur.

Une détection plus fiable de ces routes peut combiner plusieurs indices :
- deux bords approximativement parallèles ;
- une largeur relativement constante ;
- une forme allongée ;
- une orientation cohérente ;
- une continuité suffisante dans le voisinage.

#### Rôle du gradient Sobel

Le gradient Sobel peut servir à localiser des bords ou à fournir un indice complémentaire lorsqu’une route candidate a déjà été repérée. Il ne doit jamais suffire, à lui seul, à déclarer un pixel comme appartenant à une route : les bâtiments, textes, parkings et pictogrammes produisent également des gradients forts.

#### Nettoyage des candidats

Une fermeture morphologique légère peut combler de petites interruptions dans une région déjà identifiée comme candidate. Elle ne doit pas transformer des bords de bâtiments ou du texte en barrières routières.

Le résultat du détecteur représente des candidats routiers, et non une frontière garantie ou une barrière à traverser par propagation.

### 3.4. Recalage du contour (`RoadSnapper`)

Pour chaque point du contour :
1. Calculer la tangente locale du contour et sa normale extérieure.
2. Rechercher les candidats routiers dans une zone limitée autour du point, principalement dans la direction de cette normale.
3. Évaluer chaque candidat à partir de plusieurs indices :
   - distance au point d’origine ;
   - continuité des candidats le long de la route ;
   - orientation de la route par rapport au contour ;
   - cohérence avec les candidats des points voisins ;
   - éventuellement largeur et parallélisme des bords, lorsque ces indices sont disponibles.
4. Déplacer le point seulement si le meilleur candidat dépasse le seuil minimal de confiance.
5. Si aucun candidat n’est assez fiable, conserver le point d’origine.

Un pixel de route isolé, par exemple issu d’un texte ou d’un pictogramme, ne doit pas suffire à attirer le contour.

Le déplacement maximal est borné par `snapDistance`. Cette distance désigne le rayon de recherche ou le déplacement maximal autorisé autour du contour. Elle ne doit pas autoriser une propagation libre dans toute la zone dilatée du masque.

Après le recalage, un lissage modéré peut réduire les irrégularités. Le système doit limiter les déformations excessives et vérifier que le contour reconstruit ne s’auto-intersecte pas.

### 3.5. Reconstruction du masque (`PolygonBuilder` et `RoadSnappingEngine`)

`PolygonBuilder` rasterise les contours recalés pour reconstruire le masque final.
- Les composantes séparées et les trous doivent être préservés selon les contours extraits.
- Les pixels du noyau intérieur du masque initial doivent être protégés contre une perte accidentelle lors de la rasterisation.
- Si aucun point n’a été recalé de façon fiable, le résultat doit rester le masque initial, éventuellement nettoyé.
- L’absence de route détectée ne doit pas produire un masque vide.
- Le moteur ne doit pas éliminer une partie du masque uniquement parce qu’un faux positif a fragmenté une graine.

Le nouveau moteur est donc fondé sur le contour et la reconstruction polygonale. Une propagation BFS bloquée par `roadBarrier` ne constitue pas le mécanisme principal de recalage.

### 3.6. Export et transparence (`ImageExporter`)

L’image détourée est créée dans une `BufferedImage` de type `TYPE_INT_ARGB`.

Pour chaque pixel :
- si le masque final est actif, conserver la couleur originale de la carte ;
- si le masque final est inactif, mettre l’alpha à zéro ;
- près du contour, calculer éventuellement un alpha progressif sur une faible largeur pour réduire l’effet d’escalier.

L’anti-crénelage de l’image exportée doit rester distinct du masque binaire exporté. Le masque doit refléter la géométrie finale sans être modifié par le fondu alpha.

Les fichiers sont exportés au format PNG avec `ImageIO.write()`.

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

Les options liées au contour, comme la tolérance de simplification ou le seuil minimal de confiance, peuvent être ajoutées si elles sont nécessaires à l’usage.
Chaque paramètre doit avoir une définition claire et une valeur par défaut documentée.

### 4.2. Interface Graphique (GUI Swing)

La fenêtre `MainWindow` comprend :
- **Panneau supérieur** : boutons *Parcourir Carte...* et *Parcourir Calque...*, avec le nom et les dimensions des fichiers chargés.
- **Panneau central** : aperçu de la carte et du calque en surimpression, ainsi que l’image détourée sur un fond à damier pour visualiser la transparence.
- **Panneau de commandes** : réglages de sensibilité, de distance de recalage et de lissage, bouton *Détourer* et bouton *Exporter*.
- **Barre d’état** : progression, durée du traitement, nombre de pixels conservés et messages d’erreur.

Un mode de visualisation debug peut afficher séparément :
- la carte originale ;
- le masque initial ;
- les candidats routiers ;
- le contour initial ;
- le contour recalé ;
- le résultat final.

Ces vues doivent aider à diagnostiquer les faux accrochages sans modifier le résultat.

---

## 5. Gestion des Erreurs et Robustesse

| Situation | Comportement CLI | Comportement GUI |
| :--- | :--- | :--- |
| Fichier introuvable ou illisible | Message explicite sur `stderr`, code de sortie `1` | Alerte `JOptionPane` |
| Dimensions différentes entre carte et masque | Afficher les deux résolutions et terminer avec le code `1` | Afficher une erreur demandant des images concordantes |
| Aucun pixel actif détecté dans le masque | Afficher un avertissement et terminer avec le code `1` | Afficher l’erreur dans la barre d’état ou une alerte |
| Aucune route candidate fiable près du contour | Conserver le masque initial et signaler l’absence de recalage | Conserver le masque initial et afficher un avertissement |
| Réseau routier discontinu | Ne déplacer que les portions de contour ayant des candidats suffisamment fiables | Afficher le résultat et, si possible, les candidats détectés |
| Contour invalide ou trop petit pour former un polygone | Conserver le masque initial ou afficher une erreur explicite selon le cas | Afficher une erreur compréhensible |
| Paramètre hors limites | Afficher l’option et la valeur incorrectes, code de sortie `1` | Refuser la valeur et expliquer la limite |

---

## 6. Plan de Test (JUnit 5)

Dépendances à ajouter dans `pom.xml` : `org.junit.jupiter:junit-jupiter:5.10.2`.

### 6.1. BinaryMaskTest
- Vérifier les dimensions et les accès aux pixels.
- Vérifier la copie et les opérations binaires.
- Vérifier les opérations morphologiques de base.
- Vérifier le comportement aux limites de l’image.

### 6.2. GreenMaskExtractorTest
- Extraire un rectangle vert sur fond blanc.
- Vérifier plusieurs teintes de vert.
- Vérifier le traitement de pixels transparents.
- Vérifier le traitement des images binaires et en niveaux de gris.
- Vérifier qu’une image sans zone active produit un masque vide.
- Vérifier que le nettoyage ne supprime pas abusivement une forme étroite.

### 6.3. RoadCandidateDetectorTest
- Détecter une région orange synthétique.
- Détecter une région jaune ou crème synthétique.
- Ne pas classer un fond cartographique ordinaire comme route.
- Vérifier qu’un pixel isolé n’est pas considéré comme un indice suffisant pour déplacer un contour.
- Vérifier que le Sobel seul ne produit pas de candidats routiers.
- Tester séparément les limites connues de la détection des routes blanches.

### 6.4. ContourExtractorTest et ContourSimplifierTest
- Extraire le contour ordonné d’un rectangle.
- Vérifier les dimensions et la fermeture du contour.
- Vérifier la prise en charge des formes concaves.
- Vérifier le comportement pour un masque vide, un masque à un seul pixel et une forme très fine.
- Vérifier la conservation des composantes séparées et des trous selon les règles retenues.
- Vérifier que la simplification réduit le nombre de points tout en respectant sa tolérance.

### 6.5. RoadSnappingEngineTest
- Recaler un contour synthétique vers des axes routiers candidats continus.
- Vérifier qu’aucune route candidate ne renvoie le masque initial.
- Vérifier qu’un candidat isolé ne déplace pas le contour.
- Vérifier que les candidats au-delà de `snapDistance` sont ignorés.
- Vérifier que les portions sans candidat fiable conservent leur contour initial.
- Vérifier que le noyau intérieur du masque est préservé.
- Vérifier la reconstruction des composantes et des trous pris en charge.
- Vérifier le repli sur le masque initial en cas de géométrie invalide.

### 6.6. CliRunnerTest
- Vérifier `--help` et les arguments obligatoires manquants.
- Vérifier les valeurs invalides et les dimensions incompatibles.
- Vérifier un traitement complet avec des images temporaires.
- Vérifier la création des deux PNG attendus et la transparence de l’image détourée.

### 6.7. Tests avec des images de référence
Les images d’exemple du projet doivent servir de cas de référence pour examiner visuellement :
- le masque initial ;
- les candidats routiers ;
- le contour initial ;
- le contour recalé ;
- l’image finale.

Les seuils de couleur ne doivent pas être considérés comme universels : ils doivent être validés sur les styles et niveaux de zoom utilisés par le projet.
