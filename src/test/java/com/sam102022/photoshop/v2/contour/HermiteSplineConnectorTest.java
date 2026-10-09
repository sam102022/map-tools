package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du connecteur Hermite C1 et de substitution d'arcs de ronds-points")
class HermiteSplineConnectorTest {

    @Test
    @DisplayName("Génère une spline d'Hermite continue entre deux points avec tangentes alignées")
    void testHermiteSplineDirect() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        PixelPoint p0 = new PixelPoint(0, 0);
        PixelPoint t0 = new PixelPoint(1, 0);
        PixelPoint p1 = new PixelPoint(50, 0);
        PixelPoint t1 = new PixelPoint(1, 0);

        List<PixelPoint> spline = connector.evaluateHermite(p0, t0, p1, t1);
        assertFalse(spline.isEmpty());
        // Doit être parfaitement horizontal y=0
        for (PixelPoint p : spline) {
            assertEquals(0.0, p.y(), 1e-4);
        }
    }

    @Test
    @DisplayName("Active le repli linéaire sécurisé en cas de boucle excessive sur la spline d'Hermite")
    void testHermiteLoopFallback() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        PixelPoint p0 = new PixelPoint(0, 0);
        PixelPoint t0 = new PixelPoint(-1, 0); // Tangente orientée à l'envers provoquant une boucle
        PixelPoint p1 = new PixelPoint(20, 0);
        PixelPoint t1 = new PixelPoint(-1, 0);

        List<PixelPoint> spline = connector.evaluateHermite(p0, t0, p1, t1);
        assertFalse(spline.isEmpty());
        // Repli linéaire : pas de boucle aberrante
        for (PixelPoint p : spline) {
            assertTrue(p.x() >= -1.0 && p.x() <= 21.0);
        }
    }

    /**
     * Vérifie que integrateRoundaboutsWithTracking retourne le contour et la liste des ronds-points substitués.
     */
    @Test
    @DisplayName("integrateRoundaboutsWithTracking renvoie le contour et la liste des ronds-points substitués")
    void testIntegrateRoundaboutsWithTracking() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        BinaryMask mask = new BinaryMask(100, 100);
        List<PixelPoint> contour = List.of(
                new PixelPoint(10, 10),
                new PixelPoint(20, 10),
                new PixelPoint(20, 20),
                new PixelPoint(10, 20)
        );
        HermiteSplineConnector.RoundaboutSubstitutionResult result =
                connector.integrateRoundaboutsWithTracking(contour, List.of(), mask, 2.0);
        assertEquals(contour, result.contour());
        assertTrue(result.substitutedRoundabouts().isEmpty());
    }

    /**
     * Construit un contour rectangulaire fermé échantillonné au pas de 1 px.
     *
     * @param x0 Abscisse minimale.
     * @param y0 Ordonnée minimale.
     * @param x1 Abscisse maximale.
     * @param y1 Ordonnée maximale.
     * @return Contour fermé du rectangle.
     */
    private static List<PixelPoint> rectangleContour(int x0, int y0, int x1, int y1) {
        List<PixelPoint> contour = new ArrayList<>();
        for (int x = x0; x < x1; x++) {
            contour.add(new PixelPoint(x, y0));
        }
        for (int y = y0; y < y1; y++) {
            contour.add(new PixelPoint(x1, y));
        }
        for (int x = x1; x > x0; x--) {
            contour.add(new PixelPoint(x, y1));
        }
        for (int y = y1; y > y0; y--) {
            contour.add(new PixelPoint(x0, y));
        }
        return contour;
    }

    /**
     * Régression : le test de recouvrement du disque (&gt;= 20 %) doit porter sur le masque consolidé rempli
     * (Mfill de l'étalon Python), qui contient la chaussée de l'anneau, et non sur les seules cellules
     * retenues (T), qui l'excluent.
     */
    @Test
    @DisplayName("Le recouvrement du disque du giratoire est évalué sur le masque consolidé rempli")
    void testDiskOverlapUsesConsolidatedFilledMask() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        List<PixelPoint> contour = rectangleContour(10, 0, 60, 100);
        Roundabout roundabout = new Roundabout(1, new PixelPoint(50, 50),
                new EllipseModel(50, 50, 10, 10, 0.0), 80.0, 1.0);

        BinaryMask retainedCells = new BinaryMask(100, 100);
        BinaryMask consolidatedFilled = new BinaryMask(100, 100);
        for (int y = 0; y < 100; y++) {
            for (int x = 10; x <= 60; x++) {
                consolidatedFilled.set(x, y, true);
            }
        }

        HermiteSplineConnector.RoundaboutSubstitutionResult withFilled = connector.integrateRoundaboutsWithTracking(
                contour, List.of(roundabout), retainedCells, consolidatedFilled, 2.0);
        HermiteSplineConnector.RoundaboutSubstitutionResult withCellsOnly = connector.integrateRoundaboutsWithTracking(
                contour, List.of(roundabout), retainedCells, 2.0);

        assertEquals(1, withFilled.substitutedRoundabouts().size(),
                "Le giratoire couvert par Mfill doit être substitué.");
        assertTrue(withCellsOnly.substitutedRoundabouts().isEmpty(),
                "Sans chaussée dans le masque, le disque n'atteint pas 20 % de recouvrement.");
    }
}
