package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;

import java.util.List;
import java.util.Objects;

/**
 * Retire les éclats de terrain blanc laissés au bord du territoire aux entrées et sorties des giratoires.
 * <p>
 * Autour d'un giratoire frontalier, le contour vectoriel raccorde l'arc de l'anneau aux routes qui en partent ;
 * entre deux branches, il englobe parfois un petit coin de terrain situé au-delà de la chaussée (arrondi de
 * trottoir, îlot séparateur), visible comme une tache blanche sur le bord. Dans un carré de
 * {@value #MARGIN} px autour de l'anneau, chaque parcelle de terrain visible (hors chaussée de la capture
 * « Routes seules ») de moins de {@value #MAX_SLIVER_AREA} px qui touche l'extérieur transparent du territoire
 * est rendue transparente, seul le liseré anti-crénelé de la chaussée étant conservé. L'îlot central, entouré
 * par l'anneau, ne touche pas l'extérieur et reste opaque. Inversement, les petites encoches transparentes
 * (moins de {@value #MAX_NOTCH_AREA} px) taillées dans la chaussée de l'anneau par le raccord du contour sont
 * comblées d'après la capture.
 * </p>
 */
public final class RoundaboutSliverCleaner {

    /** Marge (px) autour de l'ellipse extérieure de l'anneau où les éclats sont recherchés. */
    public static final double MARGIN = 20.0;
    /** Surface maximale (px) d'un éclat de terrain retiré. */
    public static final int MAX_SLIVER_AREA = 300;
    /** Surface maximale (px) d'une encoche de chaussée comblée. */
    public static final int MAX_NOTCH_AREA = 150;
    /** Part de chaussée en deçà de laquelle un pixel est du terrain. */
    private static final float LAND_THRESHOLD = 0.5f;
    /** Opacité en deçà de laquelle un pixel est hors du territoire. */
    private static final int VISIBLE_THRESHOLD = 128;

    /**
     * Construit un nettoyeur sans état.
     */
    public RoundaboutSliverCleaner() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Retire (en place) les éclats de terrain et comble les encoches de chaussée autour de chaque giratoire.
     *
     * @param coverage    Couverture pleine image (modifiée en place).
     * @param roundabouts Giratoires inclus dans le contour (repère de la fenêtre).
     * @param roads       Capture « Routes seules » (pleine image).
     * @param cropWindow  Fenêtre de recadrage.
     * @return Nombre de pixels modifiés (éclats retirés et encoches comblées).
     * @throws NullPointerException si un argument est null.
     */
    public int clean(CoverageMask coverage, List<Roundabout> roundabouts, GoogleRoadsImage roads,
                     CropWindow cropWindow) {
        Objects.requireNonNull(coverage, "coverage ne doit pas être nulle.");
        Objects.requireNonNull(roundabouts, "roundabouts ne doit pas être nul.");
        Objects.requireNonNull(roads, "roads ne doit pas être nulle.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nulle.");
        int removed = 0;
        for (Roundabout roundabout : roundabouts) {
            removed += cleanAround(coverage, roundabout.exteriorEllipse(), roads, cropWindow);
        }
        return removed;
    }

    /**
     * Ramène l'opacité d'un pixel à sa part de chaussée si elle est inférieure.
     *
     * @param coverage Couverture pleine image (modifiée en place).
     * @param roads    Capture « Routes seules ».
     * @param gx       Abscisse globale.
     * @param gy       Ordonnée globale.
     * @return 1 si l'opacité a été réduite, 0 sinon.
     */
    private int keepRoadOnly(CoverageMask coverage, GoogleRoadsImage roads, int gx, int gy) {
        int roadAlpha = Math.round(255.0f * roads.value(gx, gy));
        if (roadAlpha >= coverage.get(gx, gy)) {
            return 0;
        }
        coverage.set(gx, gy, roadAlpha);
        return 1;
    }

