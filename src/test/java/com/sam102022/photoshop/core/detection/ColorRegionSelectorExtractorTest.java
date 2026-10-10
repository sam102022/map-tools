package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SelectorColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link ColorRegionSelectorExtractor}.
 */
@DisplayName("Tests unitaires pour ColorRegionSelectorExtractor")
class ColorRegionSelectorExtractorTest {

    private ColorRegionSelectorExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ColorRegionSelectorExtractor();
    }

    @Test
    @DisplayName("Détection et extraction de l'intérieur d'un rectangle bleu fermé")
    void testDetectClosedBlueRectangle() {
        BufferedImage image = createBlankImage(100, 100);
        drawRectangle(image, Color.BLUE, 20, 20, 60, 60, 2);

        BinaryMask interior = extractor.extractInterior(image, SelectorColor.AUTO);

        assertNotNull(interior, "Le masque intérieur ne doit pas être null.");
        assertEquals(100, interior.getWidth());
        assertEquals(100, interior.getHeight());

        // Le centre doit faire partie de l'intérieur
        assertTrue(interior.get(50, 50), "Le centre du rectangle doit être dans l'intérieur.");
        assertTrue(interior.get(30, 30), "Un point proche du coin intérieur doit être dans l'intérieur.");
        assertTrue(interior.get(70, 70), "Un point proche de l'autre coin intérieur doit être dans l'intérieur.");

        // Les coins extérieurs ne doivent pas faire partie de l'intérieur
        assertFalse(interior.get(5, 5), "L'extérieur haut-gauche ne doit pas être dans l'intérieur.");
        assertFalse(interior.get(95, 95), "L'extérieur bas-droit ne doit pas être dans l'intérieur.");
        assertFalse(interior.get(5, 95), "L'extérieur bas-gauche ne doit pas être dans l'intérieur.");
        assertFalse(interior.get(95, 5), "L'extérieur haut-droit ne doit pas être dans l'intérieur.");

        // Le tracé bleu lui-même ne fait pas partie de l'intérieur extrait
        assertFalse(interior.get(20, 20), "Le contour bleu ne doit pas faire partie de l'intérieur.");
    }

    @Test
    @DisplayName("Détection de plusieurs couleurs et désambiguïsation explicite")
    void testDetectMultipleColorsAndDisambiguation() {
        BufferedImage image = createBlankImage(120, 100);
        // Cadre rouge à gauche (x de 10 à 50)
        drawRectangle(image, Color.RED, 10, 10, 40, 80, 2);
        // Cadre bleu à droite (x de 70 à 110)
        drawRectangle(image, Color.BLUE, 70, 10, 40, 80, 2);

        List<SelectorColor> presentColors = extractor.detectPresentColors(image);
        assertEquals(2, presentColors.size(), "Deux couleurs doivent être détectées.");
        assertTrue(presentColors.contains(SelectorColor.RED));
        assertTrue(presentColors.contains(SelectorColor.BLUE));

        // En mode AUTO : ambiguïté détectée -> IllegalStateException
        IllegalStateException exceptionAuto = assertThrows(IllegalStateException.class, () ->
                extractor.resolveTargetColor(image, SelectorColor.AUTO));
        assertTrue(exceptionAuto.getMessage().contains("RED") || exceptionAuto.getMessage().contains("ROUGE"));
        assertTrue(exceptionAuto.getMessage().contains("BLUE") || exceptionAuto.getMessage().contains("BLEU"));

        assertThrows(IllegalStateException.class, () -> extractor.extractInterior(image, SelectorColor.AUTO));

        // Sélection explicite RED : intérieur rouge extrait uniquement
        BinaryMask redInterior = extractor.extractInterior(image, SelectorColor.RED);
        assertTrue(redInterior.get(30, 50), "L'intérieur du cadre rouge doit être extrait.");
        assertFalse(redInterior.get(90, 50), "L'intérieur du cadre bleu ne doit PAS être dans le masque rouge.");

        // Sélection explicite BLUE : intérieur bleu extrait uniquement
        BinaryMask blueInterior = extractor.extractInterior(image, SelectorColor.BLUE);
        assertTrue(blueInterior.get(90, 50), "L'intérieur du cadre bleu doit être extrait.");
        assertFalse(blueInterior.get(30, 50), "L'intérieur du cadre rouge ne doit PAS être dans le masque bleu.");
    }

    @Test
    @DisplayName("Réparation réussie d'une brèche étroite (3-4 pixels)")
    void testRepairSmallGap() {
        BufferedImage image = createBlankImage(100, 100);
        drawRectangle(image, Color.BLUE, 20, 20, 60, 60, 2);

        // Création d'une brèche de 4 pixels sur le bord supérieur (x=48..51, y=19..22)
        clearGap(image, 48, 19, 4, 4);

        BinaryMask interior = extractor.extractInterior(image, SelectorColor.BLUE);

        assertNotNull(interior);
        assertTrue(interior.get(50, 50), "L'intérieur doit être étanche après fermeture de la petite brèche.");
        assertFalse(interior.get(5, 5), "L'extérieur ne doit pas être contaminé.");
    }

    @Test
    @DisplayName("Échec avec IllegalStateException en présence d'une brèche large (> 10 pixels)")
    void testFailOnLargeGap() {
        BufferedImage image = createBlankImage(100, 100);
        drawRectangle(image, Color.BLUE, 20, 20, 60, 60, 2);

        // Création d'une brèche large de 20 pixels sur le bord supérieur (x=40..59, y=18..23)
        clearGap(image, 40, 18, 20, 6);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                extractor.extractInterior(image, SelectorColor.BLUE));

        assertTrue(exception.getMessage().contains("brèche non colmatable"),
                "Le message d'erreur doit indiquer une brèche non colmatable.");
    }

    @Test
    @DisplayName("Rejet des fonds cartographiques neutres sans tracé d'annotation")
    void testIgnoreMapBackgroundWithoutAnnotation() {
        BufferedImage image = createBlankImage(100, 100);
        // Dessine des éléments cartographiques non-annotations (fond beige, route orange, vert territoire)
        fillRegion(image, new Color(245, 245, 220), 0, 0, 100, 100); // beige neutre
        drawRectangle(image, new Color(100, 200, 100), 10, 10, 80, 80, 5); // vert territoire
        drawRectangle(image, new Color(250, 180, 50), 20, 20, 60, 60, 3); // route orange

        List<SelectorColor> present = extractor.detectPresentColors(image);
        assertTrue(present.isEmpty(), "Aucune couleur de sélecteur ne doit être détectée sur fond cartographique.");

        assertNull(extractor.resolveTargetColor(image, SelectorColor.AUTO),
                "resolveTargetColor doit renvoyer null si aucune annotation n'est présente.");

        assertThrows(IllegalArgumentException.class, () ->
                extractor.extractInterior(image, SelectorColor.AUTO));
    }

    @Test
    @DisplayName("Exception levée si une couleur explicite demandée est absente")
    void testRequestedColorAbsentThrowsException() {
        BufferedImage image = createBlankImage(100, 100);
        drawRectangle(image, Color.BLUE, 20, 20, 60, 60, 2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                extractor.resolveTargetColor(image, SelectorColor.RED));

        assertTrue(ex.getMessage().contains("RED") || ex.getMessage().contains("rouge") || ex.getMessage().contains("ROUGE"));
    }

    @Test
    @DisplayName("Extraction du masque binaire brut d'une couleur")
    void testExtractRawSelectorMask() {
        BufferedImage image = createBlankImage(50, 50);
        drawRectangle(image, Color.MAGENTA, 10, 10, 20, 20, 2);

        BinaryMask rawMask = extractor.extractRawSelectorMask(image, SelectorColor.MAGENTA);
        assertNotNull(rawMask);
        assertTrue(rawMask.get(10, 10), "Le pixel tracé doit être actif.");
        assertFalse(rawMask.get(0, 0), "Un pixel de fond doit être inactif.");
        assertFalse(rawMask.get(20, 20), "L'intérieur non tracé doit être inactif dans le masque brut.");
    }

    @Test
    @DisplayName("Validation des paramètres null")
    void testNullArgumentsValidation() {
        BufferedImage image = createBlankImage(50, 50);

        assertThrows(IllegalArgumentException.class, () -> extractor.detectPresentColors(null));
        assertThrows(IllegalArgumentException.class, () -> extractor.resolveTargetColor(null, SelectorColor.AUTO));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractRawSelectorMask(null, SelectorColor.RED));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractRawSelectorMask(image, null));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractInterior(null, SelectorColor.AUTO));
    }

    private BufferedImage createBlankImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private void drawRectangle(BufferedImage image, Color color, int x, int y, int width, int height, int strokeWidth) {
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.setStroke(new BasicStroke(strokeWidth));
        g.drawRect(x, y, width, height);
        g.dispose();
    }

    private void clearGap(BufferedImage image, int x, int y, int width, int height) {
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(x, y, width, height);
        g.dispose();
    }

    private void fillRegion(BufferedImage image, Color color, int x, int y, int width, int height) {
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(x, y, width, height);
        g.dispose();
    }
}
