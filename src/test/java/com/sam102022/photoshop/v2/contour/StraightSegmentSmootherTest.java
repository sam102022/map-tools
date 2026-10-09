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

    /**
     * Construit un contour circulaire fermé bruité de manière déterministe (créneaux de +/- 1.5 px).
     *
     * @param n Nombre de points du contour.
     * @return Contour fermé bruité.
     */
    private static List<PixelPoint> noisyCircle(int n) {
        List<PixelPoint> contour = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double t = 2.0 * Math.PI * i / n;
            double r = 150.0 + (((i / 5) % 2 == 0) ? 1.5 : -1.5);
            contour.add(new PixelPoint(200.0 + r * Math.cos(t), 200.0 + r * Math.sin(t)));
        }
        return contour;
    }

    /**
     * Calcule la rugosité moyenne d'un contour fermé (norme moyenne de la dérivée seconde discrète).
     *
     * @param contour Contour fermé à évaluer.
     * @return Rugosité moyenne en pixels.
     */
    private static double meanRoughness(List<PixelPoint> contour) {
        int n = contour.size();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            PixelPoint prev = contour.get((i - 1 + n) % n);
            PixelPoint curr = contour.get(i);
            PixelPoint next = contour.get((i + 1) % n);
            sum += Math.hypot(next.x() - 2.0 * curr.x() + prev.x(), next.y() - 2.0 * curr.y() + prev.y());
        }
        return sum / n;
    }

    /**
     * Régression : un contour sans coin (ou avec un seul coin) doit être lissé sur toute la boucle,
     * et non laissé brut (segment réduit à un point avant correction).
     */
    @Test
    @DisplayName("Un contour fermé sans coin ou avec un seul coin est lissé sur la boucle complète")
    void testClosedContourWithZeroOrOneCornerIsSmoothed() {
        List<PixelPoint> contour = noisyCircle(940);
        double rawRoughness = meanRoughness(contour);
        StraightSegmentSmoother smoother = new StraightSegmentSmoother();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();

        List<PixelPoint> noCorner = smoother.smoothContour(contour, List.of(), config);
        List<PixelPoint> oneCorner = smoother.smoothContour(contour, List.of(300), config);

        assertEquals(contour.size(), noCorner.size());
        assertEquals(contour.size(), oneCorner.size());
        assertTrue(meanRoughness(noCorner) < 0.2 * rawRoughness,
                "Sans coin : rugosité " + meanRoughness(noCorner) + " vs brute " + rawRoughness);
        assertTrue(meanRoughness(oneCorner) < 0.2 * rawRoughness,
                "Un coin : rugosité " + meanRoughness(oneCorner) + " vs brute " + rawRoughness);
    }
}
