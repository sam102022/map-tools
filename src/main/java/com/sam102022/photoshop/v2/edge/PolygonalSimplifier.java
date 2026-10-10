package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Réduit le contour accroché à un polygone de grands segments rectilignes, à la manière d'un détourage
 * manuel au lasso polygonal.
 * <p>
 * 1. Découpage du contour fermé par Douglas-Peucker (tolérance en pixels) ;
 * 2. Réajustement de chaque segment par une droite des moindres carrés totaux sur les points qu'il couvre ;
 * 3. Sommets placés à l'intersection des droites voisines (garde-fou : repli sur le point d'origine si les
 *    droites sont quasi parallèles ou si l'intersection s'éloigne du tracé).
 * Les courbes (giratoires, virages) sont restituées par des facettes dont la flèche n'excède pas la tolérance.
 * </p>
 */
public class PolygonalSimplifier {

    private static final Logger LOGGER = Logger.getLogger(PolygonalSimplifier.class.getName());
    private static final double MIN_SINE = 0.02;

    /**
     * Construit un simplificateur sans état.
     */
    public PolygonalSimplifier() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Simplifie le contour en polygone à grands segments.
     *
     * @param contour   Contour fermé (repère local).
     * @param tolerance Écart maximal (px) entre le polygone et le contour ; 0 ou moins désactive la simplification.
     * @return Nouveau contour réduit à ses sommets (coins réindexés), ou le contour d'origine si désactivé.
     * @throws NullPointerException si contour est null.
     */
    public SmoothVectorContour simplify(SmoothVectorContour contour, double tolerance) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        List<PixelPoint> points = contour.points();
        int n = points.size();
        if (tolerance <= 0.0 || n < 8) {
            return contour;
        }
        int[] breakpoints = closedDouglasPeucker(points, tolerance);
        if (breakpoints.length < 3) {
            return contour;
        }
        List<PixelPoint> vertices = refineVertices(points, breakpoints, tolerance);
        List<Integer> corners = remapCorners(contour.cornerIndices(), breakpoints, n);
        int initialCount = n;
        int finalCount = vertices.size();
        LOGGER.info(() -> String.format("[Polygone] Contour réduit de %d points à %d sommets.", initialCount, finalCount));
        return new SmoothVectorContour(vertices, corners, contour.width(), contour.height(),
                contour.cropWindow(), contour.substitutedRoundabouts());
    }

    /**
     * Douglas-Peucker sur un contour fermé : la boucle est coupée au point de départ et au point le plus éloigné.
     *
     * @param points    Contour fermé.
     * @param tolerance Tolérance (px).
     * @return Indices des points conservés, triés.
     */
    int[] closedDouglasPeucker(List<PixelPoint> points, double tolerance) {
        int n = points.size();
        int far = farthestFromFirst(points);
        boolean[] keep = new boolean[n + 1];
        keep[0] = true;
        keep[far] = true;
        keep[n] = true;
        simplifyRange(points, 0, far, tolerance, keep);
        simplifyRange(points, far, n, tolerance, keep);
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                count++;
            }
        }
        int[] result = new int[count];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                result[k++] = i;
            }
        }
        return result;
    }

    /**
     * Indice du point le plus éloigné du premier point.
     *
     * @param points Contour.
     * @return Indice du point le plus éloigné.
     */
    private int farthestFromFirst(List<PixelPoint> points) {
        PixelPoint first = points.get(0);
        int best = 1;
        double bestDist = -1.0;
        for (int i = 1; i < points.size(); i++) {
            double d = Math.hypot(points.get(i).x() - first.x(), points.get(i).y() - first.y());
            if (d > bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /**
     * Douglas-Peucker itératif sur l'intervalle [from, to] (l'indice n désigne le point 0, boucle fermée).
     *
     * @param points    Contour fermé.
     * @param from      Indice de début.
     * @param to        Indice de fin (peut valoir n).
     * @param tolerance Tolérance (px).
     * @param keep      Marqueurs des points conservés (taille n + 1).
     */
    private void simplifyRange(List<PixelPoint> points, int from, int to, double tolerance, boolean[] keep) {
        int n = points.size();
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{from, to});
        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int a = range[0];
            int b = range[1];
            if (b <= a + 1) {
                continue;
            }
            PixelPoint pa = points.get(a % n);
            PixelPoint pb = points.get(b % n);
            int worst = -1;
            double worstDist = tolerance;
            for (int i = a + 1; i < b; i++) {
                double d = distanceToSegmentLine(points.get(i % n), pa, pb);
                if (d > worstDist) {
                    worstDist = d;
                    worst = i;
                }
            }
            if (worst >= 0) {
                keep[worst] = true;
                stack.push(new int[]{a, worst});
                stack.push(new int[]{worst, b});
            }
        }
    }

    /**
     * Distance d'un point à la droite (a, b), ou au point a si a et b sont confondus.
     *
     * @param p Point.
     * @param a Premier point de la droite.
     * @param b Second point de la droite.
     * @return Distance (px).
     */
    private double distanceToSegmentLine(PixelPoint p, PixelPoint a, PixelPoint b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double len = Math.hypot(dx, dy);
        if (len < 1e-12) {
            return Math.hypot(p.x() - a.x(), p.y() - a.y());
        }
        return Math.abs(dx * (p.y() - a.y()) - dy * (p.x() - a.x())) / len;
    }

    /**
     * Réajuste chaque segment par une droite des moindres carrés totaux et place les sommets aux intersections.
     *
     * @param points      Contour fermé.
     * @param breakpoints Indices des sommets Douglas-Peucker.
     * @param tolerance   Tolérance (px) servant au garde-fou d'éloignement.
     * @return Sommets du polygone.
     */
    private List<PixelPoint> refineVertices(List<PixelPoint> points, int[] breakpoints, double tolerance) {
        int n = points.size();
        int m = breakpoints.length;
        double[][] lines = new double[m][];
        for (int i = 0; i < m; i++) {
            int a = breakpoints[i];
            int b = (i == m - 1) ? breakpoints[0] + n : breakpoints[i + 1];
            lines[i] = fitLine(points, a, b);
        }
        List<PixelPoint> vertices = new ArrayList<>(m);
        double maxShift = 3.0 * tolerance + 1.0;
        for (int i = 0; i < m; i++) {
            PixelPoint original = points.get(breakpoints[i]);
            PixelPoint intersection = intersect(lines[(i - 1 + m) % m], lines[i]);
            boolean usable = intersection != null
                    && Math.hypot(intersection.x() - original.x(), intersection.y() - original.y()) < maxShift;
            vertices.add(usable ? intersection : original);
        }
        return vertices;
    }

    /**
     * Droite des moindres carrés totaux sur les points d'indices [a, b] (modulo n).
     *
     * @param points Contour fermé.
     * @param a      Indice de début.
     * @param b      Indice de fin (peut dépasser n).
     * @return {cx, cy, dx, dy} : centroïde et direction unitaire.
     */
    private double[] fitLine(List<PixelPoint> points, int a, int b) {
        int n = points.size();
        int count = b - a + 1;
        double cx = 0.0;
        double cy = 0.0;
        for (int i = a; i <= b; i++) {
            cx += points.get(i % n).x();
            cy += points.get(i % n).y();
        }
        cx /= count;
        cy /= count;
        double sxx = 0.0;
        double sxy = 0.0;
        double syy = 0.0;
        for (int i = a; i <= b; i++) {
            double dx = points.get(i % n).x() - cx;
            double dy = points.get(i % n).y() - cy;
            sxx += dx * dx;
            sxy += dx * dy;
            syy += dy * dy;
        }
        double angle = 0.5 * Math.atan2(2.0 * sxy, sxx - syy);
        return new double[]{cx, cy, Math.cos(angle), Math.sin(angle)};
    }

    /**
     * Intersection de deux droites {cx, cy, dx, dy}.
     *
     * @param l1 Première droite.
     * @param l2 Seconde droite.
     * @return Point d'intersection, ou null si les droites sont quasi parallèles.
     */
    private PixelPoint intersect(double[] l1, double[] l2) {
        double det = -l1[2] * l2[3] + l1[3] * l2[2];
        if (Math.abs(det) < MIN_SINE) {
            return null;
        }
        double rx = l2[0] - l1[0];
        double ry = l2[1] - l1[1];
        double t = (-rx * l2[3] + ry * l2[2]) / det;
        return new PixelPoint(l1[0] + t * l1[2], l1[1] + t * l1[3]);
    }

    /**
     * Réindexe les coins du contour d'origine sur les sommets du polygone (sommet le plus proche en abscisse
     * curviligne).
     *
     * @param corners     Indices des coins dans le contour d'origine.
     * @param breakpoints Indices des sommets conservés.
     * @param n           Nombre de points du contour d'origine.
     * @return Indices des coins dans le polygone, sans doublon.
     */
    private List<Integer> remapCorners(List<Integer> corners, int[] breakpoints, int n) {
        List<Integer> remapped = new ArrayList<>();
        for (int corner : corners) {
            int best = 0;
            int bestGap = Integer.MAX_VALUE;
            for (int k = 0; k < breakpoints.length; k++) {
                int d = Math.abs(breakpoints[k] - corner);
                int gap = Math.min(d, n - d);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = k;
                }
            }
            if (!remapped.contains(best)) {
                remapped.add(best);
            }
        }
        return remapped;
    }
}
