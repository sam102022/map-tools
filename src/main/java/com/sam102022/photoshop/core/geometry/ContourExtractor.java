package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrait les contours ordonnés d'un masque binaire (voisinage 8-connexe, sens horaire).
 */
public class ContourExtractor {
    // 8-voisinage dans le sens horaire : 0:N, 1:NE, 2:E, 3:SE, 4:S, 5:SW, 6:W, 7:NW
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};

    /** Trace le plus grand contour extérieur avec le voisinage de Moore. */
    public List<Point> extractLargestContour(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }

        BinaryMask target = com.sam102022.photoshop.core.segmentation.MorphologyOps.keepLargestComponent(mask);
        Point start = null;
        for (int y = 0; y < target.getHeight() && start == null; y++) {
            for (int x = 0; x < target.getWidth(); x++) {
                if (target.get(x, y) && isBoundary(target, x, y)) {
                    start = new Point(x, y);
                    break;
                }
            }
        }
        if (start == null) {
            return List.of();
        }

        List<Point> contour = new ArrayList<>();
        Point current = new Point(start);
        contour.add(new Point(current));

        Point firstNext = null;
        int startScanDir = 7;
        int maxLimit = Math.max(1000, (target.getWidth() + target.getHeight()) * 8);

        for (int iteration = 0; iteration < maxLimit; iteration++) {
            Point next = null;
            int foundDir = -1;

            for (int offset = 0; offset < 8; offset++) {
                int d = (startScanDir + offset) % 8;
                int nx = current.x + DX[d];
                int ny = current.y + DY[d];

                if (target.get(nx, ny)) {
                    next = new Point(nx, ny);
                    foundDir = d;
                    break;
                }
            }

            if (next == null) {
                break;
            }

            if (firstNext == null) {
                firstNext = new Point(next);
            } else if (current.equals(start) && next.equals(firstNext)) {
                break;
            }

            contour.add(next);
            current = next;
            startScanDir = (foundDir + 5) % 8;
        }

        return List.copyOf(contour);
    }

    private boolean isBoundary(BinaryMask mask, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && !mask.get(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<Point> extract(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        List<Point> boundary = new ArrayList<>();
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (mask.get(x, y) && isBoundary(mask, x, y)) {
                    boundary.add(new Point(x, y));
                }
            }
        }
        return List.copyOf(boundary);
    }
}
