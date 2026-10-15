package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Supprime les petites boucles où le contour se recoupe lui-même.
 * <p>
 * Un accrochage local sur un autre bord de route, ou un raccord d'Hermite trop court, peut faire se croiser
 * deux segments proches du contour et former une boucle parasite. Pour chaque segment, les segments
 * suivants situés à moins de {@code maxLoopPoints} points sont testés ; en cas d'intersection, les points de
 * la boucle sont remplacés par le point d'intersection.
 * </p>
 */
public class LocalLoopRemover {

    private static final Logger LOGGER = Logger.getLogger(LocalLoopRemover.class.getName());

    /** Longueur maximale (en points) d'une boucle supprimée. */
    public static final int DEFAULT_MAX_LOOP_POINTS = 200;

    /**
     * Construit un supprimeur sans état.
     */
    public LocalLoopRemover() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Supprime les boucles locales du contour.
     *
     * @param contour       Contour fermé.
     * @param maxLoopPoints Longueur maximale d'une boucle (points).
     * @return Contour sans boucle locale (coins conservés s'ils restent valides, sinon vidés), ou l'original.
     * @throws NullPointerException si contour est null.
     */
    public SmoothVectorContour removeLoops(SmoothVectorContour contour, int maxLoopPoints) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        List<PixelPoint> points = new ArrayList<>(contour.points());
        int removedLoops = 0;
        int i = 0;
        while (i < points.size() - 3) {
            int j = findCrossing(points, i, maxLoopPoints);
            if (j < 0) {
                i++;
                continue;
            }
            PixelPoint cross = intersection(points.get(i), points.get(i + 1), points.get(j), points.get(j + 1));
            List<PixelPoint> rebuilt = new ArrayList<>(points.subList(0, i + 1));
            rebuilt.add(cross);
            rebuilt.addAll(points.subList(j + 1, points.size()));
            points = rebuilt;
            removedLoops++;
        }
        if (removedLoops == 0) {
            return contour;
        }
        int loops = removedLoops;
        LOGGER.info(() -> String.format("[Boucles] %d boucle(s) locale(s) supprimée(s) du contour.", loops));
        return new SmoothVectorContour(points, List.of(), contour.width(), contour.height(),
                contour.cropWindow(), contour.substitutedRoundabouts());
    }

    /**
     * Cherche le premier segment [j, j+1] (j &gt;= i + 2) coupant le segment [i, i+1].
     *
     * @param points        Contour.
     * @param i             Indice du segment de départ.
     * @param maxLoopPoints Fenêtre de recherche.
     * @return Indice j du segment sécant, ou -1.
     */
    private int findCrossing(List<PixelPoint> points, int i, int maxLoopPoints) {
        PixelPoint a = points.get(i);
        PixelPoint b = points.get(i + 1);
        int last = Math.min(points.size() - 2, i + maxLoopPoints);
        for (int j = i + 2; j <= last; j++) {
            if (segmentsIntersect(a, b, points.get(j), points.get(j + 1))) {
                return j;
            }
        }
        return -1;
    }

    /**
     * Test d'intersection propre de deux segments.
     *
     * @param p1 Début du premier segment.
     * @param p2 Fin du premier segment.
     * @param p3 Début du second segment.
     * @param p4 Fin du second segment.
     * @return true si les segments se coupent strictement.
     */
    private boolean segmentsIntersect(PixelPoint p1, PixelPoint p2, PixelPoint p3, PixelPoint p4) {
        double d1 = cross(p3, p4, p1);
        double d2 = cross(p3, p4, p2);
        double d3 = cross(p1, p2, p3);
        double d4 = cross(p1, p2, p4);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    /**
     * Produit vectoriel (b - a) x (c - a).
     *
     * @param a Origine.
     * @param b Premier point.
     * @param c Second point.
     * @return Produit vectoriel.
     */
    private double cross(PixelPoint a, PixelPoint b, PixelPoint c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    /**
     * Point d'intersection des droites (p1, p2) et (p3, p4), supposées sécantes.
     *
     * @param p1 Point de la première droite.
     * @param p2 Point de la première droite.
     * @param p3 Point de la seconde droite.
     * @param p4 Point de la seconde droite.
     * @return Point d'intersection.
     */
    private PixelPoint intersection(PixelPoint p1, PixelPoint p2, PixelPoint p3, PixelPoint p4) {
        double d = (p1.x() - p2.x()) * (p3.y() - p4.y()) - (p1.y() - p2.y()) * (p3.x() - p4.x());
        double t = ((p1.x() - p3.x()) * (p3.y() - p4.y()) - (p1.y() - p3.y()) * (p3.x() - p4.x())) / d;
        return new PixelPoint(p1.x() + t * (p2.x() - p1.x()), p1.y() + t * (p2.y() - p1.y()));
    }
}
