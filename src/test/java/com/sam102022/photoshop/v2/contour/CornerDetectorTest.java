package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du détecteur d'angles vifs et coins")
class CornerDetectorTest {

    @Test
    @DisplayName("Détecte exactement les 4 sommets d'un grand rectangle à pas unitaire")
    void testDetectRectangleCorners() {
        // Rectangle de 200 x 200 px échantillonné à pas 1 px (800 points)
        List<PixelPoint> contour = new ArrayList<>();
        // Côté haut (gauche à droite)
        for (int x = 0; x < 200; x++) contour.add(new PixelPoint(x, 0));
        // Côté droit (haut en bas)
        for (int y = 0; y < 200; y++) contour.add(new PixelPoint(200, y));
        // Côté bas (droite à gauche)
        for (int x = 200; x > 0; x--) contour.add(new PixelPoint(x, 200));
        // Côté gauche (bas en haut)
        for (int y = 200; y > 0; y--) contour.add(new PixelPoint(0, y));

        CornerDetector detector = new CornerDetector();
        // L=40, threshold=38 deg, preSmoothSigma=2.0
        List<Integer> corners = detector.detectCorners(contour, 40, 38.0, 2.0);

        assertEquals(4, corners.size(), "Un rectangle doit comporter exactement 4 coins détectés.");
    }

    @Test
    @DisplayName("Ne détecte aucun coin sur un cercle continu")
    void testNoCornersOnCircle() {
        List<PixelPoint> contour = new ArrayList<>();
        int n = 1885; // Circonférence 2 * pi * 300 ~= 1885 px
        double radius = 300.0;
        for (int i = 0; i < n; i++) {
            double phi = 2.0 * Math.PI * i / n;
            contour.add(new PixelPoint(400 + radius * Math.cos(phi), 400 + radius * Math.sin(phi)));
        }

        CornerDetector detector = new CornerDetector();
        List<Integer> corners = detector.detectCorners(contour, 80, 38.0, 4.0);
        assertTrue(corners.isEmpty(), "Un cercle lisse ne doit comporter aucun angle vif.");
    }
}
