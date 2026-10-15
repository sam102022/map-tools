package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires validant le calcul matriciel des surfaces d'intersection et ratios de couverture.
 */
@DisplayName("Tests unitaires du calculateur de couverture CellCoverageCalculator")
class CellCoverageCalculatorTest {

    /**
     * Valide le calcul exact des pixels d'intersection et ratios de couverture sur grille synthétique.
     */
    @Test
    @DisplayName("Calcul exact des surfaces d'intersection et ratios de couverture")
    void testComputeCoverages() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // Moitié gauche (x < 5) = Cellule 1 (50 px), Moitié droite (x >= 5) = Cellule 2 (50 px)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                labels[y * 10 + x] = (x < 5) ? 1 : 2;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 2);

        List<Cell> cells = List.of(
                new Cell(1, 50, 0, 0, 4, 9, new PixelPoint(2.0, 4.5)),
                new Cell(2, 50, 5, 0, 9, 9, new PixelPoint(7.0, 4.5))
        );

        // Polygone recouvrant entièrement la cellule 1 (50 px) et 10 px de la cellule 2 (x=5)
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 6; x++) {
                polygonMask.set(x, y, true);
            }
        }

        CellCoverageCalculator calculator = new CellCoverageCalculator();
        Map<Integer, Long> intersections = calculator.computeIntersections(labelMap, polygonMask);

        assertEquals(50L, intersections.get(1));
        assertEquals(10L, intersections.get(2));

        Map<Integer, Double> coverages = calculator.computeCoverages(labelMap, cells, polygonMask);
        assertEquals(1.0, coverages.get(1), 1e-6);
        assertEquals(0.20, coverages.get(2), 1e-6);
    }

    /**
     * Valide le rejet défensif en cas de dimensions incohérentes entre labelMap et polygonMask.
     */
    @Test
    @DisplayName("Rejet des masques de dimensions divergentes")
    void testDimensionMismatch() {
        CellLabelMap labelMap = new CellLabelMap(10, 10, new int[100], 0);
        BinaryMask polygonMask = new BinaryMask(12, 10);
        CellCoverageCalculator calculator = new CellCoverageCalculator();

        assertThrows(IllegalArgumentException.class, () -> calculator.computeIntersections(labelMap, polygonMask));
    }

    /**
     * Valide le rejet défensif lorsque les arguments passés sont nuls.
     */
    @Test
    @DisplayName("Rejet défensif en cas d'arguments nulls")
    void testNullArgumentsValidation() {
        CellCoverageCalculator calculator = new CellCoverageCalculator();
        CellLabelMap labelMap = new CellLabelMap(10, 10, new int[100], 0);
        BinaryMask polygonMask = new BinaryMask(10, 10);
        List<Cell> cells = List.of();

        assertThrows(IllegalArgumentException.class, () -> calculator.computeIntersections(null, polygonMask));
        assertThrows(IllegalArgumentException.class, () -> calculator.computeIntersections(labelMap, null));
        assertThrows(IllegalArgumentException.class, () -> calculator.computeCoverages(null, cells, polygonMask));
        assertThrows(IllegalArgumentException.class, () -> calculator.computeCoverages(labelMap, null, polygonMask));
        assertThrows(IllegalArgumentException.class, () -> calculator.computeCoverages(labelMap, cells, null));
    }
}
