# ADR-012 - Usage des imports explicites et écriture simplifiée du code Java

## Statut

✅ Acceptée

## Contexte

Lors du développement rapide ou de refactorings automatisés, il arrive que des types soient insérés dans le code en utilisant leur nom de classe pleinement qualifié (FQCN, ex : `java.awt.image.BufferedImage`, `java.awt.Point`, `java.util.List<java.awt.Point>`, `java.util.Map<String, Object>`).

Bien que cette pratique permette d'éviter l'ajout d'une clause d'importation en en-tête de fichier, elle alourdit considérablement les signatures de méthodes, pollue la lecture des déclarations de variables et nuit à l'élégance et à l'idiomatisme du code Java 21 moderne.

## Décision

Il est décidé de proscrire formellement l'utilisation de noms de classes pleinement qualifiés à l'intérieur du corps des classes, des paramètres de méthodes, des types de variables et des types de retour.

1. **Imports explicites en en-tête :** Tout type externe ou provenant d'un autre package doit être importé explicitement en haut du fichier source (ex : `import java.awt.image.BufferedImage;`, `import java.awt.Point;`, `import java.util.List;`).
2. **Écriture concise et épurée :** Le corps du fichier doit employer exclusivement le nom simple du type (ex : `BufferedImage image`, `List<Point> points`).
3. **Exceptions strictes et limitées :** L'utilisation d'un nom pleinement qualifié n'est tolérée qu'en cas d'ambiguïté insoluble de collision de nom entre deux classes de packages distincts au sein du même fichier source (ex : `java.awt.List` vs `java.util.List`), situation qui doit être évitée par conception dans le projet.

## Conséquences

*   **Positives :**
    *   Lisibilité et netteté optimales du code source à travers tous les modules du projet.
    *   Signatures de méthodes claires, aérées et conformes aux conventions Java standard.
    *   Facilité de relecture, d'analyse statique et de maintenance.
*   **Neutres / Contraintes :**
    *   Organisation rigoureuse du bloc des imports en haut de chaque fichier Java (organisé par ordre alphabétique, sans wildcards `*`).
