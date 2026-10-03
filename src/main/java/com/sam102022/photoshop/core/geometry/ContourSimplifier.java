package com.sam102022.photoshop.core.geometry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Simplificateur géométrique de contours polygonaux basé sur l'algorithme de Ramer-Douglas-Peucker (RDP).
 * <p>
 * Réduit le nombre de sommets d'un contour polygonal (ouvert ou fermé) en éliminant les points
 * situés en deçà d'une distance orthogonale seuil ({@code epsilon}) par rapport aux segments de corde,
 * tout en préservant fidèlement la topologie et la fermeture de la boucle.
 * </p>
 */
public class ContourSimplifier {

    /**
     * Simplifie un contour polygonal donné en respectant une tolérance géométrique maximale.
     * <p>
     * Pour les contours fermés (dont le premier point coïncide avec le dernier), le polygone est
     * scindé au point le plus distant afin d'appliquer RDP de manière bilatérale sans rompre la fermeture.
     * </p>
     *
     * @param points  Liste ordonnée des points formant le contour (ouvert ou fermé).
     * @param epsilon Tolérance de déviation orthogonale maximale (en pixels). Si {@code <= 0}, aucun filtrage n'est appliqué.
     * @return Nouvelle liste immuable des sommets simplifiés, conservant le statut fermé si le contour initial l'était.
     * @throws IllegalArgumentException si la liste de points fournie est null.
     */
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
            openPoints.removeLast();
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
            int dx = p.x - p0.x;
            int dy = p.y - p0.y;
            int distSq = dx * dx + dy * dy;
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                farthestIdx = i;
            }
        }

        List<Point> part1 = rdp(openPoints.subList(0, farthestIdx + 1), epsilon);
        List<Point> part2 = rdp(openPoints.subList(farthestIdx, openPoints.size()), epsilon);

        List<Point> result = new ArrayList<>(part1);
        if (part2.size() > 1) {
            result.removeLast(); // Dédupliquer le pivot
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

    /**
     * Exécute l'algorithme récursif de Ramer-Douglas-Peucker sur un sous-segment polygonal ouvert.
     *
     * @param points  Sous-ensemble ordonné de points à simplifier.
     * @param epsilon Distance orthogonale seuil en dessous de laquelle les points intermédiaires sont écartés.
     * @return Liste ordonnée des points clés conservés pour ce sous-segment.
     */
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
            result.removeLast();
            result.addAll(right);
        } else {
            result.add(start);
            result.add(end);
        }
        return result;
    }

    /**
     * Calcule la distance orthogonale minimale (euclidienne) entre un point et une droite passant par deux extrémités.
     *
     * @param pt        Point dont on souhaite mesurer l'écartement.
     * @param lineStart Extrémité de départ du segment de référence.
     * @param lineEnd   Extrémité d'arrivée du segment de référence.
     * @return Distance euclidienne orthogonale entre le point et la droite.
     */
    private double perpendicularDistance(Point pt, Point lineStart, Point lineEnd) {
        int dx = lineEnd.x - lineStart.x;
        int dy = lineEnd.y - lineStart.y;
        double lenSq = (dx * dx + dy * dy);
        if (lenSq == 0) {
            double x = (pt.x - lineStart.x);
            double y = (pt.y - lineStart.y);
            return Math.hypot(x, y);
        }
        double num = Math.abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x);
        return num / Math.sqrt(lenSq);
    }
}
