package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContourExtractorTest {

    @Test
    @DisplayName("Extraction du contour ordonné d'un rectangle simple")
    void testExtractRectangle() {
        BinaryMask mask = new BinaryMask(20, 20);
        for (int y = 5; y <= 10; y++) {
            for (int x = 5; x <= 15; x++) {
                mask.set(x, y, true);
            }
        }

        ContourExtractor extractor = new ContourExtractor();
        List<Point> contour = extractor.extractLargestContour(mask);

        assertNotNull(contour);
        assertFalse(contour.isEmpty(), "Le contour ne doit pas être vide");
        assertTrue(contour.size() < 100, "Le contour d'un rectangle 11x6 ne doit pas excéder 100 points, obtenu: " + contour.size());
        assertTrue(contour.getFirst().equals(contour.getLast()) || contour.size() >= 4);
    }

    @Test
    @DisplayName("Extraction du contour sur l'échantillon réel limites.jpg")
    void testExtractRealSample() throws Exception {
        java.nio.file.Path sampleDir = java.nio.file.Paths.get("src", "main", "resources", "sample");
        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(sampleDir.resolve("limites.jpg").toFile());
        com.sam102022.photoshop.core.detection.GreenMaskExtractor extractor = new com.sam102022.photoshop.core.detection.GreenMaskExtractor();
        BinaryMask mask = extractor.extract(img);

        long start = System.currentTimeMillis();
        ContourExtractor contourExtractor = new ContourExtractor();
        List<Point> contour = contourExtractor.extractLargestContour(mask);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(contour.isEmpty());
        assertTrue(contour.size() > 1000, "Le contour de la parcelle principale doit contenir plus de 1000 points");
        assertTrue(elapsed < 2000, "L'extraction du contour doit prendre moins de 2 secondes");
    }
}
