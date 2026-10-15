package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;

import java.util.List;
import java.util.Objects;

/**
 * Neutralise le facteur d'affinage dans une bande étroite le long du contour vectoriel.
 * <p>
 * Le facteur d'affinage provient d'un masque binaire (zone autorisée) légèrement flouté : appliqué sur un
 * contour déjà calé au sub-pixel sur le bord des routes, il réintroduit l'escalier des pixels et fait
 * onduler les grands segments. Dans une bande de {@code radius} pixels autour du contour, le facteur est
 * donc forcé à 1 : le bord rendu est celui, rectiligne et anti-crénelé, du polygone. Hors de la bande,
 * le facteur continue d'effacer les fuites intérieures.
 * </p>
 */
public class EdgeBandGuard {

    private static final double SAMPLING_STEP = 0.5;

    /**
     * Construit un garde sans état.
     */
    public EdgeBandGuard() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Retourne une copie de la carte d'affinage dont le facteur vaut 1 près du contour.
     *
     * @param map     Carte d'affinage (repère local).
     * @param contour Contour vectoriel (même repère local).
     * @param radius  Demi-largeur de la bande (px) ; 0 ou moins retourne la carte inchangée.
     * @return Carte d'affinage protégée.
     * @throws NullPointerException si map ou contour est null.
     */
    public AlphaRefinementMap protect(AlphaRefinementMap map, SmoothVectorContour contour, double radius) {
        Objects.requireNonNull(map, "map ne doit pas être nulle.");
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        List<PixelPoint> points = contour.points();
        if (radius <= 0.0 || points.size() < 2) {
            return map;
        }
        float[][] factor = map.factor();
        int r = (int) Math.ceil(radius);
        int n = points.size();
        for (int i = 0; i < n; i++) {
            PixelPoint a = points.get(i);
            PixelPoint b = points.get((i + 1) % n);
            markSegment(factor, a, b, r, map.width(), map.height());
        }
        return new AlphaRefinementMap(map.width(), map.height(), factor, map.cropWindow());
    }

    /**
     * Force le facteur à 1 autour d'un segment (échantillonné tous les 0,5 px, voisinage carré de rayon r).
     * Les coordonnées du contour désignent des centres de pixels.
     *
     * @param factor Matrice de facteurs modifiée en place.
     * @param a      Début du segment.
     * @param b      Fin du segment.
     * @param r      Rayon du voisinage (px).
     * @param width  Largeur de la matrice.
     * @param height Hauteur de la matrice.
     */
    private void markSegment(float[][] factor, PixelPoint a, PixelPoint b, int r, int width, int height) {
        double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLING_STEP));
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            int cx = (int) Math.round(a.x() + t * (b.x() - a.x()));
            int cy = (int) Math.round(a.y() + t * (b.y() - a.y()));
            for (int y = Math.max(0, cy - r); y <= Math.min(height - 1, cy + r); y++) {
                for (int x = Math.max(0, cx - r); x <= Math.min(width - 1, cx + r); x++) {
                    factor[y][x] = 1.0f;
                }
            }
        }
    }
}
