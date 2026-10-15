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
 * <p>
 * La capture est crénelée (marches d'escalier d'un pixel) : l'opacité donnée aux pixels corrigés est la couverture
 * de l'iso-ligne 0,5 de la capture lissée (gaussienne de {@value #SMOOTHING_SIGMA} px), mesurée sur une grille
 * {@value #SUPERSAMPLING} fois plus fine. Le bord corrigé est ainsi aussi lisse que le reste du contour vectoriel.
 * </p>
 */
public final class RoundaboutSliverCleaner {

    /** Marge (px) autour de l'ellipse extérieure de l'anneau où les éclats sont recherchés. */
    public static final double MARGIN = 20.0;
    /** Surface maximale (px) d'un éclat de terrain retiré. */
    public static final int MAX_SLIVER_AREA = 300;
    /** Surface maximale (px) d'une encoche de chaussée comblée. */
    public static final int MAX_NOTCH_AREA = 150;
    /**
     * Surface minimale (px) d'une encoche comblée : en deçà, l'écart avec la capture est de l'ordre de son
     * crénelage, et la combler ferait saillir ses marches sur le bord lisse du contour (les éclats de terrain blanc
     * sont, eux, toujours retirés).
     */
    public static final int MIN_NOTCH_AREA = 12;
    /** Part de chaussée en deçà de laquelle un pixel est du terrain. */
    private static final float LAND_THRESHOLD = 0.5f;
    /** Opacité en deçà de laquelle un pixel est hors du territoire. */
    private static final int VISIBLE_THRESHOLD = 128;
    /** Écart type (px) du lissage de la capture avant mesure de la couverture. */
    public static final double SMOOTHING_SIGMA = 1.2;
    /** Facteur de suréchantillonnage de la mesure de couverture. */
    public static final int SUPERSAMPLING = 4;

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
     * Ramène l'opacité d'un pixel à sa couverture de chaussée lissée si elle est inférieure.
     *
     * @param coverage  Couverture pleine image (modifiée en place).
     * @param roadAlpha Couverture de chaussée lissée du pixel (0 à 255).
     * @param gx        Abscisse globale.
     * @param gy        Ordonnée globale.
     * @return 1 si l'opacité a été réduite, 0 sinon.
     */
    private int keepRoadOnly(CoverageMask coverage, int roadAlpha, int gx, int gy) {
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
        int[] smooth = smoothRoadCoverage(roads, x0, y0, w, h, width, height);
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
                            removed += keepRoadOnly(coverage, smooth[ny * w + nx], x0 + nx, y0 + ny);
                        }
                    }
                }
            }
        }
        return removed + fillNotches(coverage, roads, smooth, x0, y0, w, h);
    }

    /**
     * Couverture (0 à 255) de chaque pixel du carré par la chaussée lissée : iso-ligne 0,5 de la capture passée
     * dans une gaussienne de {@value #SMOOTHING_SIGMA} px, mesurée sur {@value #SUPERSAMPLING} x
     * {@value #SUPERSAMPLING} échantillons par pixel (interpolation bilinéaire).
     *
     * @param roads  Capture « Routes seules ».
     * @param x0     Abscisse globale du carré.
     * @param y0     Ordonnée globale du carré.
     * @param w      Largeur du carré.
     * @param h      Hauteur du carré.
     * @param width  Largeur utile de l'image.
     * @param height Hauteur utile de l'image.
     * @return Couvertures, ligne par ligne.
     */
    private int[] smoothRoadCoverage(GoogleRoadsImage roads, int x0, int y0, int w, int h, int width, int height) {
        int pad = (int) Math.ceil(3.0 * SMOOTHING_SIGMA) + 1;
        int pw = w + 2 * pad;
        int ph = h + 2 * pad;
        float[] field = new float[pw * ph];
        for (int y = 0; y < ph; y++) {
            int gy = Math.max(0, Math.min(height - 1, y0 - pad + y));
            for (int x = 0; x < pw; x++) {
                int gx = Math.max(0, Math.min(width - 1, x0 - pad + x));
                field[y * pw + x] = roads.value(gx, gy);
            }
        }
        float[] smooth = gaussian(field, pw, ph);
        int[] coverage = new int[w * h];
        double step = 1.0 / SUPERSAMPLING;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int inside = 0;
                for (int sy = 0; sy < SUPERSAMPLING; sy++) {
                    for (int sx = 0; sx < SUPERSAMPLING; sx++) {
                        double fx = pad + x + (sx + 0.5) * step - 0.5;
                        double fy = pad + y + (sy + 0.5) * step - 0.5;
                        inside += bilinear(smooth, pw, ph, fx, fy) >= LAND_THRESHOLD ? 1 : 0;
                    }
                }
                coverage[y * w + x] = Math.round(255.0f * inside / (SUPERSAMPLING * SUPERSAMPLING));
            }
        }
        return coverage;
    }

    /**
     * Flou gaussien séparable d'écart type {@value #SMOOTHING_SIGMA} px (bords prolongés).
     *
     * @param field Valeurs, ligne par ligne.
     * @param w     Largeur.
     * @param h     Hauteur.
     * @return Valeurs lissées.
     */
    private static float[] gaussian(float[] field, int w, int h) {
        int radius = (int) Math.ceil(3.0 * SMOOTHING_SIGMA);
        float[] kernel = new float[2 * radius + 1];
        float sum = 0.0f;
        for (int k = -radius; k <= radius; k++) {
            kernel[k + radius] = (float) Math.exp(-0.5 * k * k / (SMOOTHING_SIGMA * SMOOTHING_SIGMA));
            sum += kernel[k + radius];
        }
        for (int k = 0; k < kernel.length; k++) {
            kernel[k] /= sum;
        }
        float[] rows = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float acc = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    acc += kernel[k + radius] * field[y * w + Math.max(0, Math.min(w - 1, x + k))];
                }
                rows[y * w + x] = acc;
            }
        }
        float[] out = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float acc = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    acc += kernel[k + radius] * rows[Math.max(0, Math.min(h - 1, y + k)) * w + x];
                }
                out[y * w + x] = acc;
            }
        }
        return out;
    }

    /**
     * Interpolation bilinéaire (coordonnées de centres de pixels, bords prolongés).
     *
     * @param field Valeurs, ligne par ligne.
     * @param w     Largeur.
     * @param h     Hauteur.
     * @param x     Abscisse.
     * @param y     Ordonnée.
     * @return Valeur interpolée.
     */
    private static float bilinear(float[] field, int w, int h, double x, double y) {
        int ix = (int) Math.floor(x);
        int iy = (int) Math.floor(y);
        float tx = (float) (x - ix);
        float ty = (float) (y - iy);
        int xa = Math.max(0, Math.min(w - 1, ix));
        int xb = Math.max(0, Math.min(w - 1, ix + 1));
        int ya = Math.max(0, Math.min(h - 1, iy));
        int yb = Math.max(0, Math.min(h - 1, iy + 1));
        float top = field[ya * w + xa] * (1 - tx) + field[ya * w + xb] * tx;
        float bottom = field[yb * w + xa] * (1 - tx) + field[yb * w + xb] * tx;
        return top * (1 - ty) + bottom * ty;
    }

    /**
     * Comble (en place) les encoches : petites parcelles de chaussée transparentes, enclavées entre le contour
     * et le terrain extérieur (le raccord d'Hermite a coupé le bord de l'anneau). Une route qui quitte le
     * territoire traverse le bord du carré et n'est donc pas concernée.
     *
     * @param coverage Couverture pleine image (modifiée en place).
     * @param roads    Capture « Routes seules ».
     * @param smooth   Couverture de chaussée lissée du carré (0 à 255).
     * @param x0       Abscisse globale du carré.
     * @param y0       Ordonnée globale du carré.
     * @param w        Largeur du carré.
     * @param h        Hauteur du carré.
     * @return Nombre de pixels rendus (plus) opaques.
     */
    private int fillNotches(CoverageMask coverage, GoogleRoadsImage roads, int[] smooth, int x0, int y0, int w,
                            int h) {
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
            if (touchesBorder || tail > MAX_NOTCH_AREA || tail < MIN_NOTCH_AREA) {
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
                            filled += raiseToRoad(coverage, smooth[ny * w + nx], x0 + nx, y0 + ny);
                        }
                    }
                }
            }
        }
        return filled;
    }

    /**
     * Porte l'opacité d'un pixel à sa couverture de chaussée lissée si elle est supérieure.
     *
     * @param coverage  Couverture pleine image (modifiée en place).
     * @param roadAlpha Couverture de chaussée lissée du pixel (0 à 255).
     * @param gx        Abscisse globale.
     * @param gy        Ordonnée globale.
     * @return 1 si l'opacité a été augmentée, 0 sinon.
     */
    private int raiseToRoad(CoverageMask coverage, int roadAlpha, int gx, int gy) {
        if (roadAlpha <= coverage.get(gx, gy)) {
            return 0;
        }
        coverage.set(gx, gy, roadAlpha);
        return 1;
    }
}
