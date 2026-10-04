package com.sam102022.photoshop.v2.vote;

/**
 * Politique de seuillage configurable pour la classification des cellules urbaines.
 *
 * @param insideThreshold  Seuil minimal d'inclusion pour qualifier une cellule de INSIDE (ex: 0.60).
 * @param partialThreshold Seuil minimal en deçà duquel une cellule est rejetée en OUTSIDE (ex: 0.05).
 */
public record CellSelectionPolicy(
        double insideThreshold,
        double partialThreshold
) {
    /** Seuil d'inclusion par défaut (60%). */
    public static final double DEFAULT_INSIDE_THRESHOLD = 0.60;
    /** Seuil de rejet par défaut (5%). */
    public static final double DEFAULT_PARTIAL_THRESHOLD = 0.05;

    /**
     * Valide la cohérence des seuils de sélection.
     *
     * @param insideThreshold  Seuil minimal d'inclusion.
     * @param partialThreshold Seuil minimal d'exclusion.
     * @throws IllegalArgumentException si les seuils ne respectent pas 0.0 &lt;= partialThreshold &lt; insideThreshold &lt;= 1.0.
     */
    public CellSelectionPolicy {
        if (partialThreshold < 0.0 || partialThreshold >= insideThreshold || insideThreshold > 1.0) {
            throw new IllegalArgumentException("Les seuils doivent vérifier 0.0 <= partialThreshold (" + partialThreshold
                    + ") < insideThreshold (" + insideThreshold + ") <= 1.0");
        }
    }

    /**
     * Crée une instance de politique avec les seuils par défaut (0.60 et 0.05).
     *
     * @return Politique de sélection par défaut.
     */
    public static CellSelectionPolicy defaultPolicy() {
        return new CellSelectionPolicy(DEFAULT_INSIDE_THRESHOLD, DEFAULT_PARTIAL_THRESHOLD);
    }
}
