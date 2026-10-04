package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du modèle de domaine v2.vote")
class CellDomainModelTest {

    @Test
    @DisplayName("CellSelectionPolicy : seuils par défaut et validation des invariants")
    void testPolicyDefaultsAndValidation() {
        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy();
        assertEquals(0.60, policy.insideThreshold(), 1e-6);
        assertEquals(0.05, policy.partialThreshold(), 1e-6);

        // Seuils inversés ou invalides
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(0.3, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(-0.1, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(0.8, 1.2));
    }

    @Test
    @DisplayName("CellDecision : validation des invariants")
    void testCellDecisionValidation() {
        CellDecision decision = new CellDecision(1, 100, 75, 0.75, CellState.INSIDE);
        assertEquals(1, decision.cellId());
        assertEquals(100, decision.cellArea());
        assertEquals(75, decision.intersectionArea());
        assertEquals(0.75, decision.coverage(), 1e-6);
        assertEquals(CellState.INSIDE, decision.state());

        assertThrows(IllegalArgumentException.class, () -> new CellDecision(0, 100, 50, 0.5, CellState.PARTIAL));
        assertThrows(IllegalArgumentException.class, () -> new CellDecision(1, 0, 0, 0.0, CellState.OUTSIDE));
        assertThrows(IllegalArgumentException.class, () -> new CellDecision(1, 100, 150, 1.5, CellState.INSIDE));
    }

    @Test
    @DisplayName("CellSelection : navigation et immutabilité")
    void testCellSelectionNavigation() {
        BinaryMask mask = new BinaryMask(10, 10);
        CellDecision d1 = new CellDecision(1, 50, 40, 0.8, CellState.INSIDE);
        CellDecision d2 = new CellDecision(2, 50, 5, 0.1, CellState.PARTIAL);
        CellDecision d3 = new CellDecision(3, 50, 0, 0.0, CellState.OUTSIDE);

        CellSelection selection = new CellSelection(
                Set.of(1),
                Set.of(3),
                Set.of(2),
                Map.of(1, d1, 2, d2, 3, d3),
                mask,
                Map.of(2, mask)
        );

        assertTrue(selection.isRetained(1));
        assertTrue(selection.isRetained(2));
        assertFalse(selection.isRetained(3));
        assertEquals(Optional.of(d1), selection.findDecision(1));
        assertEquals(Optional.empty(), selection.findDecision(99));
    }
}
