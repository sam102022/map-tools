package com.sam102022.photoshop.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires des entrées-sorties d'images ({@link ImageLoader}, {@link ImageExporter}, {@link ImagePixels}).
 */
class ImageIoTest {

    /**
     * Cycle export PNG puis chargement, et erreurs de chargement.
     *
     * @param tempDir Dossier temporaire.
     * @throws IOException si l'écriture échoue.
     */
    @Test
    @DisplayName("Chargement d'image avec ImageLoader.load et gestion des erreurs")
    void testImageLoaderCycleAndErrors(@TempDir Path tempDir) throws IOException {
        Path imagePath = tempDir.resolve("test_image.png");
        BufferedImage original = new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB);
        original.setRGB(10, 15, 0x123456);

        ImageExporter.savePng(original, imagePath);

        BufferedImage loaded = ImageLoader.load(imagePath);
        assertNotNull(loaded);
        assertEquals(20, loaded.getWidth());
        assertEquals(30, loaded.getHeight());
        assertEquals(0x123456, loaded.getRGB(10, 15) & 0x00FFFFFF);

        assertThrows(IOException.class, () -> ImageLoader.load(tempDir.resolve("ghost.png")));
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.load(null));
    }

    /**
     * Rejet des arguments null à l'export.
     */
    @Test
    @DisplayName("Validation des arguments null pour ImageExporter")
    void testImageExporterValidation() {
        BufferedImage img = new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.savePng(null, Path.of("test.png")));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.savePng(img, null));
    }

    /**
     * La lecture directe des pixels donne exactement le résultat de getRGB, pour les types d'images usuels, sur
     * l'image entière comme sur un rectangle.
     */
    @Test
    @DisplayName("ImagePixels.argb identique à getRGB (BGR, ABGR, RGB, ARGB, gris)")
    void testImagePixelsMatchesGetRgb() {
        int[] types = {BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_4BYTE_ABGR, BufferedImage.TYPE_INT_RGB,
                BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_BYTE_GRAY};
        Random random = new Random(7);
        for (int type : types) {
            BufferedImage image = new BufferedImage(37, 23, type);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setRGB(x, y, random.nextInt());
                }
            }
            assertArrayEquals(image.getRGB(0, 0, 37, 23, null, 0, 37), ImagePixels.argb(image),
                    "Image entière, type " + type);
            assertArrayEquals(image.getRGB(5, 3, 20, 11, null, 0, 20), ImagePixels.argb(image, 5, 3, 20, 11),
                    "Rectangle, type " + type);
        }
    }
}
