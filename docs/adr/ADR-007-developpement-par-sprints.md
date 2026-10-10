# ADR-007 - Développement par sprints

## Statut

✅ Acceptée

## Contexte

Le développement d'un système de vision par ordinateur et de géométrie algorithmique pour le détourage cartographique automatique implique de nombreuses briques interdépendantes : manipulation matricielle de bas niveau, détection de motifs colorimétriques et de gradients, morphologie mathématique discrète, extraction de contours vectoriels, interfaces utilisateur et formats d'export.

Une approche monolithique ou non structurée entraînerait des difficultés d'intégration majeures, une dette technique incontrôlée et une impossibilité d'isoler les défaillances algorithmiques. Pour garantir une progression incrémentale, testable et validée à chaque étape, le développement est jalonné en sprints thématiques successifs.

## Décision

Le développement du projet est organisé selon une feuille de route incrémentale par sprints thématiques :

**Sprint 1 : Socle Technique & Modèle Matriciel**
- Configuration du projet sous Java 21, Maven et JUnit 5.
- Implémentation du modèle matriciel compact `BinaryMask` (représentation 1D, accesseurs optimisés, inversion).
- Modules d'entrée/sortie d'images (`ImageLoader`, `ImageExporter` avec gestion des canaux ARGB et fond transparent).
- Mise en place du modèle immuable de configuration `SnappingConfig`.

**Sprint 2 : Détection Colorimétrique & Candidats Routiers**
- Implémentation de l'extracteur de masque vert grossier (`GreenMaskExtractor`) avec discrimination robuste HSV/RGB.
- Détection des axes routiers Google Maps (`RoadCandidateDetector`) : autoroutes (orange/jaune), départementales (jaune/blanc), voies urbaines (blanc/gris clair).
- Détecteur composite `RoadDetector` combinant seuillage colorimétrique et analyse de contraste / filtres de Sobel.

**Sprint 3 : Morphologie Mathématique & Géométrie Algorithmique**
- Implémentation des opérations morphologiques matricielles (`MorphologyOps`) : dilatation, érosion, fermeture, ouverture, recherche de composantes connexes.
- Extraction de contours vectoriels discrets (`ContourExtractor`).
- Simplification de polygones par l'algorithme de Ramer-Douglas-Peucker (`ContourSimplifier`).
- Conversion et manipulation vectorielle Java2D (`PolygonBuilder`, `RoadSnapper`).

**Sprint 4 : Moteur d'Aimantation Géodésique (RoadSnappingEngine)**
- Orchestration du pipeline complet d'aimantation : contraction/dilatation contrainte du masque vert vers les barrières routières détectées.
- Algorithme de propagation géodésique (BFS avec limite de distance euclidienne / de Manhattan).
- Préservation de la topologie et repli automatique en cas de géométrie dégénérée ou d'absence de candidat routier.

**Sprint 5 : Interface Ligne de Commande (CLI) & Intégration**
- Implémentation du processeur en ligne de commande (`CliRunner`) pour l'exécution scriptable et batch.
- Validation des arguments, gestion des codes d'erreur et messages de progression.
- Suite complète de tests d'intégration de bout en bout (`IntegrationCliTest`) sur des échantillons cartographiques réels.

**Sprint 6 : Interface Graphique Interactive (GUI Swing)**
- Création de l'interface graphique interactive multi-panneaux (`MainWindow`) avec prévisualisation dynamique.
- Visualisation étape par étape : image source, masque vert extrait, réseau routier détecté, masque affiné et résultat détouré transparent.
- Panneau de réglage dynamique des hyperparamètres (seuils de tolérance, rayons morphologiques, options de lissage).

**Sprint 7 : Anti-Aliasing Sub-Pixel & Modèle de Couverture Continu**
- Introduction du modèle `CoverageMask` pour une gestion fine de l'opacité continue [0..255].
- Rendu de bordure vectorielle Java2D avec antialiasing sub-pixel (`Graphics2D`, `Path2D`).
- Formule de composition alpha exacte préservant la transparence source.
- Contrôles CLI (`--antialias` / `--no-antialias`) et IHM associés.

## Conséquences

*   **Positives :**
    *   Validation incrémentale : chaque sprint produit un sous-système immédiatement exécutable et testé unitairement.
    *   Gestion maîtrisée de la complexité algorithmique en séparant les problématiques matricielles, géométriques et d'interface.
    *   Visibilité claire sur l'avancement et la maturité des composants pour toute l'équipe.
*   **Neutres / Contraintes :**
    *   Nécessite de maintenir une suite de tests d'intégration robuste garantissant la non-régression entre chaque palier.
