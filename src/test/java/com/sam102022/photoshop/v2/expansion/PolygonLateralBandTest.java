package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la bande extérieure des côtés longés par une route {@link PolygonLateralBand}.
 */
@DisplayName("Tests unitaires de la bande latérale PolygonLateralBand")
class PolygonLateralBandTest {

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
     * Polygone carré [50, 150]² ; une route de 10 px longe le côté droit à 3 px (x 153..163) ; une route qui se
     * prolonge au-delà du sommet bas-droit (x 163..200, y 150..170) ne doit pas être couverte.
     */
    @Test
    @DisplayName("Couvre la route qui longe un côté jusqu'à son bord opposé, sans suivre son prolongement")
    void testBandStopsAtFarEdgeAndAtVertex() {
        BinaryMask polygon = new BinaryMask(220, 220);
        rect(polygon, 50, 50, 150, 150);
        BinaryMask roads = new BinaryMask(220, 220);
        rect(roads, 153, 40, 163, 170);
        rect(roads, 163, 150, 200, 170);
        List<PixelPoint> outline = List.of(new PixelPoint(50, 50), new PixelPoint(150, 50),
                new PixelPoint(150, 150), new PixelPoint(50, 150));

        BinaryMask band = PolygonLateralBand.build(outline, polygon, roads, 30.0, 0.5, 2);

        assertTrue(band.get(100, 100), "Le polygone fait partie de la zone.");
        assertTrue(band.get(158, 100), "La route longée est couverte.");
        assertTrue(band.get(162, 100), "Jusqu'à son bord opposé.");
        assertFalse(band.get(170, 100), "Pas au-delà du bord opposé.");
        assertFalse(band.get(185, 160), "Le prolongement au-delà du sommet n'est pas couvert.");
        assertFalse(band.get(100, 160), "Un côté sans route n'a pas de bande.");
    }
}
