package com.sam102022.photoshop.v2.vote;

/**
 * Diagnostic immuable du résultat du vote pour une cellule donnée.
 *
 * @param cellId           Identifiant unique de la cellule (1..N).
 * @param cellArea         Surface totale de la cellule en pixels.
 * @param intersectionArea Surface d'intersection entre la cellule et le polygone d'intention en pixels.
 * @param coverage         Ratio de couverture (intersectionArea / cellArea compris entre 0.0 et 1.0).
 * @param state            État qualifié résultant de la classification.
 */
public record CellDecision(
        int cellId,
        long cellArea,
        long intersectionArea,
        double coverage,
        CellState state
) {
    /**
     * Valide les invariants de la décision pour une cellule.
     *
     * @param cellId           Identifiant unique de la cellule.
     * @param cellArea         Surface totale de la cellule.
     * @param intersectionArea Surface d'intersection avec le polygone d'intention.
     * @param coverage         Ratio de couverture calculé.
     * @param state            État qualifié de la cellule.
     * @throws IllegalArgumentException si l'un des paramètres est invalide ou incohérent.
     */
    public CellDecision {
        if (cellId <= 0) {
            throw new IllegalArgumentException("L'identifiant de cellule doit être strictement positif : " + cellId);
        }
        if (cellArea <= 0) {
            throw new IllegalArgumentException("La surface de cellule doit être strictement positive : " + cellArea);
        }
        if (intersectionArea < 0 || intersectionArea > cellArea) {
            throw new IllegalArgumentException("Surface d'intersection invalide : " + intersectionArea
                    + " pour une cellule d'aire " + cellArea);
        }
        if (coverage < 0.0 || coverage > 1.000001) {
            throw new IllegalArgumentException("Ratio de couverture hors bornes [0.0, 1.0] : " + coverage);
        }
        if (state == null) {
            throw new IllegalArgumentException("L'état de la cellule ne peut pas être null.");
        }
    }
}
