package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Export des images détourées en PNG transparent (ARGB) avec lissage progressif et export des masques.
 */
public final class ImageExporter {

    private ImageExporter() {
    }

    public static BufferedImage createClippedImage(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
        if (mask == null) throw new IllegalArgumentException("mask ne peut pas être null.");
        return createClippedImage(mapImage, CoverageMask.fromBinaryMask(mask), smoothRadius);
    }

    public static BufferedImage createClippedImage(BufferedImage mapImage, CoverageMask coverageMask, int smoothRadius) {
        if (mapImage == null || coverageMask == null) {
            throw new IllegalArgumentException("mapImage et coverageMask ne peuvent pas être null.");
        }
        if (mapImage.getWidth() != coverageMask.getWidth() || mapImage.getHeight() != coverageMask.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre mapImage et coverageMask.");
        }

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        int featherRadius = smoothRadius > 0 ? Math.min(smoothRadius, Math.max(w, h)) : 0;
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] distance = featherRadius > 0
                ? distanceFromCoverageBoundary(coverageMask.toBinaryMask(128))
                : null;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int coverage = coverageMask.get(x, y);
                if (coverage == 0) continue;
                if (distance != null) {
                    int d = distance[y * w + x];
                    if (d == Integer.MAX_VALUE) d = 0; // couverture partielle sous le seuil binaire
                    long denominator = featherRadius + 1L;
                    coverage = (int) ((coverage * (Math.min(d, featherRadius) + 1L) + denominator / 2) / denominator);
                }
                int sourcePixel = mapImage.getRGB(x, y);
                int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
                int finalAlpha = (sourceAlpha * coverage + 127) / 255;
                if (finalAlpha != 0) {
                    clipped.setRGB(x, y, (finalAlpha << 24) | (sourcePixel & 0x00FFFFFF));
                }
            }
        }

        return clipped;
    }

    /** Approxime la distance intérieure au bord avec deux passes chamfer 8-connexes. */
    private static int[] distanceFromCoverageBoundary(BinaryMask mask) {
        int w = mask.getWidth(), h = mask.getHeight(), size = w * h;
        int[] distance = new int[size];
        Arrays.fill(distance, Integer.MAX_VALUE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) continue;
                boolean boundary = false;
                for (int dy = -1; dy <= 1 && !boundary; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && !mask.get(x + dx, y + dy)) {
                            boundary = true;
                            break;
                        }
                    }
                }
                if (boundary) distance[y * w + x] = 0;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (!mask.get(x, y)) continue;
                int best = distance[i];
                best = Math.min(best, neighborDistance(distance, w, h, x - 1, y, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x, y - 1, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x - 1, y - 1, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x + 1, y - 1, 1));
                distance[i] = best;
            }
        }
        for (int y = h - 1; y >= 0; y--) {
            for (int x = w - 1; x >= 0; x--) {
                int i = y * w + x;
                if (!mask.get(x, y)) continue;
                int best = distance[i];
                best = Math.min(best, neighborDistance(distance, w, h, x + 1, y, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x, y + 1, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x - 1, y + 1, 1));
                best = Math.min(best, neighborDistance(distance, w, h, x + 1, y + 1, 1));
                distance[i] = best;
            }
        }
        return distance;
    }

    private static int neighborDistance(int[] distance, int width, int height, int x, int y, int cost) {
        if (x < 0 || x >= width || y < 0 || y >= height) return Integer.MAX_VALUE;
        int neighbor = distance[y * width + x];
        return neighbor == Integer.MAX_VALUE ? neighbor : neighbor + cost;
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
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        File file = outputPath.toFile();
        boolean written = ImageIO.write(image, "PNG", file);
        if (!written) {
            throw new IOException("Impossible d'écrire l'image au format PNG vers : " + outputPath);
        }
    }
}
