package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires pour le composant {@link CellGraph}.
 */
@DisplayName("Validation du graphe topologique CellGraph")
class CellGraphTest {

    /**
     * Vérifie le comportement nominal d'un graphe comportant des cellules sans aucune interface routière.
     */
    @Test
    @DisplayName("Graphe avec cellule isolée sans interface routière")
    void testIsolatedCell() {
        Cell cell1 = new Cell(1, 100, 0, 0, 9, 9, new PixelPoint(4.5, 4.5));
        Cell cell2 = new Cell(2, 50, 20, 20, 25, 25, new PixelPoint(22.5, 22.5));

        CellGraph graph = new CellGraph(List.of(cell1, cell2), List.of());

        assertEquals(2, graph.cells().size());
        assertEquals(0, graph.roadInterfaces().size());

        assertTrue(graph.getInterfaces(1).isEmpty());
        assertTrue(graph.getNeighbors(1).isEmpty());
        assertTrue(graph.findInterface(1, 2).isEmpty());
    }

    /**
     * Vérifie l'immutabilité stricte des collections exposées par le CellGraph.
     */
    @Test
    @DisplayName("Immutabilité des collections retournées par le CellGraph")
    void testImmutability() {
        Cell cell1 = new Cell(1, 100, 0, 0, 9, 9, new PixelPoint(4.5, 4.5));
        Cell cell2 = new Cell(2, 100, 15, 0, 24, 9, new PixelPoint(19.5, 4.5));
        BinaryMask mask = new BinaryMask(30, 10);
        RoadInterface ri = new RoadInterface(1, 1, 2, mask, 50);

        CellGraph graph = new CellGraph(List.of(cell1, cell2), List.of(ri));

        assertThrows(UnsupportedOperationException.class, () -> graph.cells().add(cell1));
        assertThrows(UnsupportedOperationException.class, () -> graph.roadInterfaces().add(ri));
        assertThrows(UnsupportedOperationException.class, () -> graph.getNeighbors(1).add(99));
    }

    /**
     * Vérifie le rejet par exception de paramètres null passés au constructeur de CellGraph.
     */
    @Test
    @DisplayName("Rejet des paramètres null à l'instanciation")
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CellGraph(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CellGraph(List.of(), null));
    }
}
