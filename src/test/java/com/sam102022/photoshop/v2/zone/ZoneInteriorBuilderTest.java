package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la construction du masque de zone sur le bord intérieur des routes
 * {@link ZoneInteriorBuilder}.
 */
@DisplayName("Tests unitaires de ZoneInteriorBuilder")
class ZoneInteriorBuilderTest {

    /**
     * Remplit un rectangle [x0, x1) x [y0, y1).
     *
     * @param m  Masque.
     * @param x0 Abscisse de début.
     * @param y0 Ordonnée de début.
     * @param x1 Abscisse de fin (exclue).
     * @param y1 Ordonnée de fin (exclue).
     */
    private static void rect(BinaryMask m, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                m.set(x, y, true);
            }
        }
    }

    /**
     * Deux îlots retenus séparés par une rue intérieure de 10 px, entourés d'une route frontière de 10 px :
     * la rue intérieure est incluse, la route frontière ne l'est pas ; un îlot isolé est écarté.
     */
    @Test
    @DisplayName("Rue intérieure incluse, route frontière exclue")
    void testInteriorStreetIncludedBoundaryRoadExcluded() {
        BinaryMask road = new BinaryMask(300, 200);
        rect(road, 0, 0, 300, 200);
        BinaryMask retained = new BinaryMask(300, 200);
        rect(retained, 20, 20, 145, 180);
        rect(retained, 155, 20, 280, 180);
        rect(retained, 290, 5, 298, 12);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 300; x++) {
                if (retained.get(x, y)) {
                    road.set(x, y, false);
                }
            }
        }

        BinaryMask zone = new ZoneInteriorBuilder().build(retained, road, List.of(), 12.0);

        assertTrue(zone.get(150, 100), "la rue intérieure doit être incluse");
        assertFalse(zone.get(15, 100), "la route frontière ouest doit être exclue");
        assertFalse(zone.get(150, 15), "le débouché de la rue sur la route frontière doit rester exclu");
        assertFalse(zone.get(294, 8), "un îlot isolé ne fait pas partie de la zone");
    }

    /**
     * Un giratoire posé sur le bord de la zone en est retiré (le contour suit l'anneau côté zone).
     */
    @Test
    @DisplayName("Giratoire frontalier exclu")
    void testBoundaryRoundaboutExcluded() {
        BinaryMask road = new BinaryMask(200, 200);
        BinaryMask retained = new BinaryMask(200, 200);
        rect(retained, 20, 20, 180, 180);
        Roundabout rb = new Roundabout(new PixelPoint(180, 100), new EllipseModel(180, 100, 25, 25, 0), 400.0, 1.0);

        BinaryMask zone = new ZoneInteriorBuilder().build(retained, road, List.of(rb), 12.0);

        assertFalse(zone.get(170, 100));
        assertTrue(zone.get(150, 100));
        assertTrue(zone.get(170, 60));
    }

    /**
     * Un îlot pris pour un giratoire mais situé entièrement à l'intérieur du tracé de la zone n'est pas retiré
     * (pas d'encoche) ; s'il est traversé par le tracé, il l'est.
     */
    @Test
    @DisplayName("Giratoire intérieur au tracé conservé")
    void testRoundaboutInsideTraceKept() {
        BinaryMask road = new BinaryMask(200, 200);
        BinaryMask retained = new BinaryMask(200, 200);
        rect(retained, 20, 20, 180, 180);
        Roundabout rb = new Roundabout(new PixelPoint(175, 100), new EllipseModel(175, 100, 12, 12, 0), 100.0, 1.0);
        BinaryMask insideTrace = new BinaryMask(200, 200);
        rect(insideTrace, 10, 10, 190, 190);
        BinaryMask crossingTrace = new BinaryMask(200, 200);
        rect(crossingTrace, 10, 10, 175, 190);

        assertTrue(new ZoneInteriorBuilder().build(retained, road, List.of(rb), 12.0, insideTrace).get(172, 100));
        assertFalse(new ZoneInteriorBuilder().build(retained, road, List.of(rb), 12.0, crossingTrace).get(172, 100));
    }
}
