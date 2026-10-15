package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.Objects;

/**
 * Filtre maximum local 2D séparable en temps linéaire O(N) utilisant l'algorithme de file monotone.
 * <p>
 * Applique successivement un filtre glissant 1D horizontal puis vertical sur une fenêtre carrée
 * de taille impaire, puis masque strictement le résultat par le réseau routier actif.
 */
public class LocalMaxFilter {

    /**
     * Applique le filtre maximum local séparable sur la carte de distances avec masquage routier.
     *
     * @param input      Matrice de distances continue d'entrée (non nulle).
     * @param road       Masque binaire de voirie pour le masquage final (non nul).
     * @param windowSize Taille de la fenêtre carrée (doit être impaire et strictement positive).
     * @return Nouvelle instance de {@link DistanceMap} contenant les valeurs maximales locales.
     */
    public DistanceMap filter(DistanceMap input, BinaryMask road, int windowSize) {
        validateInputs(input, road, windowSize);
        int width = input.width();
        int height = input.height();
        int radius = windowSize / 2;

        float[] horizTemp = new float[width * height];
        applyHorizontalPass(input.data(), horizTemp, width, height, radius);

        float[] vertTemp = new float[width * height];
        applyVerticalPass(horizTemp, vertTemp, width, height, radius);

        float[] finalData = new float[width * height];
        applyRoadMasking(vertTemp, road, finalData, width, height);

        return new DistanceMap(width, height, finalData);
    }

    /**
     * Valide les dimensions, la présence des arguments et la validité de la taille de fenêtre.
     *
     * @param input      Matrice de distances.
     * @param road       Masque routier.
     * @param windowSize Taille de la fenêtre.
     */
    private void validateInputs(DistanceMap input, BinaryMask road, int windowSize) {
        Objects.requireNonNull(input, "input ne doit pas être nul.");
        Objects.requireNonNull(road, "road ne doit pas être nul.");
        if (road.getWidth() != input.width() || road.getHeight() != input.height()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre input et road.");
        }
        if (windowSize <= 0 || windowSize % 2 == 0) {
            throw new IllegalArgumentException("windowSize doit être un entier impair strictement positif.");
        }
    }

    /**
     * Effectue la passe 1D horizontale sur chaque ligne indépendamment.
     *
     * @param src    Données d'origine.
     * @param dst    Buffer de destination.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @param radius Rayon de la fenêtre glissante.
     */
    private void applyHorizontalPass(float[] src, float[] dst, int width, int height, int radius) {
        int[] deque = new int[width];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            slideMax1D(src, offset, 1, dst, offset, 1, width, radius, deque);
        }
    }

    /**
     * Effectue la passe 1D verticale sur chaque colonne indépendamment.
     *
     * @param src    Données intermédiaires après la passe horizontale.
     * @param dst    Buffer de destination.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @param radius Rayon de la fenêtre glissante.
     */
    private void applyVerticalPass(float[] src, float[] dst, int width, int height, int radius) {
        int[] deque = new int[height];
        for (int x = 0; x < width; x++) {
            slideMax1D(src, x, width, dst, x, width, height, radius, deque);
        }
    }

    /**
     * Algorithme générique de filtre maximum glissant 1D en temps linéaire O(N).
     *
     * @param src       Tableau source float[].
     * @param srcOffset Décalage de départ source.
     * @param srcStride Pas d'incrément entre éléments consécutifs source.
     * @param dst       Tableau récepteur float[].
     * @param dstOffset Décalage de départ destination.
     * @param dstStride Pas d'incrément destination.
     * @param len       Nombre d'éléments du vecteur 1D.
     * @param radius    Rayon de la fenêtre glissante.
     * @param deque     Buffer préalloué pour la file monotone d'indices.
     */
    private void slideMax1D(float[] src, int srcOffset, int srcStride,
                            float[] dst, int dstOffset, int dstStride,
                            int len, int radius, int[] deque) {
        int head = 0;
        int tail = 0;

        int initEnd = Math.min(radius, len - 1);
        for (int j = 0; j <= initEnd; j++) {
            float val = src[srcOffset + j * srcStride];
            while (tail > head && src[srcOffset + deque[tail - 1] * srcStride] <= val) {
                tail--;
            }
            deque[tail++] = j;
        }

        for (int i = 0; i < len; i++) {
            if (i > 0) {
                int rightIdx = i + radius;
                if (rightIdx < len) {
                    float val = src[srcOffset + rightIdx * srcStride];
                    while (tail > head && src[srcOffset + deque[tail - 1] * srcStride] <= val) {
                        tail--;
                    }
                    deque[tail++] = rightIdx;
                }
            }
            int leftBound = i - radius;
            while (head < tail && deque[head] < leftBound) {
                head++;
            }
            dst[dstOffset + i * dstStride] = src[srcOffset + deque[head] * srcStride];
        }
    }

    /**
     * Masque strictement le résultat en annulant les pixels n'appartenant pas à la chaussée.
     *
     * @param src    Données filtrées 2D.
     * @param road   Masque binaire de la voirie.
     * @param dst    Buffer final float[].
     * @param width  Largeur.
     * @param height Hauteur.
     */
    private void applyRoadMasking(float[] src, BinaryMask road, float[] dst, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                dst[idx] = road.get(x, y) ? src[idx] : 0.0f;
            }
        }
    }
}
