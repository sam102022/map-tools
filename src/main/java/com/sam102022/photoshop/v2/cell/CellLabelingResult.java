package com.sam102022.photoshop.v2.cell;

import java.util.List;

/**
 * Résultat immuable de l'étiquetage en composantes connexes des cellules.
 *
 * @param labelMap Matrice d'étiquettes des cellules (0 pour route, 1..N pour les cellules).
 * @param cells    Liste immuable de toutes les cellules identifiées avec leurs métadonnées géométriques.
 */
public record CellLabelingResult(
        CellLabelMap labelMap,
        List<Cell> cells
) {

    /**
     * Valide les invariants du résultat de segmentation.
     *
     * @param labelMap Matrice d'étiquettes.
     * @param cells    Liste des cellules.
     * @throws IllegalArgumentException si l'un des paramètres est null ou si la taille ne concorde pas.
     */
    public CellLabelingResult {
        if (labelMap == null) {
            throw new IllegalArgumentException("La matrice de labels ne peut pas être null.");
        }
        if (cells == null) {
            throw new IllegalArgumentException("La liste de cellules ne peut pas être null.");
        }
        if (labelMap.cellCount() != cells.size()) {
            throw new IllegalArgumentException("Le nombre de cellules dans labelMap (" + labelMap.cellCount()
                    + ") ne concorde pas avec la taille de la liste (" + cells.size() + ").");
        }
        cells = List.copyOf(cells);
    }
}
