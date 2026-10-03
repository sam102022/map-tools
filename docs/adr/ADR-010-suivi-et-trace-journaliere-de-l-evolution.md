# ADR-010 - Suivi et trace journalière de l'évolution du projet

## Statut

✅ Acceptée

## Contexte

Au fur et à mesure de l'avancement du projet par sprints successifs, de multiples refactorings, des perfectionnements algorithmiques (détection multi-critères des routes, morphologie mathématique géodésique, simplification de polygones RDP, antialiasing sub-pixel) et des ajouts d'interfaces (CLI et GUI Swing) se succèdent. 

Afin de garantir une traçabilité parfaite des choix techniques, de faciliter la reprise de contexte par les développeurs ou agents IA, et de garder une visibilité claire sur l'historique des arbitrages, il est indispensable de conserver un journal temporel précis des évolutions apportées au jour le jour.

## Décision

Il est décidé de maintenir à la racine du projet un journal de bord centralisé sous la forme d'un fichier `JOURNAL.md`.

Ce document doit obligatoirement contenir :
1. **Rappels des principes directeurs :** Références aux ADRs fondamentaux qui cadrent les réalisations du projet.
2. **Frise chronologique par sprints :** Résumé des étapes franchies avec les versions et dates associées.
3. **Entrées journalières datées :** Section mise à jour au fil de l'eau à chaque journée de travail, décrivant de manière synthétique :
   * Les fonctionnalités et modules créés ou enrichis.
   * Les optimisations algorithmiques et refactorings majeurs.
   * Les anomalies corrigées et les cas limites traités.
4. **État opérationnel à date :** Statut de la suite de tests automatisés (`mvn test`), couverture et métriques clés.

## Conséquences

*   **Positives :**
    *   Traçabilité optimale de l'évolution du code et de l'historique des décisions quotidiennes.
    *   Passage de relais et reprise de contexte immédiats entre développeurs et assistants automatisés.
    *   Visibilité directe sur la progression concrète face aux objectifs des spécifications techniques.
*   **Neutres / Contraintes :**
    *   Nécessite une discipline systématique de mise à jour à l'issue de chaque journée ou phase de développement significative.