    /**
     * Retire les éclats et comble les encoches dans le carré entourant un anneau.
     *
     * @param coverage   Couverture pleine image (modifiée en place).
     * @param ellipse    Ellipse extérieure de l'anneau (repère de la fenêtre).
     * @param roads      Capture « Routes seules ».
     * @param cropWindow Fenêtre de recadrage.
     * @return Nombre de pixels modifiés.
     */
    private int cleanAround(CoverageMask coverage, EllipseModel ellipse, GoogleRoadsImage roads,
                            CropWindow cropWindow) {
        int width = Math.min(coverage.getWidth(), roads.width());
        int height = Math.min(coverage.getHeight(), roads.height());
        double reach = ellipse.a() + MARGIN;
        int x0 = Math.max(0, (int) Math.floor(ellipse.xc() + cropWindow.x0() - reach));
        int y0 = Math.max(0, (int) Math.floor(ellipse.yc() + cropWindow.y0() - reach));
        int x1 = Math.min(width - 1, (int) Math.ceil(ellipse.xc() + cropWindow.x0() + reach));
        int y1 = Math.min(height - 1, (int) Math.ceil(ellipse.yc() + cropWindow.y0() + reach));
        if (x1 <= x0 || y1 <= y0) {
            return 0;
        }
        int w = x1 - x0 + 1;
        int h = y1 - y0 + 1;
        boolean[] land = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                land[y * w + x] = coverage.get(x0 + x, y0 + y) >= VISIBLE_THRESHOLD
                        && roads.value(x0 + x, y0 + y) < LAND_THRESHOLD;
            }
        }
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        int removed = 0;
        for (int start = 0; start < land.length; start++) {
            if (!land[start] || visited[start]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            boolean touchesBorder = false;
            boolean touchesOutside = false;
            while (head < tail) {
                int p = queue[head++];
                int x = p % w;
                int y = p / w;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    touchesBorder = true;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if ((dx == 0 && dy == 0) || nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        if (coverage.get(x0 + nx, y0 + ny) < VISIBLE_THRESHOLD) {
                            touchesOutside = true;
                        }
                        int q = ny * w + nx;
                        if (Math.abs(dx) + Math.abs(dy) == 1 && land[q] && !visited[q]) {
                            visited[q] = true;
                            queue[tail++] = q;
                        }
                    }
                }
            }
            if (touchesBorder || !touchesOutside || tail > MAX_SLIVER_AREA) {
                continue;
            }
            // L'éclat et sa frange anti-crénelée (pixels semi-transparents voisins) ne gardent que la chaussée.
            for (int k = 0; k < tail; k++) {
                int x = queue[k] % w;
                int y = queue[k] / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                            continue;
                        }
                        boolean fringe = coverage.get(x0 + nx, y0 + ny) < VISIBLE_THRESHOLD;
                        if ((dx == 0 && dy == 0) || fringe) {
                            removed += keepRoadOnly(coverage, roads, x0 + nx, y0 + ny);
                        }
                    }
                }
            }
        }
        return removed + fillNotches(coverage, roads, x0, y0, w, h);
    }

    /**
     * Comble (en place) les encoches : petites parcelles de chaussée transparentes, enclavées entre le contour
     * et le terrain extérieur (le raccord d'Hermite a coupé le bord de l'anneau). Une route qui quitte le
     * territoire traverse le bord du carré et n'est donc pas concernée.
     *
     * @param coverage Couverture pleine image (modifiée en place).
     * @param roads    Capture « Routes seules ».
     * @param x0       Abscisse globale du carré.
     * @param y0       Ordonnée globale du carré.
     * @param w        Largeur du carré.
     * @param h        Hauteur du carré.
     * @return Nombre de pixels rendus (plus) opaques.
     */
    private int fillNotches(CoverageMask coverage, GoogleRoadsImage roads, int x0, int y0, int w, int h) {
        boolean[] notch = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                notch[y * w + x] = coverage.get(x0 + x, y0 + y) < VISIBLE_THRESHOLD
                        && roads.value(x0 + x, y0 + y) >= LAND_THRESHOLD;
            }
        }
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        int filled = 0;
        for (int start = 0; start < notch.length; start++) {
            if (!notch[start] || visited[start]) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            boolean touchesBorder = false;
            while (head < tail) {
                int p = queue[head++];
                int x = p % w;
                int y = p / w;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    touchesBorder = true;
                }
                int[] neighbours = {p - 1, p + 1, p - w, p + w};
                boolean[] valid = {x > 0, x < w - 1, y > 0, y < h - 1};
                for (int k = 0; k < neighbours.length; k++) {
                    int q = neighbours[k];
                    if (valid[k] && notch[q] && !visited[q]) {
                        visited[q] = true;
                        queue[tail++] = q;
                    }
                }
            }
            if (touchesBorder || tail > MAX_NOTCH_AREA) {
                continue;
            }
            // L'encoche et sa frange anti-crénelée reprennent la part de chaussée de la capture.
            for (int k = 0; k < tail; k++) {
                int x = queue[k] % w;
                int y = queue[k] / w;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                            filled += raiseToRoad(coverage, roads, x0 + nx, y0 + ny);
                        }
                    }
                }
            }
        }
        return filled;
    }

    /**
     * Porte l'opacité d'un pixel à sa part de chaussée si elle est supérieure.
     *
     * @param coverage Couverture pleine image (modifiée en place).
     * @param roads    Capture « Routes seules ».
     * @param gx       Abscisse globale.
     * @param gy       Ordonnée globale.
     * @return 1 si l'opacité a été augmentée, 0 sinon.
     */
    private int raiseToRoad(CoverageMask coverage, GoogleRoadsImage roads, int gx, int gy) {
        int roadAlpha = Math.round(255.0f * roads.value(gx, gy));
        if (roadAlpha <= coverage.get(gx, gy)) {
            return 0;
        }
        coverage.set(gx, gy, roadAlpha);
        return 1;
    }
}
