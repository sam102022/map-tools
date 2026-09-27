package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.CliRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationCliTest {

    @Test
    @DisplayName("Exécution complète bout-en-bout avec l'échantillon réel de calque vert (limites.jpg)")
    void testEndToEndWithGreenOverlaySample(@TempDir Path tempDir) throws Exception {
        Path sampleDir = Paths.get("src", "main", "resources", "sample");
        File mapFile = sampleDir.resolve("a découper.jpg").toFile();
        File maskFile = sampleDir.resolve("limites.jpg").toFile();

        assertTrue(mapFile.exists(), "L'image carte réelle doit exister : " + mapFile.getAbsolutePath());
        assertTrue(maskFile.exists(), "Le calque vert réel doit exister : " + maskFile.getAbsolutePath());

        Path outClipped = tempDir.resolve("clipped_green.png");
        Path outMask = tempDir.resolve("mask_green.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.getAbsolutePath(),
                "--mask", maskFile.getAbsolutePath(),
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString(),
                "--snap-distance", "40",
                "--road-sensitivity", "1.0",
                "--smooth", "1"
        });

        assertEquals(0, exitCode, "Le traitement CLI doit se terminer avec un code de succès (0)");
        assertTrue(outClipped.toFile().exists(), "Le fichier détouré doit exister");
        assertTrue(outMask.toFile().exists(), "Le fichier de masque affiné doit exister");

        BufferedImage clippedImg = ImageIO.read(outClipped.toFile());
        BufferedImage maskImg = ImageIO.read(outMask.toFile());

        assertNotNull(clippedImg);
        assertNotNull(maskImg);
        assertEquals(clippedImg.getWidth(), maskImg.getWidth());
        assertEquals(clippedImg.getHeight(), maskImg.getHeight());

        boolean hasTransparent = false;
        boolean hasOpaque = false;
        for (int y = 0; y < clippedImg.getHeight(); y += 20) {
            for (int x = 0; x < clippedImg.getWidth(); x += 20) {
                int alpha = (clippedImg.getRGB(x, y) >> 24) & 0xFF;
                if (alpha == 0) hasTransparent = true;
                if (alpha == 255) hasOpaque = true;
            }
        }

        assertTrue(hasTransparent, "L'image détourée doit contenir des zones transparentes");
        assertTrue(hasOpaque, "L'image détourée doit contenir des zones opaques");

        // Contrôle de l'anti-aliasing sub-pixel de bout en bout sur l'image détourée
        int clippedSubpixelCount = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    clippedSubpixelCount++;
                }
            }
        }
        assertTrue(clippedSubpixelCount > 1000, "L'image détourée réelle doit comporter des milliers de pixels sub-pixel anti-aliasés, trouvé: " + clippedSubpixelCount);

        // Contrôle de la présence de nuances de gris sub-pixel dans le masque exporté
        int maskSubpixelCount = 0;
        for (int y = 0; y < maskImg.getHeight(); y++) {
            for (int x = 0; x < maskImg.getWidth(); x++) {
                int sample = maskImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    maskSubpixelCount++;
                }
            }
        }
        assertTrue(maskSubpixelCount > 1000, "Le masque exporté doit comporter des milliers de nuances de gris sub-pixel, trouvé: " + maskSubpixelCount);
    }

    @Test
    @DisplayName("Exécution complète bout-en-bout avec l'échantillon réel en niveaux de gris (mask01 - Copie.jpg)")
    void testEndToEndWithGrayscaleMaskSample(@TempDir Path tempDir) throws Exception {
        Path sampleDir = Paths.get("src", "main", "resources", "sample");
        File mapFile = sampleDir.resolve("a découper.jpg").toFile();
        File maskFile = sampleDir.resolve("mask01 - Copie.jpg").toFile();

        assertTrue(mapFile.exists(), "L'image carte réelle doit exister : " + mapFile.getAbsolutePath());
        assertTrue(maskFile.exists(), "Le masque en niveaux de gris doit exister : " + maskFile.getAbsolutePath());

        Path outClipped = tempDir.resolve("clipped_gray.png");
        Path outMask = tempDir.resolve("mask_gray.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.getAbsolutePath(),
                "--mask", maskFile.getAbsolutePath(),
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString(),
                "--snap-distance", "40",
                "--road-sensitivity", "1.0",
                "--smooth", "1"
        });

        assertEquals(0, exitCode, "Le traitement CLI avec masque niveaux de gris doit réussir (0)");
        assertTrue(outClipped.toFile().exists());
        assertTrue(outMask.toFile().exists());

        BufferedImage clippedImg = ImageIO.read(outClipped.toFile());
        assertNotNull(clippedImg);
        assertEquals(2227, clippedImg.getWidth());
        assertEquals(1559, clippedImg.getHeight());

        BufferedImage maskImg = ImageIO.read(outMask.toFile());
        assertNotNull(maskImg);

        int clippedSubpixelCount = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    clippedSubpixelCount++;
                }
            }
        }
        assertTrue(clippedSubpixelCount > 1000, "L'image détourée issue du masque gris doit comporter des pixels sub-pixel, trouvé: " + clippedSubpixelCount);

        int maskSubpixelCount = 0;
        for (int y = 0; y < maskImg.getHeight(); y++) {
            for (int x = 0; x < maskImg.getWidth(); x++) {
                int sample = maskImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    maskSubpixelCount++;
                }
            }
        }
        assertTrue(maskSubpixelCount > 1000, "Le masque gris exporté doit comporter des nuances sub-pixel, trouvé: " + maskSubpixelCount);
    }
}
