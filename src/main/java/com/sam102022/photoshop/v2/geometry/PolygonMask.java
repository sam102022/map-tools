package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Masque binaire représentant le polygone d'intention issu du JSON (P[x, y] ∈ {0, 1}).
 * Constitue le contrat d'interface officiel du Sprint 1 vers les Sprints 3 et 4 (SPRINTS_V2.md).
 *
 * @param width  Largeur de l'image en pixels (> 0).
 * @param height Hauteur de l'image en pixels (> 0).
 * @param mask   Matrice binaire où les pixels à l'intérieur du polygone sont à true.
 */
public record PolygonMask(int width, int height, BinaryMask mask) {

    /**
     * Valide la cohérence des dimensions et du masque matriciel.
     */
    public PolygonMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides : " + width + "x" + height);
        }
        if (mask == null) {
            throw new IllegalArgumentException("Le masque binaire ne peut pas être null.");
        }
        if (mask.getWidth() != width || mask.getHeight() != height) {
            throw new IllegalArgumentException("Incohérence entre les dimensions spécifiées (" + width + "x" + height
                    + ") et le masque fourni (" + mask.getWidth() + "x" + mask.getHeight() + ").");
        }
    }

    /**
     * Indique si un pixel donné (x, y) est couvert par le polygone.
     *
     * @param x Abscisse pixel.
     * @param y Ordonnée pixel.
     * @return Vrai si le pixel est inclus dans le polygone d'intention.
     */
    public boolean get(int x, int y) {
        return mask.get(x, y);
    }

    /**
     * Calcule le nombre total de pixels actifs appartenant au polygone.
     *
     * @return Surface totale en pixels.
     */
    public int countActivePixels() {
        return mask.countActivePixels();
    }
}
