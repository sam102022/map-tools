package com.sam102022.photoshop.core.model;

import java.util.Arrays;

/**
 * Représentation matricielle 2D optimisée d'un masque binaire de sélection cartographique.
 * <p>
 * Chaque cellule correspond à un pixel actif ({@code true}) ou inactif ({@code false}).
 * Les données sont stockées en mémoire sous forme d'un tableau unidimensionnel aplati (row-major)
 * pour des performances d'accès et d'itération optimales.
 * </p>
 */
public class BinaryMask {
    private final int width;
    private final int height;
    private final boolean[] data;

    /**
     * Initialise un masque binaire vide (tous les pixels inactifs) aux dimensions spécifiées.
     *
     * @param width  Largeur de la matrice en pixels (> 0).
     * @param height Hauteur de la matrice en pixels (> 0).
     * @throws IllegalArgumentException si width ou height est inférieur ou égal à 0.
     */
    public BinaryMask(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new boolean[width * height];
    }

    /**
     * Initialise un masque binaire à partir d'un tableau aplati de données préexistantes.
     * Les données fournies sont copiées défensivement.
     *
     * @param width  Largeur de la matrice en pixels (> 0).
     * @param height Hauteur de la matrice en pixels (> 0).
     * @param data   Tableau linéaire booléen de taille {@code width * height}.
     * @throws IllegalArgumentException si les dimensions sont non positives ou si le tableau est nul ou de taille incohérente.
     */
    public BinaryMask(int width, int height, boolean[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (data == null || data.length != width * height) {
            throw new IllegalArgumentException("Les données du masque ne correspondent pas aux dimensions spécifiées.");
        }
        this.width = width;
        this.height = height;
        this.data = Arrays.copyOf(data, data.length);
    }

    /**
     * Obtient la largeur de la matrice en pixels.
     *
     * @return Largeur en pixels.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Obtient la hauteur de la matrice en pixels.
     *
     * @return Hauteur en pixels.
     */
    public int getHeight() {
        return height;
    }

    /**
     * Vérifie si les coordonnées spécifiées se situent à l'intérieur des limites de la matrice.
     *
     * @param x Coordonnée horizontale X.
     * @param y Coordonnée verticale Y.
     * @return Vrai si (x, y) est dans [0, width[ et [0, height[.
     */
    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Retourne l'état du pixel aux coordonnées (x, y).
     *
     * @param x Coordonnée horizontale X.
     * @param y Coordonnée verticale Y.
     * @return Vrai si le pixel est actif, faux s'il est inactif ou situé hors limites.
     */
    public boolean get(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return data[y * width + x];
    }

    /**
     * Modifie l'état du pixel aux coordonnées (x, y). L'opération est ignorée si les coordonnées sont hors limites.
     *
     * @param x     Coordonnée horizontale X.
     * @param y     Coordonnée verticale Y.
     * @param value Nouvel état booléen du pixel.
     */
    public void set(int x, int y, boolean value) {
        if (isInBounds(x, y)) {
            data[y * width + x] = value;
        }
    }

    /**
     * Dénombre le nombre total de pixels actifs dans le masque.
     *
     * @return Nombre de pixels dont la valeur est {@code true}.
     */
    public int countActivePixels() {
        int count = 0;
        for (boolean b : data) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    /**
     * Crée une copie défensive indépendante de ce masque binaire.
     *
     * @return Nouveau masque binaire identique.
     */
    public BinaryMask copy() {
        return new BinaryMask(width, height, this.data);
    }

    /**
     * Effectue une opération booléenne OU (union) pixel par pixel avec un autre masque de même dimension.
     *
     * @param other Autre masque binaire compatible.
     * @return Nouveau masque binaire résultant de l'union.
     * @throws IllegalArgumentException si other est null ou de dimensions différentes.
     */
    public BinaryMask or(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] || other.data[i];
        }
        return result;
    }

    /**
     * Effectue une opération booléenne ET (intersection) pixel par pixel avec un autre masque de même dimension.
     *
     * @param other Autre masque binaire compatible.
     * @return Nouveau masque binaire résultant de l'intersection.
     * @throws IllegalArgumentException si other est null ou de dimensions différentes.
     */
    public BinaryMask and(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] && other.data[i];
        }
        return result;
    }

    /**
     * Effectue une opération booléenne NON (inversion) pixel par pixel.
     *
     * @return Nouveau masque binaire inversé.
     */
    public BinaryMask not() {
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = !this.data[i];
        }
        return result;
    }

    /**
     * Valide que le masque fourni en paramètre possède des dimensions strictement identiques à l'instance courante.
     *
     * @param other Autre masque binaire à valider.
     * @throws IllegalArgumentException si other est null ou possède des dimensions divergentes.
     */
    private void validateSameDimensions(BinaryMask other) {
        if (other == null || other.width != this.width || other.height != this.height) {
            throw new IllegalArgumentException("Dimensions incompatibles pour l'opération matricielle.");
        }
    }
}
