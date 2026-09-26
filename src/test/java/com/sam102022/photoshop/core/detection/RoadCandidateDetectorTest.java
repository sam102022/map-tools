package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class RoadCandidateDetectorTest {

    @Test
    @DisplayName("Détecter une région orange synthétique (autoroute)")
    void testDetectOrangeHighway() {
        BufferedImage map = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 30, 30);
        g.setColor(new Color(245, 165, 100)); // orange
        g.fillRect(10, 0, 4, 30);
        g.dispose();

        RoadCandidateDetector detector = new RoadCandidateDetector();
        BinaryMask mask = detector.detect(map);

        assertTrue(mask.get(12, 15), "La voie orange doit être détectée");
        assertFalse(mask.get(2, 2), "Le fond beige ne doit pas être détecté");
    }

    @Test
    @DisplayName("Détecter une région jaune ou crème synthétique (voie principale)")
    void testDetectYellowAvenue() {
        BufferedImage map = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 30, 30);
        g.setColor(new Color(250, 230, 140)); // jaune
        g.fillRect(0, 10, 30, 4);
        g.dispose();

        RoadCandidateDetector detector = new RoadCandidateDetector();
        BinaryMask mask = detector.detect(map);

        assertTrue(mask.get(15, 12), "L'avenue jaune doit être détectée");
    }

    @Test
    @DisplayName("Ne pas classer un fond cartographique ordinaire ou une zone blanche comme route sur la seule couleur")
    void testExcludeWhiteAndBackground() {
        BufferedImage map = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 30, 30);
        // Bâtiment blanc ou zone pavée claire
        g.setColor(new Color(255, 255, 255));
        g.fillRect(5, 5, 10, 10);
        g.dispose();

        RoadCandidateDetector detector = new RoadCandidateDetector();
        BinaryMask mask = detector.detect(map);

        assertEquals(0, mask.countActivePixels(), "Les pixels blancs ou de fond ne doivent pas être pris comme routes candidates");
    }

    @Test
    @DisplayName("Validation des paramètres nulls")
    void testNullValidation() {
        RoadCandidateDetector detector = new RoadCandidateDetector();
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }
}
