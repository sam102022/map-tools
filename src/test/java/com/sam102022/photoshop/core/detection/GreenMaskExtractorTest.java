package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenMaskExtractorTest {

    @Test
    @DisplayName("Extraction d'un rectangle vert pur sur fond blanc")
    void testExtractPureGreen() {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);

        // Rectangle vert au centre (10,10 à 39,39)
        g.setColor(new Color(0, 220, 0));
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertTrue(mask.get(20, 20), "Le centre du carré vert doit être actif");
        assertFalse(mask.get(5, 5), "Le fond blanc ne doit pas être actif");
        assertTrue(mask.countActivePixels() > 800, "La majeure partie du carré 30x30 doit être détectée");
    }

    @Test
    @DisplayName("Extraction avec différentes teintes vertes (kaki, émeraude)")
    void testExtractVariousGreens() {
        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 30, 30);

        // Vert émeraude
        g.setColor(new Color(20, 180, 80));
        g.fillRect(5, 5, 10, 10);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertTrue(mask.get(10, 10), "Le vert émeraude doit être détecté");
        assertFalse(mask.get(25, 25), "Le fond noir ne doit pas être détecté");
    }

    @Test
    @DisplayName("Image sans pixel vert retourne un masque vide")
    void testExtractNoGreen() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertEquals(0, mask.countActivePixels(), "Aucun pixel ne doit être actif sur une image rouge");
    }

    @Test
    @DisplayName("Image null lance IllegalArgumentException")
    void testExtractNullImage() {
        GreenMaskExtractor extractor = new GreenMaskExtractor();
        assertThrows(IllegalArgumentException.class, () -> extractor.extract(null));
    }
}
