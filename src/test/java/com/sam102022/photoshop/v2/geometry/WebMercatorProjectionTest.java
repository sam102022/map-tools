package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de la projection cartographique Web Mercator (EPSG:3857).
 */
@DisplayName("Validation de la projection Web Mercator V2")
class WebMercatorProjectionTest {

    @Test
    @DisplayName("Le centre géographique est exactement projeté au centre de l'image (W/2, H/2)")
    void testCenterProjectsToImageCenter() {
        int w = 3810;
        int h = 2130;
        int zoom = 17;
        GeoCoordinate center = new GeoCoordinate(47.26913592666494, -1.5065059369668754);
        MapContext ctx = new MapContext(w, h, zoom, center);

        WebMercatorProjection projection = new WebMercatorProjection(ctx);
        PixelPoint projectedCenter = projection.toPixel(center);

        assertEquals(w / 2.0, projectedCenter.x(), 1e-4);
        assertEquals(h / 2.0, projectedCenter.y(), 1e-4);
    }

    @Test
    @DisplayName("Conformité avec les formules du prototype Python sur un point témoin")
    void testProjectionConsistency() {
        // Paramètres réels du Territoire CA01
        MapContext ctx = new MapContext(3810, 2130, 17, new GeoCoordinate(47.26913592666494, -1.5065059369668754));
        WebMercatorProjection projection = new WebMercatorProjection(ctx);

        // Premier point du ring CA01 : lat = 47.27217675321035, lng = -1.499395734564006
        GeoCoordinate p0 = new GeoCoordinate(47.27217675321035, -1.499395734564006);
        PixelPoint pixel = projection.toPixel(p0);

        // Au nord du centre (lat > center.lat) => ordonnée pixel plus petite (y < H/2)
        // À l'est du centre (lng > center.lng) => abscisse pixel plus grande (x > W/2)
        assertEquals(2567.72, pixel.x(), 1.0);
        assertEquals(647.30, pixel.y(), 1.0);
    }

    @Test
    @DisplayName("Rejet des paramètres null pour le constructeur et la méthode de projection")
    void testNullArgumentsValidation() {
        MapContext ctx = new MapContext(1000, 1000, 10, new GeoCoordinate(0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WebMercatorProjection(null));

        WebMercatorProjection projection = new WebMercatorProjection(ctx);
        assertThrows(IllegalArgumentException.class, () -> projection.toPixel(null));
    }
}
