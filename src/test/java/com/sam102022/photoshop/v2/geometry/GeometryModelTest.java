package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires des modèles de domaine géométrique immuables pour la V2.
 */
@DisplayName("Validation des modèles de domaine géométrique V2")
class GeometryModelTest {

    @Test
    @DisplayName("GeoCoordinate valide les plages de latitude et longitude")
    void testGeoCoordinateValidation() {
        GeoCoordinate coord = new GeoCoordinate(47.2691, -1.5065);
        assertEquals(47.2691, coord.latitude(), 1e-6);
        assertEquals(-1.5065, coord.longitude(), 1e-6);

        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(-91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, -181.0));
    }

    @Test
    @DisplayName("PixelPoint stocke les coordonnées pixel")
    void testPixelPoint() {
        PixelPoint pt = new PixelPoint(125.5, 450.25);
        assertEquals(125.5, pt.x(), 1e-6);
        assertEquals(450.25, pt.y(), 1e-6);
    }

    @Test
    @DisplayName("MapContext valide les dimensions et le niveau de zoom")
    void testMapContextValidation() {
        GeoCoordinate center = new GeoCoordinate(47.2691, -1.5065);
        MapContext ctx = new MapContext(3810, 2130, 17, center);
        assertEquals(3810, ctx.width());
        assertEquals(2130, ctx.height());
        assertEquals(17, ctx.zoom());
        assertEquals(center, ctx.center());

        assertThrows(IllegalArgumentException.class, () -> new MapContext(0, 2130, 17, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, -1, 17, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, 2130, -1, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, 2130, 17, null));
    }

    @Test
    @DisplayName("TerritoryGeometry garantit des listes non mutables")
    void testTerritoryGeometryImmutability() {
        List<GeoCoordinate> ring = List.of(
                new GeoCoordinate(47.1, -1.5),
                new GeoCoordinate(47.2, -1.5),
                new GeoCoordinate(47.2, -1.4)
        );
        TerritoryGeometry geom = new TerritoryGeometry(List.of(ring), List.of());
        assertEquals(1, geom.outerRings().size());
        assertTrue(geom.innerRings().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> new TerritoryGeometry(List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TerritoryGeometry(null, List.of()));
    }
}
