package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Opérations de morphologie mathématique 2D sur BinaryMask (dilatation, érosion, ouverture, fermeture).
 */
public final class MorphologyOps {

    private MorphologyOps() {
    }

    public static BinaryMask dilate(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(x, y)) {
                    int minX = Math.max(0, x - radius);
                    int maxX = Math.min(w - 1, x + radius);
                    int minY = Math.max(0, y - radius);
                    int maxY = Math.min(h - 1, y + radius);

                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            result.set(nx, ny, true);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static BinaryMask erode(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) {
                    continue;
                }
                boolean allNeighborsActive = true;
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(w - 1, x + radius);
                int minY = Math.max(0, y - radius);
                int maxY = Math.min(h - 1, y + radius);

                // Si le voisinage est tronqué par les bords de l'image, on considère les pixels hors champ comme inactifs
                if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) {
                    allNeighborsActive = false;
                } else {
                    checkLoop:
                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            if (!mask.get(nx, ny)) {
                                allNeighborsActive = false;
                                break checkLoop;
                            }
                        }
                    }
                }

                if (allNeighborsActive) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    public static BinaryMask close(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        return erode(dilate(mask, radius), radius);
    }

    public static BinaryMask open(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        return dilate(erode(mask, radius), radius);
    }
}
