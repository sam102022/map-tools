package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.Objects;

/**
 * Filtre de lissage gaussien bidimensionnel séparable et de seuillage doux (Sprint 8).
 * Applique une convolution gaussienne discrète 2D à conditions aux limites réfléchies (miroir),
 * suivie d'une fonction de transfert linéaire contrastée clampée dans l'intervalle [0.0..1.0].
 */
public class SoftThresholdFilter {

    /**
     * Initialise le filtre à seuillage doux.
     */
    public SoftThresholdFilter() {
    }

    /**
     * Filtre un masque binaire en lui appliquant un flou gaussien et une fonction de transfert contrastée.
     *
     * @param allowedMask Masque binaire de la région autorisée (non nul).
     * @param sigma       Écart-type du filtre gaussien en pixels (&gt; 0).
     * @param stiffness   Raideur du seuillage doux (&ge; 1.0).
     * @return Matrice flottante 2D height x width des facteurs de modulation [0.0..1.0].
     */
    public float[][] filter(BinaryMask allowedMask, double sigma, double stiffness) {
        validateInputs(allowedMask, sigma, stiffness);

        int width = allowedMask.getWidth();
        int height = allowedMask.getHeight();

        float[] kernel = build1DGaussianKernel(sigma);
        int radius = (kernel.length - 1) / 2;

        // Passe 1 : Convolution horizontale
        float[][] temp = convolveHorizontal(allowedMask, kernel, radius, width, height);

        // Passe 2 : Convolution verticale et application de la fonction de transfert
        return convolveVerticalAndContrast(temp, kernel, radius, width, height, (float) stiffness);
    }

    /**
     * Valide les invariants des arguments du filtre.
     *
     * @param allowedMask Masque binaire d'entrée.
     * @param sigma       Écart-type gaussien.
     * @param stiffness   Raideur de contraste.
     */
    private void validateInputs(BinaryMask allowedMask, double sigma, double stiffness) {
        Objects.requireNonNull(allowedMask, "allowedMask ne doit pas être nul.");
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("sigma doit être strictement positif.");
        }
        if (stiffness < 1.0) {
            throw new IllegalArgumentException("stiffness doit être supérieur ou égal à 1.0.");
        }
    }

    /**
     * Construit le noyau gaussien 1D normalisé centré de rayon R = floor(4 * sigma + 0.5).
     *
     * @param sigma Écart-type gaussien (&gt; 0).
     * @return Tableau 1D de coefficients gaussiens normalisés de somme égale à 1.
     */
    private float[] build1DGaussianKernel(double sigma) {
        int radius = Math.max(1, (int) Math.floor(4.0 * sigma + 0.5));
        int size = 2 * radius + 1;
        float[] kernel = new float[size];
        double twoSigmaSq = 2.0 * sigma * sigma;
        double sum = 0.0;

        for (int i = -radius; i <= radius; i++) {
            double val = Math.exp(-(i * i) / twoSigmaSq);
            kernel[i + radius] = (float) val;
            sum += val;
        }

        for (int i = 0; i < size; i++) {
            kernel[i] /= (float) sum;
        }

        return kernel;
    }

    /**
     * Réalise la première passe de convolution 1D horizontale le long de chaque ligne.
     *
     * @param mask   Masque binaire d'entrée.
     * @param kernel Coefficients normalisés du filtre 1D.
     * @param radius Rayon de troncature du noyau.
     * @param width  Largeur du masque en pixels.
     * @param height Hauteur du masque en pixels.
     * @return Matrice intermédiaire de convolution horizontale.
     */
    private float[][] convolveHorizontal(BinaryMask mask, float[] kernel, int radius, int width, int height) {
        float[][] temp = new float[height][width];
        for (int y = 0; y < height; y++) {
            float[] outRow = temp[y];
            int leftEnd = Math.min(radius, width);
            int rightStart = Math.max(radius, width - radius);

            // Zone frontière gauche avec miroir
            for (int x = 0; x < leftEnd; x++) {
                float sum = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    int rx = reflectIndex(x + k, width);
                    if (mask.get(rx, y)) {
                        sum += kernel[k + radius];
                    }
                }
                outRow[x] = sum;
            }

            // Cœur intérieur direct sans calcul de miroir
            for (int x = leftEnd; x < rightStart; x++) {
                float sum = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    if (mask.get(x + k, y)) {
                        sum += kernel[k + radius];
                    }
                }
                outRow[x] = sum;
            }

            // Zone frontière droite avec miroir
            for (int x = rightStart; x < width; x++) {
                float sum = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    int rx = reflectIndex(x + k, width);
                    if (mask.get(rx, y)) {
                        sum += kernel[k + radius];
                    }
                }
                outRow[x] = sum;
            }
        }
        return temp;
    }

    /**
     * Réalise la seconde passe de convolution 1D verticale et projette le résultat dans l'espace contrasté.
     *
     * @param temp      Matrice intermédiaire issue de la convolution horizontale.
     * @param kernel    Coefficients normalisés du filtre 1D.
     * @param radius    Rayon de troncature du noyau.
     * @param width     Largeur en pixels.
     * @param height    Hauteur en pixels.
     * @param stiffness Facteur de pente du seuillage doux.
     * @return Matrice finale des facteurs d'atténuation bornés dans [0.0..1.0].
     */
    private float[][] convolveVerticalAndContrast(
            float[][] temp,
            float[] kernel,
            int radius,
            int width,
            int height,
            float stiffness
    ) {
        float[][] result = new float[height][width];
        float[][] cachedRows = new float[2 * radius + 1][];

        for (int y = 0; y < height; y++) {
            for (int k = -radius; k <= radius; k++) {
                int ry = reflectIndex(y + k, height);
                cachedRows[k + radius] = temp[ry];
            }

            float[] outRow = result[y];
            for (int x = 0; x < width; x++) {
                float sum = 0.0f;
                for (int k = -radius; k <= radius; k++) {
                    sum += kernel[k + radius] * cachedRows[k + radius][x];
                }
                float val = (sum - 0.5f) * stiffness + 0.5f;
                outRow[x] = clamp(val, 0.0f, 1.0f);
            }
        }
        return result;
    }

    /**
     * Calcule l'indice réfléchi en miroir demi-échantillon (half-sample symmetric)
     * correspondant à la condition aux limites de scipy.ndimage (mode 'reflect').
     *
     * @param index Coordonnée pouvant être en dehors des bornes.
     * @param size  Dimension totale du signal (doit être &gt; 0).
     * @return Coordonnée valide ramenée dans [0..size-1].
     */
    static int reflectIndex(int index, int size) {
        if (size <= 1) {
            return 0;
        }
        int i = index;
        while (i < 0 || i >= size) {
            if (i < 0) {
                i = -i - 1;
            } else {
                i = 2 * size - 1 - i;
            }
        }
        return i;
    }

    /**
     * Borne une valeur flottante entre un seuil minimal et un seuil maximal.
     *
     * @param value Valeur à borner.
     * @param min   Borne inférieure.
     * @param max   Borne supérieure.
     * @return Valeur bornée dans [min..max].
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
