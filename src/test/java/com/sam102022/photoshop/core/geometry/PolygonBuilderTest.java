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
    @DisplayName("Rasterisation avec anti-aliasing : distribution quantitative et positionnement géométrique précis")
    void testRasterizeCoverageAntialiasingOn() {
        PolygonBuilder builder = new PolygonBuilder();
        // Triangle oblique : (2, 2) -> (18, 5) -> (8, 18)
        List<Point> triangle = List.of(
                new Point(2, 2),
                new Point(18, 5),
                new Point(8, 18),
                new Point(2, 2)
        );

        com.sam102022.photoshop.core.model.CoverageMask mask = builder.rasterizeCoverage(25, 25, triangle, true);

        // 1. Positionnement géométrique :
        // Le centre géométrique du triangle (~ (9, 8)) doit être pleinement opaque (255)
        assertEquals(255, mask.get(9, 8), "Le centre intérieur doit être à 255");
        assertEquals(255, mask.get(8, 7), "L'intérieur profond doit être à 255");

        // Les coins extérieurs éloignés doivent être strictement transparents (0)
        assertEquals(0, mask.get(0, 0), "Coin extérieur (0,0) doit être 0");
        assertEquals(0, mask.get(24, 24), "Coin extérieur (24,24) doit être 0");
        assertEquals(0, mask.get(24, 0), "Coin extérieur (24,0) doit être 0");
        assertEquals(0, mask.get(0, 24), "Coin extérieur (0,24) doit être 0");

        // 2. Distribution quantitative de la couverture :
        int lowSubpixel = 0;   // ]0, 128[
        int highSubpixel = 0;  // [128, 255[
        int fullCovered = 0;   // 255
        int totalIntermediate = 0;

        for (int y = 0; y < 25; y++) {
            for (int x = 0; x < 25; x++) {
                int cov = mask.get(x, y);
                if (cov == 255) {
                    fullCovered++;
                } else if (cov > 0) {
                    totalIntermediate++;
                    if (cov < 128) lowSubpixel++;
                    else highSubpixel++;

                    // 3. Localisation : chaque pixel intermédiaire doit être sur la frontière
                    // (avoir au moins un voisin à 0 et au moins un voisin avec couverture > 0)
                    boolean hasZeroNeighbor = false;
                    boolean hasPositiveNeighbor = false;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < 25 && ny >= 0 && ny < 25) {
                                if (mask.get(nx, ny) == 0) hasZeroNeighbor = true;
                                if (mask.get(nx, ny) > 0) hasPositiveNeighbor = true;
                            } else {
                                hasZeroNeighbor = true; // Hors cadre = 0
                            }
                        }
                    }
                    assertTrue(hasZeroNeighbor, "Le pixel intermédiaire (" + x + "," + y + ") doit border une zone à 0");
                    assertTrue(hasPositiveNeighbor, "Le pixel intermédiaire (" + x + "," + y + ") doit border une zone intérieure");
                }
            }
        }

        // Le périmètre du triangle étant ~45 pixels, on attend entre 25 et 70 pixels de transition sub-pixel
        assertTrue(totalIntermediate >= 25 && totalIntermediate <= 70,
                "Nombre raisonnable de pixels intermédiaires attendu (entre 25 et 70), obtenu: " + totalIntermediate);
        assertTrue(lowSubpixel > 5, "Doit comporter des valeurs sub-pixel < 128, obtenu: " + lowSubpixel);
        assertTrue(highSubpixel > 5, "Doit comporter des valeurs sub-pixel >= 128, obtenu: " + highSubpixel);
        assertTrue(fullCovered >= 50, "Doit comporter un noyau opaque suffisant (>= 50), obtenu: " + fullCovered);
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
