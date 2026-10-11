package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Supprime les petites bosses d'un contour le long d'un bord rectiligne.
 * <p>
 * Au débouché d'une rue secondaire, le bord d'un boulevard est interrompu : le contour accroché au bord de
 * route s'y enfonce ou s'en écarte sur quelques dizaines de pixels et forme une bosse. Une portion du contour
 * est remplacée par sa corde lorsque :
 * </p>
 * <ul>
 *     <li>sa longueur d'arc ne dépasse pas {@link #MAX_BUMP_LENGTH} et sa corde mesure au moins {@link #MIN_CHORD} ;</li>
 *     <li>le contour, de part et d'autre, reste aligné sur la droite de la corde sur {@link #ARM_LENGTH}
 *     (à {@link #ARM_TOLERANCE} près) — on est donc sur un bord rectiligne, ni dans un angle ni dans un giratoire ;</li>
 *     <li>la portion s'écarte de la corde d'au moins {@link #MIN_DEVIATION} et d'au plus {@link #MAX_DEVIATION} ;</li>
 *     <li>la corde n'entre dans aucun giratoire et la surface balayée est essentiellement de la chaussée.</li>
 * </ul>
 */
public class StraightEdgeBumpRemover {

    private static final Logger LOGGER = Logger.getLogger(StraightEdgeBumpRemover.class.getName());

    /** Longueur d'arc maximale (px) d'une bosse supprimée. */
    public static final double MAX_BUMP_LENGTH = 90.0;
    /** Longueur minimale (px) de la corde remplaçant une bosse. */
    public static final double MIN_CHORD = 6.0;
    /** Longueur (px) du bord rectiligne exigé de part et d'autre de la bosse. */
    public static final double ARM_LENGTH = 50.0;
    /** Écart maximal (px) entre le bord rectiligne et la droite de la corde. */
    public static final double ARM_TOLERANCE = 1.5;
    /** Écart minimal (px) à la corde pour considérer une bosse. */
    public static final double MIN_DEVIATION = 1.0;
    /** Écart maximal (px) à la corde d'une bosse supprimée. */
    public static final double MAX_DEVIATION = 25.0;
    /** Nombre de pixels de terre toujours tolérés dans la surface balayée par une bosse. */
    public static final int MAX_SWEPT_LAND_PIXELS = 60;
    /** Part de terre tolérée dans la surface balayée par une bosse. */
    public static final double MAX_SWEPT_LAND_FRACTION = 0.45;
    /** Marge (px) en deçà de laquelle deux cordes sont jugées également alignées (la plus longue l'emporte). */
    private static final double ALIGNMENT_EPSILON = 0.05;
    /** Pas (px) des points reconstruits le long de la corde. */
    private static final double CHORD_STEP = 1.0;

    /**
     * Construit un supprimeur sans état.
     */
    public StraightEdgeBumpRemover() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Remplace par leur corde les bosses du contour situées sur un bord rectiligne.
     *
     * @param contour     Contour fermé.
     * @param roadMask    Masque des routes, dans le repère du contour.
     * @param roundabouts Giratoires du réseau, dans le repère du contour.
     * @return Contour redressé (coins hors bosses conservés), ou l'original si aucune bosse n'est trouvée.
     * @throws NullPointerException si un argument est null.
     */
    public SmoothVectorContour remove(SmoothVectorContour contour, BinaryMask roadMask, List<Roundabout> roundabouts) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(roundabouts, "roundabouts ne doit pas être nul.");
        List<PixelPoint> points = contour.points();
        int n = points.size();
        if (n < 8) {
            return contour;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int k = 0; k < n; k++) {
            xs[k] = points.get(k).x();
            ys[k] = points.get(k).y();
        }
        double[] arc = cumulativeArc(xs, ys);
        double perimeter = arc[n];
        if (perimeter < 2.0 * ARM_LENGTH + MAX_BUMP_LENGTH) {
            return contour;
        }

        // 1. Pour chaque début, la corde la mieux alignée sur le bord rectiligne (puis la plus longue).
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < n - 2; i++) {
            int best = -1;
            double bestMisalignment = Double.MAX_VALUE;
            for (int j = i + 2; j < n && arc[j] - arc[i] <= MAX_BUMP_LENGTH; j++) {
                double misalignment = bumpMisalignment(xs, ys, arc, perimeter, i, j);
                if (misalignment <= bestMisalignment + ALIGNMENT_EPSILON) {
                    bestMisalignment = Math.min(bestMisalignment, misalignment);
                    best = j;
                }
            }
            if (best >= 0) {
                candidates.add(new Candidate(i, best, Math.round(bestMisalignment / ALIGNMENT_EPSILON)));
            }
        }
        // 2. Sélection gloutonne des cordes disjointes, les mieux alignées puis les plus longues d'abord.
        candidates.sort(Comparator.comparingLong(Candidate::alignmentRank)
                .thenComparing(Comparator.comparingInt((Candidate c) -> c.end() - c.start()).reversed()));
        boolean[] used = new boolean[n];
        List<int[]> spans = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (overlaps(used, candidate.start(), candidate.end())
                    || crossesRoundabout(xs, ys, candidate.start(), candidate.end(), roundabouts)
                    || !sweepsRoadOnly(xs, ys, candidate.start(), candidate.end(), roadMask)) {
                continue;
            }
            for (int k = candidate.start(); k <= candidate.end(); k++) {
                used[k] = true;
            }
            spans.add(new int[]{candidate.start(), candidate.end()});
        }
        spans.sort(Comparator.comparingInt(span -> span[0]));
        if (spans.isEmpty()) {
            return contour;
        }
        return rebuild(contour, spans);
    }

    /**
     * Teste si une portion chevauche une portion déjà retenue (les extrémités peuvent être partagées).
     *
     * @param used  Points déjà couverts par une portion retenue.
     * @param start Début de la portion.
     * @param end   Fin de la portion.
     * @return true en cas de chevauchement.
     */
    private boolean overlaps(boolean[] used, int start, int end) {
        for (int k = start + 1; k < end; k++) {
            if (used[k]) {
                return true;
            }
        }
        return used[start] && used[end];
    }

    /**
     * Teste si la portion [i, j] est une bosse sur un bord rectiligne et mesure l'alignement de sa corde.
     *
     * @param xs        Abscisses.
     * @param ys        Ordonnées.
     * @param arc       Abscisses curvilignes cumulées.
     * @param perimeter Périmètre.
     * @param i         Début de la portion.
     * @param j         Fin de la portion.
     * @return Écart maximal (px) des bords rectilignes à la droite de la corde, ou l'infini si la portion
     *         n'est pas une bosse.
     */
    private double bumpMisalignment(double[] xs, double[] ys, double[] arc, double perimeter, int i, int j) {
        int n = xs.length;
        double dx = xs[j] - xs[i];
        double dy = ys[j] - ys[i];
        double chord = Math.hypot(dx, dy);
        if (chord < MIN_CHORD) {
            return Double.POSITIVE_INFINITY;
        }
        double ux = dx / chord;
        double uy = dy / chord;
        // Bras amont : points précédant i sur ARM_LENGTH, alignés et situés avant le début de la corde.
        double misalignment = 0.0;
        double travelled = 0.0;
        int k = i;
        while (travelled < ARM_LENGTH) {
            int prev = Math.floorMod(k - 1, n);
            travelled += Math.hypot(xs[k] - xs[prev], ys[k] - ys[prev]);
            k = prev;
            double rx = xs[k] - xs[i];
            double ry = ys[k] - ys[i];
            misalignment = Math.max(misalignment, Math.abs(rx * uy - ry * ux));
            if (Math.abs(rx * uy - ry * ux) > ARM_TOLERANCE || rx * ux + ry * uy > ARM_TOLERANCE) {
                return Double.POSITIVE_INFINITY;
            }
        }
        // Bras aval : points suivant j sur ARM_LENGTH, alignés et situés après la fin de la corde.
        travelled = 0.0;
        k = j;
        while (travelled < ARM_LENGTH) {
            int next = (k + 1) % n;
            travelled += Math.hypot(xs[next] - xs[k], ys[next] - ys[k]);
            k = next;
            double rx = xs[k] - xs[j];
            double ry = ys[k] - ys[j];
            misalignment = Math.max(misalignment, Math.abs(rx * uy - ry * ux));
            if (Math.abs(rx * uy - ry * ux) > ARM_TOLERANCE || rx * ux + ry * uy < -ARM_TOLERANCE) {
                return Double.POSITIVE_INFINITY;
            }
        }
        double maxDeviation = 0.0;
        for (int m = i + 1; m < j; m++) {
            double rx = xs[m] - xs[i];
            double ry = ys[m] - ys[i];
            maxDeviation = Math.max(maxDeviation, Math.abs(rx * uy - ry * ux));
            if (maxDeviation > MAX_DEVIATION) {
                return Double.POSITIVE_INFINITY;
            }
        }
        return maxDeviation >= MIN_DEVIATION && arc[j] - arc[i] < perimeter / 4.0
                ? misalignment : Double.POSITIVE_INFINITY;
    }

    /**
     * Vérifie que la surface balayée entre la bosse et sa corde est essentiellement de la chaussée : une bosse
     * au débouché d'une rue ne couvre que la bouche de cette rue, pas un pâté de maisons.
     *
     * @param xs       Abscisses.
     * @param ys       Ordonnées.
     * @param i        Début de la portion.
     * @param j        Fin de la portion.
     * @param roadMask Masque des routes (repère du contour).
     * @return true si la corde peut remplacer la portion.
     */
    private boolean sweepsRoadOnly(double[] xs, double[] ys, int i, int j, BinaryMask roadMask) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int k = i; k <= j; k++) {
            minX = Math.min(minX, xs[k]);
            minY = Math.min(minY, ys[k]);
            maxX = Math.max(maxX, xs[k]);
            maxY = Math.max(maxY, ys[k]);
        }
        int landPixels = 0;
        int area = 0;
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
                if (!inside(xs, ys, i, j, x + 0.5, y + 0.5)) {
                    continue;
                }
                area++;
                if (roadMask.isInBounds(x, y) && !roadMask.get(x, y)) {
                    landPixels++;
                }
            }
        }
        return landPixels <= MAX_SWEPT_LAND_PIXELS || landPixels <= MAX_SWEPT_LAND_FRACTION * area;
    }

    /**
     * Teste si la corde [i, j] entre dans l'anneau d'un giratoire (ellipse extérieure de sa chaussée).
     *
     * @param xs          Abscisses.
     * @param ys          Ordonnées.
     * @param i           Début de la portion.
     * @param j           Fin de la portion.
     * @param roundabouts Giratoires détectés (repère du contour).
     * @return true si la corde traverse un giratoire.
     */
    private boolean crossesRoundabout(double[] xs, double[] ys, int i, int j, List<Roundabout> roundabouts) {
        double length = Math.hypot(xs[j] - xs[i], ys[j] - ys[i]);
        int steps = Math.max(1, (int) Math.ceil(length));
        for (Roundabout roundabout : roundabouts) {
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                PixelPoint unit = roundabout.exteriorEllipse().toUnitCircle(
                        new PixelPoint(xs[i] + t * (xs[j] - xs[i]), ys[i] + t * (ys[j] - ys[i])));
                if (Math.hypot(unit.x(), unit.y()) < 1.0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Test pair-impair d'appartenance d'un point au polygone formé par la portion [i, j] fermée par sa corde.
     *
     * @param xs Abscisses.
     * @param ys Ordonnées.
     * @param i  Début de la portion.
     * @param j  Fin de la portion.
     * @param px Abscisse testée.
     * @param py Ordonnée testée.
     * @return true si le point est intérieur.
     */
    private boolean inside(double[] xs, double[] ys, int i, int j, double px, double py) {
        boolean in = false;
        for (int k = i, prev = j; k <= j; prev = k, k++) {
            if ((ys[k] > py) != (ys[prev] > py)
                    && px < (xs[prev] - xs[k]) * (py - ys[k]) / (ys[prev] - ys[k]) + xs[k]) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * Reconstruit le contour en remplaçant chaque portion par sa corde échantillonnée.
     *
     * @param contour Contour d'origine.
     * @param spans   Portions [début, fin] triées et disjointes.
     * @return Nouveau contour.
     */
    private SmoothVectorContour rebuild(SmoothVectorContour contour, List<int[]> spans) {
        List<PixelPoint> points = contour.points();
        int n = points.size();
        int[] newIndex = new int[n];
        List<PixelPoint> rebuilt = new ArrayList<>(n);
        int spanIndex = 0;
        int k = 0;
        while (k < n) {
            if (spanIndex < spans.size() && spans.get(spanIndex)[0] == k) {
                int end = spans.get(spanIndex)[1];
                PixelPoint a = points.get(k);
                PixelPoint b = points.get(end);
                newIndex[k] = rebuilt.size();
                rebuilt.add(a);
                double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
                int steps = (int) Math.ceil(length / CHORD_STEP);
                for (int s = 1; s < steps; s++) {
                    double t = (double) s / steps;
                    rebuilt.add(new PixelPoint(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y())));
                }
                for (int m = k + 1; m < end; m++) {
                    newIndex[m] = -1;
                }
                spanIndex++;
                k = end;
                continue;
            }
            newIndex[k] = rebuilt.size();
            rebuilt.add(points.get(k));
            k++;
        }
        List<Integer> corners = new ArrayList<>();
        for (int corner : contour.cornerIndices()) {
            if (corner >= 0 && corner < n && newIndex[corner] >= 0) {
                corners.add(newIndex[corner]);
            }
        }
        int count = spans.size();
        LOGGER.info(() -> String.format("[Bosses] %d bosse(s) supprimée(s) le long de bords rectilignes.", count));
        return new SmoothVectorContour(rebuilt, corners, contour.width(), contour.height(),
                contour.cropWindow(), contour.substitutedRoundabouts());
    }

    /**
     * Portion candidate au remplacement par sa corde.
     *
     * @param start         Indice de début.
     * @param end           Indice de fin.
     * @param alignmentRank Écart d'alignement des bords rectilignes, en multiples de {@link #ALIGNMENT_EPSILON}.
     */
    private record Candidate(int start, int end, long alignmentRank) {
    }

    /**
     * Abscisses curvilignes cumulées ; la dernière case contient le périmètre.
     *
     * @param xs Abscisses.
     * @param ys Ordonnées.
     * @return Tableau de taille n + 1.
     */
    private double[] cumulativeArc(double[] xs, double[] ys) {
        int n = xs.length;
        double[] arc = new double[n + 1];
        for (int k = 1; k <= n; k++) {
            int prev = k - 1;
            int cur = k % n;
            arc[k] = arc[k - 1] + Math.hypot(xs[cur] - xs[prev], ys[cur] - ys[prev]);
        }
        return arc;
    }
}
