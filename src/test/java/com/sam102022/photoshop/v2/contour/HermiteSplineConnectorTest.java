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
}
