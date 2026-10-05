package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.v2.cell.CropWindow;

import java.util.Objects;

/**
 * Matrice flottante 2D immuable encapsulant les facteurs d'atténuation d'opacité [0.0..1.0]
 * issus du filtrage spectral et de la modulation par protection des ronds-points.
 *
 * @param width      Largeur de la matrice locale en pixels.
 * @param height     Hauteur de la matrice locale en pixels.
 * @param factor     Matrice 2D des facteurs d'atténuation compris entre 0.0f et 1.0f.
 * @param cropWindow Fenêtre de recadrage d'origine pour l'ancrage global sur l'image source.
 */
public record AlphaRefinementMap(
        int width,
        int height,
        float[][] factor,
        CropWindow cropWindow
) {

    /**
     * Valide les invariants et effectue une copie défensive de la matrice de facteurs.
     *
     * @param width      Largeur de la matrice (doit être &gt; 0).
     * @param height     Hauteur de la matrice (doit être &gt; 0).
     * @param factor     Matrice 2D non nulle de dimensions height x width.
     * @param cropWindow Fenêtre de recadrage non nulle.
     * @throws IllegalArgumentException si les dimensions sont invalides ou incohérentes.
     * @throws NullPointerException     si factor ou cropWindow est nul.
     */
    public AlphaRefinementMap {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions width et height doivent être strictement positives.");
        }
        Objects.requireNonNull(factor, "factor ne doit pas être nul.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nul.");
        if (factor.length != height) {
            throw new IllegalArgumentException("Les dimensions du tableau factor ne correspondent pas à width et height.");
        }
        float[][] copy = new float[height][width];
        for (int y = 0; y < height; y++) {
            if (factor[y] == null || factor[y].length != width) {
                throw new IllegalArgumentException("Les dimensions du tableau factor ne correspondent pas à width et height.");
            }
            System.arraycopy(factor[y], 0, copy[y], 0, width);
        }
        factor = copy;
    }

    /**
     * Renvoie une copie défensive de la matrice 2D des facteurs d'atténuation.
     *
     * @return un tableau bidimensionnel de flottants copie conforme de la matrice interne.
     */
    @Override
    public float[][] factor() {
        float[][] copy = new float[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(factor[y], 0, copy[y], 0, width);
        }
        return copy;
    }

    /**
     * Récupère le facteur de modulation pour une coordonnée locale (x, y).
     * Si les coordonnées se situent en dehors des bornes [0..width-1] x [0..height-1],
     * la méthode renvoie 0.0f de manière sécurisée.
     *
     * @param x Abscisse locale relative à la fenêtre de recadrage.
     * @param y Ordonnée locale relative à la fenêtre de recadrage.
     * @return le coefficient d'atténuation spectral à cette position, ou 0.0f si hors limites.
     */
    public float factorAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return 0.0f;
        }
        return factor[y][x];
    }
}
