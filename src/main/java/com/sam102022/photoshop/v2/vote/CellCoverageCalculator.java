package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculateur de surface d'intersection spatiale et de ratio de couverture
 * entre chaque cellule urbaine et le masque du polygone d'intention.
 * Effectue un parcours matriciel optimisé en complexité temporelle O(W * H).
 */
public class CellCoverageCalculator {

    /**
     * Constructeur par défaut du calculateur de couverture.
     */
    public CellCoverageCalculator() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Calcule le nombre de pixels d'intersection entre chaque cellule et le polygone d'intention.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @return Table associative associant chaque identifiant de cellule au nombre de pixels d'intersection.
     * @throws IllegalArgumentException si l'un des masques est null ou si leurs dimensions diffèrent.
     */
    public Map<Integer, Long> computeIntersections(CellLabelMap labelMap, BinaryMask croppedPolygonMask) {
        validateInputs(labelMap, croppedPolygonMask);

        int cellCount = labelMap.cellCount();
        long[] counts = new long[cellCount + 1];

        int width = labelMap.width();
        int height = labelMap.height();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (croppedPolygonMask.get(x, y)) {
                    int label = labelMap.getLabel(x, y);
                    if (label > 0 && label <= cellCount) {
                        counts[label]++;
                    }
                }
            }
        }

        Map<Integer, Long> result = new HashMap<>(cellCount);
        for (int i = 1; i <= cellCount; i++) {
            result.put(i, counts[i]);
        }
        return result;
    }

    /**
     * Calcule le ratio de couverture (intersection / surface totale) pour chaque cellule urbaine.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param cells              Liste des cellules urbaines segmentées.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @return Table associative associant chaque identifiant de cellule à son ratio de couverture compris entre 0.0 et 1.0.
     * @throws IllegalArgumentException si l'un des paramètres est null ou si les masques ont des dimensions incohérentes.
     */
    public Map<Integer, Double> computeCoverages(CellLabelMap labelMap, List<Cell> cells, BinaryMask croppedPolygonMask) {
        if (cells == null) {
            throw new IllegalArgumentException("La liste des cellules ne peut pas être null.");
        }
        Map<Integer, Long> intersections = computeIntersections(labelMap, croppedPolygonMask);
        Map<Integer, Double> coverages = new HashMap<>(cells.size());

        for (Cell cell : cells) {
            if (cell == null) {
                continue;
            }
            long inter = intersections.getOrDefault(cell.id(), 0L);
            double cov = cell.area() > 0 ? (double) inter / cell.area() : 0.0;
            coverages.put(cell.id(), Math.min(1.0, Math.max(0.0, cov)));
        }
        return coverages;
    }

    /**
     * Valide défensivement la présence et la compatibilité dimensionnelle des masques d'entrée.
     *
     * @param labelMap    Matrice d'étiquettes de cellules à valider.
     * @param polygonMask Masque binaire du polygone d'intention à valider.
     * @throws IllegalArgumentException si l'un des masques est null ou si les dimensions ne concordent pas.
     */
    private void validateInputs(CellLabelMap labelMap, BinaryMask polygonMask) {
        if (labelMap == null || polygonMask == null) {
            throw new IllegalArgumentException("Les masques labelMap et polygonMask ne peuvent pas être null.");
        }
        if (labelMap.width() != polygonMask.getWidth() || labelMap.height() != polygonMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence de dimensions : labelMap=" + labelMap.width() + "x"
                    + labelMap.height() + ", polygonMask=" + polygonMask.getWidth() + "x" + polygonMask.getHeight());
        }
    }
}
