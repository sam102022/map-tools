# ADR-014 - Javadoc obligatoire pour chaque classe et chaque méthode

## Statut

✅ Acceptée

## Contexte

Dans une application d'ingénierie logicielle avancée dédiée au traitement d'images, à la vision par ordinateur et à la géométrie algorithmique, les méthodes manipulent de nombreux paramètres assortis de contraintes strictes :
- Dimensions d'images devant être strictement positives (`width > 0`, `height > 0`).
- Coordonnées spatiales devant respecter les bornes de la matrice d'image ($[0, \text{width}-1] \times [0, \text{height}-1]$).
- Intervalles de seuillage colorimétrique ou de couverture compris obligatoirement entre $0$ et $255$.
- Tolérances de simplification et rayons de morphologie mathématique non négatifs.

L'absence d'une documentation technique claire contraint les développeurs et agents de maintenance à examiner le corps interne de chaque méthode pour deviner les préconditions, les garanties de retour et les exceptions susceptibles d'être levées.

## Décision

Pour garantir l'auto-documentation permanente, la maintenabilité à long terme et la qualité irréprochable de la codebase, la documentation Javadoc rédigée en **français** est **strictement obligatoire** pour :

1. **Chaque classe, record, interface ou énumération :**
   * Le bloc d'en-tête doit décrire précisément le rôle fonctionnel du type, son niveau d'abstraction et ses responsabilités au sein de l'architecture.
   * Pour les `record` Java 21, documenter chaque composant à l'aide de la balise `@param`.

2. **Chaque méthode et chaque constructeur (public, protégé ou privé) :**
   * Description claire de l'opération réalisée et de l'algorithme sous-jacent si pertinent.
   * Balise `@param` obligatoire pour chaque argument, explicitant son rôle et son domaine de validité.
   * Balise `@return` obligatoire (pour toute méthode non-`void`) décrivant la valeur renvoyée et les cas particuliers (ex : valeur sentinelle, liste vide, objet immuable).
   * Balise `@throws` obligatoire pour chaque exception vérifiée ou non-vérifiée levée explicitement (ex : `IllegalArgumentException`, `IndexOutOfBoundsException`, `IOException`).

Toute modification de code ne respectant pas ce standard de documentation exhaustive en français est systématiquement rejetée lors de la phase de revue.

## Conséquences

*   **Positives :**
    *   Compréhension instantanée des contrats d'interface, des préconditions et postconditions sans ouvrir le corps d'implémentation.
    *   Sécurité algorithmique renforcée : les cas limites et les plages de valeurs autorisées sont formellement explicités.
    *   Génération aisée d'une documentation HTML Java standard (`mvn javadoc:javadoc`) complète et homogène.
*   **Neutres / Contraintes :**
    *   Exige un effort rédactionnel rigoureux lors de la création ou du refactoring de chaque méthode.
