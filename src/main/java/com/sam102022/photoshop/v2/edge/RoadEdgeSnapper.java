package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Accroche le contour vectoriel lissé sur le bord vectoriel réel des routes de la carte.
 * <p>
 * Pour chaque point du contour, le champ de « routéité » ({@link RoadEdgeField}) est sondé le long de la
 * normale extérieure ; le passage chaussée → fond (iso-ligne 0,5) le plus proche, précédé d'au moins
 * quelques pixels de chaussée, donne le décalage d'accrochage. Les décalages sont ensuite régularisés
 * (médiane glissante de rejet des accrochages parasites puis lissage gaussien pondéré) : les tronçons
 * longeant une route suivent exactement son bord anti-crénelé, les autres (traversées, débouchés de rues)
 * conservent le tracé lissé avec une transition continue.
 * </p>
 */
public class RoadEdgeSnapper {

    private static final Logger LOGGER = Logger.getLogger(RoadEdgeSnapper.class.getName());
    private static final double ISO_LEVEL = 0.5;
    private static final int TANGENT_OFFSET = 2;

    /**
     * Construit un accrocheur sans état.
     */
    public RoadEdgeSnapper() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Accroche le contour sur le bord des routes de la carte.
     *
     * @param contour  Contour vectoriel lissé (repère local de la fenêtre de recadrage).
     * @param mapImage Carte source pleine résolution.
     * @param config   Configuration d'accrochage.
     * @return Nouveau contour accroché (même nombre de points, mêmes coins et giratoires), ou le contour
     *         d'origine si l'accrochage est désactivé ou le contour trop court.
     * @throws NullPointerException si un argument est null.
     */
    public SmoothVectorContour snap(SmoothVectorContour contour, BufferedImage mapImage, RoadEdgeSnapConfig config) {
        return snap(contour, mapImage, config, false);
    }

    /**
     * Accroche le contour sur le bord des routes, côté extérieur (territoire : la route frontière est incluse)
     * ou côté intérieur (zone : la route frontière est exclue, le contour suit le bord de chaussée tourné vers
     * l'intérieur de la zone). En mode intérieur, la recherche se fait le long de la normale intérieure : la
     * chaussée est alors à l'extérieur du contour et le fond à l'intérieur.
     *
     * @param contour   Contour vectoriel lissé (repère local de la fenêtre de recadrage).
     * @param mapImage  Carte source pleine résolution.
     * @param config    Configuration d'accrochage.
     * @param innerEdge Vrai pour accrocher le bord intérieur des routes (mode zone).
     * @return Nouveau contour accroché, ou le contour d'origine si l'accrochage est désactivé ou le contour
     *         trop court.
     * @throws NullPointerException si un argument est null.
     */
    public SmoothVectorContour snap(SmoothVectorContour contour, BufferedImage mapImage, RoadEdgeSnapConfig config,
                                    boolean innerEdge) {
        return snap(contour, mapImage, config, innerEdge, null);
    }

    /**
     * Accroche le contour sur le bord des routes en lisant le bord de chaussée dans la capture Google
     * « Routes seules » lorsqu'elle est fournie (sinon dans les couleurs de la carte).
     *
     * @param contour     Contour vectoriel lissé (repère local de la fenêtre de recadrage).
     * @param mapImage    Carte source pleine résolution.
     * @param config      Configuration d'accrochage.
     * @param innerEdge   Vrai pour accrocher le bord intérieur des routes (mode zone).
     * @param googleRoads Capture « Routes seules », ou null.
     * @return Nouveau contour accroché, ou le contour d'origine si l'accrochage est désactivé ou le contour
     *         trop court.
     * @throws NullPointerException si contour, mapImage ou config est null.
     */
    public SmoothVectorContour snap(SmoothVectorContour contour, BufferedImage mapImage, RoadEdgeSnapConfig config,
                                    boolean innerEdge, GoogleRoadsImage googleRoads) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(config, "config ne doit pas être nulle.");
        List<PixelPoint> points = contour.points();
        int n = points.size();
        if (!config.enabled() || n < 2 * TANGENT_OFFSET + 3) {
            return contour;
        }

        RoadEdgeField field = googleRoads != null
                ? new RoadEdgeField(googleRoads, contour.cropWindow())
                : new RoadEdgeField(mapImage, contour.cropWindow(), config.fieldScale());
        double[][] normals = computeOutwardNormals(points);
        if (innerEdge) {
            for (double[] normal : normals) {
                normal[0] = -normal[0];
                normal[1] = -normal[1];
            }
        }
        double[] rawOffsets = findEdgeOffsets(points, normals, field, config);
        double[] offsets = regularizeOffsets(rawOffsets, config);

