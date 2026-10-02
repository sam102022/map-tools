package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation de la rasterisation de polygones V2")
class PolygonRasterizerTest {

    @Test
    @DisplayName("Rasterisation d'un polygone carré simple")
    void testSimpleSquareRasterization() {
        // Carré de (10, 10) à (30, 30) dans une image 40x40
        List<PixelPoint> outer = List.of(
                new PixelPoint(10, 10),
                new PixelPoint(30, 10),
                new PixelPoint(30, 30),
                new PixelPoint(10, 30)
        );

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask mask = rasterizer.rasterize(40, 40, List.of(outer), List.of());

        assertEquals(40, mask.width());
        assertEquals(40, mask.height());

        // L'intérieur doit être actif
        assertTrue(mask.get(20, 20));
        // L'extérieur doit être inactif
        assertFalse(mask.get(5, 5));
        assertFalse(mask.get(35, 35));
    }

    @Test
    @DisplayName("Rasterisation avec trou intérieur (Winding Rule Even-Odd)")
    void testSquareWithHoleRasterization() {
        // Carré externe (10, 10) à (40, 40)
        List<PixelPoint> outer = List.of(
                new PixelPoint(10, 10),
                new PixelPoint(40, 10),
                new PixelPoint(40, 40),
                new PixelPoint(10, 40)
        );
        // Trou interne (20, 20) à (30, 30)
        List<PixelPoint> hole = List.of(
                new PixelPoint(20, 20),
                new PixelPoint(30, 20),
                new PixelPoint(30, 30),
                new PixelPoint(20, 30)
        );

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask mask = rasterizer.rasterize(50, 50, List.of(outer), List.of(hole));

        // Entre le contour et le trou : actif
        assertTrue(mask.get(15, 15));
        // Dans le trou : inactif
        assertFalse(mask.get(25, 25));
        // Hors du carré : inactif
        assertFalse(mask.get(5, 5));
    }

    @Test
    @DisplayName("Rasterisation avec anneaux extérieurs vides ou nuls retourne un masque vide")
    void testEmptyOuterRingsReturnsEmptyMask() {
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask maskNull = rasterizer.rasterize(20, 20, null, null);
        PolygonMask maskEmpty = rasterizer.rasterize(20, 20, List.of(), null);

        assertEquals(20, maskNull.width());
        assertEquals(20, maskNull.height());
        assertFalse(maskNull.get(10, 10));

        assertEquals(20, maskEmpty.width());
        assertEquals(20, maskEmpty.height());
        assertFalse(maskEmpty.get(10, 10));
    }

    @Test
    @DisplayName("Rejet des dimensions négatives ou nulles")
    void testInvalidDimensions() {
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        assertThrows(IllegalArgumentException.class, () -> rasterizer.rasterize(0, 10, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> rasterizer.rasterize(10, -5, List.of(), null));
    }
}
