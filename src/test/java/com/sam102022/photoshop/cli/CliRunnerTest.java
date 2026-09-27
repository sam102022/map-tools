package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliRunnerTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Affichage de l'aide avec --help ou -h")
    void testHelpOption() {
        int exitCode1 = CliRunner.run(new String[]{"--help"});
        assertEquals(0, exitCode1);
        assertTrue(outContent.toString().contains("Usage:"));
        assertTrue(outContent.toString().contains("--map"));

        outContent.reset();
        int exitCode2 = CliRunner.run(new String[]{"-h"});
        assertEquals(0, exitCode2);
        assertTrue(outContent.toString().contains("Usage:"));
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants ou null")
    void testMissingArguments() {
        assertEquals(1, CliRunner.run(new String[]{}));
        assertEquals(1, CliRunner.run(null));
        assertEquals(1, CliRunner.run(new String[]{"--map", "only_map.png"}));
    }

    @Test
    @DisplayName("Échec si une option attendue n'a pas de valeur")
    void testMissingOptionValue() {
        int exitCode = CliRunner.run(new String[]{"--map", "map.png", "--mask"});
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Valeur manquante"));
    }

    @Test
    @DisplayName("Échec si un paramètre numérique est invalide")
    void testInvalidNumericArgument() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--snap-distance", "invalid_int"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Valeur entière invalide"));
    }

    @Test
    @DisplayName("Échec si un paramètre de configuration est hors bornes")
    void testInvalidConfigBounds() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--snap-distance", "-10"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Erreur de syntaxe ou de configuration"));
    }

    @Test
    @DisplayName("Exécution complète d'un scénario CLI avec anti-aliasing par défaut et contrôle des nuances de gris dans mask-out")
    void testEndToEndCliRun(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out.png");
        Path maskOutFile = tempDir.resolve("mask_out.png");

        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        // Triangle oblique pour générer des bords sub-pixel anti-aliasés
        Polygon poly = new Polygon(new int[]{5, 35, 15}, new int[]{5, 10, 35}, 3);
        g.fillPolygon(poly);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--snap-distance", "15",
                "--aa"
        });

        assertEquals(0, exitCode);
        assertTrue(outFile.toFile().exists());
        assertTrue(maskOutFile.toFile().exists());

        // 1. Vérifier que mask-out contient des pixels gris intermédiaires ]0, 255[
        BufferedImage maskOutImg = ImageIO.read(maskOutFile.toFile());
        assertNotNull(maskOutImg);
        int grayIntermediates = 0;
        for (int y = 0; y < maskOutImg.getHeight(); y++) {
            for (int x = 0; x < maskOutImg.getWidth(); x++) {
                int sample = maskOutImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    grayIntermediates++;
                }
            }
        }
        assertTrue(grayIntermediates > 5, "mask-out doit contenir des pixels gris sub-pixel intermédiaires ]0, 255[, trouvé: " + grayIntermediates);

        // 2. Vérifier que l'image détourée contient des valeurs alpha progressives
        BufferedImage clippedImg = ImageIO.read(outFile.toFile());
        assertNotNull(clippedImg);
        int alphaIntermediates = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    alphaIntermediates++;
                }
            }
        }
        assertTrue(alphaIntermediates > 5, "L'image détourée doit contenir un dégradé alpha progressif, trouvé: " + alphaIntermediates);
    }

    @Test
    @DisplayName("Option --no-aa / --no-antialias désactive l'anti-aliasing (sorties strictement binaires)")
    void testCliWithNoAntialiasingOption(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out_no_aa.png");
        Path maskOutFile = tempDir.resolve("mask_no_aa.png");

        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        Polygon poly = new Polygon(new int[]{5, 35, 15}, new int[]{5, 10, 35}, 3);
        g.fillPolygon(poly);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--smooth", "0",
                "--no-aa"
        });

        assertEquals(0, exitCode);

        // mask-out doit être strictement binaire (0 ou 255)
        BufferedImage maskOutImg = ImageIO.read(maskOutFile.toFile());
        for (int y = 0; y < maskOutImg.getHeight(); y++) {
            for (int x = 0; x < maskOutImg.getWidth(); x++) {
                int sample = maskOutImg.getRaster().getSample(x, y, 0);
                assertTrue(sample == 0 || sample == 255, "Sans AA, mask-out doit être strictement 0 ou 255, trouvé: " + sample);
            }
        }

        // image détourée doit avoir un canal alpha strictement binaire
        BufferedImage clippedImg = ImageIO.read(outFile.toFile());
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                assertTrue(alpha == 0 || alpha == 255, "Sans AA, l'alpha doit être strictement 0 ou 255, trouvé: " + alpha);
            }
        }
    }

    @Test
    @DisplayName("Échec si conflit entre --aa et --no-aa")
    void testCliConflictingAntialiasingOptions() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--aa",
                "--no-aa"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Conflit"));
    }
}
