# ADR-003 - Double mode d'exécution CLI headless et GUI Swing interactive

## Statut

✅ Acceptée

## Contexte

L'application de détourage et d'alignement cartographique répond à deux besoins opérationnels complémentaires :

1. **Automatisation par scripts et intégration batch (Headless) :**
   * Exécution au sein de scripts automatisés, de pipelines de production ou d'environnements d'intégration continue sans serveur graphique X11/Wayland ni écran physique.
   * Traitement rapide par lot de nombreuses cartes géographiques sans intervention humaine.
   * Besoin de codes de sortie de processus explicites (`0` pour succès, valeurs non-nulles pour échec) et de messages console synthétiques.

2. **Exploration visuelle et calibration interactive (GUI) :**
   * L'utilisateur humain ou le concepteur d'algorithmes a besoin d'observer immédiatement le masque extrait, la détection des axes routiers et la découpe finale.
   * Ajustement visuel interactif des hyperparamètres (seuils de tolérance couleur, rayons morphologiques, activation ou désactivation du lissage et de l'antialiasing).

## Décision

Il est formellement décidé de structurer l'application selon un **modèle d'exécution dual reposant sur un moteur algorithmique strictement agnostique** :

```
                  ┌────────────────────────────────────────┐
                  │       Point d'entrée Main.java         │
                  └───────────────────┬────────────────────┘
                                      │
                         Drapeau --gui présent ?
                                      │
                     ┌────────────────┴────────────────┐
                 Non │                             Oui │
                     ▼                                 ▼
         ┌───────────────────────┐         ┌───────────────────────┐
         │  CliRunner (Console)  │         │  MainWindow (Swing)   │
         └───────────┬───────────┘         └───────────┬───────────┘
                     │                                 │
                     └────────────────┬────────────────┘
                                      ▼
                        ┌───────────────────────────┐
                        │    RoadSnappingEngine     │
                        │ (Cœur algorithmique pur)  │
                        └───────────────────────────┘
```

1. **Moteur algorithmique pur (`RoadSnappingEngine`) :** Le moteur ne dépend d'aucun composant d'interface utilisateur (ni Swing, ni console directe). Il prend en entrée des images en mémoire et un objet de configuration `SnappingConfig`, et produit les masques et images calculées.
2. **Mode Ligne de Commande par défaut (`CliRunner`) :**
   * Traitement direct en mode console.
   * Respect absolu des contraintes d'exécution *headless* (`java.awt.headless=true`).
   * Interface CLI riche supportant les arguments nommés (`--image`, `--mask`, `--output`, `--antialias`, `--no-antialias`, `--smooth-radius`, etc.).
3. **Mode Interface Graphique interactive (`MainWindow`) :**
   * Déclenché explicitement par le paramètre `--gui`.
   * Initialisation de la fenêtre sur l'Event Dispatch Thread via `SwingUtilities.invokeLater()`.
   * Rendu multi-panneaux permettant d'inspecter chaque étape intermédiaire du traitement.

## Conséquences

*   **Positives :**
    *   Polyvalence complète : l'outil s'adapte aussi bien à un serveur d'automatisation qu'au poste d'un graphiste ou développeur.
    *   Réutilisation totale de la logique métier sans duplication de code.
    *   Sécurité d'exécution sur des serveurs distants sans risque de `HeadlessException`.
*   **Neutres / Contraintes :**
    *   Nécessite de veiller à ce que chaque nouvelle option algorithmique soit exposée de manière synchrone à la fois dans les arguments CLI et dans les panneaux de l'IHM Swing.
