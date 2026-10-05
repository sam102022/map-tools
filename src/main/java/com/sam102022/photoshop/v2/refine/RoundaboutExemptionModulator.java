package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.List;
import java.util.Objects;

/**
 * Modulateur d'exemption spectrale des carrefours giratoires (Sprint 8).
 * Protège les anneaux et îlots des ronds-points substitués par un bouclier
 * interpolé en smoothstep cubique C1 entre les rayons normés FZ0 et FZ1.
 */
public class RoundaboutExemptionModulator {

    /**
     * Initialise le modulateur d'exemption.
     */
    public RoundaboutExemptionModulator() {
    }

    /**
     * Applique la modulation de protection des ronds-points sur la matrice des facteurs spectraux.
     *
     * @param rawFactor              Matrice flottante 2D height x width des facteurs bruts (non nulle).
     * @param substitutedRoundabouts Liste des ronds-points substitués à protéger (non nulle).
     * @param fz0                    Rayon normé interne d'exemption totale (&gt; 0).
     * @param fz1                    Rayon normé externe de transition smoothstep (&gt; fz0).
     * @return Nouvelle matrice flottante 2D protégée.
     */
    public float[][] modulate(
            float[][] rawFactor,
            List<Roundabout> substitutedRoundabouts,
            double fz0,
            double fz1
    ) {
        validateInputs(rawFactor, substitutedRoundabouts, fz0, fz1);

        int height = rawFactor.length;
        int width = rawFactor[0].length;

        if (substitutedRoundabouts.isEmpty()) {
            return copyFactor(rawFactor, width, height);
        }

        float[][] zw = new float[height][width];
        for (Roundabout rb : substitutedRoundabouts) {
            accumulateRoundaboutShield(zw, rb.exteriorEllipse(), fz0, fz1, width, height);
        }

        return applyShield(rawFactor, zw, width, height);
    }

    /**
     * Valide l'intégrité et les intervalles des paramètres d'entrée.
     *
     * @param rawFactor              Matrice flottante 2D.
     * @param substitutedRoundabouts Liste des ronds-points.
     * @param fz0                    Seuil interne.
     * @param fz1                    Seuil externe.
     */
    private void validateInputs(
            float[][] rawFactor,
            List<Roundabout> substitutedRoundabouts,
            double fz0,
            double fz1
    ) {
        Objects.requireNonNull(rawFactor, "rawFactor ne doit pas être nul.");
        Objects.requireNonNull(substitutedRoundabouts, "substitutedRoundabouts ne doit pas être nulle.");
        if (rawFactor.length == 0 || rawFactor[0] == null || rawFactor[0].length == 0) {
            throw new IllegalArgumentException("rawFactor ne doit pas être une matrice vide.");
        }
        if (fz0 <= 0.0) {
            throw new IllegalArgumentException("fz0 doit être strictement positif.");
        }
        if (fz1 <= fz0) {
            throw new IllegalArgumentException("fz1 doit être strictement supérieur à fz0.");
        }
    }

    /**
     * Calcule et accumule le champ de protection smoothstep cubique d'un rond-point dans la matrice zw.
     *
     * @param zw      Matrice d'accumulation du bouclier.
     * @param ellipse Modèle d'ellipse extérieure du rond-point.
     * @param fz0     Rayon interne d'exemption totale.
     * @param fz1     Rayon externe de fin de transition.
     * @param width   Largeur de la matrice.
     * @param height  Hauteur de la matrice.
     */
    private void accumulateRoundaboutShield(
            float[][] zw,
            EllipseModel ellipse,
            double fz0,
            double fz1,
            int width,
            int height
    ) {
        int rad = (int) Math.floor(3.0 * ellipse.a()) + 4;
        int ye = (int) Math.round(ellipse.yc());
        int xe = (int) Math.round(ellipse.xc());

        int ya = Math.max(0, ye - rad);
        int yb = Math.min(height, ye + rad + 1);
        int xa = Math.max(0, xe - rad);
        int xb = Math.min(width, xe + rad + 1);

        double invDelta = 1.0 / (fz1 - fz0);

        for (int y = ya; y < yb; y++) {
            for (int x = xa; x < xb; x++) {
                PixelPoint u = ellipse.toUnitCircle(new PixelPoint(x, y));
                double rho = Math.hypot(u.x(), u.y());
                double t = clamp((fz1 - rho) * invDelta, 0.0, 1.0);
                float s = (float) (t * t * (3.0 - 2.0 * t));
                if (s > zw[y][x]) {
                    zw[y][x] = s;
                }
            }
        }
    }

    /**
     * Combine la matrice brute et le bouclier zw pour sanctuariser les carrefours giratoires :
     * Lorsque le bouclier est total (zw = 1.0), le facteur vaut 1.0 ;
     * en l'absence de bouclier (zw = 0.0), le facteur d'entrée est scrupuleusement conservé.
     *
     * @param rawFactor Matrice brute d'entrée.
     * @param zw        Matrice du bouclier de protection [0..1].
     * @param width     Largeur de la matrice.
     * @param height    Hauteur de la matrice.
     * @return Matrice protégée.
     */
    private float[][] applyShield(float[][] rawFactor, float[][] zw, int width, int height) {
        float[][] protectedFactor = new float[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float raw = rawFactor[y][x];
                float shield = zw[y][x];
                protectedFactor[y][x] = raw + shield * (1.0f - raw);
            }
        }
        return protectedFactor;
    }

    /**
     * Clone défensivement une matrice 2D flottante.
     *
     * @param source Matrice d'origine.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Copie indépendante.
     */
    private float[][] copyFactor(float[][] source, int width, int height) {
        float[][] copy = new float[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, width);
        }
        return copy;
    }

    /**
     * Borne une valeur double dans [min..max].
     *
     * @param value Valeur d'entrée.
     * @param min   Borne minimale.
     * @param max   Borne maximale.
     * @return Valeur bornée.
     */
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
