# ADR-013 - Standardisation du fuseau horaire pour LocalDate.now

## Statut

✅ Acceptée

## Contexte

Dans une application manipulant des logs, des horodatages d'export de fichiers d'images et des métadonnées temporelles de traitement, l'utilisation de dates dépend étroitement du fuseau horaire de l'environnement d'exécution.

L'appel sans argument `LocalDate.now()` repose implicitement sur l'horloge système par défaut de la machine hôte. Dans des contextes hétérogènes (postes de développement Windows, conteneurs Docker ou runners de CI souvent calés en UTC), cette dépendance implicite peut entraîner des décalages d'un jour sur les métadonnées de journalisation ou les noms de fichiers d'export générés autour de minuit.

## Décision

Il est formellement décidé de proscrire l'appel direct sans paramètre à `LocalDate.now()` dans l'ensemble du projet.

1. **Explicitation du fuseau horaire :** Tout appel d'obtention de la date courante doit obligatoirement expliciter la zone horaire ciblée :
   ```java
   LocalDate.now(ZoneId.systemDefault());
   ```
2. **Injectabilité pour les tests :** Pour les composants nécessitant un contrôle temporel déterministe dans les tests automatisés, privilégier l'injection d'une instance `java.time.Clock`.

Cette règle garantit la cohérence et l'intelligibilité des dates opérationnelles calculées par l'application, quel que soit l'environnement de déploiement.

## Conséquences

*   **Positives :**
    *   Absence d'ambiguïté temporelle entre les environnements locaux et les serveurs d'intégration continue.
    *   Facilité de test et de simulation temporelle déterministe.
*   **Neutres / Contraintes :**
    *   Exige des développeurs et outils d'analyse statique une vigilance sur les instanciations de `LocalDate`.
