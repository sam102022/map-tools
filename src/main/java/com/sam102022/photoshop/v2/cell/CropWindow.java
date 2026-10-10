package com.sam102022.photoshop.v2.cell;

/**
 * Fenêtre de recadrage rectangulaire (Region of Interest) sur l'espace matriciel.
 * Définit l'origine (x0, y0) et les dimensions (width, height) de la sous-région d'intérêt.
 *
 * @param x0     Abscisse minimale (incluse).
 * @param y0     Ordonnée minimale (incluse).
 * @param width  Largeur de la fenêtre de recadrage en pixels.
 * @param height Hauteur de la fenêtre de recadrage en pixels.
 */
public record CropWindow(
        int x0,
        int y0,
        int width,
        int height
) {

    /**
     * Valide les invariants de la fenêtre de découpe.
     *
     * @param x0     Abscisse minimale.
     * @param y0     Ordonnée minimale.
     * @param width  Largeur en pixels.
     * @param height Hauteur en pixels.
     * @throws IllegalArgumentException si width ou height est inférieur ou égal à zéro, ou si x0/y0 est négatif.
     */
    public CropWindow {
        if (x0 < 0 || y0 < 0) {
            throw new IllegalArgumentException("Les coordonnées d'origine x0 et y0 doivent être positives ou nulles.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions width et height doivent être strictement positives.");
        }
    }

    /**
     * Indique si un point de coordonnées absolues (x, y) est inclus dans cette fenêtre.
     *
     * @param x Abscisse absolue.
     * @param y Ordonnée absolue.
     * @return true si le point est contenu dans la fenêtre, false sinon.
     */
    public boolean contains(int x, int y) {
        return x >= x0 && x < x0 + width && y >= y0 && y < y0 + height;
    }

    /**
     * Abscisse maximale exclusive.
     *
     * @return x0 + width.
     */
    public int x1() {
        return x0 + width;
    }

    /**
     * Ordonnée maximale exclusive.
     *
     * @return y0 + height.
     */
    public int y1() {
        return y0 + height;
    }
}
