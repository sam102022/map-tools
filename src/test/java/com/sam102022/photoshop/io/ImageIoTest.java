package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageIoTest {

    @Test
    @DisplayName("Vérification de concordance des dimensions des images")
    void testValidateDimensions() {
        BufferedImage img1 = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage img3 = new BufferedImage(150, 200, BufferedImage.TYPE_INT_RGB);

        assertDoesNotThrow(() -> ImageLoader.validateDimensions(img1, img2));
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.validateDimensions(img1, img3));
    }

    @Test
    @DisplayName("Génération de l'image détourée avec canal alpha transparent et export PNG")
    void testCreateClippedImageAndExport(@TempDir Path tempDir) throws IOException {
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        BinaryMask mask = new BinaryMask(10, 10);
        mask.set(5, 5, true);

        BufferedImage clipped = ImageExporter.createClippedImage(map, mask, 0);
        assertEquals(BufferedImage.TYPE_INT_ARGB, clipped.getType());

        // Le pixel actif doit avoir un alpha plein (255)
        int insideAlpha = (clipped.getRGB(5, 5) >> 24) & 0xFF;
        assertEquals(255, insideAlpha);

        // Le pixel inactif doit être complètement transparent (alpha = 0)
        int outsideAlpha = (clipped.getRGB(0, 0) >> 24) & 0xFF;
        assertEquals(0, outsideAlpha);

        Path outFile = tempDir.resolve("clipped.png");
        ImageExporter.savePng(clipped, outFile);
        assertTrue(outFile.toFile().exists());
        assertTrue(outFile.toFile().length() > 0);
    }

    @Test
    @DisplayName("Génération du PNG de visualisation du masque binaire")
    void testCreateMaskImageAndExport(@TempDir Path tempDir) throws IOException {
        BinaryMask mask = new BinaryMask(10, 10);
        mask.set(2, 2, true);

        BufferedImage maskImage = ImageExporter.createMaskImage(mask);
        int whitePixel = maskImage.getRGB(2, 2) & 0x00FFFFFF;
        int blackPixel = maskImage.getRGB(0, 0) & 0x00FFFFFF;

        assertEquals(0xFFFFFF, whitePixel, "Pixel actif blanc");
        assertEquals(0x000000, blackPixel, "Pixel inactif noir");

        Path maskFile = tempDir.resolve("mask.png");
        ImageExporter.savePng(maskImage, maskFile);
        assertTrue(maskFile.toFile().exists());
    }
}
