package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lisseur par régression quadratique locale robuste (LQR) avec M-estimateur de Cauchy/Tukey.
 * Épouse la courbure réelle des axes routiers sans les raboter et rejette les carrefours
 * et départs de rues transversales comme des valeurs aberrantes (outliers).
 */
public class RobustLqrSmoother {

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

        int k = Math.max(1, (int) Math.floor(3.5 * sigma));
        double[] g = new double[2 * k + 1];
        for (int u = -k; u <= k; u++) {
            g[u + k] = Math.exp(-0.5 * (u / sigma) * (u / sigma));
        }

        double[] fitX = solveLqrIterations(yX, yY, g, k, n, scale, iterations, 0);
        double[] fitY = solveLqrIterations(yX, yY, g, k, n, scale, iterations, 1);

        List<PixelPoint> result = new ArrayList<>(n);
        result.add(p0);
        for (int t = 1; t < n - 1; t++) {
            result.add(new PixelPoint(baseX[t] + fitX[t], baseY[t] + fitY[t]));
        }
        result.add(p1);

        return Collections.unmodifiableList(result);
    }

    private List<Integer> collectSegmentIndices(int startIdx, int endIdx, int n) {
        List<Integer> indices = new ArrayList<>();
        int curr = startIdx;
        while (true) {
            indices.add(curr);
            if (curr == endIdx) {
                break;
            }
            curr = (curr + 1) % n;
        }
        return indices;
    }

    private double[] solveLqrIterations(double[] yX, double[] yY, double[] g, int k, int n,
                                        double scale, int iterations, int coord) {
        double[] w = new double[n];
        java.util.Arrays.fill(w, 1.0);
        double[] fitX = java.util.Arrays.copyOf(yX, n);
        double[] fitY = java.util.Arrays.copyOf(yY, n);

        for (int it = 0; it < iterations; it++) {
            double[] nextFitX = computeLqrPass(yX, w, g, k, n);
            double[] nextFitY = computeLqrPass(yY, w, g, k, n);

            fitX = nextFitX;
            fitY = nextFitY;

            updateRobustWeights(yX, yY, fitX, fitY, w, scale, n);
        }

        return coord == 0 ? fitX : fitY;
    }

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
