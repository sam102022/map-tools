package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Détecteur multi-échelles d'angles vifs et de coins géométriques sur contours fermés.
 * Applique un pré-filtrage gaussien périodique léger, évalue les déviations angulaires
 * sur une fenêtre sécante large (L = 80 px) et extrait les maxima locaux stricts (seuil = 38°).
 */
public class CornerDetector {

    /**
     * Détecte les indices des coins préservés le long d'un contour fermé.
     *
     * @param contour          Liste ordonnée des points du contour fermé rééchantillonné.
     * @param windowL          Demi-largeur de fenêtre sécante en pixels (L, typiquement 80 px).
     * @param thresholdDegrees Seuil angulaire minimal en degrés (typiquement 38.0°).
     * @param preSmoothSigma   Écart-type du lissage gaussien préliminaire (typiquement 4.0 px).
     * @return Liste ordonnée immuable des indices de sommets d'angles vifs.
     */
    public List<Integer> detectCorners(List<PixelPoint> contour, int windowL, double thresholdDegrees, double preSmoothSigma) {
        if (contour == null || contour.size() < 2 * windowL + 1) {
            return List.of();
        }

        int n = contour.size();
        List<PixelPoint> preSmoothed = applyPeriodicGaussianSmoothing(contour, preSmoothSigma);
        double[] angles = computeAngularDeviations(preSmoothed, windowL);

        List<Integer> corners = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (angles[i] >= thresholdDegrees && isStrictLocalMaximum(angles, i, windowL, n)) {
                corners.add(i);
            }
        }

        return Collections.unmodifiableList(corners);
    }

    private List<PixelPoint> applyPeriodicGaussianSmoothing(List<PixelPoint> contour, double sigma) {
        int n = contour.size();
        int k = Math.max(1, (int) Math.floor(4.0 * sigma));
        double[] weights = new double[2 * k + 1];
        double weightSum = 0.0;

        for (int i = -k; i <= k; i++) {
            double w = Math.exp(-0.5 * (i / sigma) * (i / sigma));
            weights[i + k] = w;
            weightSum += w;
        }

        List<PixelPoint> smoothed = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double sumX = 0.0;
            double sumY = 0.0;
            for (int offset = -k; offset <= k; offset++) {
                int idx = Math.floorMod(i + offset, n);
                PixelPoint p = contour.get(idx);
                double w = weights[offset + k];
                sumX += p.x() * w;
                sumY += p.y() * w;
            }
            smoothed.add(new PixelPoint(sumX / weightSum, sumY / weightSum));
        }
        return smoothed;
    }

    private double[] computeAngularDeviations(List<PixelPoint> smoothed, int windowL) {
        int n = smoothed.size();
        double[] angles = new double[n];

        for (int i = 0; i < n; i++) {
            PixelPoint prev = smoothed.get(Math.floorMod(i - windowL, n));
            PixelPoint curr = smoothed.get(i);
            PixelPoint next = smoothed.get(Math.floorMod(i + windowL, n));

            double v1x = curr.x() - prev.x();
            double v1y = curr.y() - prev.y();
            double v2x = next.x() - curr.x();
            double v2y = next.y() - curr.y();

            double cross = v1x * v2y - v1y * v2x;
            double dot = v1x * v2x + v1y * v2y;
            double rad = Math.abs(Math.atan2(cross, dot));
            angles[i] = Math.toDegrees(rad);
        }
        return angles;
    }

    private boolean isStrictLocalMaximum(double[] angles, int centerIdx, int windowL, int n) {
        double centerVal = angles[centerIdx];
        for (int offset = -windowL; offset <= windowL; offset++) {
            if (offset == 0) {
                continue;
            }
            int idx = Math.floorMod(centerIdx + offset, n);
            if (angles[idx] > centerVal) {
                return false;
            }
        }
        return true;
    }
}
