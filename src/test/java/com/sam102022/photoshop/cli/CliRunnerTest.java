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
    @DisplayName("Exécution complète d'un scénario CLI nominal")
    void testEndToEndCliRun(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out.png");
        Path maskOutFile = tempDir.resolve("mask_out.png");

        BufferedImage map = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        g.fillRect(5, 5, 20, 20);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--snap-distance", "15"
        });

        assertEquals(0, exitCode);
        assertTrue(outFile.toFile().exists());
        assertTrue(maskOutFile.toFile().exists());
    }
}
