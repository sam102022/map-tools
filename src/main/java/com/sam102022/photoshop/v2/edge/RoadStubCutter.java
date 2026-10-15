package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Coupe les amorces de rues qui dépassent du territoire à un sommet du tracé.
 * <p>
 * Là où un sommet du tracé tombe un peu au-delà d'un carrefour, le contour remonte dans cette rue sur quelques
 * mètres puis la traverse : une petite dent dépasse au coin du territoire. Une portion du contour est remplacée par
 * sa corde lorsque :
 * </p>
 * <ul>
 *     <li>sa longueur d'arc ne dépasse pas {@link #MAX_STUB_LENGTH}, sa corde mesure au moins {@link #MIN_CHORD} et
 *     elle dépasse vers l'extérieur du territoire de {@link #MIN_DEPTH} à {@link #MAX_DEPTH} px ;</li>
 *     <li>le contour est rectiligne sur {@link #ARM_LENGTH} px de part et d'autre (deux bords de route formant un
 *     coin) et ce coin est à moins de {@link #MAX_VERTEX_DISTANCE} px d'un sommet du tracé de l'utilisateur ;</li>
 *     <li>la portion traverse une chaussée (au moins {@link #MIN_CROSSING_POINTS} points en pleine chaussée) :
 *     c'est la coupe de la rue, et non un arrondi de trottoir qui longe le bord ;</li>
 *     <li>la surface retirée est de la chaussée (au plus {@link #MAX_LAND_FRACTION} de terre) et la corde
 *     n'entre dans aucun giratoire.</li>
 * </ul>
 */
public class RoadStubCutter {

    private static final Logger LOGGER = Logger.getLogger(RoadStubCutter.class.getName());

    /** Longueur d'arc maximale (px) d'une amorce coupée. */
    public static final double MAX_STUB_LENGTH = 140.0;
    /** Longueur minimale (px) de la corde. */
    public static final double MIN_CHORD = 6.0;
    /** Dépassement minimal (px) vers l'extérieur. */
    public static final double MIN_DEPTH = 3.0;
    /** Dépassement maximal (px) vers l'extérieur. */
    public static final double MAX_DEPTH = 30.0;
    /** Longueur (px) du bord rectiligne exigé de part et d'autre. */
    public static final double ARM_LENGTH = 20.0;
    /** Écart maximal (px) d'un bord à sa droite. */
    public static final double ARM_TOLERANCE = 1.5;
    /** Nombre minimal de points du contour en pleine chaussée (coupe d'une rue). */
    public static final int MIN_CROSSING_POINTS = 3;
    /** Part maximale de terre dans la surface retirée. */
    public static final double MAX_LAND_FRACTION = 0.1;
    /** Rayon (px) du voisinage entièrement en chaussée d'un point « en pleine chaussée ». */
    private static final int CROSSING_RADIUS = 2;
    /** Sinus minimal de l'angle entre les deux bords (en deçà, ils sont alignés : cas des bosses). */
    public static final double MIN_CORNER_SINE = 0.7;
    /** Distance maximale (px) entre le coin et un sommet du tracé de l'utilisateur. */
    public static final double MAX_VERTEX_DISTANCE = 30.0;
    /** Corde maximale (px) d'une amorce de rue latérale sur un bord rectiligne. */
    public static final double MAX_SIDE_CHORD = 40.0;
    /** Dépassement maximal (px) d'une amorce de rue latérale. */
    public static final double MAX_SIDE_DEPTH = 15.0;
    /** Distance maximale (px) entre une amorce latérale et un sommet du tracé. */
    public static final double MAX_SIDE_VERTEX_DISTANCE = 60.0;
    /** Surface maximale (px) retirée. */
    public static final int MAX_REMOVED_AREA = 800;
    /** Marge (px) autour des giratoires où rien n'est coupé. */
    public static final double ROUNDABOUT_MARGIN = 6.0;
    /** Pas (px) des points reconstruits le long de la corde. */
    private static final double CHORD_STEP = 1.0;

    /**
     * Construit un coupeur sans état.
     */
    public RoadStubCutter() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Coupe les amorces de rues du contour.
     *
     * @param contour     Contour fermé.
     * @param roadMask    Masque des routes, dans le repère du contour.
     * @param roundabouts Giratoires du réseau, dans le repère du contour.
     * @param traceVertices Sommets du tracé de l'utilisateur, dans le repère du contour : seuls les coins situés à
     *                    moins de {@link #MAX_VERTEX_DISTANCE} px d'un sommet sont redressés.
     * @return Contour corrigé, ou l'original si aucune amorce n'est trouvée.
     * @throws NullPointerException si un argument est null.
     */
    public SmoothVectorContour cut(SmoothVectorContour contour, BinaryMask roadMask, List<Roundabout> roundabouts,
                                   List<PixelPoint> traceVertices) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(roundabouts, "roundabouts ne doit pas être nul.");
        Objects.requireNonNull(traceVertices, "traceVertices ne doit pas être nul.");
        List<PixelPoint> points = contour.points();
        int n = points.size();
        if (n < 8) {
            return contour;
        }
        double[] xs = new double[n];
        double[] ys = new double[n];
        double signedArea = 0.0;
        for (int k = 0; k < n; k++) {
            xs[k] = points.get(k).x();
            ys[k] = points.get(k).y();
        }
        for (int k = 0; k < n; k++) {
            int next = (k + 1) % n;
            signedArea += xs[k] * ys[next] - xs[next] * ys[k];
        }
        // Côté extérieur de la corde (a -> b) : signe du produit vectoriel des points dépassant vers l'extérieur.
        double outside = signedArea > 0 ? 1.0 : -1.0;
        double[] segment = new double[n];
        double[] arc = new double[n + 1];
        for (int k = 0; k < n; k++) {
            int prev = Math.floorMod(k - 1, n);
            segment[k] = Math.hypot(xs[k] - xs[prev], ys[k] - ys[prev]);
        }
        for (int k = 1; k <= n; k++) {
            arc[k] = arc[k - 1] + segment[k % n];
        }
        if (arc[n] < 2.0 * ARM_LENGTH + MAX_STUB_LENGTH) {
            return contour;
        }
        boolean[] crossing = new boolean[n];
        for (int k = 0; k < n; k++) {
            crossing[k] = insideRoad(roadMask, xs[k], ys[k]);
        }
        int[] armBefore = new int[n];
        int[] armAfter = new int[n];
        for (int k = 0; k < n; k++) {
            armBefore[k] = straightArm(xs, ys, segment, k, -1);
            armAfter[k] = straightArm(xs, ys, segment, k, 1);
        }

        List<double[]> spans = new ArrayList<>();
        int i = 0;
        while (i < n - 2) {
            double[] best = null;
            if (armBefore[i] >= 0) {
                int crossings = 0;
                for (int j = i + 1; j < n && arc[j] - arc[i] <= MAX_STUB_LENGTH; j++) {
                    if (crossing[j]) {
                        crossings++;
                    }
                    if (crossings >= MIN_CROSSING_POINTS && armAfter[j] >= 0) {
                        double[] corner = stubCorner(xs, ys, i, j, armBefore[i], armAfter[j], outside, roadMask,
                                roundabouts, traceVertices);
                        if (corner == null) {
                            corner = sideStreetStub(xs, ys, i, j, armBefore[i], armAfter[j], outside, roadMask,
                                    roundabouts, traceVertices);
                        }
                        if (corner != null) {
                            best = new double[]{i, j, corner[0], corner[1]};
                            break;
                        }
                    }
                }
            }
            if (best == null) {
                i++;
            } else {
                spans.add(best);
                i = (int) best[1];
            }
        }
        if (spans.isEmpty()) {
            return contour;
        }
        int count = spans.size();
        LOGGER.info(() -> String.format("[Amorces] %d amorce(s) de rue coupée(s) aux angles du territoire.", count));
        return rebuild(contour, spans);
    }

    /**
     * Teste si le contour est rectiligne sur {@link #ARM_LENGTH} avant (sens -1) ou après (sens +1) le point k.
     *
     * @param xs        Abscisses.
     * @param ys        Ordonnées.
     * @param segment   Longueur du segment aboutissant à chaque point.
     * @param k         Point de départ.
     * @param direction -1 (amont) ou +1 (aval).
     * @return Indice de l'extrémité du bras s'il est rectiligne, -1 sinon.
     */
    private int straightArm(double[] xs, double[] ys, double[] segment, int k, int direction) {
        int n = xs.length;
        List<Integer> arm = new ArrayList<>();
        double travelled = 0.0;
        int m = k;
        while (travelled < ARM_LENGTH && arm.size() < n) {
            int next = Math.floorMod(m + direction, n);
            travelled += direction > 0 ? segment[next] : segment[m];
            m = next;
            arm.add(m);
        }
        int end = arm.get(arm.size() - 1);
        double dx = xs[end] - xs[k];
        double dy = ys[end] - ys[k];
        double length = Math.hypot(dx, dy);
        if (length < 0.8 * ARM_LENGTH) {
            return -1;
        }
        for (int p : arm) {
            double off = Math.abs((xs[p] - xs[k]) * dy - (ys[p] - ys[k]) * dx) / length;
            if (off > ARM_TOLERANCE) {
                return -1;
            }
        }
        return end;
    }

    /**
     * Teste si la portion [i, j] est une amorce à couper et renvoie le coin où se rejoignent les deux bords.
     * <p>
     * Le coin est l'intersection des droites des deux bras rectilignes ; la portion est remplacée par les deux
     * segments i → coin → j, si aucun de ses points n'entre dans l'angle formé par les deux bords et que la surface
     * retirée est de la chaussée.
     * </p>
     *
     * @param xs          Abscisses.
     * @param ys          Ordonnées.
     * @param i           Début.
     * @param j           Fin.
     * @param armStart    Extrémité amont du bras précédant i.
     * @param armEnd      Extrémité aval du bras suivant j.
     * @param outside     Signe du produit vectoriel du côté extérieur.
     * @param roadMask    Masque des routes.
     * @param roundabouts Giratoires.
     * @param traceVertices Sommets du tracé.
     * @return Coin {x, y}, ou null si la portion n'est pas une amorce.
     */
    private double[] stubCorner(double[] xs, double[] ys, int i, int j, int armStart, int armEnd, double outside,
                                BinaryMask roadMask, List<Roundabout> roundabouts, List<PixelPoint> traceVertices) {
        if (Math.hypot(xs[j] - xs[i], ys[j] - ys[i]) < MIN_CHORD) {
            return null;
        }
        // Droites des bras : (armStart -> i) prolongée au-delà de i, (j -> armEnd) prolongée en deçà de j.
        double ux = xs[i] - xs[armStart];
        double uy = ys[i] - ys[armStart];
        double vx = xs[armEnd] - xs[j];
        double vy = ys[armEnd] - ys[j];
        double cross = ux * vy - uy * vx;
        double norms = Math.hypot(ux, uy) * Math.hypot(vx, vy);
        if (Math.abs(cross) < MIN_CORNER_SINE * norms) {
            return null;
        }
        double t = ((xs[j] - xs[i]) * vy - (ys[j] - ys[i]) * vx) / cross;
        double s = ((xs[j] - xs[i]) * uy - (ys[j] - ys[i]) * ux) / cross;
        if (t < 0.0 || s > 0.0) {
            return null;
        }
        double cx = xs[i] + t * ux;
        double cy = ys[i] + t * uy;
        if (!nearVertex(traceVertices, cx, cy, MAX_VERTEX_DISTANCE)) {
            return null;
        }
        if (Math.hypot(cx - xs[i], cy - ys[i]) > MAX_STUB_LENGTH / 2.0
                || Math.hypot(cx - xs[j], cy - ys[j]) > MAX_STUB_LENGTH / 2.0) {
            return null;
        }
        // Chaque point de la portion est au-delà d'au moins un des deux bords (côté extérieur) : aucun n'entre
        // dans l'angle formé par les deux bords.
        double depth = 0.0;
        for (int m = i + 1; m < j; m++) {
            double side1 = ((xs[m] - xs[i]) * uy - (ys[m] - ys[i]) * ux) / Math.hypot(ux, uy) * outside;
            double side2 = ((xs[m] - xs[j]) * vy - (ys[m] - ys[j]) * vx) / Math.hypot(vx, vy) * outside;
            if (side1 < -ARM_TOLERANCE && side2 < -ARM_TOLERANCE) {
                return null;
            }
            depth = Math.max(depth, Math.max(side1, side2));
        }
        if (depth < MIN_DEPTH || depth > MAX_DEPTH) {
            return null;
        }
        double[] px = polygonX(xs, i, j, cx);
        double[] py = polygonX(ys, i, j, cy);
        return awayFromRoundabouts(px, py, roundabouts) && mostlyRoad(px, py, roadMask) ? new double[]{cx, cy} : null;
    }

    /**
     * Teste si la portion [i, j] est une amorce de rue latérale sur un bord rectiligne, près d'un coin du tracé.
     * <p>
     * Près d'un sommet du tracé, le bord d'une route frontière n'est souvent rectiligne que sur une vingtaine de
     * pixels, trop peu pour {@link StraightEdgeBumpRemover} : l'embouchure d'une rue latérale y forme une bosse. La
     * portion est remplacée par sa corde si les deux bords qui l'encadrent sont dans son prolongement, si elle ne
     * fait que dépasser vers l'extérieur (de {@link #MIN_DEPTH} à {@link #MAX_SIDE_DEPTH} px) et si la surface
     * retirée est de la chaussée.
     * </p>
     *
     * @param xs            Abscisses.
     * @param ys            Ordonnées.
     * @param i             Début.
     * @param j             Fin.
     * @param armStart      Extrémité amont du bras précédant i.
     * @param armEnd        Extrémité aval du bras suivant j.
     * @param outside       Signe du produit vectoriel du côté extérieur.
     * @param roadMask      Masque des routes.
     * @param roundabouts   Giratoires.
     * @param traceVertices Sommets du tracé.
     * @return Milieu de la corde (la portion devient i → milieu → j), ou null.
     */
    private double[] sideStreetStub(double[] xs, double[] ys, int i, int j, int armStart, int armEnd, double outside,
                                    BinaryMask roadMask, List<Roundabout> roundabouts,
                                    List<PixelPoint> traceVertices) {
        double dx = xs[j] - xs[i];
        double dy = ys[j] - ys[i];
        double chord = Math.hypot(dx, dy);
        if (chord < MIN_CHORD || chord > MAX_SIDE_CHORD
                || !collinear(dx, dy, xs[i] - xs[armStart], ys[i] - ys[armStart])
                || !collinear(dx, dy, xs[armEnd] - xs[j], ys[armEnd] - ys[j])) {
            return null;
        }
        double mx = (xs[i] + xs[j]) / 2.0;
        double my = (ys[i] + ys[j]) / 2.0;
        if (!nearVertex(traceVertices, mx, my, MAX_SIDE_VERTEX_DISTANCE)) {
            return null;
        }
        double depth = 0.0;
        for (int m = i + 1; m < j; m++) {
            double side = ((xs[m] - xs[i]) * dy - (ys[m] - ys[i]) * dx) / chord * outside;
            if (side < -ARM_TOLERANCE) {
                return null;
            }
            depth = Math.max(depth, side);
        }
        if (depth < MIN_DEPTH || depth > MAX_SIDE_DEPTH) {
            return null;
        }
        double[] px = polygonX(xs, i, j, mx);
        double[] py = polygonX(ys, i, j, my);
        return awayFromRoundabouts(px, py, roundabouts) && mostlyRoad(px, py, roadMask) ? new double[]{mx, my} : null;
    }

    /**
     * Indique si une direction est dans le prolongement d'une autre (sinus de l'angle au plus 1 - {@link
     * #MIN_CORNER_SINE}, soit environ 17°).
     *
     * @param ax Première direction, abscisse.
     * @param ay Première direction, ordonnée.
     * @param bx Seconde direction, abscisse.
     * @param by Seconde direction, ordonnée.
     * @return true si les directions sont alignées et de même sens.
     */
    private static boolean collinear(double ax, double ay, double bx, double by) {
        double norms = Math.hypot(ax, ay) * Math.hypot(bx, by);
        return norms > 0.0 && ax * bx + ay * by > 0.0 && Math.abs(ax * by - ay * bx) <= (1.0 - MIN_CORNER_SINE) * norms;
    }

    /**
     * Indique si un point est proche d'un sommet du tracé.
     *
     * @param traceVertices Sommets du tracé.
     * @param x             Abscisse.
     * @param y             Ordonnée.
     * @param distance      Distance maximale (px).
     * @return true si un sommet est à moins de distance.
     */
    private static boolean nearVertex(List<PixelPoint> traceVertices, double x, double y, double distance) {
        for (PixelPoint vertex : traceVertices) {
            if (Math.hypot(vertex.x() - x, vertex.y() - y) <= distance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indique si aucun sommet du polygone n'entre dans un giratoire (marge {@link #ROUNDABOUT_MARGIN} comprise).
     *
     * @param px          Abscisses.
     * @param py          Ordonnées.
     * @param roundabouts Giratoires.
     * @return true si le polygone reste hors des giratoires.
     */
    private static boolean awayFromRoundabouts(double[] px, double[] py, List<Roundabout> roundabouts) {
        for (Roundabout roundabout : roundabouts) {
            EllipseModel ellipse = roundabout.exteriorEllipse();
            for (int m = 0; m < px.length; m++) {
                PixelPoint unit = ellipse.toUnitCircle(new PixelPoint(px[m], py[m]));
                if (Math.hypot(unit.x(), unit.y()) < 1.0 + ROUNDABOUT_MARGIN / ellipse.a()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Sommets du polygone retiré : la portion [i, j] puis le coin.
     *
     * @param values Coordonnées (abscisses ou ordonnées) du contour.
     * @param i      Début.
     * @param j      Fin.
     * @param corner Coordonnée du coin.
     * @return Coordonnées des sommets.
     */
    private static double[] polygonX(double[] values, int i, int j, double corner) {
        double[] out = new double[j - i + 2];
        System.arraycopy(values, i, out, 0, j - i + 1);
        out[j - i + 1] = corner;
        return out;
    }

    /**
     * Teste si la surface du polygone retiré est de la chaussée.
     *
     * @param px       Abscisses des sommets.
     * @param py       Ordonnées des sommets.
     * @param roadMask Masque des routes.
     * @return true si la part de terre ne dépasse pas {@link #MAX_LAND_FRACTION}.
     */
    private boolean mostlyRoad(double[] px, double[] py, BinaryMask roadMask) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int k = 0; k < px.length; k++) {
            minX = Math.min(minX, px[k]);
            minY = Math.min(minY, py[k]);
            maxX = Math.max(maxX, px[k]);
            maxY = Math.max(maxY, py[k]);
        }
        int area = 0;
        int land = 0;
        for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
            for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
                if (!inside(px, py, x + 0.5, y + 0.5)) {
                    continue;
                }
                area++;
                if (roadMask.isInBounds(x, y) && !roadMask.get(x, y)) {
                    land++;
                }
            }
        }
        return area > 0 && area <= MAX_REMOVED_AREA && land <= MAX_LAND_FRACTION * area;
    }

    /**
     * Teste si un point est en pleine chaussée (tout son voisinage de rayon {@value #CROSSING_RADIUS} est route).
     *
     * @param roadMask Masque des routes.
     * @param x        Abscisse.
     * @param y        Ordonnée.
     * @return true en pleine chaussée.
     */
    private boolean insideRoad(BinaryMask roadMask, double x, double y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        for (int dy = -CROSSING_RADIUS; dy <= CROSSING_RADIUS; dy++) {
            for (int dx = -CROSSING_RADIUS; dx <= CROSSING_RADIUS; dx++) {
                if (!roadMask.get(cx + dx, cy + dy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Test pair-impair d'appartenance d'un point à un polygone fermé.
     *
     * @param px Abscisses des sommets.
     * @param py Ordonnées des sommets.
     * @param x  Abscisse testée.
     * @param y  Ordonnée testée.
     * @return true si le point est intérieur.
     */
    private static boolean inside(double[] px, double[] py, double x, double y) {
        boolean in = false;
        for (int k = 0, prev = px.length - 1; k < px.length; prev = k, k++) {
            if ((py[k] > y) != (py[prev] > y)
                    && x < (px[prev] - px[k]) * (y - py[k]) / (py[prev] - py[k]) + px[k]) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * Reconstruit le contour en remplaçant chaque portion par les segments début → coin → fin, échantillonnés.
     *
     * @param contour Contour d'origine.
     * @param spans   Portions {début, fin, x du coin, y du coin}, triées et disjointes.
     * @return Nouveau contour.
     */
    private SmoothVectorContour rebuild(SmoothVectorContour contour, List<double[]> spans) {
        List<PixelPoint> points = contour.points();
        int n = points.size();
        int[] newIndex = new int[n];
        List<PixelPoint> rebuilt = new ArrayList<>(n);
        int spanIndex = 0;
        int k = 0;
        while (k < n) {
            if (spanIndex < spans.size() && (int) spans.get(spanIndex)[0] == k) {
                double[] span = spans.get(spanIndex);
                int end = (int) span[1];
                PixelPoint corner = new PixelPoint(span[2], span[3]);
                newIndex[k] = rebuilt.size();
                rebuilt.add(points.get(k));
                addSegment(rebuilt, points.get(k), corner);
                rebuilt.add(corner);
                addSegment(rebuilt, corner, points.get(end));
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
        return new SmoothVectorContour(rebuilt, corners, contour.width(), contour.height(),
                contour.cropWindow(), contour.substitutedRoundabouts());
    }

    /**
     * Ajoute les points intermédiaires (pas {@value #CHORD_STEP} px) d'un segment, extrémités exclues.
     *
     * @param out Liste complétée.
     * @param a   Début.
     * @param b   Fin.
     */
    private static void addSegment(List<PixelPoint> out, PixelPoint a, PixelPoint b) {
        double length = Math.hypot(b.x() - a.x(), b.y() - a.y());
        int steps = (int) Math.ceil(length / CHORD_STEP);
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            out.add(new PixelPoint(a.x() + t * (b.x() - a.x()), a.y() + t * (b.y() - a.y())));
        }
    }
}
