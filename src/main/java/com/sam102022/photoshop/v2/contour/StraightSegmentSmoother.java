package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Lisseur adaptatif multi-échelle des tronçons droits (Sprint 7).
 * Compare un lissage LQR fin local (sigma nominal) et un lissage LQR large (sigma étendu)
 * afin de détecter les sections rectilignes, d'absorber les encoches d'icônes cartographiques
 * et d'assurer une transition continue C1 par moyenne mobile de boîte.
 */
public class StraightSegmentSmoother {

    private static final Logger LOGGER = Logger.getLogger(StraightSegmentSmoother.class.getName());

    private final RobustLqrSmoother lqrSmoother;

    /**
     * Initialise une nouvelle instance avec le lisseur LQR standard.
     */
    public StraightSegmentSmoother() {
        this(new RobustLqrSmoother());
    }

    /**
     * Initialise le lisseur de tronçons avec le solveur LQR injecté.
     *
     * @param lqrSmoother Solveur quadratique robuste LQR injecté.
     */
    public StraightSegmentSmoother(RobustLqrSmoother lqrSmoother) {
        this.lqrSmoother = Objects.requireNonNull(lqrSmoother, "Le lisseur LQR ne doit pas être nul.");
    }

    /**
     * Lisse l'ensemble du contour fermé en partitionnant le tracé entre les coins détectés.
     *
     * @param contour       Contour vectoriel ordonné fermé.
     * @param cornerIndices Indices des angles vifs préservés.
     * @param config        Configuration des hyperparamètres de lissage.
     * @return Nouveau contour vectoriel lissé avec tronçons droits redressés.
     */
    public List<PixelPoint> smoothContour(List<PixelPoint> contour, List<Integer> cornerIndices,
                                          ContourSmoothingConfig config) {
        Objects.requireNonNull(contour, "Le contour ne doit pas être nul.");
        Objects.requireNonNull(config, "La configuration ne doit pas être nulle.");
        if (contour.isEmpty()) {
            return List.of();
        }

        int n = contour.size();
        List<PixelPoint> result = new ArrayList<>(contour);
        List<Integer> corners = (cornerIndices == null || cornerIndices.isEmpty())
                ? List.of(0)
                : cornerIndices;

        int numCorners = corners.size();
        LOGGER.fine(() -> String.format("[Sprint 7] Partitionnement du contour en %d segments de lissage.", numCorners));

        for (int i = 0; i < numCorners; i++) {
            int startIdx = corners.get(i);
            int endIdx = corners.get((i + 1) % numCorners);

            List<Integer> segIndices = collectSegmentIndices(startIdx, endIdx, n);
            List<PixelPoint> segment = new ArrayList<>(segIndices.size());
            for (int idx : segIndices) {
                segment.add(contour.get(idx));
            }

            if (segment.size() >= 2.5 * config.lqrSigma()) {
                List<PixelPoint> smoothedSeg = smoothSegment(segment, config);
                for (int s = 0; s < segIndices.size(); s++) {
                    result.set(segIndices.get(s), smoothedSeg.get(s));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Lisse un segment ouvert individuel par double régression LQR et transition continue C1.
     *
     * @param segment Points ordonnés du segment.
     * @param config  Configuration des hyperparamètres de lissage.
     * @return Segment de points lissés et redressés.
     */
    public List<PixelPoint> smoothSegment(List<PixelPoint> segment, ContourSmoothingConfig config) {
        Objects.requireNonNull(segment, "Le segment ne doit pas être nul.");
        Objects.requireNonNull(config, "La configuration ne doit pas être nulle.");

        int n = segment.size();
        if (n < 2.5 * config.lqrSigma()) {
            return segment;
        }

        BaselineResiduals residuals = computeBaselineAndResiduals(segment);

        RobustLqrSmoother.LqrResidualResult smA = lqrSmoother.smoothResiduals(
                residuals.yX(), residuals.yY(),
                config.lqrSigma(), config.lqrScale(), config.lqrIterations()
        );

        double wideSigma = config.straightFactor() * config.lqrSigma();
        RobustLqrSmoother.LqrResidualResult smB = lqrSmoother.smoothResiduals(
                residuals.yX(), residuals.yY(),
                wideSigma, config.straightScale(), 8
        );

        double[] rawWeights = computeStraightnessWeights(smA, smB, config.straightThreshold(), n);
        double[] smoothWeights = applyBoxFilterWithEdgePadding(rawWeights, config.straightTransitionK(), n);

        return assembleSmoothedPoints(residuals, smA, smB, smoothWeights, n);
    }

    /**
     * Calcule la ligne de base reliant P0 à P1 et extrait les signaux de résidus 2D.
     *
     * @param segment Segment de points originaux.
     * @return Conteneur des coordonnées de base et des résidus (yX, yY).
     */
    private BaselineResiduals computeBaselineAndResiduals(List<PixelPoint> segment) {
        int n = segment.size();
        PixelPoint p0 = segment.get(0);
        PixelPoint p1 = segment.get(n - 1);

        double[] baseX = new double[n];
        double[] baseY = new double[n];
        double[] yX = new double[n];
        double[] yY = new double[n];

        double dx = p1.x() - p0.x();
        double dy = p1.y() - p0.y();
        double denom = n - 1.0;

        for (int t = 0; t < n; t++) {
            double alpha = t / denom;
            baseX[t] = p0.x() + alpha * dx;
            baseY[t] = p0.y() + alpha * dy;
            PixelPoint pt = segment.get(t);
            yX[t] = pt.x() - baseX[t];
            yY[t] = pt.y() - baseY[t];
        }

        return new BaselineResiduals(baseX, baseY, yX, yY);
    }

    /**
     * Calcule le poids brut de rectitude à partir de l'écart euclidien entre lissage fin et lissage large.
     *
     * @param smA       Lissage fin local (sigma nominal).
     * @param smB       Lissage large étendu (sigma large).
     * @param threshold Seuil de déviation maximale admissible en pixels.
     * @param n         Nombre de points.
     * @return Tableau des poids bruts bornés dans [0.0, 1.0].
     */
    private double[] computeStraightnessWeights(RobustLqrSmoother.LqrResidualResult smA,
                                                RobustLqrSmoother.LqrResidualResult smB,
                                                double threshold,
                                                int n) {
        double[] rawWeights = new double[n];
        double halfThreshold = threshold / 2.0;

        for (int t = 0; t < n; t++) {
            double dx = smB.fitX()[t] - smA.fitX()[t];
            double dy = smB.fitY()[t] - smA.fitY()[t];
            double delta = Math.hypot(dx, dy);
            double val = (threshold - delta) / halfThreshold;
            rawWeights[t] = Math.clamp(val, 0.0, 1.0);
        }

        return rawWeights;
    }

    /**
     * Applique un filtre boîte 1D avec réplication des bords pour garantir une transition continue C1.
     *
     * @param raw Tableau d'entrée des poids bruts.
     * @param k   Demi-largeur de fenêtre (taille totale 2k + 1).
     * @param n   Nombre de points du tableau.
     * @return Tableau lissé des poids de rectitude.
     */
    private double[] applyBoxFilterWithEdgePadding(double[] raw, int k, int n) {
        double[] filtered = new double[n];
        int windowSize = 2 * k + 1;
        double invWindow = 1.0 / windowSize;

        for (int i = 0; i < n; i++) {
            double sum = 0.0;
            for (int u = -k; u <= k; u++) {
                int idx = Math.clamp(i + u, 0, n - 1);
                sum += raw[idx];
            }
            filtered[i] = sum * invWindow;
        }

        return filtered;
    }

    /**
     * Reconstitue les coordonnées cartésiennes lissées en combinant les deux échelles selon le poids de rectitude.
     *
     * @param residuals     Ligne de base et résidus d'origine.
     * @param smA           Lissage fin local.
     * @param smB           Lissage large étendu.
     * @param smoothWeights Poids lissés continus C1.
     * @param n             Nombre de points.
     * @return Liste ordonnée immuable des points sub-pixels résultants.
     */
    private List<PixelPoint> assembleSmoothedPoints(BaselineResiduals residuals,
                                                   RobustLqrSmoother.LqrResidualResult smA,
                                                   RobustLqrSmoother.LqrResidualResult smB,
                                                   double[] smoothWeights,
                                                   int n) {
        List<PixelPoint> result = new ArrayList<>(n);

        for (int t = 0; t < n; t++) {
            double w = smoothWeights[t];
            double smX = smA.fitX()[t] + w * (smB.fitX()[t] - smA.fitX()[t]);
            double smY = smA.fitY()[t] + w * (smB.fitY()[t] - smA.fitY()[t]);

            double x = residuals.baseX()[t] + smX;
            double y = residuals.baseY()[t] + smY;
            result.add(new PixelPoint(x, y));
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Collecte de manière circulaire ordonnée les indices d'un sous-segment entre deux coins.
     *
     * @param startIdx Indice de départ.
     * @param endIdx   Indice d'arrivée.
     * Lorsque {@code startIdx == endIdx} (contour sans coin ou avec un seul coin), le segment couvre la
     * boucle complète et se referme sur son point de départ ({@code n + 1} indices), conformément à
     * l'étalon Python {@code ids = arange(a, a + N + 1) % N}.
     *
     * @param n        Nombre total de points du contour fermé.
     * @return Liste ordonnée des indices le long du contour.
     */
    private List<Integer> collectSegmentIndices(int startIdx, int endIdx, int n) {
        List<Integer> indices = new ArrayList<>();
        int curr = startIdx;
        boolean fullLoop = startIdx == endIdx && n > 1;
        while (true) {
            indices.add(curr);
            if (curr == endIdx && !(fullLoop && indices.size() == 1)) {
                break;
            }
            curr = (curr + 1) % n;
        }
        return indices;
    }

    /**
     * Structure interne immuable stockant les coordonnées de base et les résidus d'un segment.
     *
     * @param baseX Coordonnées X de la droite de référence.
     * @param baseY Coordonnées Y de la droite de référence.
     * @param yX    Signaux résiduels en X par rapport à la droite.
     * @param yY    Signaux résiduels en Y par rapport à la droite.
     */
    private record BaselineResiduals(double[] baseX, double[] baseY, double[] yX, double[] yY) {}
}
