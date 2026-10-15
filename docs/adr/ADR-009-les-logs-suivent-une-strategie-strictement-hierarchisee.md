# ADR-009 - Les logs suivent une stratégie de journalisation strictement hiérarchisée

## Statut

✅ Acceptée

## Contexte

Dans une application de traitement d'images combinant exécution en ligne de commande (CLI automatisée/batch) et interface graphique Swing, le suivi du traitement et le diagnostic des anomalies requièrent une observabilité claire. 

Sans convention stricte de journalisation, les messages de console deviennent soit trop verbeux (polluant la sortie standard avec des millions de valeurs de pixels), soit trop cryptiques (rendant impossible l'analyse d'un échec de détourage sur une image spécifique).

## Décision

Pour garantir une observabilité maximale, une traçabilité sans faille et une maintenance facilitée du pipeline de traitement d'images et d'aimantation cartographique, nous appliquons une stratégie stricte et hiérarchisée de journalisation (logs) :

1. **Langue des Logs (français) :**
   * Tous les messages de log (INFO, DEBUG, ERROR) doivent être obligatoirement rédigés en **français** pour assurer une cohérence et une intelligibilité totale par l'équipe de développement.

2. **Niveau INFO (Suivi des étapes clés du traitement) :**
   * Sert à suivre le cycle de vie principal du traitement et les étapes majeures du pipeline.
   * Doit être concis, explicite et synthétique.
   * Exemples :
     * Début du traitement : chargement des fichiers images sources avec leurs dimensions (`W x H`).
     * Détection du masque vert : volume de pixels initiaux identifiés (`583626 pixels verts`).
     * Détection des routes : volume de pixels candidats identifiés (`90624 candidats routiers`).
     * Recalage géodésique : nombre de pixels retenus après aimantation.
     * Export des résultats : chemins des fichiers écrits sur disque (`clipped_output.png`, `mask_output.png`) et durée totale d'exécution.

3. **Niveau DEBUG (Aide au diagnostic et débogage algorithmique) :**
   * Sert à enregistrer les détails techniques internes indispensables pour comprendre le comportement fin d'un algorithme lors du développement ou d'une analyse d'anomalie.
   * Peut être verbeux et inclure des données quantitatives intermédiaires.
   * Exemples :
     * Détails des composantes couleur HSV/RGB et écarts par rapport aux seuils configurés.
     * Nombre d'itérations du parcours de propagation BFS ou de l'érosion géodésique.
     * Coordonnées des points de contour extraits avant et après l'algorithme de Ramer-Douglas-Peucker (`ContourSimplifier`).
     * Matrice de convolution Sobel appliquée et gradient moyen calculé.

4. **Niveau ERROR (Traçabilité systématique des anomalies et échecs) :**
   * **Règle absolue :** Un log d'erreur avec contexte explicite doit être écrit **systématiquement juste avant de lever ou de propager une exception**, ou au moment de sa capture définitive.
   * Le message de log doit contenir un descriptif clair de l'anomalie, les paramètres de contexte ayant provoqué l'échec (ex : chemin du fichier image, dimensions attendues vs réelles, coordonnées hors limites), et la cause racine (`Throwable`) pour enregistrer la pile d'exécution complète (`stack trace`).

## Conséquences

*   **Positives :**
    *   Sortie console CLI propre, lisible et informative pour les utilisateurs et scripts batch.
    *   Diagnostic rapide des anomalies géométriques ou de parsing sans avoir à exécuter un débogueur pas-à-pas.
    *   Séparation nette entre le flux opérationnel standard (INFO) et les traces diagnostiques fines (DEBUG).
*   **Neutres / Contraintes :**
    *   Nécessite de veiller à ne pas logger de données pixel par pixel en niveau INFO pour éviter toute dégradation des performances.