        List<PixelPoint> snapped = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            PixelPoint p = points.get(i);
            snapped.add(new PixelPoint(p.x() + offsets[i] * normals[i][0], p.y() + offsets[i] * normals[i][1]));
        }
        logStatistics(rawOffsets);
        return new SmoothVectorContour(snapped, contour.cornerIndices(), contour.width(), contour.height(),
                contour.cropWindow(), contour.substitutedRoundabouts());
    }

    /**
     * Calcule les normales extérieures unitaires du contour fermé (orientation déduite de l'aire signée).
     *
     * @param points Contour fermé.
     * @return Tableau [n][2] des normales extérieures.
     */
    double[][] computeOutwardNormals(List<PixelPoint> points) {
        int n = points.size();
        double sign = signedArea(points) > 0.0 ? 1.0 : -1.0;
        double[][] normals = new double[n][2];
        for (int i = 0; i < n; i++) {
            PixelPoint next = points.get(Math.floorMod(i + TANGENT_OFFSET, n));
            PixelPoint prev = points.get(Math.floorMod(i - TANGENT_OFFSET, n));
            double tx = next.x() - prev.x();
            double ty = next.y() - prev.y();
            double len = Math.hypot(tx, ty) + 1e-9;
            normals[i][0] = sign * ty / len;
            normals[i][1] = -sign * tx / len;
        }
        return normals;
    }

    /**
     * Calcule l'aire signée (formule du lacet) du contour fermé.
     *
     * @param points Contour fermé.
     * @return Aire signée.
     */
    private double signedArea(List<PixelPoint> points) {
        int n = points.size();
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            PixelPoint a = points.get(i);
            PixelPoint b = points.get((i + 1) % n);
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return 0.5 * sum;
    }

    /**
     * Recherche, pour chaque point, le décalage normal du bord de route le plus proche (NaN si absent).
     *
     * @param points  Contour.
     * @param normals Normales extérieures.
     * @param field   Champ de routéité.
     * @param config  Configuration.
     * @return Décalages bruts (px), NaN là où aucun bord n'a été trouvé.
     */
    private double[] findEdgeOffsets(List<PixelPoint> points, double[][] normals, RoadEdgeField field,
                                     RoadEdgeSnapConfig config) {
        int steps = (int) Math.round(2.0 * config.searchRadius() / config.searchStep());
        int probe = (int) Math.round(config.insideProbeLength() / config.searchStep());
        double[] samples = new double[steps + 1];
        double[] offsets = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            PixelPoint p = points.get(i);
            for (int k = 0; k <= steps; k++) {
                double t = -config.searchRadius() + k * config.searchStep();
                samples[k] = field.sample(p.x() + t * normals[i][0], p.y() + t * normals[i][1]);
            }
            offsets[i] = nearestRoadToBackgroundCrossing(samples, probe, config);
        }
        return offsets;
    }

    /**
     * Trouve, dans un profil échantillonné vers l'extérieur, le passage chaussée → fond le plus proche de 0.
     *
     * @param samples Profil du champ, de -searchRadius à +searchRadius.
     * @param probe   Nombre d'échantillons sondés côté intérieur.
     * @param config  Configuration.
     * @return Décalage (px) interpolé linéairement, ou NaN si aucun passage valide.
     */
    private double nearestRoadToBackgroundCrossing(double[] samples, int probe, RoadEdgeSnapConfig config) {
        double best = Double.NaN;
        for (int k = 0; k + 1 < samples.length; k++) {
            if (samples[k] >= ISO_LEVEL && samples[k + 1] < ISO_LEVEL
                    && insideRoadRatio(samples, k, probe) > config.minInsideRoadRatio()) {
                double frac = (samples[k] - ISO_LEVEL) / (samples[k] - samples[k + 1]);
                double t = -config.searchRadius() + (k + frac) * config.searchStep();
                if (Double.isNaN(best) || Math.abs(t) < Math.abs(best)) {
                    best = t;
                }
            }
        }
        return best;
    }

    /**
     * Proportion d'échantillons de chaussée sur les {@code probe} échantillons précédant l'indice k (inclus).
     *
     * @param samples Profil du champ.
     * @param k       Indice du dernier échantillon de chaussée.
     * @param probe   Longueur du sondage (en échantillons).
     * @return Proportion dans [0, 1].
     */
    private double insideRoadRatio(double[] samples, int k, int probe) {
        int start = Math.max(0, k - probe);
        int count = 0;
        for (int j = start; j <= k; j++) {
            if (samples[j] >= ISO_LEVEL) {
                count++;
            }
        }
        return (double) count / (k - start + 1);
    }

    /**
     * Régularise les décalages : rejet des accrochages éloignés de la médiane locale, puis moyenne
     * gaussienne pondérée périodique ; le poids lissé module le décalage pour une transition continue
     * vers les zones non accrochées.
     *
     * @param raw    Décalages bruts (NaN = non accroché).
     * @param config Configuration.
     * @return Décalages finaux (px).
     */
    double[] regularizeOffsets(double[] raw, RoadEdgeSnapConfig config) {
        int n = raw.length;
        double[] median = circularNanMedian(raw, config.medianHalfWindow());
        double[] values = new double[n];
        double[] weights = new double[n];
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(raw[i]) && !Double.isNaN(median[i])
                    && Math.abs(raw[i] - median[i]) < config.outlierTolerance()) {
                values[i] = raw[i];
                weights[i] = 1.0;
            }
        }
        double[] kernel = gaussianKernel(config.smoothingSigma());
        double[] num = circularConvolve(values, kernel);
        double[] den = circularConvolve(weights, kernel);
        double[] result = new double[n];
        for (int i = 0; i < n; i++) {
            double mean = num[i] / Math.max(den[i], 1e-6);
            result[i] = mean * Math.clamp(den[i] * 1.5, 0.0, 1.0);
        }
        return result;
    }

    /**
     * Médiane glissante périodique ignorant les NaN (NaN si la fenêtre ne contient aucune valeur).
     *
     * @param values     Signal périodique.
     * @param halfWindow Demi-fenêtre.
     * @return Médianes locales.
     */
    private double[] circularNanMedian(double[] values, int halfWindow) {
        int n = values.length;
        double[] out = new double[n];
        double[] buffer = new double[2 * halfWindow + 1];
        for (int i = 0; i < n; i++) {
            int count = 0;
            for (int o = -halfWindow; o <= halfWindow; o++) {
                double v = values[Math.floorMod(i + o, n)];
                if (!Double.isNaN(v)) {
                    buffer[count++] = v;
                }
            }
            out[i] = count == 0 ? Double.NaN : median(buffer, count);
        }
        return out;
    }

    /**
     * Médiane des {@code count} premières valeurs du tampon (le tampon est trié en place).
     *
     * @param buffer Tampon de valeurs.
     * @param count  Nombre de valeurs valides.
     * @return Médiane.
     */
    private double median(double[] buffer, int count) {
        Arrays.sort(buffer, 0, count);
        int mid = count / 2;
        return (count % 2 == 1) ? buffer[mid] : 0.5 * (buffer[mid - 1] + buffer[mid]);
    }

    /**
     * Noyau gaussien discret normalisé (troncature à 4 sigma) : le poids lissé reste dans [0, 1].
     *
     * @param sigma Écart-type en points.
     * @return Noyau de taille 2r+1 de somme 1.
     */
    private double[] gaussianKernel(double sigma) {
        int radius = (int) Math.floor(4.0 * sigma + 0.5);
        double[] kernel = new double[2 * radius + 1];
        double sum = 0.0;
        for (int u = -radius; u <= radius; u++) {
            kernel[u + radius] = Math.exp(-0.5 * (u / sigma) * (u / sigma));
            sum += kernel[u + radius];
        }
        for (int i = 0; i < kernel.length; i++) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    /**
     * Convolution périodique d'un signal par un noyau centré.
     *
     * @param signal Signal périodique.
     * @param kernel Noyau centré de taille impaire.
     * @return Signal convolué.
     */
    private double[] circularConvolve(double[] signal, double[] kernel) {
        int n = signal.length;
        int radius = kernel.length / 2;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int u = -radius; u <= radius; u++) {
                sum += kernel[u + radius] * signal[Math.floorMod(i + u, n)];
            }
            out[i] = sum;
        }
        return out;
    }

    /**
     * Journalise la proportion de points accrochés au bord d'une route.
     *
     * @param rawOffsets Décalages bruts.
     */
    private void logStatistics(double[] rawOffsets) {
        long snappedCount = Arrays.stream(rawOffsets).filter(v -> !Double.isNaN(v)).count();
        double ratio = 100.0 * snappedCount / Math.max(1, rawOffsets.length);
        LOGGER.info(() -> String.format("[Bord vectoriel] %.1f %% du contour accroché au bord des routes.", ratio));
    }
}
