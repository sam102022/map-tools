package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.contour.SubpixelContourExtractor;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Rectifie le bord d'une zone en grands segments parallèles au tracé de l'utilisateur (« lasso polygonal »).
 * <p>
 * Le bord intérieur des routes est interrompu par les débouchés de rues, entrées de parking, raccords de
 * contre-allée et terre-pleins, qui font onduler un bord calé pixel par pixel. Or l'utilisateur trace ses zones
 * en lignes droites le long des routes : chaque côté du tracé (après simplification) est donc reporté vers
 * l'intérieur de la distance médiane qui le sépare du masque de zone, mesurée le long de sa normale. Le
 * polygone ainsi obtenu longe le bord de chaussée en lignes droites ; la médiane ignore les décrochements
 * locaux. Le masque final est l'intersection de ce polygone et du masque de zone dont les renfoncements de moins
 * de 2 x {@value #BUMP_RADIUS} px sont comblés (les giratoires exclus, plus larges, restent exclus).
 */
public final class TraceAlignedOutline {

    /** Tolérance (px) de simplification du tracé en côtés rectilignes. */
    public static final double TRACE_TOLERANCE = 2.5;
    /** Distance maximale (px) recherchée entre un côté du tracé et le masque de zone. */
    public static final double MAX_OFFSET = 40.0;
    /** Rayon (px) de comblement des renfoncements du masque de zone. */
    public static final double BUMP_RADIUS = 20.0;
    /** Pas (px) des stations de mesure le long d'un côté. */
    private static final double STATION_STEP = 2.0;
    /** Pas (px) de la recherche le long de la normale. */
    private static final double PROBE_STEP = 0.5;
    /** Nombre minimal de mesures pour reporter un côté. */
    private static final int MIN_HITS = 3;

    private final SubpixelContourExtractor extractor = new SubpixelContourExtractor();
    private final PolygonRasterizer rasterizer = new PolygonRasterizer();
    private final DiskMorphology morphology;

    /**
     * Initialise la rectification avec la morphologie par défaut.
     */
    public TraceAlignedOutline() {
        this(new DiskMorphology());
    }

    /**
     * Initialise la rectification avec une morphologie injectée.
     *
     * @param morphology Morphologie par disques (non nulle).
     */
    public TraceAlignedOutline(DiskMorphology morphology) {
        this.morphology = Objects.requireNonNull(morphology, "morphology ne doit pas être nulle.");
    }

    /**
     * Rectifie le masque de zone.
     *
     * @param zoneMask Masque de zone (bord de chaussée intérieur, rues intérieures comprises).
     * @param trace    Tracé de la zone rastérisé (même repère).
     * @return Masque rectifié (une composante, sans trou), ou le masque d'origine si le tracé est inexploitable.
     */
    public BinaryMask align(BinaryMask zoneMask, BinaryMask trace) {
        Objects.requireNonNull(zoneMask, "zoneMask ne doit pas être nul.");
        Objects.requireNonNull(trace, "trace ne doit pas être nul.");
        List<PixelPoint> vertices = simplifyClosed(extractor.extract(trace, 1.0), TRACE_TOLERANCE);
        if (vertices.size() < 3) {
            return zoneMask;
        }
        List<PixelPoint> outline = offsetPolygon(vertices, zoneMask, trace);
        if (outline.size() < 3) {
            return zoneMask;
        }
        BinaryMask polygon = rasterizer.rasterize(zoneMask.getWidth(), zoneMask.getHeight(), List.of(outline),
                List.of()).mask();
        BinaryMask filled = morphology.close(zoneMask, BUMP_RADIUS);
        return MaskComponents.fillHoles(MaskComponents.largest(polygon.and(filled)));
    }

    /**
     * Reporte chaque côté du tracé vers l'intérieur de sa distance médiane au masque de zone, puis relie les
     * côtés reportés par l'intersection des droites consécutives.
     *
     * @param vertices Sommets du tracé simplifié.
     * @param zoneMask Masque de zone.
     * @param trace    Tracé rastérisé (pour orienter les normales vers l'intérieur).
     * @return Sommets du polygone reporté.
     */
    List<PixelPoint> offsetPolygon(List<PixelPoint> vertices, BinaryMask zoneMask, BinaryMask trace) {
        int n = vertices.size();
        double[][] lines = new double[n][];
        for (int i = 0; i < n; i++) {
            PixelPoint a = vertices.get(i);
            PixelPoint b = vertices.get((i + 1) % n);
            double len = Math.hypot(b.x() - a.x(), b.y() - a.y());
            if (len < 1e-6) {
                continue;
            }
            double ux = (b.x() - a.x()) / len;
            double uy = (b.y() - a.y()) / len;
            double nx = -uy;
            double ny = ux;
            double mx = (a.x() + b.x()) / 2.0;
            double my = (a.y() + b.y()) / 2.0;
            if (!inside(trace, mx + 3.0 * nx, my + 3.0 * ny)) {
                nx = -nx;
                ny = -ny;
            }
            double d = medianOffset(a, ux, uy, nx, ny, len, zoneMask);
            lines[i] = new double[]{a.x() + d * nx, a.y() + d * ny, ux, uy, d};
        }
        List<PixelPoint> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double[] prev = lines[Math.floorMod(i - 1, n)];
            double[] next = lines[i];
            if (prev == null || next == null) {
                continue;
            }
            out.add(junction(prev, next, vertices.get(i)));
        }
        return out;
    }

    /**
     * Distance médiane, le long de la normale intérieure, entre un côté du tracé et le masque de zone.
     *
     * @param a        Origine du côté.
     * @param ux       Direction du côté (x).
     * @param uy       Direction du côté (y).
     * @param nx       Normale intérieure (x).
     * @param ny       Normale intérieure (y).
     * @param len      Longueur du côté.
     * @param zoneMask Masque de zone.
     * @return Distance médiane (0 si trop peu de mesures).
     */
    private double medianOffset(PixelPoint a, double ux, double uy, double nx, double ny, double len,
                                BinaryMask zoneMask) {
        int stations = (int) Math.floor(len / STATION_STEP);
        double[] hits = new double[stations + 1];
        int count = 0;
        for (int s = 0; s <= stations; s++) {
            double px = a.x() + s * STATION_STEP * ux;
            double py = a.y() + s * STATION_STEP * uy;
            for (double t = 0.0; t <= MAX_OFFSET; t += PROBE_STEP) {
                if (inside(zoneMask, px + t * nx, py + t * ny)) {
                    hits[count++] = t;
                    break;
                }
            }
        }
        if (count < MIN_HITS) {
            return 0.0;
        }
        Arrays.sort(hits, 0, count);
        return count % 2 == 1 ? hits[count / 2] : 0.5 * (hits[count / 2 - 1] + hits[count / 2]);
    }

    /**
     * Sommet reliant deux côtés reportés : intersection des deux droites, ou point médian des extrémités
     * reportées si les droites sont presque parallèles ou si l'intersection s'éloigne trop du sommet d'origine.
     *
     * @param prev     Droite du côté précédent {x, y, ux, uy, d}.
     * @param next     Droite du côté suivant.
     * @param original Sommet d'origine du tracé.
     * @return Sommet du polygone reporté.
     */
    private static PixelPoint junction(double[] prev, double[] next, PixelPoint original) {
        double cross = prev[2] * next[3] - prev[3] * next[2];
        double limit = 3.0 * Math.max(Math.max(prev[4], next[4]), 2.0);
        if (Math.abs(cross) > 1e-3) {
            double dx = next[0] - prev[0];
            double dy = next[1] - prev[1];
            double t = (dx * next[3] - dy * next[2]) / cross;
            PixelPoint p = new PixelPoint(prev[0] + t * prev[2], prev[1] + t * prev[3]);
            if (Math.hypot(p.x() - original.x(), p.y() - original.y()) <= limit) {
                return p;
            }
        }
        // Projection du sommet d'origine sur chaque droite reportée, puis milieu.
        double[] pa = project(prev, original);
        double[] pb = project(next, original);
        return new PixelPoint((pa[0] + pb[0]) / 2.0, (pa[1] + pb[1]) / 2.0);
    }

    /**
     * Projection orthogonale d'un point sur une droite {x, y, ux, uy, d}.
     *
     * @param line Droite.
     * @param p    Point.
     * @return Coordonnées projetées.
     */
    private static double[] project(double[] line, PixelPoint p) {
        double t = (p.x() - line[0]) * line[2] + (p.y() - line[1]) * line[3];
        return new double[]{line[0] + t * line[2], line[1] + t * line[3]};
    }

    /**
     * Teste l'appartenance d'un point (coordonnées réelles) à un masque, hors masque à l'extérieur de l'image.
     *
     * @param mask Masque.
     * @param x    Abscisse.
     * @param y    Ordonnée.
     * @return true si le pixel le plus proche est dans le masque.
     */
    private static boolean inside(BinaryMask mask, double x, double y) {
        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        return ix >= 0 && iy >= 0 && ix < mask.getWidth() && iy < mask.getHeight() && mask.get(ix, iy);
    }

    /**
     * Simplification de Douglas-Peucker d'un contour fermé : les deux points les plus éloignés servent d'ancres.
     *
     * @param ring      Contour fermé.
     * @param tolerance Écart maximal (px).
     * @return Sommets conservés, dans l'ordre du contour.
     */
    static List<PixelPoint> simplifyClosed(List<PixelPoint> ring, double tolerance) {
        int n = ring.size();
        if (n < 4) {
            return ring;
        }
        int far = 0;
        double best = -1.0;
        for (int i = 0; i < n; i++) {
            double d = Math.hypot(ring.get(i).x() - ring.get(0).x(), ring.get(i).y() - ring.get(0).y());
            if (d > best) {
                best = d;
                far = i;
            }
        }
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[far] = true;
        douglasPeucker(ring, 0, far, tolerance, keep);
        douglasPeucker(ring, far, n, tolerance, keep);
        List<PixelPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out.add(ring.get(i));
            }
        }
        return out;
    }

    /**
     * Étape récursive (pile explicite) de Douglas-Peucker entre deux indices (l'indice n désigne le point 0).
     *
     * @param ring      Contour.
     * @param start     Indice de début.
     * @param end       Indice de fin (exclu de la recherche ; n = retour au point 0).
     * @param tolerance Écart maximal.
     * @param keep      Marqueurs des sommets conservés.
     */
    private static void douglasPeucker(List<PixelPoint> ring, int start, int end, double tolerance, boolean[] keep) {
        int n = ring.size();
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[]{start, end});
        while (!stack.isEmpty()) {
            int[] seg = stack.remove(stack.size() - 1);
            PixelPoint a = ring.get(seg[0] % n);
            PixelPoint b = ring.get(seg[1] % n);
            double len = Math.hypot(b.x() - a.x(), b.y() - a.y());
            int idx = -1;
            double max = tolerance;
            for (int i = seg[0] + 1; i < seg[1]; i++) {
                PixelPoint p = ring.get(i % n);
                double d = len < 1e-9 ? Math.hypot(p.x() - a.x(), p.y() - a.y())
                        : Math.abs((b.x() - a.x()) * (a.y() - p.y()) - (a.x() - p.x()) * (b.y() - a.y())) / len;
                if (d > max) {
                    max = d;
                    idx = i;
                }
            }
            if (idx >= 0) {
                keep[idx % n] = true;
                stack.add(new int[]{seg[0], idx});
                stack.add(new int[]{idx, seg[1]});
            }
        }
    }
}
