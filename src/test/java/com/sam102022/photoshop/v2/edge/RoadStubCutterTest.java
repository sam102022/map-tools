package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la coupe des amorces de rues aux sommets du tracé {@link RoadStubCutter}.
 */
@DisplayName("Tests unitaires du coupeur d'amorces RoadStubCutter")
class RoadStubCutterTest {

    private static final int SIZE = 300;

    /**
     * Une amorce de 20 px qui dépasse au coin haut-droit d'un carré, près d'un sommet du tracé, est coupée au coin.
     */
    @Test
    @DisplayName("Coupe l'amorce au coin quand un sommet du tracé est proche")
    void testCutsStubNearTraceVertex() {
        SmoothVectorContour contour = squareWithStub();

        SmoothVectorContour cut = new RoadStubCutter().cut(contour, fullRoad(), List.of(),
                List.of(new PixelPoint(252, 48)));

        for (PixelPoint p : cut.points()) {
            assertTrue(p.y() >= 50.0 - 0.6, "L'amorce doit être coupée : " + p);
        }
        assertTrue(cut.points().stream().anyMatch(p -> Math.hypot(p.x() - 250, p.y() - 50) < 0.6),
                "Le contour doit passer par le coin des deux bords.");
    }

    /**
     * Sans sommet du tracé à proximité, le contour est inchangé.
     */
    @Test
    @DisplayName("Ne coupe rien loin des sommets du tracé")
    void testKeepsStubAwayFromTraceVertices() {
        SmoothVectorContour contour = squareWithStub();

        SmoothVectorContour cut = new RoadStubCutter().cut(contour, fullRoad(), List.of(),
                List.of(new PixelPoint(50, 250)));

        assertSame(contour, cut, "Le contour doit être inchangé.");
    }

    /**
     * Une embouchure de rue latérale (8 px de dépassement, 20 px de large) sur un bord rectiligne, à moins de 60 px
     * d'un sommet du tracé, est remplacée par sa corde.
     */
    @Test
    @DisplayName("Coupe l'embouchure d'une rue latérale près d'un sommet du tracé")
    void testCutsSideStreetStubNearTraceVertex() {
        List<PixelPoint> points = new ArrayList<>();
        addSegment(points, 50, 50, 140, 50);
        addSegment(points, 140, 50, 140, 42);
        addSegment(points, 140, 42, 160, 42);
        addSegment(points, 160, 42, 160, 50);
        addSegment(points, 160, 50, 250, 50);
        addSegment(points, 250, 50, 250, 250);
        addSegment(points, 250, 250, 50, 250);
        addSegment(points, 50, 250, 50, 50);
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), SIZE, SIZE,
                new CropWindow(0, 0, SIZE, SIZE));

        SmoothVectorContour cut = new RoadStubCutter().cut(contour, fullRoad(), List.of(),
                List.of(new PixelPoint(150, 0)));

        for (PixelPoint p : cut.points()) {
            assertTrue(p.y() >= 50.0 - 0.6, "L'embouchure doit être coupée : " + p);
        }
    }

    /**
     * Carré [50, 250]² parcouru au pas de 1 px, avec une amorce rectangulaire de 15 x 20 px au coin haut-droit.
     *
     * @return Contour fermé.
     */
    private SmoothVectorContour squareWithStub() {
        List<PixelPoint> points = new ArrayList<>();
        addSegment(points, 50, 50, 235, 50);
        addSegment(points, 235, 50, 235, 30);
        addSegment(points, 235, 30, 250, 30);
        addSegment(points, 250, 30, 250, 250);
        addSegment(points, 250, 250, 50, 250);
        addSegment(points, 50, 250, 50, 50);
        return new SmoothVectorContour(points, List.of(), SIZE, SIZE, new CropWindow(0, 0, SIZE, SIZE));
    }

    /**
     * Ajoute les points d'un segment au pas de 1 px, extrémité finale exclue.
     *
     * @param points Liste complétée.
     * @param x0     Abscisse de départ.
     * @param y0     Ordonnée de départ.
     * @param x1     Abscisse d'arrivée.
     * @param y1     Ordonnée d'arrivée.
     */
    private void addSegment(List<PixelPoint> points, double x0, double y0, double x1, double y1) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0));
        for (int s = 0; s < steps; s++) {
            double t = (double) s / steps;
            points.add(new PixelPoint(x0 + t * (x1 - x0), y0 + t * (y1 - y0)));
        }
    }

    /**
     * Masque entièrement en chaussée.
     *
     * @return Masque.
     */
    private BinaryMask fullRoad() {
        BinaryMask mask = new BinaryMask(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                mask.set(x, y, true);
            }
        }
        return mask;
    }
}
