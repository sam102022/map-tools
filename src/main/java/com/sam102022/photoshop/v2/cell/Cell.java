package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.v2.geometry.PixelPoint;

/**
 * Record immuable représentant une cellule urbaine / parcelle issue de la segmentation topologique.
 *
 * @param id       Identifiant unique strictement positif de la cellule (1..N).
 * @param area     Surface totale en pixels de la cellule.
 * @param minX     Abscisse minimale occupée par la cellule dans le repère local.
 * @param minY     Ordonnée minimale occupée par la cellule dans le repère local.
 * @param maxX     Abscisse maximale occupée par la cellule dans le repère local.
 * @param maxY     Ordonnée maximale occupée par la cellule dans le repère local.
 * @param centroid Centre de gravité barycentrique calculé sur l'ensemble des pixels de la cellule.
 */
public record Cell(
        int id,
        long area,
        int minX,
        int minY,
        int maxX,
        int maxY,
        PixelPoint centroid
) {

    /**
     * Valide les invariants défensifs de la cellule.
     *
     * @param id       Identifiant unique.
     * @param area     Surface en pixels.
     * @param minX     Abscisse minimale.
     * @param minY     Ordonnée minimale.
     * @param maxX     Abscisse maximale.
     * @param maxY     Ordonnée maximale.
     * @param centroid Centre géométrique.
     * @throws IllegalArgumentException si l'identifiant est <= 0, l'aire <= 0, les bornes incohérentes ou le centroïde null.
     */
    public Cell {
        if (id <= 0) {
            throw new IllegalArgumentException("L'identifiant de cellule doit être strictement positif : " + id);
        }
        if (area <= 0) {
            throw new IllegalArgumentException("La surface de la cellule doit être strictement positive : " + area);
        }
        if (minX > maxX || minY > maxY) {
            throw new IllegalArgumentException("Bornes rectangulaires incohérentes : [" + minX + ".." + maxX
                    + ", " + minY + ".." + maxY + "]");
        }
        if (centroid == null) {
            throw new IllegalArgumentException("Le centroïde de la cellule ne peut pas être null.");
        }
    }

    /**
     * Calcule la largeur de la boîte englobante de la cellule.
     *
     * @return Largeur en pixels (maxX - minX + 1).
     */
    public int width() {
        return maxX - minX + 1;
    }

    /**
     * Calcule la hauteur de la boîte englobante de la cellule.
     *
     * @return Hauteur en pixels (maxY - minY + 1).
     */
    public int height() {
        return maxY - minY + 1;
    }
}
