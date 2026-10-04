package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Résultat immuable de la sélection des cellules et masque d'amorçage pour le recalage.
 *
 * @param insideCellIds    Identifiants des cellules retenues à 100%.
 * @param outsideCellIds   Identifiants des cellules rejetées à 100%.
 * @param partialCellIds   Identifiants des cellules à découpage partiel.
 * @param decisions        Table des diagnostics indexée par identifiant de cellule.
 * @param retainedMask     Masque binaire complet T (cellules INSIDE et portions retenues des PARTIAL).
 * @param partialCellMasks Sous-masques binaires individuels de chaque cellule partielle.
 */
public record CellSelection(
        Set<Integer> insideCellIds,
        Set<Integer> outsideCellIds,
        Set<Integer> partialCellIds,
        Map<Integer, CellDecision> decisions,
        BinaryMask retainedMask,
        Map<Integer, BinaryMask> partialCellMasks
) {
    /**
     * Valide et crée des copies défensives non modifiables des ensembles et dictionnaires.
     *
     * @param insideCellIds    Identifiants des cellules retenues à 100%.
     * @param outsideCellIds   Identifiants des cellules rejetées à 100%.
     * @param partialCellIds   Identifiants des cellules à découpage partiel.
     * @param decisions        Table des diagnostics par cellule.
     * @param retainedMask     Masque binaire complet T.
     * @param partialCellMasks Sous-masques binaires de chaque cellule partielle.
     * @throws IllegalArgumentException si un composant obligatoire est null.
     */
    public CellSelection {
        if (insideCellIds == null || outsideCellIds == null || partialCellIds == null
                || decisions == null || retainedMask == null || partialCellMasks == null) {
            throw new IllegalArgumentException("Les composants de CellSelection ne peuvent pas être null.");
        }
        insideCellIds = Set.copyOf(insideCellIds);
        outsideCellIds = Set.copyOf(outsideCellIds);
        partialCellIds = Set.copyOf(partialCellIds);
        decisions = Map.copyOf(decisions);
        partialCellMasks = Collections.unmodifiableMap(Map.copyOf(partialCellMasks));
    }

    /**
     * Indique si une cellule est retenue (soit INSIDE à 100%, soit PARTIAL restreinte).
     *
     * @param cellId Identifiant de la cellule à vérifier.
     * @return {@code true} si la cellule est retenue totalement ou partiellement, sinon {@code false}.
     */
    public boolean isRetained(int cellId) {
        return insideCellIds.contains(cellId) || partialCellIds.contains(cellId);
    }

    /**
     * Recherche la décision diagnostique associée à une cellule donnée.
     *
     * @param cellId Identifiant de la cellule recherchée.
     * @return Un {@link Optional} contenant la décision si trouvée, sinon vide.
     */
    public Optional<CellDecision> findDecision(int cellId) {
        return Optional.ofNullable(decisions.get(cellId));
    }
}
