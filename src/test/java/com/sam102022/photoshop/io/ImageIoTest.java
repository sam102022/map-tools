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
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.validateDimensions(null, img2));
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.validateDimensions(img1, null));
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
    @DisplayName("Lissage des bords (smoothRadius > 0) avec atténuation alpha")
    void testCreateClippedImageWithSmoothing() {
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BinaryMask mask = new BinaryMask(10, 10);

        // Carré 6x6 au centre (de 2 à 7)
        for (int y = 2; y <= 7; y++) {
            for (int x = 2; x <= 7; x++) {
                mask.set(x, y, true);
            }
        }

        BufferedImage clipped = ImageExporter.createClippedImage(map, mask, 1);

        // Pixel central (4, 4) situé au cœur -> alpha 255
        int coreAlpha = (clipped.getRGB(4, 4) >> 24) & 0xFF;
        assertEquals(255, coreAlpha);

        // Pixel de bordure (2, 2) situé sur le pourtour adouci -> alpha progressif atténué
        int borderAlpha = (clipped.getRGB(2, 2) >> 24) & 0xFF;
        assertTrue(borderAlpha > 0 && borderAlpha < 255, "L'alpha de bordure doit être progressif");

        // Pixel extérieur (0, 0) -> alpha 0
        int outsideAlpha = (clipped.getRGB(0, 0) >> 24) & 0xFF;
        assertEquals(0, outsideAlpha);
    }

    @Test
    @DisplayName("Chargement d'image avec ImageLoader.load et gestion des erreurs")
    void testImageLoaderCycleAndErrors(@TempDir Path tempDir) throws IOException {
        Path imagePath = tempDir.resolve("test_image.png");
        BufferedImage original = new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB);
        original.setRGB(10, 15, 0x123456);

        ImageExporter.savePng(original, imagePath);

        // Chargement réussi
        BufferedImage loaded = ImageLoader.load(imagePath);
        assertNotNull(loaded);
        assertEquals(20, loaded.getWidth());
        assertEquals(30, loaded.getHeight());
        assertEquals(0x123456, loaded.getRGB(10, 15) & 0x00FFFFFF);

        // Fichier inexistant
        Path nonexistent = tempDir.resolve("ghost.png");
        assertThrows(IOException.class, () -> ImageLoader.load(nonexistent));

        // Paramètre null
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.load(null));
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

    @Test
    @DisplayName("Validation précise de la formule alpha et préservation RGB avec un CoverageMask direct")
    void testCreateClippedImageWithCoverageMask() {
        BufferedImage map = new BufferedImage(4, 1, BufferedImage.TYPE_INT_ARGB);
        // Pixel 0 : Opaque rouge (0xFFFF0000)
        map.setRGB(0, 0, 0xFFFF0000);
        // Pixel 1 : Opaque vert (0xFF00FF00)
        map.setRGB(1, 0, 0xFF00FF00);
        // Pixel 2 : Opaque bleu (0xFF0000FF)
        map.setRGB(2, 0, 0xFF0000FF);
        // Pixel 3 : Semi-transparent (alpha = 128, RGB = 0x55AA33)
        map.setRGB(3, 0, (128 << 24) | 0x0055AA33);

        com.sam102022.photoshop.core.model.CoverageMask coverage = new com.sam102022.photoshop.core.model.CoverageMask(4, 1);
        coverage.set(0, 0, 255); // Pleine couverture
        coverage.set(1, 0, 128); // Demi-couverture sub-pixel
        coverage.set(2, 0, 0);   // Nulle
        coverage.set(3, 0, 128); // Demi-couverture sur pixel source semi-transparent

        BufferedImage clipped = ImageExporter.createClippedImage(map, coverage, 0);

        // Pixel 0 : source 255, cov 255 -> alpha 255, RGB 0xFF0000
        int p0 = clipped.getRGB(0, 0);
        assertEquals(255, (p0 >>> 24) & 0xFF, "Source 255 * Cov 255 => Alpha 255");
        assertEquals(0xFF0000, p0 & 0x00FFFFFF, "Le rouge RGB doit être intact");

        // Pixel 1 : source 255, cov 128 -> alpha 128, RGB 0x00FF00
        int p1 = clipped.getRGB(1, 0);
        assertEquals(128, (p1 >>> 24) & 0xFF, "Source 255 * Cov 128 => Alpha 128");
        assertEquals(0x00FF00, p1 & 0x00FFFFFF, "Le vert RGB doit être intact");

        // Pixel 2 : source 255, cov 0 -> pixel transparent 0x00000000
        int p2 = clipped.getRGB(2, 0);
        assertEquals(0, p2, "Cov 0 => Pixel totalement transparent");

        // Pixel 3 : source 128, cov 128 -> finalAlpha = (128 * 128 + 127) / 255 = 64
        int p3 = clipped.getRGB(3, 0);
        int expectedAlpha3 = (128 * 128 + 127) / 255;
        assertEquals(expectedAlpha3, (p3 >>> 24) & 0xFF, "Source 128 * Cov 128 => Alpha 64");
        assertEquals(0x55AA33, p3 & 0x00FFFFFF, "Le RGB source doit être conservé");
    }

    @Test
    @DisplayName("Génération et export du PNG de masque en niveaux de gris (CoverageMask)")
    void testCreateCoverageMaskImageAndExport(@TempDir Path tempDir) throws IOException {
        com.sam102022.photoshop.core.model.CoverageMask mask = new com.sam102022.photoshop.core.model.CoverageMask(3, 1);
        mask.set(0, 0, 0);
        mask.set(1, 0, 128);
        mask.set(2, 0, 255);

        BufferedImage grayImg = ImageExporter.createCoverageMaskImage(mask);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, grayImg.getType(), "L'image de couverture doit être en niveaux de gris BYTE_GRAY");
        assertEquals(0, grayImg.getRaster().getSample(0, 0, 0));
        assertEquals(128, grayImg.getRaster().getSample(1, 0, 0), "Doit conserver les nuances sub-pixel de gris 128");
        assertEquals(255, grayImg.getRaster().getSample(2, 0, 0));

        Path maskFile = tempDir.resolve("coverage_mask.png");
        ImageExporter.savePng(grayImg, maskFile);
        assertTrue(maskFile.toFile().exists());

        BufferedImage reloaded = ImageLoader.load(maskFile);
        assertNotNull(reloaded);
        // Après rechargement PNG, vérifier que la nuance intermédiaire est bien conservée
        int loadedSample = reloaded.getRaster().getSample(1, 0, 0);
        assertEquals(128, loadedSample, "La nuance de gris 128 doit être fidèlement persistée dans le PNG");
    }

    @Test
    @DisplayName("Validation des arguments null pour ImageExporter")
    void testImageExporterValidation() {
        BinaryMask mask = new BinaryMask(10, 10);
        com.sam102022.photoshop.core.model.CoverageMask covMask = new com.sam102022.photoshop.core.model.CoverageMask(10, 10);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BufferedImage wrongSize = new BufferedImage(15, 10, BufferedImage.TYPE_INT_RGB);

        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(null, mask, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(img, (BinaryMask) null, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(null, covMask, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(img, (com.sam102022.photoshop.core.model.CoverageMask) null, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(wrongSize, mask, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createClippedImage(wrongSize, covMask, 0));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createMaskImage(null));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.createCoverageMaskImage(null));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.savePng(null, Path.of("test.png")));
        assertThrows(IllegalArgumentException.class, () -> ImageExporter.savePng(img, null));
    }
}
