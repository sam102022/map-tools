package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de l'extraction des zones tracées {@link ZoneExtractor}.
 */
@DisplayName("Tests unitaires de ZoneExtractor")
class ZoneExtractorTest {

    /**
     * Crée un plan blanc de 400 x 300 px.
     *
     * @return Image et contexte graphique.
     */
    private static BufferedImage blankPlan() {
        BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        return img;
    }

    /**
     * Trace un rectangle de 3 px d'épaisseur dans la couleur d'une zone.
     *
     * @param img  Plan.
     * @param zone Zone.
     * @param x    Abscisse du coin.
     * @param y    Ordonnée du coin.
     * @param w    Largeur.
     * @param h    Hauteur.
     */
    private static void outline(BufferedImage img, ZoneColor zone, int x, int y, int w, int h) {
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(zone.rgb()));
        g.setStroke(new BasicStroke(3f));
        g.drawRect(x, y, w, h);
        g.dispose();
    }

    /**
     * Un trait interrompu par un pictogramme blanc (8 px) est refermé ; la zone couvre l'intérieur jusqu'à
     * l'axe du trait ; un pictogramme isolé de même couleur est ignoré.
     */
    @Test
    @DisplayName("Trait interrompu refermé et pictogrammes ignorés")
    void testGapClosedAndIconsIgnored() {
        BufferedImage img = blankPlan();
        outline(img, ZoneColor.ROUGE, 50, 50, 300, 200);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(196, 40, 8, 20);
        g.setColor(new Color(ZoneColor.ROUGE.rgb()));
        g.fillOval(10, 10, 20, 20);
        g.dispose();

        List<BinaryMask> parts = new ZoneExtractor().extract(img, ZoneColor.ROUGE);

        assertEquals(1, parts.size());
        BinaryMask part = parts.get(0);
        assertTrue(part.get(200, 150));
        assertTrue(part.get(52, 150), "la zone doit atteindre l'axe du trait");
        assertFalse(part.get(46, 150), "la zone ne doit pas déborder du trait");
        assertFalse(part.get(20, 20), "le pictogramme isolé n'est pas une zone");
        assertEquals(300.0 * 200.0, part.countActivePixels(), 300.0 * 200.0 * 0.03);
    }

    /**
     * Deux régions délimitées par le même trait forment deux parties ; une couleur absente donne une liste vide.
     */
    @Test
    @DisplayName("Zone en deux parties et couleur absente")
    void testTwoPartsAndAbsentColour() {
        BufferedImage img = blankPlan();
        outline(img, ZoneColor.MAUVE, 30, 30, 160, 200);
        outline(img, ZoneColor.MAUVE, 190, 30, 170, 200);

        List<BinaryMask> parts = new ZoneExtractor().extract(img, ZoneColor.MAUVE);

        assertEquals(2, parts.size());
        assertTrue(parts.get(0).get(270, 130));
        assertTrue(parts.get(1).get(110, 130));
        assertTrue(new ZoneExtractor().extract(img, ZoneColor.CYAN).isEmpty());
    }
}
