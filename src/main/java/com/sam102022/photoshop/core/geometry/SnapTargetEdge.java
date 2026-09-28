package com.sam102022.photoshop.core.geometry;

/**
 * Cible géométrique du recalage d'un contour le long d'un ruban routier.
 */
public enum SnapTargetEdge {
    /**
     * Calage au bord extérieur du ruban routier (englobe l'emprise de la route).
     * Mode appliqué pour le détourage de territoire global.
     */
    OUTER,

    /**
     * Calage au bord intérieur du ruban routier (s'arrête au pied de la chaussée).
     * Mode appliqué pour le découpage de zone afin de ne pas englober la chaussée.
     */
    INNER
}
