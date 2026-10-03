package com.sam102022.photoshop.v2.geometry;

/**
 * Coordonnée géographique WGS-84 (GPS).
 *
 * @param latitude  Latitude en degrés dans [-90.0, 90.0].
 * @param longitude Longitude en degrés dans [-180.0, 180.0].
 */
public record GeoCoordinate(double latitude, double longitude) {

    /**
     * Valide que la latitude et la longitude respectent les bornes géographiques valides.
     *
     * @param latitude  Latitude en degrés dans [-90.0, 90.0].
     * @param longitude Longitude en degrés dans [-180.0, 180.0].
     * @throws IllegalArgumentException si la latitude ou la longitude est hors limites.
     */
    public GeoCoordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude hors limites [-90, 90] : " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude hors limites [-180, 180] : " + longitude);
        }
    }
}
