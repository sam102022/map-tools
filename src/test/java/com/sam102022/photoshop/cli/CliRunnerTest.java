package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliRunnerTest {

    @Test
    @DisplayName("Affichage de l'aide avec --help ou -h")
    void testHelpOption() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = CliRunner.run(new String[]{"--help"});
            assertEquals(0, exitCode);
            assertTrue(outContent.toString().contains("Usage:"));
            assertTrue(outContent.toString().contains("--map"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants")
    void testMissingArguments() {
        int exitCode = CliRunner.run(new String[]{});
        assertEquals(1, exitCode);
    }

    @Test
    @DisplayName("Exécution complète d'un scénario CLI nominal")
    void testEndToEndCliRun(@TempDir Path tempDir) throws Exception {
        // Créer de fausses images valides
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
