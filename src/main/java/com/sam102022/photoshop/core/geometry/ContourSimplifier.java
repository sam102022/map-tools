package com.sam102022.photoshop.core.geometry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Réduit le nombre de points d'un contour polygonal via l'algorithme de Ramer-Douglas-Peucker
 * tout en conservant la fidélité géométrique dans la limite d'une tolérance donnée.
 */
public class ContourSimplifier {

    public List<Point> simplify(List<Point> points, double epsilon) {
        if (points == null) {
            throw new IllegalArgumentException("La liste de points ne peut pas être null.");
        }
        if (points.size() < 3 || epsilon <= 0) {
            return List.copyOf(points);
        }

        boolean closed = points.getFirst().equals(points.getLast());
        List<Point> openPoints = new ArrayList<>(points);
        if (closed && openPoints.size() > 1) {
            openPoints.remove(openPoints.size() - 1);
        }

        if (openPoints.size() < 3) {
            return List.copyOf(points);
        }

        // Pour un contour fermé, scinder en deux au point le plus éloigné du départ
        int farthestIdx = 0;
        double maxDistSq = 0;
        Point p0 = openPoints.getFirst();
        for (int i = 1; i < openPoints.size(); i++) {
            Point p = openPoints.get(i);
            double dx = p.x - p0.x;
            double dy = p.y - p0.y;
            double distSq = dx * dx + dy * dy;
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                farthestIdx = i;
            }
        }

        List<Point> part1 = rdp(openPoints.subList(0, farthestIdx + 1), epsilon);
        List<Point> part2 = rdp(openPoints.subList(farthestIdx, openPoints.size()), epsilon);

        List<Point> result = new ArrayList<>(part1);
        if (part2.size() > 1) {
            result.remove(result.size() - 1); // Dédupliquer le pivot
            result.addAll(part2);
        }

        // Rattacher le dernier point au premier pour le second segment fermé
        List<Point> closingSegment = rdp(List.of(openPoints.getLast(), openPoints.getFirst()), epsilon);
        if (closingSegment.size() > 2) {
            for (int i = 1; i < closingSegment.size() - 1; i++) {
                result.add(closingSegment.get(i));
            }
        }

        if (closed && !result.getFirst().equals(result.getLast())) {
            result.add(new Point(result.getFirst()));
        }

        return List.copyOf(result);
    }

    private List<Point> rdp(List<Point> points, double epsilon) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }

        double maxDist = 0;
        int index = 0;
        Point start = points.getFirst();
        Point end = points.getLast();

        for (int i = 1; i < points.size() - 1; i++) {
            double dist = perpendicularDistance(points.get(i), start, end);
            if (dist > maxDist) {
                maxDist = dist;
                index = i;
            }
        }

        List<Point> result = new ArrayList<>();
        if (maxDist > epsilon) {
            List<Point> left = rdp(points.subList(0, index + 1), epsilon);
            List<Point> right = rdp(points.subList(index, points.size()), epsilon);
            result.addAll(left);
            result.remove(result.size() - 1);
            result.addAll(right);
        } else {
            result.add(start);
            result.add(end);
        }
        return result;
    }

    private double perpendicularDistance(Point pt, Point lineStart, Point lineEnd) {
        double dx = lineEnd.x - lineStart.x;
        double dy = lineEnd.y - lineStart.y;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) {
            return Math.hypot(pt.x - lineStart.x, pt.y - lineStart.y);
        }
        double num = Math.abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x);
        return num / Math.sqrt(lenSq);
    }
}
