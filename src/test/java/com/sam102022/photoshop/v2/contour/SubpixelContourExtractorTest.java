package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests de l'extracteur de contour sub-pixel Marching Squares 2D")
class SubpixelContourExtractorTest {

    @Test
    @DisplayName("Extrait un contour sub-pixel fermé sur un rectangle binaire avec précision de bord")
    void testExtractOnRectangle() {
        int w = 30;
        int h = 30;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 5; y < 25; y++) {
            for (int x = 5; x < 25; x++) {
                mask.set(x, y, true);
            }
        }

        SubpixelContourExtractor extractor = new SubpixelContourExtractor();
        List<PixelPoint> contour = extractor.extract(mask, 1.0);

        assertFalse(contour.isEmpty(), "Le contour extrait ne doit pas être vide.");
        // Le pas moyen le long du contour doit être proche de 1.0 px
        double totalStep = 0.0;
        for (int i = 0; i < contour.size(); i++) {
            PixelPoint p1 = contour.get(i);
            PixelPoint p2 = contour.get((i + 1) % contour.size());
            totalStep += Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
        }
        double avgStep = totalStep / contour.size();
        assertEquals(1.0, avgStep, 0.05, "Le pas moyen entre points consécutifs doit être proche de 1.0 px");
    }

    @Test
    @DisplayName("Comble automatiquement les cavités intérieures avant l'extraction")
    void testComblementTrousInterieurs() {
        int w = 40;
        int h = 40;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 5; y < 35; y++) {
            for (int x = 5; x < 35; x++) {
                mask.set(x, y, true);
            }
        }
        // Trou intérieur de 10x10 px
        for (int y = 15; y < 25; y++) {
            for (int x = 15; x < 25; x++) {
                mask.set(x, y, false);
            }
        }

        SubpixelContourExtractor extractor = new SubpixelContourExtractor();
        List<PixelPoint> contour = extractor.extract(mask, 1.0);

        // Une seule boucle extérieure doit être produite
        assertFalse(contour.isEmpty());
        // Vérification qu'aucun point n'est dans la zone du trou intérieur
        for (PixelPoint p : contour) {
            boolean inHole = (p.x() >= 15.0 && p.x() <= 24.0 && p.y() >= 15.0 && p.y() <= 24.0);
            assertFalse(inHole, "Le contour ne doit pas traverser le trou comblé.");
        }
    }
}
