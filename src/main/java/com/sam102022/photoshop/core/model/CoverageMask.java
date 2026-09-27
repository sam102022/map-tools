package com.sam102022.photoshop.core.model;

/**
 * Modèle matriciel 2D de couverture géométrique sub-pixel continue [0..255].
 * <p>
 * Chaque cellule représente la fraction surfacique d'un pixel recouverte par le polygone détouré,
 * allant de {@code 0} (pixel entièrement extérieur / transparent) à {@code 255} (pixel entièrement intérieur / opaque).
 * Les valeurs intermédiaires correspondent aux transitions douces anti-aliasées sur la frontière.
 * </p>
 */
public final class CoverageMask {
    private final int width;
    private final int height;
    private final byte[] data;

    /**
     * Initialise un masque de couverture vide (tous les pixels à 0) aux dimensions spécifiées.
     *
     * @param width  Largeur de la matrice en pixels (> 0).
     * @param height Hauteur de la matrice en pixels (> 0).
     * @throws IllegalArgumentException si les dimensions sont non positives ou dépassent la capacité mémoire.
     */
    public CoverageMask(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        long size = (long) width * height;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Le masque est trop grand : " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new byte[(int) size];
    }

    /**
     * Constructeur interne créant un masque de couverture à partir d'un tampon d'octets direct.
     *
     * @param width  Largeur de la matrice en pixels.
     * @param height Hauteur de la matrice en pixels.
     * @param data   Tableau linéaire d'octets contenant les valeurs de couverture.
     */
    private CoverageMask(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
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
     * Récupère la valeur de couverture géométrique non signée [0..255] au pixel (x, y).
     *
     * @param x Coordonnée horizontale X.
     * @param y Coordonnée verticale Y.
     * @return Valeur de couverture dans [0..255].
     * @throws IndexOutOfBoundsException si les coordonnées sont hors limites.
     */
    public int get(int x, int y) {
        checkBounds(x, y);
        return data[y * width + x] & 0xFF;
    }

    /**
     * Définit la valeur de couverture au pixel (x, y) en appliquant un bornage automatique dans [0..255].
     *
     * @param x     Coordonnée horizontale X.
     * @param y     Coordonnée verticale Y.
     * @param value Valeur de couverture à assigner (automatiquement bornée entre 0 et 255).
     * @throws IndexOutOfBoundsException si les coordonnées sont hors limites.
     */
    public void set(int x, int y, int value) {
        checkBounds(x, y);
        data[y * width + x] = (byte) Math.clamp(value, 0, 255);
    }

    /**
     * Convertit ce masque de couverture continue en masque binaire en appliquant un seuil de coupure.
     * Tout pixel ayant une couverture supérieure ou égale au seuil devient actif ({@code true}).
     *
     * @param threshold Seuil d'activation binaire compris entre 0 et 255 inclus.
     * @return Nouveau masque binaire seuillé.
     * @throws IllegalArgumentException si le seuil est hors de l'intervalle [0..255].
     */
    public BinaryMask toBinaryMask(int threshold) {
        if (threshold < 0 || threshold > 255) {
            throw new IllegalArgumentException("Le seuil doit être compris entre 0 et 255 : " + threshold);
        }
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            if ((data[i] & 0xFF) >= threshold) {
                result.set(i % width, i / width, true);
            }
        }
        return result;
    }

    /**
     * Convertit un masque binaire existant en masque de couverture continue.
     * Chaque pixel actif est promu à la valeur opaque maximale (255) et chaque pixel inactif à 0.
     *
     * @param mask Masque binaire d'origine.
     * @return Nouveau masque de couverture continue correspondant.
     * @throws IllegalArgumentException si mask est null.
     */
    public static CoverageMask fromBinaryMask(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque binaire ne peut pas être null.");
        }
        CoverageMask result = new CoverageMask(mask.getWidth(), mask.getHeight());
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, 255);
                }
            }
        }
        return result;
    }

    /**
     * Combine ce masque de couverture avec un autre de même dimension en retenant pixel par pixel la valeur maximale.
     *
     * @param other Autre masque de couverture compatible.
     * @return Nouveau masque de couverture combiné.
     * @throws IllegalArgumentException si other est null ou possède des dimensions divergentes.
     */
    public CoverageMask max(CoverageMask other) {
        if (other == null || width != other.width || height != other.height) {
            throw new IllegalArgumentException("Dimensions incompatibles pour la combinaison des masques de couverture.");
        }
        byte[] combined = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            combined[i] = (byte) Math.max(data[i] & 0xFF, other.data[i] & 0xFF);
        }
        return new CoverageMask(width, height, combined);
    }

    /**
     * Valide que les coordonnées fournies se situent strictement à l'intérieur de la grille matricielle.
     *
     * @param x Coordonnée horizontale X.
     * @param y Coordonnée verticale Y.
     * @throws IndexOutOfBoundsException si (x, y) est hors limites.
     */
    private void checkBounds(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Coordonnées hors limites : (" + x + ", " + y + ") pour " + width + "x" + height);
        }
    }
}
