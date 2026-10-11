package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires pour les contrats et modèles de domaine du Sprint 3
 * ({@link CropWindow}, {@link CellLabelMap}, {@link Cell}).
 */
@DisplayName("Validation des contrats et invariants du domaine du Sprint 3")
class CellDomainModelTest {

    /**
     * Vérifie les calculs de bornes, d'inclusion et la validation des préconditions de CropWindow.
     */
    @Test
    @DisplayName("CropWindow : validation des bornes et prédicats")
    void testCropWindow() {
        CropWindow window = new CropWindow(10, 20, 100, 200);

        assertEquals(10, window.x0());
        assertEquals(20, window.y0());
        assertEquals(100, window.width());
        assertEquals(200, window.height());
        assertEquals(110, window.x1());
        assertEquals(220, window.y1());

        assertTrue(window.contains(10, 20));
        assertTrue(window.contains(109, 219));
        assertFalse(window.contains(9, 20));
        assertFalse(window.contains(110, 220));

        assertThrows(IllegalArgumentException.class, () -> new CropWindow(-1, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new CropWindow(0, -1, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new CropWindow(0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CropWindow(0, 0, 10, 0));
    }

    /**
     * Vérifie les accès, les requêtes d'appartenance et l'isolation par copie défensive de CellLabelMap.
     */
    @Test
    @DisplayName("CellLabelMap : validation des accès, copies défensives et limites")
    void testCellLabelMap() {
        int[] labels = new int[]{
                0, 1, 1,
                0, 0, 2
        };
        CellLabelMap map = new CellLabelMap(3, 2, labels, 2);

        assertEquals(3, map.width());
        assertEquals(2, map.height());
        assertEquals(2, map.cellCount());

        assertTrue(map.isRoad(0, 0));
        assertTrue(map.isCell(1, 0));
        assertEquals(1, map.getLabel(1, 0));
        assertEquals(2, map.getLabel(2, 1));

        // Hors limites
        assertEquals(0, map.getLabel(-1, 0));
        assertFalse(map.isRoad(-1, 0));
        assertFalse(map.isCell(-1, 0));

        // Test copie défensive
        labels[1] = 99;
        assertEquals(1, map.getLabel(1, 0));

        int[] copy = map.labels();
        copy[1] = 99;
        assertEquals(1, map.getLabel(1, 0));

        assertThrows(IllegalArgumentException.class, () -> new CellLabelMap(3, 2, new int[5], 2));
        assertThrows(IllegalArgumentException.class, () -> new CellLabelMap(0, 2, new int[0], 0));
        assertThrows(IllegalArgumentException.class, () -> new CellLabelMap(3, 2, null, 2));
    }

    /**
     * Vérifie les invariants géométriques, l'immuabilité et les dimensions de Cell.
     */
    @Test
    @DisplayName("Cell : validation des invariants et calcul des dimensions")
    void testCell() {
        Cell cell = new Cell(1, 50, 10, 20, 15, 30, new PixelPoint(12.5, 25.0));

        assertEquals(1, cell.id());
        assertEquals(50, cell.area());
        assertEquals(10, cell.minX());
        assertEquals(20, cell.minY());
        assertEquals(15, cell.maxX());
        assertEquals(30, cell.maxY());
        assertEquals(6, cell.width());
        assertEquals(11, cell.height());
        assertEquals(12.5, cell.centroid().x());

        assertThrows(IllegalArgumentException.class,
                () -> new Cell(0, 50, 10, 20, 15, 30, new PixelPoint(12.5, 25.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Cell(1, 0, 10, 20, 15, 30, new PixelPoint(12.5, 25.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Cell(1, 50, 20, 20, 15, 30, new PixelPoint(12.5, 25.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new Cell(1, 50, 10, 20, 15, 30, null));
    }
}
