package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fondu cubique de préservation des angles vifs par fonction de transition d'Hermite (Smoothstep).
 * Conserve l'arête brute à 100% à distance &lt;= R0 des coins et applique le lissage à 100% à distance &gt;= R1.
 */
public class CornerPreservationBlender {

    /**
     * Initialise une nouvelle instance du modulateur par fondu progressif de coins.
     */
    public CornerPreservationBlender() {
    }

    /**
     * Interpole en douceur entre contour brut et contour lissé selon la proximité des coins.
     *
     * @param rawContour      Contour brut rééchantillonné.
     * @param smoothedContour Contour issu du lissage LQR.
     * @param cornerIndices   Indices des coins préservés.
     * @param r0              Rayon de préservation brute totale (typiquement 35.0 px).
     * @param r1              Rayon marquant le début du lissage plein effet (typiquement 95.0 px).
     * @return Contour continu fusionné.
     */
    public List<PixelPoint> blend(List<PixelPoint> rawContour, List<PixelPoint> smoothedContour,
                                  List<Integer> cornerIndices, double r0, double r1) {
        if (rawContour == null || smoothedContour == null || rawContour.isEmpty()) {
            return List.of();
        }

        int n = rawContour.size();
        if (cornerIndices == null || cornerIndices.isEmpty()) {
            return smoothedContour;
        }

        List<PixelPoint> blended = new ArrayList<>(n);
        double deltaR = r1 - r0;

        for (int i = 0; i < n; i++) {
            double minDist = computeMinCornerDistance(i, cornerIndices, n);
            double alpha = Math.clamp((minDist - r0) / deltaR, 0.0, 1.0);
            double s = alpha * alpha * (3.0 - 2.0 * alpha);

            PixelPoint raw = rawContour.get(i);
            PixelPoint sm = smoothedContour.get(i);

            double x = raw.x() + s * (sm.x() - raw.x());
            double y = raw.y() + s * (sm.y() - raw.y());
            blended.add(new PixelPoint(x, y));
        }

        return Collections.unmodifiableList(blended);
    }

    /**
     * Calcule la distance curviligne minimale périodique d'un indice à l'ensemble des coins.
     *
     * @param idx     Indice courant le long du contour fermé.
     * @param corners Liste des indices de coins.
     * @param n       Nombre total de points du contour fermé.
     * @return Distance minimale en nombre de pas (pixels).
     */
    private double computeMinCornerDistance(int idx, List<Integer> corners, int n) {
        double minDist = Double.MAX_VALUE;
        for (int c : corners) {
            int d = Math.abs(idx - c);
            int periodicD = Math.min(d, n - d);
            if (periodicD < minDist) {
                minDist = periodicD;
            }
        }
        return minDist;
    }
}
