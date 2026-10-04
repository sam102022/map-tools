package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'extraction d'interfaces routières et la construction du CellGraph.
 */
class RoadInterfaceExtractorTest {

    @Test
    @DisplayName("Extraction d'interface routière entre deux cellules séparées par une route de 2px")
    void testTwoCellsSeparatedByRoad() {
        // Matrice 10x6
        // Cell 1 : y in [0..1]
        // Road   : y in [2..3] (largeur 2px)
        // Cell 2 : y in [4..5]
        BinaryMask roadMask = new BinaryMask(10, 6);
        for (int x = 0; x < 10; x++) {
            roadMask.set(x, 2, true);
            roadMask.set(x, 3, true);
        }

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(roadMask);

        assertEquals(2, labelingResult.cells().size());

        RoadInterfaceExtractor extractor = new RoadInterfaceExtractor();
        CellGraph graph = extractor.buildGraph(labelingResult, roadMask);

        assertNotNull(graph);
        assertEquals(2, graph.cells().size());
        assertEquals(1, graph.roadInterfaces().size());

        RoadInterface ri = graph.roadInterfaces().get(0);
        assertEquals(1, ri.cellA());
        assertEquals(2, ri.cellB());
        assertTrue(ri.area() > 0);

        Optional<RoadInterface> found = graph.findInterface(1, 2);
        assertTrue(found.isPresent());
        assertEquals(ri.id(), found.get().id());
    }

    @Test
    @DisplayName("Carrefour en T séparant 3 cellules urbaines")
    void testThreeCellsWithIntersection() {
        // Matrice 11x11
        // Route horizontale à y=5
        // Route verticale de x=5, y in [5..10]
        // Cellule 1 : haut (y < 5)
        // Cellule 2 : bas-gauche (x < 5, y > 5)
        // Cellule 3 : bas-droite (x > 5, y > 5)
        BinaryMask roadMask = new BinaryMask(11, 11);
        for (int x = 0; x < 11; x++) {
            roadMask.set(x, 5, true);
        }
        for (int y = 5; y < 11; y++) {
            roadMask.set(5, y, true);
        }

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(roadMask);

        assertEquals(3, labelingResult.cells().size());

        RoadInterfaceExtractor extractor = new RoadInterfaceExtractor();
        CellGraph graph = extractor.buildGraph(labelingResult, roadMask);

        assertEquals(3, graph.cells().size());
        // Doit comporter 3 interfaces : (1-2), (1-3), (2-3)
        assertEquals(3, graph.roadInterfaces().size());

        assertTrue(graph.findInterface(1, 2).isPresent());
        assertTrue(graph.findInterface(1, 3).isPresent());
        assertTrue(graph.findInterface(2, 3).isPresent());
    }

    @Test
    @DisplayName("Validation défensive des paramètres invalides")
    void testValidation() {
        RoadInterfaceExtractor extractor = new RoadInterfaceExtractor();
        BinaryMask mask = new BinaryMask(10, 10);
        CellLabelMap map = new CellLabelMap(10, 10, new int[100], 0);

        assertThrows(IllegalArgumentException.class, () -> extractor.extractInterfaces(null, mask));
        assertThrows(IllegalArgumentException.class, () -> extractor.extractInterfaces(map, null));

        CellLabelMap discordantMap = new CellLabelMap(5, 5, new int[25], 0);
        assertThrows(IllegalArgumentException.class, () -> extractor.extractInterfaces(discordantMap, mask));

        assertThrows(IllegalArgumentException.class, () -> extractor.buildGraph(null, mask));
    }
}
