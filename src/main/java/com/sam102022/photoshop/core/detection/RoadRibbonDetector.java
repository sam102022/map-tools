package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Détecte les rues locales comme des bandes claires délimitées par deux bordures
 * sombres et vérifie que cette signature se prolonge le long de l'axe.
 */
final class RoadRibbonDetector {
    private static final int MIN_ROAD_LUMINANCE = 220;
    private static final int MIN_BORDER_LUMINANCE = 125;
    private static final int MAX_BORDER_LUMINANCE = 248;
    private static final int MIN_HALF_WIDTH = 2;
    private static final int MAX_HALF_WIDTH = 10;
    private static final int[] WIDTHS = {2, 3, 4, 5, 6, 8, 10};
    private static final int[] NORMAL_X = {1, 0, 1, 1, 2, 1, 2, 1};
    private static final int[] NORMAL_Y = {0, 1, 1, -1, 1, 2, -1, -2};
    private static final int[] CONTINUITY_OFFSETS = {5, 10};

    BinaryMask detect(BufferedImage image, float sensitivity) {
        if (image == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (sensitivity <= 0.0f) {
            throw new IllegalArgumentException("La sensibilité aux routes doit être strictement positive.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = image.getRGB(0, 0, width, height, null, 0, width);
        int[] luminance = new int[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int pixel = rgb[i];
            luminance[i] = (int) Math.round(0.299 * ((pixel >>> 16) & 0xff)
                    + 0.587 * ((pixel >>> 8) & 0xff) + 0.114 * (pixel & 0xff));
        }

        // Le paramètre ne règle que le contraste nécessaire entre chaussée et bordures.
        int minContrast = Math.max(3, Math.min(18, Math.round(8.0f / sensitivity)));
        BinaryMask roads = new BinaryMask(width, height);

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                int index = y * width + x;
                if (luminance[index] < MIN_ROAD_LUMINANCE || !isNeutral(rgb[index])) {
                    continue;
                }

                for (int direction = 0; direction < NORMAL_X.length; direction++) {
                    int nx = NORMAL_X[direction];
                    int ny = NORMAL_Y[direction];
                    int halfWidth = findHalfWidth(rgb, luminance, x, y, nx, ny,
                            MIN_HALF_WIDTH, MAX_HALF_WIDTH, minContrast, width, height);
                    if (halfWidth == 0 || !hasLongitudinalContinuity(rgb, luminance,
                            x, y, nx, ny, halfWidth, minContrast, width, height)) {
                        continue;
                    }
                    fillBand(roads, x, y, nx, ny, halfWidth, width, height);
                }
            }
        }
        return roads;
    }

    /** Finds a symmetric pair of darker edges around a bright, neutral road surface. */
    private static int findHalfWidth(int[] rgb, int[] luminance, int x, int y, int nx, int ny,
                                     int minWidth, int maxWidth, int minContrast,
                                     int imageWidth, int imageHeight) {
        int centerIndex = y * imageWidth + x;
        int centerLum = luminance[centerIndex];
        if (centerLum < MIN_ROAD_LUMINANCE || !isNeutral(rgb[centerIndex])) return 0;

        for (int halfWidth : WIDTHS) {
            if (halfWidth < minWidth || halfWidth > maxWidth) continue;
            int leftX = sampleX(x, nx, ny, -halfWidth);
            int leftY = sampleY(y, nx, ny, -halfWidth);
            int rightX = sampleX(x, nx, ny, halfWidth);
            int rightY = sampleY(y, nx, ny, halfWidth);
            if (!inside(leftX, leftY, imageWidth, imageHeight)
                    || !inside(rightX, rightY, imageWidth, imageHeight)) continue;

            int leftIndex = leftY * imageWidth + leftX;
            int rightIndex = rightY * imageWidth + rightX;
            int leftLum = luminance[leftIndex];
            int rightLum = luminance[rightIndex];
            if (isValidBorder(rgb[leftIndex], leftLum, centerLum, minContrast)
                    && isValidBorder(rgb[rightIndex], rightLum, centerLum, minContrast)) {
                return halfWidth;
            }
        }
        return 0;
    }

    private static boolean isValidBorder(int rgb, int borderLum, int roadLum, int minContrast) {
        return borderLum >= MIN_BORDER_LUMINANCE && borderLum <= MAX_BORDER_LUMINANCE
                && isNeutral(rgb) && roadLum - borderLum >= minContrast;
    }

    /** Requires matching paired edges at short and medium distances on both sides of the axis. */
    private static boolean hasLongitudinalContinuity(int[] rgb, int[] luminance,
                                                      int x, int y, int nx, int ny,
                                                      int halfWidth, int minContrast,
                                                      int imageWidth, int imageHeight) {
        int tx = -ny;
        int ty = nx;
        for (int sign : new int[]{-1, 1}) {
            for (int offset : CONTINUITY_OFFSETS) {
                boolean supported = false;
                for (int drift = -1; drift <= 1 && !supported; drift++) {
                    for (int lateral = -1; lateral <= 1 && !supported; lateral++) {
                        int sx = sampleX(x, tx, ty, sign * offset + drift)
                                + sampleX(0, nx, ny, lateral);
                        int sy = sampleY(y, tx, ty, sign * offset + drift)
                                + sampleY(0, nx, ny, lateral);
                        if (!inside(sx, sy, imageWidth, imageHeight)) continue;
                        int localWidth = findHalfWidth(rgb, luminance, sx, sy, nx, ny,
                                Math.max(MIN_HALF_WIDTH, halfWidth - 2),
                                Math.min(MAX_HALF_WIDTH, halfWidth + 2), minContrast,
                                imageWidth, imageHeight);
                        supported = localWidth > 0;
                    }
                }
                if (!supported) return false;
            }
        }
        return true;
    }

    private static void fillBand(BinaryMask mask, int x, int y, int nx, int ny,
                                 int halfWidth, int imageWidth, int imageHeight) {
        for (int distance = -halfWidth; distance <= halfWidth; distance++) {
            int px = sampleX(x, nx, ny, distance);
            int py = sampleY(y, nx, ny, distance);
            if (inside(px, py, imageWidth, imageHeight)) mask.set(px, py, true);
        }
    }

    private static int sampleX(int origin, int dx, int dy, int distance) {
        return origin + (int) Math.round(distance * dx / Math.hypot(dx, dy));
    }

    private static int sampleY(int origin, int dx, int dy, int distance) {
        return origin + (int) Math.round(distance * dy / Math.hypot(dx, dy));
    }

    private static boolean isNeutral(int rgb) {
        int r = (rgb >>> 16) & 0xff;
        int g = (rgb >>> 8) & 0xff;
        int b = rgb & 0xff;
        return Math.abs(r - g) <= 12 && Math.abs(r - b) <= 12 && Math.abs(g - b) <= 12;
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
}
