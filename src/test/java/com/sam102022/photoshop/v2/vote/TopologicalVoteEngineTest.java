package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'orchestration complète du moteur de vote topologique.
 */
@DisplayName("Tests unitaires du moteur TopologicalVoteEngine")
class TopologicalVoteEngineTest {

    /**
     * Valide l'orchestration de bout en bout avec la politique par défaut.
     */
    @Test
    @DisplayName("Orchestration complète du vote topologique avec politique par défaut")
    void testExecuteDefaultPolicy() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // 3 bandes horizontales :
        // y 0..2 : Cell 1 (30 px)
        // y 3..6 : Cell 2 (40 px)
        // y 7..9 : Cell 3 (30 px)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                if (y < 3) labels[y * 10 + x] = 1;
                else if (y < 7) labels[y * 10 + x] = 2;
                else labels[y * 10 + x] = 3;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 3);

        List<Cell> cells = List.of(
                new Cell(1, 30, 0, 0, 9, 2, new PixelPoint(4.5, 1.0)),
                new Cell(2, 40, 0, 3, 9, 6, new PixelPoint(4.5, 4.5)),
                new Cell(3, 30, 0, 7, 9, 9, new PixelPoint(4.5, 8.0))
        );

        // Polygone recouvrant entièrement Cell 1 (30 px), 10 px de Cell 2 (cov = 0.25), 0 px de Cell 3
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 10; x++) polygonMask.set(x, y, true);
        }
        for (int x = 0; x < 10; x++) {
            polygonMask.set(x, 3, true); // 10 px dans Cell 2
        }

        TopologicalVoteEngine engine = new TopologicalVoteEngine();
        CellSelection selection = engine.execute(labelMap, cells, polygonMask);

        assertNotNull(selection);
        assertEquals(1, selection.insideCellIds().size());
        assertTrue(selection.insideCellIds().contains(1));

        assertEquals(1, selection.partialCellIds().size());
        assertTrue(selection.partialCellIds().contains(2));

        assertEquals(1, selection.outsideCellIds().size());
        assertTrue(selection.outsideCellIds().contains(3));

        // Politique par défaut : la bande partielle de Cell 2 (1 px d'épaisseur) est une lamelle
        // inférieure à 20 px, retirée de T ; seul Cell 1 subsiste (30 px).
        assertEquals(30, selection.retainedMask().countActivePixels());

        // Filtre désactivé (étalon Python v7) : 30 (Cell 1) + 10 (Cell 2) = 40 px
        CellSelection legacy = engine.execute(labelMap, cells, polygonMask, new CellSelectionPolicy(0.60, 0.05, 0.0));
        assertEquals(40, legacy.retainedMask().countActivePixels());
    }

    /**
     * Valide le fonctionnement avec injection de dépendances personnalisées.
     */
    @Test
    @DisplayName("Orchestration avec injection explicite des dépendances")
    void testCustomDependencyInjection() {
        CellCoverageCalculator coverageCalculator = new CellCoverageCalculator();
        CellClassifier classifier = new CellClassifier();
        PartialCellResolver partialResolver = new PartialCellResolver();

        TopologicalVoteEngine engine = new TopologicalVoteEngine(coverageCalculator, classifier, partialResolver);
        assertNotNull(engine);

        int width = 4;
        int height = 4;
        int[] labels = new int[16];
        for (int i = 0; i < 16; i++) {
            labels[i] = 1;
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 1);
        List<Cell> cells = List.of(new Cell(1, 16, 0, 0, 3, 3, new PixelPoint(1.5, 1.5)));
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int i = 0; i < 16; i++) {
            polygonMask.set(i % width, i / width, true);
        }

        CellSelection selection = engine.execute(labelMap, cells, polygonMask);
        assertNotNull(selection);
        assertEquals(1, selection.insideCellIds().size());
        assertTrue(selection.insideCellIds().contains(1));
        assertEquals(16, selection.retainedMask().countActivePixels());
    }

    /**
     * Valide le rejet défensif en cas d'arguments nulls.
     */
    @Test
    @DisplayName("Rejet défensif des arguments nulls")
    void testDefensiveValidation() {
        TopologicalVoteEngine engine = new TopologicalVoteEngine();
        CellLabelMap labelMap = new CellLabelMap(10, 10, new int[100], 0);
        List<Cell> cells = List.of();
        BinaryMask polygonMask = new BinaryMask(10, 10);
        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy();

        assertThrows(IllegalArgumentException.class, () -> engine.execute(null, cells, polygonMask, policy));
        assertThrows(IllegalArgumentException.class, () -> engine.execute(labelMap, null, polygonMask, policy));
        assertThrows(IllegalArgumentException.class, () -> engine.execute(labelMap, cells, null, policy));
        assertThrows(IllegalArgumentException.class, () -> engine.execute(labelMap, cells, polygonMask, null));

        // Rejet des dépendances nulles dans le constructeur
        CellCoverageCalculator calc = new CellCoverageCalculator();
        CellClassifier cls = new CellClassifier();
        PartialCellResolver res = new PartialCellResolver();

        assertThrows(IllegalArgumentException.class, () -> new TopologicalVoteEngine(null, cls, res));
        assertThrows(IllegalArgumentException.class, () -> new TopologicalVoteEngine(calc, null, res));
        assertThrows(IllegalArgumentException.class, () -> new TopologicalVoteEngine(calc, cls, null));
    }
}
