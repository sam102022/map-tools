package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la suppression des boucles locales {@link LocalLoopRemover}.
 */
@DisplayName("Tests unitaires du supprimeur de boucles LocalLoopRemover")
class LocalLoopRemoverTest {

    /**
     * Une petite boucle sur le côté haut d'un carré est remplacée par le point de croisement.
     */
    @Test
    @DisplayName("Supprime une petite boucle où le contour se recoupe")
    void testRemovesSmallLoop() {
        List<PixelPoint> pts = new ArrayList<>();
        for (int x = 0; x <= 40; x++) {
            pts.add(new PixelPoint(x, 0));
        }
        // Boucle : on monte, on revient en arrière puis on recoupe le segment horizontal
        pts.add(new PixelPoint(40, -10));
        pts.add(new PixelPoint(30, -10));
        pts.add(new PixelPoint(35, 5));
        for (int x = 45; x <= 100; x++) {
            pts.add(new PixelPoint(x, 0));
        }
        pts.add(new PixelPoint(100, 100));
        pts.add(new PixelPoint(0, 100));
        SmoothVectorContour contour = new SmoothVectorContour(pts, List.of(), 200, 200, new CropWindow(0, 0, 200, 200));

        SmoothVectorContour cleaned = new LocalLoopRemover().removeLoops(contour, 200);

        assertTrue(cleaned.points().size() < pts.size(), "Des points de la boucle doivent être retirés.");
        for (PixelPoint p : cleaned.points()) {
            assertTrue(p.y() >= -1e-9, "Aucun point de la boucle ne doit subsister : " + p);
        }
    }

    /**
     * Un contour simple (sans recoupement) est retourné tel quel.
     */
    @Test
    @DisplayName("Un contour sans recoupement est inchangé")
    void testSimpleContourUnchanged() {
        List<PixelPoint> square = List.of(new PixelPoint(0, 0), new PixelPoint(50, 0), new PixelPoint(50, 50),
                new PixelPoint(0, 50));
        SmoothVectorContour contour = new SmoothVectorContour(square, List.of(), 100, 100, new CropWindow(0, 0, 100, 100));

        SmoothVectorContour result = new LocalLoopRemover().removeLoops(contour, 200);

        assertSame(contour, result);
        assertEquals(4, result.points().size());
    }
}
