package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PolygonBuilderTest {

    @Test
    @DisplayName("Rasterisation d'un carré simple 10x10")
    void testRasterizeSquare() {
        PolygonBuilder builder = new PolygonBuilder();
        List<Point> square = List.of(
                new Point(5, 5),
                new Point(15, 5),
                new Point(15, 15),
                new Point(5, 15),
                new Point(5, 5)
        );

        BinaryMask mask = builder.rasterize(30, 30, square);
        assertTrue(mask.get(10, 10), "Le centre du carré doit être rempli");
        assertTrue(mask.get(6, 6));
        assertFalse(mask.get(2, 2), "L'extérieur ne doit pas être rempli");
        assertFalse(mask.get(25, 25));
        assertTrue(mask.countActivePixels() >= 80, "La quasi-totalité des 100 pixels doit être active");
    }

    @Test
    @DisplayName("Polygone dégénéré (< 3 points) produit un masque vide")
    void testDegeneratePolygon() {
        PolygonBuilder builder = new PolygonBuilder();
        BinaryMask mask = builder.rasterize(20, 20, List.of(new Point(5, 5), new Point(6, 6)));
        assertEquals(0, mask.countActivePixels());
    }

    @Test
    @DisplayName("Validation des arguments nulls ou dimensions invalides")
    void testInvalidArguments() {
        PolygonBuilder builder = new PolygonBuilder();
        assertThrows(IllegalArgumentException.class, () -> builder.rasterize(0, 20, List.of()));
        assertThrows(IllegalArgumentException.class, () -> builder.rasterize(20, -5, List.of()));
        assertThrows(IllegalArgumentException.class, () -> builder.rasterize(20, 20, null));
    }

    @Test
    @DisplayName("Rasterisation avec anti-aliasing activé produit des valeurs de couverture sub-pixel intermédiaires")
    void testRasterizeCoverageAntialiasingOn() {
        PolygonBuilder builder = new PolygonBuilder();
        // Triangle oblique
        List<Point> triangle = List.of(
                new Point(2, 2),
                new Point(18, 5),
                new Point(8, 18),
                new Point(2, 2)
        );

        com.sam102022.photoshop.core.model.CoverageMask mask = builder.rasterizeCoverage(25, 25, triangle, true);
        boolean hasIntermediate = false;
        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                int cov = mask.get(x, y);
                if (cov > 0 && cov < 255) {
                    hasIntermediate = true;
                    break;
                }
            }
        }
        assertTrue(hasIntermediate, "Des valeurs de couverture strictement comprises entre 0 et 255 doivent être présentes avec AA");
    }

    @Test
    @DisplayName("Rasterisation sans anti-aliasing produit exclusivement 0 ou 255")
    void testRasterizeCoverageAntialiasingOff() {
        PolygonBuilder builder = new PolygonBuilder();
        List<Point> triangle = List.of(
                new Point(2, 2),
                new Point(18, 5),
                new Point(8, 18),
                new Point(2, 2)
        );

        com.sam102022.photoshop.core.model.CoverageMask mask = builder.rasterizeCoverage(25, 25, triangle, false);
        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                int cov = mask.get(x, y);
                assertTrue(cov == 0 || cov == 255, "Sans AA, la couverture doit être strictement binaire (0 ou 255), trouvé: " + cov);
            }
        }
    }
}
