package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** Déplace localement les sommets d'un contour vers des candidats routiers. */
public class RoadSnapper {
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};

    public List<Point> snap(List<Point> contour, BinaryMask roadCandidates, int imageWidth,
                            int imageHeight, SnappingConfig config) {
        if (contour == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du recalage ne peuvent pas être null.");
        }
        if (roadCandidates.getWidth() != imageWidth || roadCandidates.getHeight() != imageHeight) {
            throw new IllegalArgumentException("Dimensions incompatibles entre contour et routes.");
        }
        if (contour.size() < 3) return List.copyOf(contour);

        double area = signedArea(contour);
        List<Point> snapped = new ArrayList<>(contour.size());
        for (int i = 0; i < contour.size(); i++) {
            Point previous = contour.get((i + contour.size() - 1) % contour.size());
            Point current = contour.get(i);
            Point next = contour.get((i + 1) % contour.size());
            double tx = next.x - previous.x, ty = next.y - previous.y;
            double length = Math.hypot(tx, ty);
            if (length == 0 || area == 0) {
                snapped.add(new Point(current));
                continue;
            }
            // L'ordre du contour détermine le côté extérieur de sa normale.
            double nx = (area > 0 ? ty : -ty) / length;
            double ny = (area > 0 ? -tx : tx) / length;
            Point snappedPoint = new Point(current);
            int runStart = -1;
            int runEnd = -1;
            boolean inFirstRoadBand = false;
            int radius = config.snapDistance();
            for (int step = 1; step <= radius; step++) {
                int x = (int) Math.round(current.x + nx * step);
                int y = (int) Math.round(current.y + ny * step);
                if (x < 0 || x >= imageWidth || y < 0 || y >= imageHeight) break;
                if (roadCandidates.get(x, y)) {
                    if (inFirstRoadBand) {
                        runEnd = step;
                        continue;
                    }
                    // Une route doit se prolonger parallèlement au contour ; un pixel isolé
                    // provenant d'un texte ou d'un pictogramme n'est pas un bon point d'accroche.
                    if (tangentSupport(roadCandidates, x, y, tx / length, ty / length) >= 2) {
                        runStart = step;
                        runEnd = step;
                        inFirstRoadBand = true;
                    }
                } else if (inFirstRoadBand) {
                    // On a traversé le premier ruban routier continu : ne pas accrocher
                    // une seconde route située plus loin.
                    break;
                }
            }
            if (runStart >= 0) {
                // Le contour est placé juste après le bord extérieur du ruban, côté
                // extérieur de la zone. Ainsi la dernière rangée de pixels de chaussée
                // reste à l'intérieur du masque final.
                int targetStep = Math.min(runEnd + 1, radius);
                int targetX = (int) Math.round(current.x + nx * targetStep);
                int targetY = (int) Math.round(current.y + ny * targetStep);
                snappedPoint = new Point(targetX, targetY);
            }
            snapped.add(snappedPoint);
        }
        return List.copyOf(snapped);
    }

    private int tangentSupport(BinaryMask candidates, int x, int y, double tx, double ty) {
        int support = 0;
        for (int offset : new int[]{-4, -2, 2, 4}) {
            int sx = (int) Math.round(x + tx * offset);
            int sy = (int) Math.round(y + ty * offset);
            boolean nearby = false;
            for (int across = -1; across <= 1; across++) {
                int px = (int) Math.round(sx - ty * across);
                int py = (int) Math.round(sy + tx * across);
                if (candidates.get(px, py)) {
                    nearby = true;
                    break;
                }
            }
            if (nearby) support++;
        }
        return support;
    }

    private double signedArea(List<Point> points) {
        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i), b = points.get((i + 1) % points.size());
            area += (double) a.x * b.y - (double) b.x * a.y;
        }
        return area / 2.0;
    }
}
