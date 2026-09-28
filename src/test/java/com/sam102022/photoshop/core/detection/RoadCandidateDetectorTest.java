package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link RoadCandidateDetector}.
 * <p>
 * Valide la détection des axes routiers majeurs colorés (orange, jaune),
 * des rues locales blanches et grises avec bordures de corridor,
 * et la modulation par {@code roadSensitivity}.
 * </p>
 */
class RoadCandidateDetectorTest {

    private RoadCandidateDetector detector;

    @BeforeEach
    void setUp() {
        detector = new RoadCandidateDetector();
    }

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

        BinaryMask mask = detector.detect(map);

        assertTrue(mask.get(15, 12), "L'avenue jaune doit être détectée");
    }

    @Test
    @DisplayName("Détecter une rue locale blanche avec bordures grises neutres")
    void testDetectWhiteAndGreyLocalStreets() {
        int w = 20, h = 20;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        // Fond résidentiel beige / marron clair
        g.setColor(new Color(238, 230, 220));
        g.fillRect(0, 0, w, h);

        // Rue locale verticale à x=10 : chaussée blanche x=10, bordures grises à x=9 et x=11
        g.setColor(new Color(200, 200, 200)); // Bordure grise
        g.drawLine(9, 0, 9, h - 1);
        g.drawLine(11, 0, 11, h - 1);
        g.setColor(new Color(245, 245, 245)); // Chaussée blanche neutre
        g.drawLine(10, 0, 10, h - 1);
        g.dispose();

        BinaryMask roads = detector.detect(map, 1.0f);
        assertTrue(roads.get(10, 5), "La chaussée blanche doit être détectée");
        assertTrue(roads.get(9, 5) || roads.get(11, 5), "Les bordures grises de chaussée doivent être détectées");
        assertFalse(roads.get(2, 2), "Le fond beige résidentiel ne doit pas être une route");
    }

    @Test
    @DisplayName("Rejeter les surfaces blanches larges et uniformes sans structure de corridor")
    void testRejectUniformWhiteAreas() {
        int w = 30, h = 30;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(245, 245, 245)); // Grand aplat blanc uniforme (toiture)
        g.fillRect(0, 0, w, h);
        g.dispose();

        BinaryMask roads = detector.detect(map, 1.0f);
        // Au centre d'un grand aplat uniforme sans bordures à proximité, aucun pixel de route ne doit être détecté
        assertFalse(roads.get(15, 15), "Un grand aplat blanc uniforme sans bordure ne doit pas être une route");
    }

    @Test
    @DisplayName("Modulation de la détection selon roadSensitivity")
    void testRoadSensitivityModulation() {
        int w = 20, h = 20;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(238, 230, 220));
        g.fillRect(0, 0, w, h);

        // Rue à plus faible contraste (chaussée à 233, bordure à 210)
        g.setColor(new Color(210, 210, 210));
        g.drawLine(9, 0, 9, h - 1);
        g.drawLine(11, 0, 11, h - 1);
        g.setColor(new Color(233, 233, 233));
        g.drawLine(10, 0, 10, h - 1);
        g.dispose();

        BinaryMask roadsLowSens = detector.detect(map, 0.7f);
        BinaryMask roadsHighSens = detector.detect(map, 1.5f);

        // Avec une sensibilité élevée, la rue peu contrastée doit être détectée
        assertTrue(roadsHighSens.get(10, 5), "Avec haute sensibilité, la rue peu contrastée doit être détectée");
        // Avec une faible sensibilité, elle doit être filtrée
        assertFalse(roadsLowSens.get(10, 5), "Avec basse sensibilité, la rue peu contrastée doit être filtrée");
    }

    @Test
    @DisplayName("Validation des paramètres nulls")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null, 1.0f));
        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> detector.detect(img, 0.0f));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(img, -1.0f));
    }
}
