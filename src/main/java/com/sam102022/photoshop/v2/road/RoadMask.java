package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Contrat officiel immuable représentant le masque des routes en version brute et fermée topologiquement.
 * Conforme au contrat I/O défini dans SPRINTS_V2.md (Sprint 2 ➔ Sprint 3).
 *
 * @param width  Largeur de la matrice en pixels (> 0).
 * @param height Hauteur de la matrice en pixels (> 0).
 * @param raw    Masque binaire brut issu directement de la détection (sans altération morphologique).
 * @param closed Masque binaire après fermeture topologique minimale garantissant l'étanchéité des voies.
 */
public record RoadMask(
        int width,
        int height,
        BinaryMask raw,
        BinaryMask closed
) {
    /**
     * Valide l'intégrité et la cohérence dimensionnelle des masques.
     *
     * @param width  Largeur de la matrice en pixels (> 0).
     * @param height Hauteur de la matrice en pixels (> 0).
     * @param raw    Masque binaire brut issu directement de la détection.
     * @param closed Masque binaire fermé topologiquement.
     * @throws IllegalArgumentException si les dimensions sont non strictement positives, si un masque est null ou si les dimensions ne concordent pas.
     */
    public RoadMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (raw == null || closed == null) {
            throw new IllegalArgumentException("Les masques raw et closed ne peuvent pas être null.");
        }
        if (raw.getWidth() != width || raw.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour raw (" + raw.getWidth() + "x" + raw.getHeight()
                    + ") par rapport à " + width + "x" + height);
        }
        if (closed.getWidth() != width || closed.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour closed (" + closed.getWidth() + "x" + closed.getHeight()
                    + ") par rapport à " + width + "x" + height);
        }
    }
}
