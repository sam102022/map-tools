package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.image.BufferedImage;

/**
 * Détecte les axes routiers Google Maps par analyse spectrale des couleurs
 * (autoroutes orange, routes jaunes, rues blanches contrastées) et gradient Sobel.
 */
public class RoadDetector {

    public BinaryMask detectRoads(BufferedImage mapImage, SnappingConfig config) {
        if (mapImage == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (config == null) {
            throw new IllegalArgumentException("La configuration ne peut pas être null.");
        }

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        BinaryMask rawBarrier = new BinaryMask(w, h);

        int[] luminance = new int[w * h];

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int rgb = mapImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                luminance[rowOffset + x] = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (isRoadColor(r, g, b)) {
                    rawBarrier.set(x, y, true);
                }
            }
        }

        // Détection de contours Sobel pour capturer les délimitations nettes des routes blanches
        float sensitivity = config.roadSensitivity();
        int sobelThreshold = Math.max(30, (int) (75 / sensitivity));
        int sobelThresholdSq = sobelThreshold * sobelThreshold;

        for (int y = 1; y < h - 1; y++) {
            int prevRow = (y - 1) * w;
            int currRow = y * w;
            int nextRow = (y + 1) * w;

            for (int x = 1; x < w - 1; x++) {
                if (rawBarrier.get(x, y)) {
                    continue;
                }

                // Gradients Sobel Gx et Gy
                int gx = -luminance[prevRow + (x - 1)] + luminance[prevRow + (x + 1)]
                        - 2 * luminance[currRow + (x - 1)] + 2 * luminance[currRow + (x + 1)]
                        - luminance[nextRow + (x - 1)] + luminance[nextRow + (x + 1)];

                int gy = -luminance[prevRow + (x - 1)] - 2 * luminance[prevRow + x] - luminance[prevRow + (x + 1)]
                        + luminance[nextRow + (x - 1)] + 2 * luminance[nextRow + x] + luminance[nextRow + (x + 1)];

                int magSq = gx * gx + gy * gy;
                if (magSq >= sobelThresholdSq) {
                    rawBarrier.set(x, y, true);
                }
            }
        }

        // Fermeture morphologique pour ponter les brèches (textes, ponts, intersections)
        return MorphologyOps.close(rawBarrier, config.closingRadius());
    }

    private boolean isRoadColor(int r, int g, int b) {
        // 1. Autoroutes & Voies rapides (orange / saumon)
        if (r >= 210 && g >= 130 && g <= 220 && b >= 50 && b <= 165 && (r - g) >= 15) {
            return true;
        }

        // 2. Routes principales & avenues (jaune / crème)
        if (r >= 220 && g >= 200 && b >= 100 && b <= 210 && Math.abs(r - g) <= 35 && (r - b) >= 30) {
            return true;
        }

        // 3. Rues secondaires & urbaines (blanc cassé très lumineux)
        if (r >= 240 && g >= 240 && b >= 240) {
            return true;
        }

        return false;
    }
}
