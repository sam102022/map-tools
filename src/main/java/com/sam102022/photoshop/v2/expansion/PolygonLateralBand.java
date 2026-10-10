package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Bande extérieure des côtés d'un polygone longés par une route, limitée au bord opposé de cette route.
 * <p>
 * Chaque côté du polygone est échantillonné tous les {@value #BIN_LENGTH} px. En chaque échantillon, on
 * avance perpendiculairement au côté, vers l'extérieur du polygone, jusqu'à {@code reach} px : la première
 * chaussée rencontrée donne une profondeur (son bord opposé). Un côté est retenu si une chaussée est trouvée
 * sur au moins {@code minAlongFraction} de sa longueur (la route longe le côté) ; sa bande s'étend alors
 * jusqu'à la profondeur médiane de cette chaussée.
 * </p>
 * <p>
 * La bande ne s'étend donc ni au-delà des sommets saillants, ni au-delà du bord opposé de la route longée :
 * une route qui prolonge le territoire au-delà d'un échangeur, ou qui passe sous la route longée, n'est pas
 * suivie.
 * </p>
 */
public final class PolygonLateralBand {

    /** Longueur (px) d'un intervalle d'échantillonnage le long d'un côté. */
    private static final double BIN_LENGTH = 5.0;
    /** Pas (px) du balayage de la bande. */
    private static final double PAINT_STEP = 0.5;
    /** Marge (px) ajoutée au bord opposé de la chaussée. */
    private static final double EDGE_MARGIN = 2.0;

    private PolygonLateralBand() {
    }

    /**
     * Construit la zone : polygone et bandes extérieures des côtés longés par une route.
     *
     * @param outline          Sommets du polygone (repère du masque).
     * @param polygonMask      Masque rasterisé du polygone (même repère).
     * @param roads            Masque des routes candidates.
     * @param reach            Profondeur maximale (px) de recherche de la route hors du polygone.
     * @param minAlongFraction Fraction minimale de la longueur du côté longée par une route.
     * @param maxGap           Interruption maximale (px) de la chaussée : marquages au sol, ou terre-plein central
     *                         d'un boulevard à chaussées séparées.
     * @return Masque de la zone (contient le polygone).
     * @throws NullPointerException si un argument est null.
     */
    public static BinaryMask build(List<PixelPoint> outline, BinaryMask polygonMask, BinaryMask roads,
                                   double reach, double minAlongFraction, int maxGap) {
        Objects.requireNonNull(outline, "outline ne doit pas être nul.");
        Objects.requireNonNull(polygonMask, "polygonMask ne doit pas être nul.");
        Objects.requireNonNull(roads, "roads ne doit pas être nul.");
        BinaryMask out = polygonMask.copy();
        int n = outline.size();
        for (int i = 0; i < n; i++) {
            PixelPoint a = outline.get(i);
            PixelPoint b = outline.get((i + 1) % n);
            double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
            if (length < 1e-9) {
                continue;
            }
            double ux = (b.x() - a.x()) / length;
            double uy = (b.y() - a.y()) / length;
            double[] outward = outwardNormal(a, ux, uy, length, polygonMask);
            double depth = roadDepth(a, ux, uy, length, outward, roads, reach, minAlongFraction, maxGap);
            if (depth > 0.0) {
                paintBand(out, a, b, length, outward, depth + EDGE_MARGIN);
            }
        }
        return out;
    }

    /**
     * Normale du côté orientée vers l'extérieur du polygone (sondage au milieu du côté).
     *
     * @param a           Début du côté.
     * @param ux          Direction du côté (x).
     * @param uy          Direction du côté (y).
     * @param length      Longueur du côté.
     * @param polygonMask Polygone.
     * @return {nx, ny}.
     */
    private static double[] outwardNormal(PixelPoint a, double ux, double uy, double length, BinaryMask polygonMask) {
        double mx = a.x() + ux * length / 2.0;
        double my = a.y() + uy * length / 2.0;
        boolean leftInside = get(polygonMask, mx - uy * 3.0, my + ux * 3.0);
        return leftInside ? new double[]{uy, -ux} : new double[]{-uy, ux};
    }

    /**
     * Profondeur médiane (px) du bord opposé de la première chaussée rencontrée hors du polygone, ou 0 si la
     * chaussée ne longe pas le côté sur au moins {@code minAlongFraction} de sa longueur.
     *
     * @param a                Début du côté.
     * @param ux               Direction du côté (x).
     * @param uy               Direction du côté (y).
     * @param length           Longueur du côté.
     * @param outward          Normale extérieure.
     * @param roads            Routes candidates.
     * @param reach            Profondeur maximale de recherche.
     * @param minAlongFraction Fraction minimale.
     * @param maxGap           Interruption maximale de la chaussée (px).
     * @return Profondeur médiane, ou 0.
     */
    private static double roadDepth(PixelPoint a, double ux, double uy, double length, double[] outward,
                                    BinaryMask roads, double reach, double minAlongFraction, int maxGap) {
        int bins = Math.max(1, (int) Math.ceil(length / BIN_LENGTH));
        double[] depths = new double[bins];
        int found = 0;
        for (int k = 0; k < bins; k++) {
            double t = Math.min(length, (k + 0.5) * BIN_LENGTH);
            double depth = farEdge(a.x() + ux * t, a.y() + uy * t, outward, roads, reach, maxGap);
            if (depth > 0.0) {
                depths[found++] = depth;
            }
        }
        if (found == 0 || (double) found / bins < minAlongFraction) {
            return 0.0;
        }
        Arrays.sort(depths, 0, found);
        return depths[found / 2];
    }

    /**
     * Le long d'une perpendiculaire extérieure, distance du bord opposé de la première chaussée rencontrée.
     *
     * @param x       Point de départ (x).
     * @param y       Point de départ (y).
     * @param outward Normale extérieure.
     * @param roads   Routes candidates.
     * @param reach   Profondeur maximale.
     * @param maxGap  Interruption maximale de la chaussée (px).
     * @return Distance du bord opposé, ou 0 si aucune chaussée n'est rencontrée.
     */
    private static double farEdge(double x, double y, double[] outward, BinaryMask roads, double reach,
                                  int maxGap) {
        int s = 0;
        while (s <= reach && !get(roads, x + outward[0] * s, y + outward[1] * s)) {
            s++;
        }
        if (s > reach) {
            return 0.0;
        }
        int lastRoad = s;
        int gap = 0;
        while (gap <= maxGap && s <= 2 * reach) {
            if (get(roads, x + outward[0] * s, y + outward[1] * s)) {
                lastRoad = s;
                gap = 0;
            } else {
                gap++;
            }
            s++;
        }
        return lastRoad + 1.0;
    }

    /**
     * Active les pixels de la bande extérieure d'un côté, jusqu'à la profondeur donnée.
     *
     * @param out     Masque modifié en place.
     * @param a       Début du côté.
     * @param b       Fin du côté.
     * @param length  Longueur du côté.
     * @param outward Normale extérieure.
     * @param depth   Profondeur (px).
     */
    private static void paintBand(BinaryMask out, PixelPoint a, PixelPoint b, double length, double[] outward,
                                  double depth) {
        double ux = (b.x() - a.x()) / length;
        double uy = (b.y() - a.y()) / length;
        // Balayage dans le repère du côté (pas de 0,5 px) : coût proportionnel à la surface de la bande.
        for (double t = 0.0; t <= length; t += PAINT_STEP) {
            for (double s = -1.0; s <= depth; s += PAINT_STEP) {
                int x = (int) Math.round(a.x() + ux * t + outward[0] * s);
                int y = (int) Math.round(a.y() + uy * t + outward[1] * s);
                if (x >= 0 && y >= 0 && x < out.getWidth() && y < out.getHeight()) {
                    out.set(x, y, true);
                }
            }
        }
    }

    /**
     * Lecture d'un masque en coordonnées réelles (arrondi), faux hors image.
     *
     * @param mask Masque.
     * @param x    Abscisse.
     * @param y    Ordonnée.
     * @return Valeur du pixel.
     */
    private static boolean get(BinaryMask mask, double x, double y) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        return ix >= 0 && iy >= 0 && ix < mask.getWidth() && iy < mask.getHeight() && mask.get(ix, iy);
    }
}
