package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Export des images détourées en PNG transparent (ARGB) avec lissage progressif et export des masques.
 */
public final class ImageExporter {

    private ImageExporter() {
    }

    public static BufferedImage createClippedImage(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
        if (mapImage == null || mask == null) {
            throw new IllegalArgumentException("mapImage et mask ne peuvent pas être null.");
        }
        if (mapImage.getWidth() != mask.getWidth() || mapImage.getHeight() != mask.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre mapImage et mask.");
        }

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        if (smoothRadius <= 0) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (mask.get(x, y)) {
                        int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                        clipped.setRGB(x, y, 0xFF000000 | rgb);
                    } else {
                        clipped.setRGB(x, y, 0x00000000);
                    }
                }
            }
            return clipped;
        }

        // Lissage de contour : détection des bords intérieurs par érosion
        BinaryMask coreMask = MorphologyOps.erode(mask, smoothRadius);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) {
                    clipped.setRGB(x, y, 0x00000000);
                } else if (coreMask.get(x, y)) {
                    int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                    clipped.setRGB(x, y, 0xFF000000 | rgb);
                } else {
                    // Pixel situé sur la bordure : calcul d'un alpha progressif
                    int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                    int alpha = 160; // Atténuation anti-escalier sur le pourtour
                    clipped.setRGB(x, y, (alpha << 24) | rgb);
                }
            }
        }

        return clipped;
    }

    public static BufferedImage createMaskImage(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("mask ne peut pas être null.");
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, mask.get(x, y) ? 0xFFFFFF : 0x000000);
            }
        }
        return image;
    }

    public static void savePng(BufferedImage image, Path outputPath) throws IOException {
        if (image == null || outputPath == null) {
            throw new IllegalArgumentException("image et outputPath ne peuvent pas être null.");
        }
        File file = outputPath.toFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        boolean written = ImageIO.write(image, "PNG", file);
        if (!written) {
            throw new IOException("Impossible d'écrire l'image au format PNG vers : " + outputPath);
        }
    }
}
