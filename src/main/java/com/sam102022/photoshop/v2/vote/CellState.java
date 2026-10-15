package com.sam102022.photoshop.v2.vote;

/**
 * Qualification de l'état d'inclusion d'une cellule par rapport au territoire d'intention.
 */
public enum CellState {
    /** Cellule entièrement absorbée dans le territoire (couverture supérieure ou égale au seuil inside). */
    INSIDE,
    /** Cellule entièrement rejetée en dehors du territoire (couverture inférieure ou égale au seuil partial). */
    OUTSIDE,
    /** Cellule frontière mixte conservée uniquement sur son intersection avec le polygone d'intention. */
    PARTIAL
}
