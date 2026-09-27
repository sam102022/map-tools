# ADR-001 - Socle 100% Java standard sans dépendances natives (Java 21, Java2D, Swing)

## Statut

✅ Acceptée

## Contexte

Le détourage cartographique automatisé et le recalage sur un réseau routier font appel à des techniques de vision par ordinateur, de morphologie mathématique et de géométrie algorithmique. Dans ce domaine, la pratique courante consiste fréquemment à s'appuyer sur des bibliothèques tierces reconnues telles qu'OpenCV (via bindings JNI/JavaCV) ou GDAL.

Cependant, l'introduction de frameworks natifs engendre des contraintes lourdes pour le projet :
- Dépendance envers des binaires natifs précompilés spécifiques à chaque plateforme et architecture processeur (Windows x64, Linux amd64/arm64, macOS x64/Apple Silicon).
- Risques récurrents d'échecs de chargement de bibliothèques dynamiques (`UnsatisfiedLinkError`, conflits de DLLs).
- Fragilité et lenteur des pipelines d'intégration continue (CI/CD) devant configurer des environnements natifs spécifiques.
- Risques de plantages fatals de la JVM (*segmentation faults*) non interceptables par les mécanismes d'exception Java standard.
- Poids excessif des livrables et complexité de distribution pour les utilisateurs finaux.

## Décision

Il est formellement décidé de construire l'ensemble de l'application sur un **socle 100% Java standard (Java 21)**, sans **aucune dépendance d'exécution tierce ni liaison native** :

1. **Manipulation d'images et I/O :** Utilisation exclusive de l'API standard `java.awt.image.BufferedImage` et `javax.imageio.ImageIO` pour la lecture, la manipulation de matrices de pixels et l'export PNG avec canal alpha transparent.
2. **Géométrie vectorielle et rendu :** Exploitation du moteur Java2D (`java.awt.Graphics2D`, `java.awt.geom.Path2D`, `java.awt.AlphaComposite`, `RenderingHints`) pour le calcul sub-pixel, le lissage et le compositing d'images.
3. **Interface graphique :** Utilisation exclusive de Java Swing (`javax.swing.*`) pour l'IHM interactive optionnelle, garantissant une intégration graphique native et sans friction sous Windows, macOS et Linux.
4. **Algorithmes natifs réimplémentés en Java pur :** L'ensemble des algorithmes spécifiques (convolution Sobel, seuillages colorimétriques HSV/RGB, dilatation/érosion morphologique, Ramer-Douglas-Peucker, parcours BFS géodésique contraint) est développé de manière pure et autonome au sein de la codebase.
5. **Gestionnaire de dépendances :** Le fichier `pom.xml` ne contient aucune dépendance de production (seul JUnit Jupiter 5 est présent avec le scope `test`).

## Conséquences

*   **Positives :**
    *   Portabilité absolue : le fichier JAR généré s'exécute immédiatement sur n'importe quel système d'exploitation muni d'une JVM 21, sans installation de packages C++ préalables.
    *   Build Maven ultra-rapide et déterministe en environnement d'intégration continue.
    *   Stabilité et sécurité de la mémoire gérée par le Garbage Collector de la JVM.
    *   Maîtrise totale du code source et des structures de données sans boîte noire externe.
*   **Neutres / Contraintes :**
    *   Nécessite d'implémenter et de maintenir en interne les algorithmes de filtrage et de géométrie matricielle.
    *   Exige une attention particulière à l'allocation mémoire et à la localité du cache processeur lors de la manipulation de grands tableaux de pixels.
