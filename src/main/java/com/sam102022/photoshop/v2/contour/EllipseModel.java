package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;

/**
 * Modèle géométrique d'une ellipse plane dans le repère local.
 *
 * @param xc    Abscisse du centre géométrique.
 * @param yc    Ordonnée du centre géométrique.
 * @param a     Demi-grand axe (a &gt;= b &gt; 0).
 * @param b     Demi-petit axe (b &gt; 0).
 * @param theta Angle d'orientation du demi-grand axe en radians [-pi, +pi].
 */
public record EllipseModel(
        double xc,
        double yc,
        double a,
        double b,
        double theta
) {

    /**
     * Valide les invariants géométriques de l'ellipse.
     */
    public EllipseModel {
        if (a <= 0.0 || b <= 0.0) {
            throw new IllegalArgumentException("Les demi-axes de l'ellipse doivent être strictement positifs.");
        }
        if (a < b) {
            throw new IllegalArgumentException("Le demi-grand axe 'a' doit être >= au demi-petit axe 'b'.");
        }
    }

    /**
     * Convertit un point du repère local en coordonnées normalisées du cercle unité.
     *
     * @param p Point dans le repère local.
     * @return Point normalisé (u, v) où u^2 + v^2 = 1 sur le bord de l'ellipse.
     */
    public PixelPoint toUnitCircle(PixelPoint p) {
        double dx = p.x() - xc;
        double dy = p.y() - yc;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double u = (dx * cos + dy * sin) / a;
        double v = (-dx * sin + dy * cos) / b;
        return new PixelPoint(u, v);
    }

    /**
     * Reconvertit un point du cercle unité normalisé vers le repère euclidien local.
     *
     * @param u Coordonnée normalisée u.
     * @param v Coordonnée normalisée v.
     * @return Point dans le repère euclidien local.
     */
    public PixelPoint fromUnitCircle(double u, double v) {
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        double xLocal = u * a;
        double yLocal = v * b;
        double x = xc + xLocal * cos - yLocal * sin;
        double y = yc + xLocal * sin + yLocal * cos;
        return new PixelPoint(x, y);
    }
}
