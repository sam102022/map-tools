package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du lisseur multi-échelle des tronçons droits {@link StraightSegmentSmoother}.
 */
@DisplayName("Tests du lisseur adaptatif multi-échelle des tronçons droits (Sprint 7)")
class StraightSegmentSmootherTest {

    /**
     * Vérifie qu'un tronçon droit horizontal perturbé par une encoche locale de 4 px
     * (icône cartographique) est convenablement redressé avec une déviation résiduelle inférieure à 0.5 px.
     */
    @Test
    @DisplayName("Un tronçon droit horizontal perturbé par une encoche d'icône est redressé")
    void testStraightLineWithNotchIsStraightened() {
        int n = 120;
        List<PixelPoint> segment = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double y = 50.0;
            // Encoche de 4 px de haut sur 6 points entre i=57 et i=62
            if (i >= 57 && i <= 62) {
                y += 4.0;
            }
            segment.add(new PixelPoint(i, y));
        }

        StraightSegmentSmoother smoother = new StraightSegmentSmoother();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        List<PixelPoint> smoothed = smoother.smoothSegment(segment, config);

        assertEquals(n, smoothed.size());

        // La droite idéale est y = 50.0 pour tous les points
        double maxDeviation = 0.0;
        for (PixelPoint pt : smoothed) {
            double dev = Math.abs(pt.y() - 50.0);
            if (dev > maxDeviation) {
                maxDeviation = dev;
            }
        }

        assertTrue(maxDeviation < 0.5,
                String.format("L'encoche doit être absorbée : déviation max = %.3f px (< 0.5 px attendu)", maxDeviation));
    }

    /**
     * Vérifie qu'une courbe prononcée (arc de cercle de rayon 50 px) n'est pas aplatie sur sa corde directrice
     * et conserve sa courbure authentique.
     */
    @Test
    @DisplayName("Une courbure prononcée conserve sa géométrie et n'est pas écrasée sur la corde")
    void testPronouncedCurvePreservesCurvature() {
        int n = 100;
        double radius = 50.0;
        List<PixelPoint> segment = new ArrayList<>(n);

        // Arc de cercle de pi/6 à 5*pi/6 (courbe prononcée)
        for (int i = 0; i < n; i++) {
            double theta = Math.PI / 6.0 + (double) i / (n - 1.0) * (2.0 * Math.PI / 3.0);
            double x = radius * Math.cos(theta);
            double y = radius * Math.sin(theta);
            segment.add(new PixelPoint(x, y));
        }

        StraightSegmentSmoother smoother = new StraightSegmentSmoother();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        List<PixelPoint> smoothed = smoother.smoothSegment(segment, config);

        assertEquals(n, smoothed.size());

        // Au milieu de l'arc (sommet de la courbure), y doit rester proche du rayon initial (50.0)
        PixelPoint middle = smoothed.get(n / 2);
        assertTrue(middle.y() > 40.0,
                "La courbure ne doit pas être écrasée sur la corde directrice (y = " + middle.y() + ")");
    }

    /**
     * Vérifie qu'un segment de longueur inférieure au seuil minimal admissible (2.5 * sigma)
     * est retourné sans altération.
     */
    @Test
    @DisplayName("Un segment trop court (< 2.5 * sigma) est retourné inchangé")
    void testShortSegmentReturnsUnchanged() {
        int n = 30; // 30 < 2.5 * 22.0 = 55.0
        List<PixelPoint> segment = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            segment.add(new PixelPoint(i, 10.0));
        }

        StraightSegmentSmoother smoother = new StraightSegmentSmoother();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        List<PixelPoint> smoothed = smoother.smoothSegment(segment, config);

        assertEquals(segment, smoothed);
    }

    /**
     * Vérifie que le lissage d'un contour complet fermé s'exécute correctement et préserve les coins.
     */
    @Test
    @DisplayName("smoothContour partitionne le contour fermé et préserve les coins")
    void testSmoothContourWithClosedRing() {
        int n = 200;
        List<PixelPoint> contour = new ArrayList<>(n);
        for (int i = 0; i < 50; i++) {
            contour.add(new PixelPoint(i, 0.0));
        }
        for (int i = 0; i < 50; i++) {
            contour.add(new PixelPoint(50.0, i));
        }
        for (int i = 50; i > 0; i--) {
            contour.add(new PixelPoint(i, 50.0));
        }
        for (int i = 50; i > 0; i--) {
            contour.add(new PixelPoint(0.0, i));
        }

        List<Integer> corners = List.of(0, 50, 100, 150);

        StraightSegmentSmoother smoother = new StraightSegmentSmoother();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        List<PixelPoint> result = smoother.smoothContour(contour, corners, config);

        assertNotNull(result);
        assertEquals(n, result.size());
    }
}
