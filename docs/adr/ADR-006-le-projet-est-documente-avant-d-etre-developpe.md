# ADR-006 - Le projet est documenté avant d'être développé

## Statut

✅ Acceptée

## Contexte

Dans le cadre de projets d'ingénierie logicielle impliquant des algorithmes complexes (traitement matriciel d'images, vision par ordinateur, géométrie algorithmique et recalage géodésique), aborder le développement par du codage direct sans cadrage formel préalable conduit inévitablement à :
- Des modèles mathématiques et géométriques imprécis ou mal définis.
- Du code jetable ou instable nécessitant de multiples réécritures complètes.
- Des incohérences d'architecture entre les modules (détection, géométrie, segmentation, I/O).
- Une perte de visibilité sur les cas limites (bords d'images, polygones dégénérés, artefacts graphiques).

Pour garantir la pérennité, la robustesse algorithmique et la maintenabilité du système de détourage et d'alignement cartographique, il est impératif d'établir une discipline d'ingénierie stricte où la documentation technique précède systématiquement la réalisation.

## Décision

Il est formellement décidé que toute fonctionnalité majeure, composant ou refactoring substantiel doit respecter scrupuleusement le cycle en trois étapes :

1. **Documentation & Spécification technique (`docs/superpowers/specs/`) :**
   * Définition exhaustive des besoins utilisateurs, des objectifs algorithmiques et des flux de données.
   * Modélisation mathématique rigoureuse (formules d'interpolation, composition alpha, métriques de distance, seuillages).
   * Spécification des entrées/sorties, formats de fichiers supportés, et gestion des cas limites.
   * Définition des critères d'acceptation et des métriques de validation.

2. **Conception & Planification opérationnelle (`docs/superpowers/plans/`) :**
   * Découpage du travail en tâches unitaires, séquentielles et vérifiables individuellement.
   * Identification précise des classes, interfaces et packages à créer ou modifier.
   * Définition préalable de la stratégie de test unitaire et d'intégration associée à chaque tâche.

3. **Implémentation guidée par les tests (TDD) :**
   * Écriture systématique des tests de validation avant le code de production.
   * Implémentation chirurgicale respectant fidèlement la conception validée.
   * Validation complète de non-régression (`mvn test`).

**Règle d'or :** Jamais d'implémentation sans spécification et plan préalablement rédigés et validés.

## Conséquences

*   **Positives :**
    *   Excellence algorithmique : les choix mathématiques et géométriques sont pensés et validés avant d'être codés.
    *   Réduction drastique des régressions et du temps perdu en débogage spéculatif.
    *   Auto-documentation permanente facilitant l'onboarding et l'audit technique.
    *   Traçabilité parfaite entre les exigences initiales et le code source final.
*   **Neutres / Contraintes :**
    *   Nécessite une rigueur intellectuelle continue et un investissement initial en temps avant de produire la première ligne de code exécutable.
