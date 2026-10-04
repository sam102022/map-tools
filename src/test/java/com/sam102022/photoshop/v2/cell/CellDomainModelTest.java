package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant les invariants et méthodes des contrats de domaine du Sprint 3.
 */
class CellDomainModelTest {

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

    @Test
    @DisplayName("RoadInterface : validation de l'ordonnancement cellA < cellB et opposés")
    void testRoadInterface() {
        BinaryMask mask = new BinaryMask(10, 10);
        RoadInterface ri = new RoadInterface(1, 2, 5, mask, 15);

        assertEquals(1, ri.id());
        assertEquals(2, ri.cellA());
        assertEquals(5, ri.cellB());
        assertEquals(15, ri.area());
        assertNotNull(ri.mask());

        assertTrue(ri.connects(2));
        assertTrue(ri.connects(5));
        assertFalse(ri.connects(3));

        assertEquals(5, ri.getOppositeCell(2));
        assertEquals(2, ri.getOppositeCell(5));
        assertThrows(IllegalArgumentException.class, () -> ri.getOppositeCell(3));

        // Rejet cellA >= cellB
        assertThrows(IllegalArgumentException.class, () -> new RoadInterface(1, 5, 2, mask, 15));
        assertThrows(IllegalArgumentException.class, () -> new RoadInterface(1, 2, 2, mask, 15));
        assertThrows(IllegalArgumentException.class, () -> new RoadInterface(0, 2, 5, mask, 15));
        assertThrows(IllegalArgumentException.class, () -> new RoadInterface(1, 2, 5, null, 15));
        assertThrows(IllegalArgumentException.class, () -> new RoadInterface(1, 2, 5, mask, 0));
    }

    @Test
    @DisplayName("CellGraph : validation de la navigation et relations de voisinage")
    void testCellGraph() {
        Cell cell1 = new Cell(1, 20, 0, 0, 4, 4, new PixelPoint(2, 2));
        Cell cell2 = new Cell(2, 30, 6, 0, 10, 4, new PixelPoint(8, 2));
        Cell cell3 = new Cell(3, 40, 0, 6, 10, 10, new PixelPoint(5, 8));

        BinaryMask mask = new BinaryMask(11, 11);
        RoadInterface ri12 = new RoadInterface(1, 1, 2, mask, 10);
        RoadInterface ri13 = new RoadInterface(2, 1, 3, mask, 12);

        CellGraph graph = new CellGraph(List.of(cell1, cell2, cell3), List.of(ri12, ri13));

        assertEquals(3, graph.cells().size());
        assertEquals(2, graph.roadInterfaces().size());

        assertTrue(graph.findCell(1).isPresent());
        assertTrue(graph.findCell(99).isEmpty());

        assertEquals(2, graph.getInterfaces(1).size());
        assertEquals(1, graph.getInterfaces(2).size());
        assertEquals(0, graph.getInterfaces(99).size());

        Optional<RoadInterface> found = graph.findInterface(1, 2);
        assertTrue(found.isPresent());
        assertEquals(1, found.get().id());

        Optional<RoadInterface> reverseOrder = graph.findInterface(2, 1);
        assertTrue(reverseOrder.isPresent());
        assertEquals(1, reverseOrder.get().id());

        assertTrue(graph.findInterface(2, 3).isEmpty());

        Set<Integer> neighbors1 = graph.getNeighbors(1);
        assertEquals(Set.of(2, 3), neighbors1);

        Set<Integer> neighbors2 = graph.getNeighbors(2);
        assertEquals(Set.of(1), neighbors2);
    }
}
