package com.sam102022.photoshop.v2.geometry;

/**
 * Paramètres cartographiques de cadrage de l'image capturée.
 *
 * @param width  Largeur de l'image en pixels.
 * @param height Hauteur de l'image en pixels.
 * @param zoom   Niveau de zoom Web Mercator (ex: 17).
 * @param center Coordonnée géographique GPS correspondant au centre géométrique exact de l'image.
 */
public record MapContext(int width, int height, int zoom, GeoCoordinate center) {

    /**
     * Valide la cohérence des paramètres cartographiques.
     *
     * @param width  Largeur de l'image en pixels (> 0).
     * @param height Hauteur de l'image en pixels (> 0).
     * @param zoom   Niveau de zoom Web Mercator [0, 24].
     * @param center Coordonnée géographique GPS non nulle du centre cartographique.
     * @throws IllegalArgumentException si les dimensions sont non positives, le zoom hors limites, ou le centre null.
     */
    public MapContext {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions d'image invalides : " + width + "x" + height);
        }
        if (zoom < 0 || zoom > 24) {
            throw new IllegalArgumentException("Zoom Web Mercator invalide : " + zoom);
        }
        if (center == null) {
            throw new IllegalArgumentException("Le centre cartographique ne peut pas être null.");
        }
    }
}
