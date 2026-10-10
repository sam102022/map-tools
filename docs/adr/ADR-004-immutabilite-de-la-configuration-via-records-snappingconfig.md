# ADR-004 - Immutabilité de la configuration via les Java Records (SnappingConfig)

## Statut

✅ Acceptée

## Contexte

Le fonctionnement des algorithmes de détection colorimétrique, de morphologie mathématique, d'aimantation géodésique et de rendu dépend d'un ensemble substantiel de paramètres et d'hyperparamètres :
- Plages de teintes, saturation et luminosité HSV pour le vert.
- Tolérances de couleur et seuils de gradient Sobel pour les routes.
- Rayon maximal d'aimantation (*snapping radius*).
- Rayon de dilatation et d'érosion morphologique.
- Rayon de lissage (*smooth radius*) et activation de l'anticrénelage sub-pixel (*antialias*).

Dans les architectures classiques, ces paramètres sont souvent représentés par des JavaBeans mutables dotés de *getters* et *setters*, ou dispersés à travers des signatures de méthodes comportant une dizaine d'arguments primitifs. Cette approche présente des risques majeurs :
- Risques de corruptions d'état et d'effets de bord imprévisibles lors de manipulations concurrentes (ex : modification d'un curseur Swing pendant qu'un calcul tourne en tâche de fond).
- Absence de validation centralisée menant à des états incohérents (ex : rayon de lissage négatif, seuil couleur supérieur à 255).
- Signatures de méthodes complexes et instables.

## Décision

Il est formellement décidé de modéliser la configuration globale sous la forme d'un **Java Record immuable : `SnappingConfig`** :

```java
public record SnappingConfig(
    int greenHueMin,
    int greenHueMax,
    float greenSatMin,
    float greenValMin,
    int roadColorTolerance,
    int roadSobelThreshold,
    int maxSnappingDistance,
    int morphologyRadius,
    int smoothRadius,
    boolean antialias
) {
    // Constructeur compact validant les invariants (Fail-Fast)
    public SnappingConfig {
        if (smoothRadius < 0) {
            throw new IllegalArgumentException("Le rayon de lissage ne peut pas être négatif.");
        }
        if (maxSnappingDistance < 0) {
            throw new IllegalArgumentException("La distance d'aimantation ne peut pas être négative.");
        }
        // ... validations des intervalles [0..255] et [0..360]
    }
}
```

1. **Immutabilité garantie par le langage :** Chaque composant est `final`, sans aucun mutateur (*setter*). Tout changement de valeur passe par l'instanciation d'une nouvelle instance (méthodes d'évolution de type `withAntialias(boolean)`).
2. **Validation systématique au constructeur compact (*Fail-Fast*) :** Tous les invariants géométriques et colorimétriques sont contrôlés dès l'instanciation. Il est impossible de créer ou de propager une configuration invalide.
3. **Profil par défaut calibré :** Mise à disposition d'une méthode de fabrique statique `defaultConfig()` fournissant un jeu de paramètres optimisé pour les captures Google Maps standard.

## Conséquences

*   **Positives :**
    *   *Thread-safety* absolue : une instance de configuration peut être partagée en toute sécurité entre l'IHM Swing et des threads de traitement asynchrones.
    *   Code concis et idiomatique Java 21, bénéficiant des implémentations automatiques optimales de `equals()`, `hashCode()` et `toString()`.
    *   Simplicité des signatures de méthodes à travers toute la hiérarchie : passage d'une instance unique `SnappingConfig config`.
*   **Neutres / Contraintes :**
    *   Nécessite la recréation d'une instance à chaque modification d'un curseur ou d'une option dans l'interface graphique.
