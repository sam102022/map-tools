package com.sam102022.photoshop.v2.cell;

import java.util.Arrays;

/**
 * Matrice compacte de stockage des étiquettes entières de segmentation des cellules.
 * La valeur 0 désigne le réseau routier ou barrière infranchissable,
 * tandis que les valeurs strictement positives 1..cellCount désignent les cellules urbaines.
 *
 * @param width     Largeur en pixels de la matrice.
 * @param height    Hauteur en pixels de la matrice.
 * @param labels    Tableau plat linéaire des étiquettes (taille width * height).
 * @param cellCount Nombre total de cellules disjointes identifiées.
 */
public record CellLabelMap(
        int width,
        int height,
        int[] labels,
        int cellCount
) {

    /**
     * Valide les dimensions et crée une copie défensive du tableau d'étiquettes.
     *
     * @param width     Largeur de la matrice.
     * @param height    Hauteur de la matrice.
     * @param labels    Tableau plat d'étiquettes.
     * @param cellCount Nombre total de cellules.
     * @throws IllegalArgumentException si les dimensions sont non valides ou si le tableau est null ou incohérent.
     */
    public CellLabelMap {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions width et height doivent être strictement positives.");
        }
        if (labels == null) {
            throw new IllegalArgumentException("Le tableau d'étiquettes labels ne peut pas être null.");
        }
        if (labels.length != width * height) {
            throw new IllegalArgumentException("La taille du tableau labels (" + labels.length
                    + ") ne correspond pas à width * height (" + (width * height) + ").");
        }
        if (cellCount < 0) {
            throw new IllegalArgumentException("Le nombre de cellules cellCount ne peut pas être négatif.");
        }
        labels = Arrays.copyOf(labels, labels.length);
    }

    /**
     * Retourne l'étiquette de la cellule au pixel (x, y).
     *
     * @param x Abscisse locale.
     * @param y Ordonnée locale.
     * @return Identifiant de cellule (1..cellCount), 0 si route ou hors limites.
     */
    public int getLabel(int x, int y) {
        if (!isInBounds(x, y)) {
            return 0;
        }
        return labels[y * width + x];
    }

    /**
     * Indique si le pixel (x, y) appartient au réseau routier (label == 0).
     *
     * @param x Abscisse locale.
     * @param y Ordonnée locale.
     * @return true si le pixel est une route, false s'il s'agit d'une cellule ou est hors limites.
     */
    public boolean isRoad(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return labels[y * width + x] == 0;
    }

    /**
     * Indique si le pixel (x, y) appartient à une cellule urbaine (label > 0).
     *
     * @param x Abscisse locale.
     * @param y Ordonnée locale.
     * @return true si le pixel est une cellule, false s'il s'agit d'une route ou est hors limites.
     */
    public boolean isCell(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return labels[y * width + x] > 0;
    }

    /**
     * Vérifie si les coordonnées fournies sont à l'intérieur des limites de la matrice.
     *
     * @param x Abscisse à tester.
     * @param y Ordonnée à tester.
     * @return true si (x, y) est dans la matrice, false sinon.
     */
    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Fournit une copie défensive du tableau des étiquettes.
     *
     * @return Tableau plat d'étiquettes cloné.
     */
    @Override
    public int[] labels() {
        return Arrays.copyOf(labels, labels.length);
    }
}
