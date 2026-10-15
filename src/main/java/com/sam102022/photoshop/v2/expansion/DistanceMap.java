package com.sam102022.photoshop.v2.expansion;

import java.util.Objects;

/**
 * Matrice 2D continue de distances réelles stockée sur tableau plat unidimensionnel float[].
 *
 * @param width  Largeur de la matrice en pixels (strictement positive).
 * @param height Hauteur de la matrice en pixels (strictement positive).
 * @param data   Tableau linéaire de dimensions width * height.
 */
public record DistanceMap(
        int width,
        int height,
        float[] data
) {
    /**
     * Constructeur canonique avec validation des dimensions et de la taille du buffer.
     *
     * @param width  Largeur de la matrice (> 0).
     * @param height Hauteur de la matrice (> 0).
     * @param data   Buffer 1D sous-jacent non nul de taille width * height.
     */
    public DistanceMap {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides pour DistanceMap.");
        }
        Objects.requireNonNull(data, "Le tableau de données ne doit pas être nul.");
        if (data.length != width * height) {
            throw new IllegalArgumentException("Taille du tableau data incohérente avec width * height.");
        }
    }

    /**
     * Récupère la valeur de distance au pixel spécifié.
     *
     * @param x Coordonnée X (colonne).
     * @param y Coordonnée Y (ligne).
     * @return Valeur flottante de distance.
     */
    public float get(int x, int y) {
        return data[y * width + x];
    }

    /**
     * Modifie la valeur de distance au pixel spécifié.
     *
     * @param x     Coordonnée X (colonne).
     * @param y     Coordonnée Y (ligne).
     * @param value Nouvelle valeur de distance.
     */
    public void set(int x, int y, float value) {
        data[y * width + x] = value;
    }
}
