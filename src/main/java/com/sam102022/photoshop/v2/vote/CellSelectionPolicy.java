package com.sam102022.photoshop.v2.vote;

/**
 * Politique de seuillage configurable pour la classification des cellules urbaines.
 *
 * @param insideThreshold  Seuil minimal d'inclusion pour qualifier une cellule de INSIDE (ex: 0.60).
 * @param partialThreshold Seuil minimal en deçà duquel une cellule est rejetée en OUTSIDE (ex: 0.05).
 * @param minPartialThickness Épaisseur minimale (px) d'une lamelle cellule partielle ∩ polygone conservée
 *                            dans T ; 0 désactive le filtre (comportement de l'étalon Python v7).
 */
public record CellSelectionPolicy(
        double insideThreshold,
        double partialThreshold,
        double minPartialThickness
) {
    /** Seuil d'inclusion par défaut (60%). */
    public static final double DEFAULT_INSIDE_THRESHOLD = 0.60;
    /** Seuil de rejet par défaut (5%). */
    public static final double DEFAULT_PARTIAL_THRESHOLD = 0.05;
    /** Épaisseur minimale par défaut d'une lamelle partielle conservée (20 px). */
    public static final double DEFAULT_MIN_PARTIAL_THICKNESS = 25.0;

    /**
     * Valide la cohérence des seuils de sélection.
     *
     * @param insideThreshold  Seuil minimal d'inclusion.
     * @param partialThreshold Seuil minimal d'exclusion.
     * @param minPartialThickness Épaisseur minimale d'une lamelle partielle conservée (px).
     * @throws IllegalArgumentException si les seuils ne respectent pas 0.0 &lt;= partialThreshold &lt; insideThreshold &lt;= 1.0
     *                                  ou si minPartialThickness est négatif.
     */
    public CellSelectionPolicy {
        if (partialThreshold < 0.0 || partialThreshold >= insideThreshold || insideThreshold > 1.0) {
            throw new IllegalArgumentException("Les seuils doivent vérifier 0.0 <= partialThreshold (" + partialThreshold
                    + ") < insideThreshold (" + insideThreshold + ") <= 1.0");
        }
        if (minPartialThickness < 0.0) {
            throw new IllegalArgumentException("minPartialThickness doit être positif ou nul : " + minPartialThickness);
        }
    }

    /**
     * Construit une politique avec l'épaisseur minimale de lamelle par défaut.
     *
     * @param insideThreshold  Seuil minimal d'inclusion.
     * @param partialThreshold Seuil minimal d'exclusion.
     */
    public CellSelectionPolicy(double insideThreshold, double partialThreshold) {
        this(insideThreshold, partialThreshold, DEFAULT_MIN_PARTIAL_THICKNESS);
    }

    /**
     * Crée une instance de politique avec les seuils par défaut (0.60, 0.05 et lamelles &gt;= 20 px).
     *
     * @return Politique de sélection par défaut.
     */
    public static CellSelectionPolicy defaultPolicy() {
        return new CellSelectionPolicy(DEFAULT_INSIDE_THRESHOLD, DEFAULT_PARTIAL_THRESHOLD);
    }
}
