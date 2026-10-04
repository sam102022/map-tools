package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'étiqueteur CellLabeler en 4-connexité stricte.
 */
class CellLabelerTest {

    @Test
    @DisplayName("Deux cellules étanches séparées par une route horizontale")
    void testTwoCellsSeparatedByRoad() {
        BinaryMask road = new BinaryMask(5, 5);
        // Ligne médiane (y = 2) = route
        for (int x = 0; x < 5; x++) {
            road.set(x, 2, true);
        }

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult result = labeler.label(road);

        assertEquals(2, result.labelMap().cellCount());
        assertEquals(2, result.cells().size());

        Cell cellTop = result.cells().get(0);
        Cell cellBottom = result.cells().get(1);

        assertEquals(10, cellTop.area());    // 5 x 2
        assertEquals(10, cellBottom.area()); // 5 x 2

        assertEquals(0, cellTop.minY());
        assertEquals(1, cellTop.maxY());
        assertEquals(3, cellBottom.minY());
        assertEquals(4, cellBottom.maxY());
    }

    @Test
    @DisplayName("Preuve de la 4-connexité : deux pixels diagonaux ne fusionnent PAS")
    void testStrictFourConnectivityNoDiagonalBridge() {
        // Matrice 2x2 en damier :
        // (0,0) = cell, (1,0) = road
        // (0,1) = road, (1,1) = cell
        BinaryMask road = new BinaryMask(2, 2);
        road.set(1, 0, true);
        road.set(0, 1, true);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult result = labeler.label(road);

        // En 4-connexité, (0,0) et (1,1) ne sont PAS reliés -> 2 cellules distinctes !
        assertEquals(2, result.labelMap().cellCount(),
                "En 4-connexité, les pixels diagonaux doivent former 2 cellules distinctes.");
        assertEquals(2, result.cells().size());

        int label00 = result.labelMap().getLabel(0, 0);
        int label11 = result.labelMap().getLabel(1, 1);

        assertTrue(label00 > 0);
        assertTrue(label11 > 0);
        assertNotEquals(label00, label11, "Les deux pixels en diagonale ne doivent pas avoir le même label.");
    }

    @Test
    @DisplayName("Masque entièrement route -> 0 cellule")
    void testAllRoad() {
        BinaryMask road = new BinaryMask(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                road.set(x, y, true);
            }
        }

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult result = labeler.label(road);

        assertEquals(0, result.labelMap().cellCount());
        assertTrue(result.cells().isEmpty());
    }

    @Test
    @DisplayName("Masque entièrement ouvert (aucune route) -> 1 cellule de surface totale")
    void testAllOpen() {
        BinaryMask road = new BinaryMask(10, 8);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult result = labeler.label(road);

        assertEquals(1, result.labelMap().cellCount());
        assertEquals(1, result.cells().size());

        Cell cell = result.cells().get(0);
        assertEquals(80, cell.area());
        assertEquals(0, cell.minX());
        assertEquals(9, cell.maxX());
        assertEquals(0, cell.minY());
        assertEquals(7, cell.maxY());
        assertEquals(4.5, cell.centroid().x(), 1e-6);
        assertEquals(3.5, cell.centroid().y(), 1e-6);
    }

    @Test
    @DisplayName("Rejet d'un masque null")
    void testNullMask() {
        CellLabeler labeler = new CellLabeler();
        assertThrows(IllegalArgumentException.class, () -> labeler.label(null));
    }
}
