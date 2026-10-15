package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lisseur par régression quadratique locale robuste (LQR) avec M-estimateur de Cauchy/Tukey.
 * Épouse la courbure réelle des axes routiers sans les raboter et rejette les carrefours
 * et départs de rues transversales comme des valeurs aberrantes (outliers).
 */
public class RobustLqrSmoother {

    /**
     * Résultat immuable du lissage quadratique robuste sur les signaux de résidus 2D (fitX et fitY).
     *
     * @param fitX Tableau des résidus lissés en abscisse.
     * @param fitY Tableau des résidus lissés en ordonnée.
     */
    public record LqrResidualResult(double[] fitX, double[] fitY) {
        /**
         * Constructeur compact avec validation d'invariants.
         */
        public LqrResidualResult {
            Objects.requireNonNull(fitX, "fitX ne doit pas être nul.");
            Objects.requireNonNull(fitY, "fitY ne doit pas être nul.");
            if (fitX.length != fitY.length) {
                throw new IllegalArgumentException("fitX et fitY doivent avoir la même longueur.");
            }
        }
    }

    /**
     * Initialise une nouvelle instance du lisseur quadratique robuste LQR.
     */
    public RobustLqrSmoother() {
    }

    /**
     * Lisse l'ensemble du contour fermé en partitionnant par les coins détectés.
     *
     * @param contour       Contour fermé rééchantillonné.
     * @param cornerIndices Indices ordonnés des coins.
     * @param sigma         Écart-type du noyau LQR en pixels (typiquement 22.0 px).
     * @param scale         Seuil de coupure du M-estimateur de Cauchy/Tukey (typiquement 3.0 px).
     * @param iterations    Nombre d'itérations de repondération robuste (typiquement 6).
     * @return Contour lissé avec extrémités de segments fixées.
     */
    public List<PixelPoint> smoothContour(List<PixelPoint> contour, List<Integer> cornerIndices,
                                          double sigma, double scale, int iterations) {
        if (contour == null || contour.isEmpty()) {
            return List.of();
        }

        int n = contour.size();
        List<PixelPoint> result = new ArrayList<>(contour);
        List<Integer> corners = (cornerIndices == null || cornerIndices.isEmpty())
                ? List.of(0)
                : cornerIndices;

        int numCorners = corners.size();
        for (int i = 0; i < numCorners; i++) {
            int startIdx = corners.get(i);
            int endIdx = corners.get((i + 1) % numCorners);

            List<Integer> segIndices = collectSegmentIndices(startIdx, endIdx, n);
            List<PixelPoint> segment = new ArrayList<>(segIndices.size());
            for (int idx : segIndices) {
                segment.add(contour.get(idx));
            }

            if (segment.size() >= 2.5 * sigma) {
                List<PixelPoint> smoothedSeg = smoothSegment(segment, sigma, scale, iterations);
                for (int s = 0; s < segIndices.size(); s++) {
                    result.set(segIndices.get(s), smoothedSeg.get(s));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Lisse un segment ouvert individuel en soustrayant sa corde directrice et en appliquant LQR.
     *
     * @param segment    Points ordonnés du segment.
     * @param sigma      Écart-type du noyau gaussien (px).
     * @param scale      Échelle Cauchy (px).
     * @param iterations Nombre d'itérations.
     * @return Segment lissé avec extrémités identiques au segment d'origine.
     */
    public List<PixelPoint> smoothSegment(List<PixelPoint> segment, double sigma, double scale, int iterations) {
        int n = segment.size();
        if (n < 2.5 * sigma) {
            return segment;
        }

        PixelPoint p0 = segment.get(0);
        PixelPoint p1 = segment.get(n - 1);

        double[] baseX = new double[n];
        double[] baseY = new double[n];
        double[] yX = new double[n];
        double[] yY = new double[n];

        for (int t = 0; t < n; t++) {
            double alpha = (double) t / (n - 1.0);
            baseX[t] = p0.x() + alpha * (p1.x() - p0.x());
            baseY[t] = p0.y() + alpha * (p1.y() - p0.y());
            yX[t] = segment.get(t).x() - baseX[t];
            yY[t] = segment.get(t).y() - baseY[t];
        }

        LqrResidualResult fit = smoothResiduals(yX, yY, sigma, scale, iterations);
        double[] fitX = fit.fitX();
        double[] fitY = fit.fitY();

        List<PixelPoint> result = new ArrayList<>(n);
        result.add(p0);
        for (int t = 1; t < n - 1; t++) {
            result.add(new PixelPoint(baseX[t] + fitX[t], baseY[t] + fitY[t]));
        }
        result.add(p1);

        return Collections.unmodifiableList(result);
    }

    /**
     * Lisse directement les signaux de résidus 2D (yX et yY) par régression quadratique locale robuste.
     *
     * @param yX         Signal des résidus en abscisse.
     * @param yY         Signal des résidus en ordonnée.
     * @param sigma      Écart-type du noyau gaussien (en points/pixels).
     * @param scale      Échelle de coupure du M-estimateur de Cauchy.
     * @param iterations Nombre d'itérations de repondération robuste.
     * @return Résultat contenant les signaux de résidus lissés fitX et fitY.
     */
    public LqrResidualResult smoothResiduals(double[] yX, double[] yY, double sigma, double scale, int iterations) {
        Objects.requireNonNull(yX, "yX ne doit pas être nul.");
        Objects.requireNonNull(yY, "yY ne doit pas être nul.");
        int n = yX.length;
        if (n == 0) {
            return new LqrResidualResult(new double[0], new double[0]);
        }

        int k = Math.max(1, (int) Math.floor(3.5 * sigma));
        double[] g = new double[2 * k + 1];
        for (int u = -k; u <= k; u++) {
            g[u + k] = Math.exp(-0.5 * (u / sigma) * (u / sigma));
        }

        double[] w = new double[n];
        Arrays.fill(w, 1.0);
        double[] fitX = Arrays.copyOf(yX, n);
        double[] fitY = Arrays.copyOf(yY, n);

        for (int it = 0; it < iterations; it++) {
            fitX = computeLqrPass(yX, w, g, k, n);
            fitY = computeLqrPass(yY, w, g, k, n);
            updateRobustWeights(yX, yY, fitX, fitY, w, scale, n);
        }

        return new LqrResidualResult(fitX, fitY);
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
     * Calcule une passe locale de convolution polynomiale d'ordre 2 pour un signal 1D pondéré.
     *
     * @param y Signal d'entrée 1D.
     * @param w Poids courants de l'estimateur de Cauchy.
     * @param g Pondérations gaussiennes.
     * @param k Demi-largeur de fenêtre.
     * @param n Longueur du signal.
     * @return Valeurs polynomiales évaluées au point central t.
     */
    private double[] computeLqrPass(double[] y, double[] w, double[] g, int k, int n) {
        double[] fit = new double[n];

        for (int t = 0; t < n; t++) {
            double m0 = 0.0, m1 = 0.0, m2 = 0.0, m3 = 0.0, m4 = 0.0;
            double b0 = 0.0, b1 = 0.0, b2 = 0.0;

            for (int u = -k; u <= k; u++) {
                int ti = t + u;
                if (ti >= 0 && ti < n) {
                    double weight = w[ti] * g[u + k];
                    double u2 = (double) u * u;
                    m0 += weight;
                    m1 += weight * u;
                    m2 += weight * u2;
                    m3 += weight * u2 * u;
                    m4 += weight * u2 * u2;

                    double wy = weight * y[ti];
                    b0 += wy;
                    b1 += wy * u;
                    b2 += wy * u2;
                }
            }

            double lambda = 1e-3 * m0 + 1e-9;
            fit[t] = solveCramerOrder0(m0 + lambda, m1, m2, m1, m2 + lambda, m3, m2, m3, m4 + lambda, b0, b1, b2);
        }

        return fit;
    }

    /**
     * Résout la composante d'ordre 0 (valeur centrale) du système linéaire 3x3 par formule explicite de Cramer.
     *
     * @param a00 Coefficient (0,0) de la matrice A.
     * @param a01 Coefficient (0,1) de la matrice A.
     * @param a02 Coefficient (0,2) de la matrice A.
     * @param a10 Coefficient (1,0) de la matrice A.
     * @param a11 Coefficient (1,1) de la matrice A.
     * @param a12 Coefficient (1,2) de la matrice A.
     * @param a20 Coefficient (2,0) de la matrice A.
     * @param a21 Coefficient (2,1) de la matrice A.
     * @param a22 Coefficient (2,2) de la matrice A.
     * @param b0  Second membre ligne 0.
     * @param b1  Second membre ligne 1.
     * @param b2  Second membre ligne 2.
     * @return Valeur scalaire estimée c0.
     */
    private double solveCramerOrder0(double a00, double a01, double a02,
                                     double a10, double a11, double a12,
                                     double a20, double a21, double a22,
                                     double b0, double b1, double b2) {
        double detA = a00 * (a11 * a22 - a12 * a21)
                - a01 * (a10 * a22 - a12 * a20)
                + a02 * (a10 * a21 - a11 * a20);

        if (Math.abs(detA) < 1e-15) {
            return b0 / Math.max(a00, 1e-6);
        }

        double detA0 = b0 * (a11 * a22 - a12 * a21)
                - a01 * (b1 * a22 - a12 * b2)
                + a02 * (b1 * a21 - a11 * b2);

        return detA0 / detA;
    }

    /**
     * Met à jour les poids robustes w(t) selon la fonction de perte de Cauchy/Tukey.
     *
     * @param yX    Signal d'origine X.
     * @param yY    Signal d'origine Y.
     * @param fitX  Signal ajusté X.
     * @param fitY  Signal ajusté Y.
     * @param w     Tableau des poids à mettre à jour in-place.
     * @param scale Échelle de normalisation du résidu.
     * @param n     Nombre de points.
     */
    private void updateRobustWeights(double[] yX, double[] yY, double[] fitX, double[] fitY,
                                     double[] w, double scale, int n) {
        for (int t = 0; t < n; t++) {
            double dx = yX[t] - fitX[t];
            double dy = yY[t] - fitY[t];
            double dev = Math.hypot(dx, dy);
            double term = 1.0 + (dev / scale) * (dev / scale);
            w[t] = 1.0 / (term * term);
        }
    }
}
