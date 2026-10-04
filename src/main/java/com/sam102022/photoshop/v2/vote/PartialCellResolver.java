package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Résolveur d'intersection des cellules partielles et générateur du masque d'amorçage T.
 * <p>
 * Conserve intégralement les cellules qualifiées INSIDE et restreint les cellules PARTIAL
 * à leur stricte intersection avec le polygone d'intention cartographique.
 * </p>
 */
public class PartialCellResolver {

    /**
     * Résultat immuable de la résolution contenant le masque global T et les masques individuels partiels.
     *
     * @param retainedMask Masque binaire d'amorçage T global regroupant parcelles pleines et partielles.
     * @param partialMasks Table associative associant chaque identifiant partiel à son masque restreint individuel.
     */
    public record ResolutionResult(
            BinaryMask retainedMask,
            Map<Integer, BinaryMask> partialMasks
    ) {
        /**
         * Valide les arguments et assure l'immutabilité du dictionnaire des masques partiels.
         *
         * @param retainedMask Masque binaire d'amorçage T global.
         * @param partialMasks Table des sous-masques binaires individuels par identifiant de cellule partielle.
         * @throws IllegalArgumentException si retainedMask ou partialMasks est null.
         */
        public ResolutionResult {
            if (retainedMask == null || partialMasks == null) {
                throw new IllegalArgumentException("Les composants de ResolutionResult ne peuvent pas être null.");
            }
            partialMasks = Map.copyOf(partialMasks);
        }
    }

    /**
     * Construit une nouvelle instance du résolveur de parcelles partielles.
     */
    public PartialCellResolver() {
        // Constructeur par défaut explicite
    }

    /**
     * Construit le masque d'amorçage complet T et les masques individuels des cellules partielles.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @param insideCellIds      Identifiants des cellules conservées à 100%.
     * @param partialCellIds     Identifiants des cellules à découpage partiel.
     * @return Résultat de résolution immuable.
     * @throws IllegalArgumentException si un argument est null ou en cas de divergence dimensionnelle.
     */
    public ResolutionResult resolve(
            CellLabelMap labelMap,
            BinaryMask croppedPolygonMask,
            Set<Integer> insideCellIds,
            Set<Integer> partialCellIds
    ) {
        validateInputs(labelMap, croppedPolygonMask, insideCellIds, partialCellIds);

        int width = labelMap.width();
        int height = labelMap.height();

        BinaryMask retainedMask = new BinaryMask(width, height);
        Map<Integer, BinaryMask> partialMasks = initializePartialMasks(width, height, partialCellIds);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = labelMap.getLabel(x, y);
                processPixel(x, y, label, croppedPolygonMask, insideCellIds, partialCellIds, retainedMask, partialMasks);
            }
        }

        return new ResolutionResult(retainedMask, partialMasks);
    }

    /**
     * Valide défensivement les paramètres d'entrée de la résolution.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param croppedPolygonMask Masque binaire du polygone d'intention.
     * @param insideCellIds      Ensemble des identifiants de cellules INSIDE.
     * @param partialCellIds     Ensemble des identifiants de cellules PARTIAL.
     * @throws IllegalArgumentException si un argument est null ou en cas d'incohérence dimensionnelle.
     */
    private void validateInputs(
            CellLabelMap labelMap,
            BinaryMask croppedPolygonMask,
            Set<Integer> insideCellIds,
            Set<Integer> partialCellIds
    ) {
        if (labelMap == null || croppedPolygonMask == null || insideCellIds == null || partialCellIds == null) {
            throw new IllegalArgumentException("Aucun argument de résolution ne peut être null.");
        }
        if (labelMap.width() != croppedPolygonMask.getWidth() || labelMap.height() != croppedPolygonMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence dimensionnelle entre labelMap ("
                    + labelMap.width() + "x" + labelMap.height() + ") et croppedPolygonMask ("
                    + croppedPolygonMask.getWidth() + "x" + croppedPolygonMask.getHeight() + ").");
        }
    }

    /**
     * Initialise les masques binaires individuels pour chacune des cellules partielles.
     *
     * @param width          Largeur des masques matriciels.
     * @param height         Hauteur des masques matriciels.
     * @param partialCellIds Ensemble des identifiants des cellules partielles.
     * @return Table associative mutable des masques partiels vierges.
     */
    private Map<Integer, BinaryMask> initializePartialMasks(
            int width,
            int height,
            Set<Integer> partialCellIds
    ) {
        Map<Integer, BinaryMask> partialMasks = new HashMap<>(partialCellIds.size());
        for (int partialId : partialCellIds) {
            partialMasks.put(partialId, new BinaryMask(width, height));
        }
        return partialMasks;
    }

    /**
     * Traite un pixel unitaire en affectant son état au masque d'amorçage T et au masque partiel correspondant.
     *
     * @param x                  Coordonnée X du pixel.
     * @param y                  Coordonnée Y du pixel.
     * @param label              Étiquette de la cellule au pixel (x, y).
     * @param croppedPolygonMask Masque binaire du polygone d'intention.
     * @param insideCellIds      Ensemble des identifiants des cellules INSIDE.
     * @param partialCellIds     Ensemble des identifiants des cellules PARTIAL.
     * @param retainedMask       Masque binaire d'amorçage T global en cours de construction.
     * @param partialMasks       Table associative des masques partiels individuels en cours d'alimentation.
     */
    private void processPixel(
            int x,
            int y,
            int label,
            BinaryMask croppedPolygonMask,
            Set<Integer> insideCellIds,
            Set<Integer> partialCellIds,
            BinaryMask retainedMask,
            Map<Integer, BinaryMask> partialMasks
    ) {
        if (label <= 0) {
            return;
        }
        if (insideCellIds.contains(label)) {
            retainedMask.set(x, y, true);
        } else if (partialCellIds.contains(label) && croppedPolygonMask.get(x, y)) {
            retainedMask.set(x, y, true);
            BinaryMask partialMask = partialMasks.get(label);
            if (partialMask != null) {
                partialMask.set(x, y, true);
            }
        }
    }
}
