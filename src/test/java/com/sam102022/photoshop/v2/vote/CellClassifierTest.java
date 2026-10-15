package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires validant la qualification des cellules urbaines en INSIDE, PARTIAL et OUTSIDE
 * selon les seuils configurés de la politique de sélection.
 */
@DisplayName("Tests unitaires du classificateur CellClassifier")
class CellClassifierTest {

    /**
     * Valide la classification nominale et le comportement aux bornes exactes des seuils.
     */
    @Test
    @DisplayName("Classification selon les seuils : INSIDE, PARTIAL et OUTSIDE")
    void testClassification() {
        List<Cell> cells = List.of(
                new Cell(1, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(2, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(3, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(4, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(5, 100, 0, 0, 9, 9, new PixelPoint(5, 5))
        );

        Map<Integer, Long> intersections = Map.of(
                1, 75L, // cov = 0.75 -> INSIDE
                2, 60L, // cov = 0.60 -> INSIDE (borne exacte)
                3, 30L, // cov = 0.30 -> PARTIAL
                4, 5L,  // cov = 0.05 -> OUTSIDE (borne exacte)
                5, 2L   // cov = 0.02 -> OUTSIDE
        );

        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy(); // 0.60 / 0.05
        CellClassifier classifier = new CellClassifier();

        Map<Integer, CellDecision> decisions = classifier.classify(cells, intersections, policy);

        assertEquals(CellState.INSIDE, decisions.get(1).state());
        assertEquals(CellState.INSIDE, decisions.get(2).state());
        assertEquals(CellState.PARTIAL, decisions.get(3).state());
        assertEquals(CellState.OUTSIDE, decisions.get(4).state());
        assertEquals(CellState.OUTSIDE, decisions.get(5).state());
    }

    /**
     * Valide le rejet défensif en cas d'arguments nulls.
     */
    @Test
    @DisplayName("Rejet des arguments nulls")
    void testNullArgumentsValidation() {
        CellClassifier classifier = new CellClassifier();
        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy();
        List<Cell> cells = List.of(new Cell(1, 10, 0, 0, 2, 2, new PixelPoint(1, 1)));
        Map<Integer, Long> intersections = Map.of(1, 5L);

        assertThrows(IllegalArgumentException.class, () -> classifier.classify(null, intersections, policy));
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(cells, null, policy));
        assertThrows(IllegalArgumentException.class, () -> classifier.classify(cells, intersections, null));
    }
}
