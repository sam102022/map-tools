package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du solveur direct d'ellipse algébrique (Halir & Flusser 1998)")
class AlgebraicEllipseFitterTest {

    @Test
    @DisplayName("Ajuste avec une précision < 1% une ellipse connue bruitée")
    void testFitKnownEllipse() {
        double expectedXc = 120.0;
        double expectedYc = 250.0;
        double expectedA = 45.0;
        double expectedB = 25.0;
        double expectedTheta = 0.6; // ~34.4 degrés

        List<PixelPoint> points = new ArrayList<>();
        int n = 40;
        for (int i = 0; i < n; i++) {
            double phi = 2.0 * Math.PI * i / n;
            double u = expectedA * Math.cos(phi);
            double v = expectedB * Math.sin(phi);
            double x = expectedXc + u * Math.cos(expectedTheta) - v * Math.sin(expectedTheta);
            double y = expectedYc + u * Math.sin(expectedTheta) + v * Math.cos(expectedTheta);
            // Bruit déterministe léger +/- 0.2 px
            double noise = ((i % 3) - 1) * 0.2;
            points.add(new PixelPoint(x + noise, y - noise));
        }

        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();
        Optional<EllipseModel> fittedOpt = fitter.fit(points);
        assertTrue(fittedOpt.isPresent(), "L'ellipse doit être ajustée avec succès.");

        EllipseModel fitted = fittedOpt.get();
        assertEquals(expectedXc, fitted.xc(), 1.0, "Centre X proche");
        assertEquals(expectedYc, fitted.yc(), 1.0, "Centre Y proche");
        assertEquals(expectedA, fitted.a(), 1.0, "Demi-grand axe proche");
        assertEquals(expectedB, fitted.b(), 1.0, "Demi-petit axe proche");
        assertTrue(fitted.a() >= fitted.b(), "L'invariant a >= b doit être respecté");
    }

    @Test
    @DisplayName("Retourne Optional.empty() si le nuage de points est insuffisant ou colinéaire")
    void testDegeneratePoints() {
        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();
        assertFalse(fitter.fit(List.of()).isPresent());
        assertFalse(fitter.fit(List.of(new PixelPoint(0, 0), new PixelPoint(1, 1))).isPresent());

        // Points alignés sur une ligne droite
        List<PixelPoint> colinear = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            colinear.add(new PixelPoint(i * 10, i * 10));
        }
        assertFalse(fitter.fit(colinear).isPresent());
    }

    @Test
    @DisplayName("Calcule les résidus de distance euclidienne exacte à l'ellipse par méthode itérative")
    void testComputeResiduals() {
        EllipseModel ellipse = new EllipseModel(0, 0, 20.0, 10.0, 0.0);
        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();

        // Point à l'extérieur : (25, 5)
        double res = fitter.distanceToEllipse(new PixelPoint(25.0, 5.0), ellipse);
        assertEquals(6.159263, res, 1e-4);

        // Point sur l'axe : (0, 15)
        double resY = fitter.distanceToEllipse(new PixelPoint(0.0, 15.0), ellipse);
        assertEquals(5.0, resY, 1e-4);
    }
}
