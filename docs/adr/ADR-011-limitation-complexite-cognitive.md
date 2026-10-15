# ADR-011 - Limitation de la complexité cognitive du code et découpage en sous-méthodes

## Statut

✅ Acceptée

## Contexte

Dans le développement d'algorithmes de traitement d'images et de vision par ordinateur (parcours de propagation BFS, convolutions matricielles de Sobel, calculs de gradients, simplification géométrique de Ramer-Douglas-Peucker, composition alpha sub-pixel), le code manipule fréquemment des boucles imbriquées sur les coordonnées spatiales $(x, y)$, des tests de bornes matricielles et de multiples conditions de seuillage colorimétrique.

Une complexité cognitive trop élevée au sein d'une même méthode nuit gravement à la lisibilité, à la maintenabilité et à la testabilité du code, tout en augmentant considérablement le risque d'introduire des régressions subtiles (bugs de décalage d'un pixel / *off-by-one*, conditions de bord mal gérées, boucles infinies). Pour maintenir les standards d'excellence technique du projet (ADR-008), il est indispensable d'imposer une limite quantitative stricte.

## Décision

Il est formellement décidé de limiter strictement la complexité cognitive de toute méthode du projet :

1. **Seuil maximal :** La complexité cognitive d'une méthode individuelle ne doit jamais dépasser le score de **15** (selon la métrique SonarQube / Cognitive Complexity).
2. **Découpage systématique :** Dès qu'une méthode dépasse ou approche cette limite, elle doit être subdivisée en sous-méthodes privées, pures et ciblées sur une sous-étape logique précise :
   * Isolation de la vérification des bornes d'un masque (`isInside(x, y, w, h)`).
   * Extraction et itération sur les voisins 4-connexes ou 8-connexes.
   * Calcul unitaire d'un pixel (ex : formule de convolution Sobel en un point, distance d'un point à un segment).
   * Conversion colorimétrique ou seuillage individuel ARGB.
3. **Application universelle :** Cette règle s'applique à l'ensemble du code de production rédigé par les développeurs ainsi qu'à tout code généré ou refactorisé par les agents automatisés.

## Conséquences

*   **Positives :**
    *   Lisibilité remarquable du code : chaque méthode principale lit comme un scénario de haut niveau orchestrant des sous-méthodes expressives.
    *   Facilité accrue de tests unitaires ciblés sur des fonctions pures et déterministes.
    *   Réduction de la charge mentale d'analyse lors des revues de code et de la maintenance.
*   **Neutres / Contraintes :**
    *   Augmentation du nombre de petites méthodes d'assistance privées, imposant une rigueur sur le nommage explicite des méthodes et la clarté de leur signature.
