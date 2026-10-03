package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires pour le composant {@link RoadDetectorStyle}.
 */
@DisplayName("Tests unitaires du détecteur colorimétrique RoadDetectorStyle")
class RoadDetectorStyleTest {

    /**
     * Vérifie la règle colorimétrique sur un pixel type de route bleu-gris.
     */
    @Test
    @DisplayName("Validation de la règle colorimétrique sur un pixel de route bleu-gris")
    void testDetectRoadColor() {
        BufferedImage img = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        // Pixel de route type : R=180, G=195, B=205 => (B-R)=25 >= 12, (B-G)=10 in [2, 22], R=180 < 228
        img.setRGB(1, 1, new Color(180, 195, 205).getRGB());

        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask mask = detector.detect(img);

        assertNotNull(mask);
        assertEquals(3, mask.getWidth());
        assertEquals(3, mask.getHeight());
        assertTrue(mask.get(1, 1), "Le pixel bleu-gris doit être détecté comme route");
        assertFalse(mask.get(0, 0), "Le fond noir ne doit pas être détecté comme route");
    }

    /**
     * Vérifie le rejet des teintes non routières : vert, blanc, eau saturée, rouge excessif.
     */
    @Test
    @DisplayName("Rejet des teintes non routières : vert, blanc, eau saturée, rouge excessif")
    void testRejectNonRoadColors() {
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        // 0: Vert (parc) R=180, G=220, B=180 => B-G = -40 (hors limites)
        img.setRGB(0, 0, new Color(180, 220, 180).getRGB());
        // 1: Blanc pur R=255, G=255, B=255 => R >= 228
        img.setRGB(1, 0, new Color(255, 255, 255).getRGB());
        // 2: Eau cyan R=120, G=180, B=230 => B-G = 50 > 22
        img.setRGB(2, 0, new Color(120, 180, 230).getRGB());
        // 3: Trop rouge R=230, G=235, B=245 => R=230 >= 228
        img.setRGB(3, 0, new Color(230, 235, 245).getRGB());

        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask mask = detector.detect(img);

        assertFalse(mask.get(0, 0), "Vert rejeté");
        assertFalse(mask.get(1, 0), "Blanc rejeté");
        assertFalse(mask.get(2, 0), "Eau cyan rejetée");
        assertFalse(mask.get(3, 0), "Rouge excessif rejeté");
    }

    /**
     * Vérifie le rejet d'une image null avec IllegalArgumentException.
     */
    @Test
    @DisplayName("Rejet d'une image null")
    void testNullImage() {
        RoadDetectorStyle detector = new RoadDetectorStyle();
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }
}
