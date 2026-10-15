package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
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
 * Tests unitaires de la suppression des bosses le long des bords rectilignes {@link StraightEdgeBumpRemover}.
 */
@DisplayName("Tests unitaires du supprimeur de bosses StraightEdgeBumpRemover")
class StraightEdgeBumpRemoverTest {

    private static final int WIDTH = 500;
    private static final int HEIGHT = 300;
    private static final double BOTTOM = 200.0;

    /**
     * Une bosse de 10 px au milieu du bord bas d'un rectangle, sur de la chaussée, est remplacée par sa corde.
     */
    @Test
    @DisplayName("Supprime une bosse sur un bord rectiligne bordé de chaussée")
    void testRemovesBumpOnRoad() {
        SmoothVectorContour contour = rectangleWithBump();

        SmoothVectorContour cleaned = new StraightEdgeBumpRemover().remove(contour, filledMask(true), List.of());

        for (PixelPoint p : cleaned.points()) {
            assertTrue(p.y() <= BOTTOM + 0.1, "La bosse doit être remplacée par sa corde : " + p);
        }
        assertTrue(containsPoint(cleaned, 400, 0) && containsPoint(cleaned, 400, 200)
                        && containsPoint(cleaned, 0, 200) && containsPoint(cleaned, 0, 0),
                "Les angles du rectangle doivent être conservés.");
    }

    /**
     * Une bosse dont la corde balaierait de la terre (un pâté de maisons) est conservée.
     */
    @Test
    @DisplayName("Conserve une bosse qui balaierait de la terre")
    void testKeepsBumpOverLand() {
        SmoothVectorContour contour = rectangleWithBump();

        SmoothVectorContour cleaned = new StraightEdgeBumpRemover().remove(contour, filledMask(false), List.of());

        assertSame(contour, cleaned, "Le contour doit être inchangé.");
    }

    /**
     * Une bosse contournant un giratoire n'est pas coupée par une corde traversant l'anneau.
     */
    @Test
    @DisplayName("Ne coupe pas un giratoire")
    void testKeepsRoundabout() {
        SmoothVectorContour contour = rectangleWithBump();
        Roundabout roundabout = new Roundabout(new PixelPoint(200, 195),
                new EllipseModel(200, 195, 12, 12, 0.0), 300.0, 1.0);

        SmoothVectorContour cleaned = new StraightEdgeBumpRemover().remove(contour, filledMask(true),
                List.of(roundabout));

        assertSame(contour, cleaned, "Le contour doit être inchangé.");
    }

    /**
     * Un rectangle sans bosse est retourné tel quel.
     */
    @Test
    @DisplayName("Un contour sans bosse est inchangé")
    void testStraightContourUnchanged() {
        List<PixelPoint> points = new ArrayList<>();
        addSegment(points, 0, 0, 400, 0);
        addSegment(points, 400, 0, 400, BOTTOM);
        addSegment(points, 400, BOTTOM, 0, BOTTOM);
        addSegment(points, 0, BOTTOM, 0, 0);
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), WIDTH, HEIGHT,
                new CropWindow(0, 0, WIDTH, HEIGHT));

        SmoothVectorContour cleaned = new StraightEdgeBumpRemover().remove(contour, filledMask(true), List.of());

        assertSame(contour, cleaned, "Le contour doit être inchangé.");
        assertEquals(points.size(), cleaned.points().size());
    }

    /**
     * Rectangle 400 x 200 parcouru au pas de 1 px, avec une bosse triangulaire de 10 px sous le bord bas.
     *
     * @return Contour fermé.
     */
    private SmoothVectorContour rectangleWithBump() {
        List<PixelPoint> points = new ArrayList<>();
        addSegment(points, 0, 0, 400, 0);
        addSegment(points, 400, 0, 400, BOTTOM);
        addSegment(points, 400, BOTTOM, 220, BOTTOM);
        addSegment(points, 220, BOTTOM, 200, BOTTOM + 10);
        addSegment(points, 200, BOTTOM + 10, 180, BOTTOM);
        addSegment(points, 180, BOTTOM, 0, BOTTOM);
        addSegment(points, 0, BOTTOM, 0, 0);
        return new SmoothVectorContour(points, List.of(), WIDTH, HEIGHT, new CropWindow(0, 0, WIDTH, HEIGHT));
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
     * Masque uniforme.
     *
     * @param value Valeur de tous les pixels (true = chaussée).
     * @return Masque.
     */
    private BinaryMask filledMask(boolean value) {
        BinaryMask mask = new BinaryMask(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                mask.set(x, y, value);
            }
        }
        return mask;
    }

    /**
     * Teste la présence d'un point (à 1e-6 près) dans le contour.
     *
     * @param contour Contour.
     * @param x       Abscisse.
     * @param y       Ordonnée.
     * @return true si le point est présent.
     */
    private boolean containsPoint(SmoothVectorContour contour, double x, double y) {
        return contour.points().stream().anyMatch(p -> Math.abs(p.x() - x) < 1e-6 && Math.abs(p.y() - y) < 1e-6);
    }
}
