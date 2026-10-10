package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Record immuable représentant la portion localisée de chaussée séparant deux cellules urbaines adjacentes.
 *
 * @param id    Identifiant unique de l'interface routière.
 * @param cellA Identifiant de la première cellule (toujours strictement inférieur à cellB).
 * @param cellB Identifiant de la seconde cellule (toujours strictement supérieur à cellA).
 * @param mask  Masque binaire des pixels de chaussée composant cette interface.
 * @param area  Nombre total de pixels de chaussée affectés à cette interface.
 */
public record RoadInterface(
        int id,
        int cellA,
        int cellB,
        BinaryMask mask,
        long area
) {

    /**
     * Valide les invariants de l'interface routière.
     *
     * @param id    Identifiant de l'interface.
     * @param cellA Première cellule.
     * @param cellB Seconde cellule.
     * @param mask  Masque binaire.
     * @param area  Surface de l'interface.
     * @throws IllegalArgumentException si id <= 0, cellA >= cellB, cellA <= 0, mask est null ou area <= 0.
     */
    public RoadInterface {
        if (id <= 0) {
            throw new IllegalArgumentException("L'identifiant d'interface doit être strictement positif : " + id);
        }
        if (cellA <= 0 || cellB <= 0) {
            throw new IllegalArgumentException("Les identifiants de cellules doivent être strictement positifs : "
                    + cellA + ", " + cellB);
        }
        if (cellA >= cellB) {
            throw new IllegalArgumentException("L'invariant d'ordonnancement strict cellA < cellB n'est pas respecté : "
                    + cellA + " >= " + cellB);
        }
        if (mask == null) {
            throw new IllegalArgumentException("Le masque de l'interface routière ne peut pas être null.");
        }
        if (area <= 0) {
            throw new IllegalArgumentException("La surface de l'interface routière doit être strictement positive : " + area);
        }
    }

    /**
     * Indique si l'interface relie une cellule spécifique passée en paramètre.
     *
     * @param cellId Identifiant de la cellule à tester.
     * @return true si la cellule est cellA ou cellB, false sinon.
     */
    public boolean connects(int cellId) {
        return cellA == cellId || cellB == cellId;
    }

    /**
     * Retourne l'identifiant de la cellule opposée à celle fournie.
     *
     * @param cellId Identifiant de l'une des deux cellules connectées.
     * @return Identifiant de la cellule partenaire.
     * @throws IllegalArgumentException si cellId n'est ni cellA ni cellB.
     */
    public int getOppositeCell(int cellId) {
        if (cellId == cellA) {
            return cellB;
        }
        if (cellId == cellB) {
            return cellA;
        }
        throw new IllegalArgumentException("La cellule " + cellId + " n'appartient pas à l'interface " + id
                + " (connecte " + cellA + " et " + cellB + ").");
    }
}
