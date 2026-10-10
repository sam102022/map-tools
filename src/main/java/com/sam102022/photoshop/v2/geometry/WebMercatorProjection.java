package com.sam102022.photoshop.v2.geometry;

/**
 * Moteur de projection Web Mercator (EPSG:3857) convertissant les coordonnées géographiques
 * en coordonnées pixels relatives au cadrage de l'image cartographique locale.
 */
public final class WebMercatorProjection {

    private final MapContext context;
    private final double worldScale;
    private final double centerWorldX;
    private final double centerWorldY;

    /**
     * Initialise la projection pour un contexte cartographique donné.
     *
     * @param context Contexte d'image (dimensions, zoom, centre).
     * @throws IllegalArgumentException Si le contexte est null.
     */
    public WebMercatorProjection(MapContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Le contexte cartographique ne peut pas être null.");
        }
        this.context = context;
        this.worldScale = 256.0 * Math.pow(2, context.zoom());
        this.centerWorldX = computeWorldX(context.center().longitude());
        this.centerWorldY = computeWorldY(context.center().latitude());
    }

    /**
     * Projette une coordonnée GPS en coordonnée pixel locale dans le repère de l'image.
     *
     * @param coordinate Coordonnée géographique WGS-84.
     * @return Point en coordonnées pixels [0..width, 0..height].
     * @throws IllegalArgumentException Si la coordonnée est null.
     */
    public PixelPoint toPixel(GeoCoordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("La coordonnée ne peut pas être null.");
        }
        double worldX = computeWorldX(coordinate.longitude());
        double worldY = computeWorldY(coordinate.latitude());

        double localX = worldX - centerWorldX + (context.width() / 2.0);
        double localY = worldY - centerWorldY + (context.height() / 2.0);
        return new PixelPoint(localX, localY);
    }

    /**
     * Calcule la coordonnée mondiale X selon la projection Web Mercator.
     *
     * @param longitude Longitude en degrés.
     * @return Coordonnée mondiale X sur la sphère projetée.
     */
    private double computeWorldX(double longitude) {
        return (longitude + 180.0) / 360.0 * worldScale;
    }

    /**
     * Calcule la coordonnée mondiale Y selon la projection Web Mercator.
     *
     * @param latitude Latitude en degrés.
     * @return Coordonnée mondiale Y sur la sphère projetée.
     */
    private double computeWorldY(double latitude) {
        double sinLat = Math.sin(Math.toRadians(latitude));
        // Borner sinLat pour éviter les singularités aux pôles
        sinLat = Math.clamp(sinLat, -0.9999, 0.9999);
        return (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * worldScale;
    }
}
