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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link DarkDemarcationDetector}.
 * <p>
 * Valide la détection des démarcations cartographiques sombres internes au territoire,
 * l'immunité contre les annotations colorées de sélection, le filtrage des bruits de petite taille
 * ainsi que le respect des contraintes dimensionnelles et contractuelles.
 * </p>
 */
@DisplayName("Tests unitaires pour DarkDemarcationDetector")
class DarkDemarcationDetectorTest {

    private DarkDemarcationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new DarkDemarcationDetector();
    }

    @Test
    @DisplayName("Détection d'une ligne sombre tracée dans le territoire")
    void testDetectDarkDemarcationLine() {
        int width = 100;
        int height = 100;

        BufferedImage carte = createFilledImage(width, height, new Color(240, 240, 240));
        BufferedImage limites = createFilledImage(width, height, new Color(180, 230, 180));
        BinaryMask territory = createSquareTerritory(width, height, 10, 10, 80, 80);

        // Ligne sombre de 40 pixels dans limites : x de 30 à 69, y = 50, couleur sombre (60, 60, 60)
        // Y_L = 60 <= 125, Y_C = 240, Y_C - Y_L = 180 >= 45
        Color darkColor = new Color(60, 60, 60);
        for (int x = 30; x < 70; x++) {
            limites.setRGB(x, 50, darkColor.getRGB());
        }

        BinaryMask result = detector.detect(carte, limites, territory);

        assertNotNull(result, "Le masque de démarcation ne doit pas être null.");
        assertEquals(width, result.getWidth());
        assertEquals(height, result.getHeight());

        for (int x = 30; x < 70; x++) {
            assertTrue(result.get(x, 50), "Le pixel (" + x + ", 50) doit être détecté comme démarcation sombre.");
        }
        assertEquals(40, result.countActivePixels(), "Exactement 40 pixels doivent être détectés.");
    }

    @Test
    @DisplayName("Immunité contre les tracés d'annotations colorées rouge et bleue")
    void testDoNotConfuseWithColorAnnotations() {
        int width = 100;
        int height = 100;

        BufferedImage carte = createFilledImage(width, height, new Color(240, 240, 240));
        BufferedImage limites = createFilledImage(width, height, new Color(180, 230, 180));
        BinaryMask territory = createSquareTerritory(width, height, 10, 10, 80, 80);

        // Tracé d'annotation rouge vif (220, 20, 20) de 40 pixels
        Color redAnnotation = new Color(220, 20, 20);
        for (int x = 30; x < 70; x++) {
            limites.setRGB(x, 30, redAnnotation.getRGB());
        }

        // Tracé d'annotation bleu vif (20, 30, 220) de 40 pixels
        Color blueAnnotation = new Color(20, 30, 220);
        for (int x = 30; x < 70; x++) {
            limites.setRGB(x, 70, blueAnnotation.getRGB());
        }

        // Tracé d'annotation magenta (220, 20, 220) de 40 pixels
        Color magentaAnnotation = new Color(220, 20, 220);
        for (int x = 30; x < 70; x++) {
            limites.setRGB(x, 40, magentaAnnotation.getRGB());
        }

        // Tracé d'annotation cyan (20, 220, 220) de 40 pixels
        Color cyanAnnotation = new Color(20, 220, 220);
        for (int x = 30; x < 70; x++) {
            limites.setRGB(x, 60, cyanAnnotation.getRGB());
        }

        BinaryMask result = detector.detect(carte, limites, territory);

        assertNotNull(result, "Le masque de résultat ne doit pas être null.");
        assertEquals(0, result.countActivePixels(),
                "Les tracés d'annotations rouge, bleue, magenta et cyan ne doivent pas être classés comme démarcations sombres.");
    }

    @Test
    @DisplayName("Filtrage des artefacts isolés de petite taille (< 15 pixels)")
    void testFilterSmallNoise() {
        int width = 100;
        int height = 100;

        BufferedImage carte = createFilledImage(width, height, new Color(240, 240, 240));
        BufferedImage limites = createFilledImage(width, height, new Color(180, 230, 180));
        BinaryMask territory = createSquareTerritory(width, height, 10, 10, 80, 80);

        Color darkColor = new Color(60, 60, 60);

        // Artefact isolé de 4 pixels (carré 2x2)
        limites.setRGB(50, 50, darkColor.getRGB());
        limites.setRGB(51, 50, darkColor.getRGB());
        limites.setRGB(50, 51, darkColor.getRGB());
        limites.setRGB(51, 51, darkColor.getRGB());

        // Ligne valide de 20 pixels (>= 15)
        for (int x = 15; x < 35; x++) {
            limites.setRGB(x, 20, darkColor.getRGB());
        }

        BinaryMask result = detector.detect(carte, limites, territory);

        assertFalse(result.get(50, 50), "L'artefact de 4 pixels doit être filtré.");
        assertFalse(result.get(51, 50), "L'artefact de 4 pixels doit être filtré.");
        assertFalse(result.get(50, 51), "L'artefact de 4 pixels doit être filtré.");
        assertFalse(result.get(51, 51), "L'artefact de 4 pixels doit être filtré.");

        for (int x = 15; x < 35; x++) {
            assertTrue(result.get(x, 20), "La ligne valide de 20 pixels doit être conservée.");
        }
        assertEquals(20, result.countActivePixels(), "Seule la composante de 20 pixels doit être retenue.");
    }

    @Test
    @DisplayName("Ignorer les démarcations situées en dehors du territoire")
    void testIgnorePixelsOutsideTerritory() {
        int width = 100;
        int height = 100;

        BufferedImage carte = createFilledImage(width, height, new Color(240, 240, 240));
        BufferedImage limites = createFilledImage(width, height, new Color(180, 230, 180));
        // Territoire confiné entre y = 30 et y = 80
        BinaryMask territory = createSquareTerritory(width, height, 20, 30, 60, 50);

        // Ligne sombre tracée à y = 10 (hors territoire)
        Color darkColor = new Color(60, 60, 60);
        for (int x = 20; x < 60; x++) {
            limites.setRGB(x, 10, darkColor.getRGB());
        }

        BinaryMask result = detector.detect(carte, limites, territory);

        assertEquals(0, result.countActivePixels(),
                "Une ligne sombre hors du masque de territoire ne doit pas être retenue.");
    }

    @Test
    @DisplayName("Validation des arguments null et des dimensions incompatibles")
    void testValidationNullAndDimensions() {
        BufferedImage validImage = createFilledImage(50, 50, Color.WHITE);
        BinaryMask validMask = new BinaryMask(50, 50);

        assertThrows(IllegalArgumentException.class,
                () -> detector.detect(null, validImage, validMask),
                "Une carte null doit lever IllegalArgumentException.");

        assertThrows(IllegalArgumentException.class,
                () -> detector.detect(validImage, null, validMask),
                "Une image de limites null doit lever IllegalArgumentException.");

        assertThrows(IllegalArgumentException.class,
                () -> detector.detect(validImage, validImage, null),
                "Un masque de territoire null doit lever IllegalArgumentException.");

        BufferedImage mismatchImage = createFilledImage(40, 50, Color.WHITE);
        assertThrows(IllegalArgumentException.class,
                () -> detector.detect(validImage, mismatchImage, validMask),
                "Des dimensions différentes entre carte et limites doivent lever IllegalArgumentException.");

        BinaryMask mismatchMask = new BinaryMask(50, 40);
        assertThrows(IllegalArgumentException.class,
                () -> detector.detect(validImage, validImage, mismatchMask),
                "Des dimensions différentes entre carte et territoire doivent lever IllegalArgumentException.");
    }

    /**
     * Crée une image unie de dimensions spécifiées avec une couleur d'arrière-plan donnée.
     *
     * @param width  Largeur en pixels.
     * @param height Hauteur en pixels.
     * @param color  Couleur de remplissage.
     * @return Nouvelle instance de {@link BufferedImage}.
     */
    private BufferedImage createFilledImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(color);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        return image;
    }

    /**
     * Crée un masque binaire contenant un rectangle de territoire actif.
     *
     * @param width  Largeur du masque.
     * @param height Hauteur du masque.
     * @param rx     Origine X du rectangle de territoire.
     * @param ry     Origine Y du rectangle de territoire.
     * @param rw     Largeur du rectangle.
     * @param rh     Hauteur du rectangle.
     * @return Nouveau masque binaire initialisé.
     */
    private BinaryMask createSquareTerritory(int width, int height, int rx, int ry, int rw, int rh) {
        BinaryMask territory = new BinaryMask(width, height);
        for (int y = ry; y < ry + rh && y < height; y++) {
            for (int x = rx; x < rx + rw && x < width; x++) {
                territory.set(x, y, true);
            }
        }
        return territory;
    }
}
