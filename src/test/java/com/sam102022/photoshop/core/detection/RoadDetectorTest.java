package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadDetectorTest {

    @Test
    @DisplayName("Détection d'une autoroute orange Google Maps")
    void testDetectOrangeHighway() {
        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 40, 40);

        // Voie rapide orange
        g.setColor(new Color(245, 165, 100));
        g.fillRect(18, 0, 4, 40);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(20, 20), "La ligne orange d'autoroute doit former une barrière routière");
        assertFalse(roadBarrier.get(5, 5), "Le fond ne doit pas être détecté comme route");
    }

    @Test
    @DisplayName("Détection d'une route principale jaune")
    void testDetectYellowAvenue() {
        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 40, 40);

        // Route jaune / crème
        g.setColor(new Color(250, 230, 140));
        g.fillRect(0, 18, 40, 4);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(20, 20), "L'avenue jaune doit être détectée");
    }

    @Test
    @DisplayName("Rebouchage d'une brèche routière grâce à la fermeture morphologique")
    void testGapFillingThroughClosing() {
        BufferedImage map = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 50, 50);

        // Ligne d'autoroute avec interruption de 2 pixels
        g.setColor(new Color(245, 165, 100));
        g.fillRect(20, 0, 4, 23);
        g.fillRect(20, 26, 4, 24);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(21, 24), "La brèche dans la route doit être pontée par la fermeture morphologique");
    }

    @Test
    @DisplayName("Le blanc ou le fond ordinaire ne produit pas de candidats routiers sur la seule couleur")
    void testWhiteAloneDoesNotProduceRoads() {
        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 40, 40);

        // Zone blanche (bâtiment ou pavé)
        g.setColor(new Color(255, 255, 255));
        g.fillRect(15, 0, 6, 40);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertEquals(0, roadBarrier.countActivePixels(), "La couleur blanche seule ne doit pas générer de route");
    }

    @Test
    @DisplayName("Rejet des paramètres nulls")
    void testNullParametersThrow() {
        RoadDetector detector = new RoadDetector();
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        assertThrows(IllegalArgumentException.class, () -> detector.detectRoads(null, SnappingConfig.defaults()));
        assertThrows(IllegalArgumentException.class, () -> detector.detectRoads(map, null));
    }
}
