# ADR-008 - Une seule responsabilité par classe (Single Responsibility Principle)

## Statut

✅ Acceptée

## Contexte

Dans les applications de traitement d'images combinant algorithmes de vision par ordinateur et interfaces graphiques, la tentation est fréquente de concentrer dans une même classe le chargement d'images, le filtrage de pixels, les calculs géométriques, l'affichage à l'écran et la sauvegarde sur disque. 

Ces classes "omniscientes" (ou *God Classes*) engendrent un couplage fort, rendent l'écriture de tests unitaires extrêmement difficile, et augmentent exponentiellement le risque de régression à chaque modification de code.

## Décision

Il est formellement décidé que chaque classe, interface ou record de l'application doit respecter scrupuleusement le principe de responsabilité unique (**Single Responsibility Principle - SRP**). Une classe ne doit avoir qu'une seule et unique raison de changer.

L'architecture du projet est découpée en couches étanches avec des attributions précises :

1. **Modèles de Domaine (`com.sam102022.photoshop.core.model`) :**
   * `BinaryMask` : stockage et manipulation matricielle 2D booléenne uniquement.
   * `CoverageMask` : stockage d'une grille de couverture continue [0..255] et conversions associées.
   * `SnappingConfig` : transport immuable des paramètres et hyperparamètres de l'algorithme, sans logique de traitement.

2. **Détection de Motifs (`com.sam102022.photoshop.core.detection`) :**
   * `GreenMaskExtractor` : isolation exclusive de la composante verte du masque grossier.
   * `RoadCandidateDetector` : identification spectrale des pixels de routes (autoroutes, départementales, voies urbaines).
   * `RoadDetector` : combinaison multi-critères (couleur et gradients de Sobel).

3. **Géométrie Algorithmique (`com.sam102022.photoshop.core.geometry`) :**
   * `ContourExtractor` : extraction ordonnée des pixels de contour à partir d'un masque binaire.
   * `ContourSimplifier` : simplification polygonale via l'algorithme Ramer-Douglas-Peucker.
   * `PolygonBuilder` : construction de polygones vectoriels et objets `Shape` AWT.
   * `RoadSnapper` : projection et recalage local des sommets vectoriels sur les axes candidats.

4. **Segmentation & Morphologie (`com.sam102022.photoshop.core.segmentation`) :**
   * `MorphologyOps` : primitives mathématiques pures (dilatation, érosion, ouverture, fermeture, analyse de connexité).
   * `RoadSnappingEngine` : orchestration du pipeline de recalage géodésique, sans I/O ni rendu graphique direct.

5. **Entrées / Sorties (`com.sam102022.photoshop.io`) :**
   * `ImageLoader` : lecture sécurisée, décodage et validation des fichiers images sources.
   * `ImageExporter` : application du canal alpha (avec ou sans lissage/anticrénelage) et encodage PNG transparent.

6. **Interfaces Utilisateur (`com.sam102022.photoshop.cli` & `.gui`) :**
   * `CliRunner` : parsing des arguments en ligne de commande, validation des options et pilotage du moteur en mode headless.
   * `MainWindow` : rendu de la fenêtre Swing, gestion des événements de l'IHM et visualisation interactive.

## Conséquences

*   **Positives :**
    *   Testabilité unitaire maximale : chaque algorithme peut être testé de manière isolée sur des matrices ou images synthétiques simples sans mock complexe.
    *   Composabilité : le moteur de traitement peut être invoqué à l'identique depuis la CLI ou depuis l'IHM Swing.
    *   Facilité de refactoring : modifier l'algorithme d'anticrénelage ou le filtre Sobel n'affecte en rien l'IHM ou les I/O.
*   **Neutres / Contraintes :**
    *   Augmentation du nombre de fichiers sources, nécessitant une structure de packages claire et documentée.
