package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link ZoneBarrierConsolidator}.
 * <p>
 * Valide l'union étanche des axes routiers, des démarcations sombres et de l'extérieur du territoire,
 * le colmatage morphologique des diagonales 8-connexes et le traitement robuste des arguments.
 * </p>
 */
@DisplayName("Tests unitaires pour ZoneBarrierConsolidator")
class ZoneBarrierConsolidatorTest {

    private ZoneBarrierConsolidator consolidator;

    @BeforeEach
    void setUp() {
        consolidator = new ZoneBarrierConsolidator();
    }

    @Test
    @DisplayName("Consolidation des routes, démarcations et extérieur du territoire")
    void testConsolidateBarriers() {
        int w = 20;
        int h = 20;

        BinaryMask roads = new BinaryMask(w, h);
        roads.set(10, 5, true);

        BinaryMask demarcations = new BinaryMask(w, h);
        demarcations.set(5, 10, true);

        BinaryMask territory = new BinaryMask(w, h);
        // Territoire actif sur [2..18, 2..18]
        for (int y = 2; y <= 18; y++) {
            for (int x = 2; x <= 18; x++) {
                territory.set(x, y, true);
            }
        }

        BinaryMask barriers = consolidator.consolidate(roads, demarcations, territory);

        assertNotNull(barriers);
        assertTrue(barriers.get(10, 5), "La route doit être classée comme barrière.");
        assertTrue(barriers.get(5, 10), "La démarcation sombre doit être classée comme barrière.");
        assertTrue(barriers.get(0, 0), "L'extérieur du territoire doit être classé comme barrière.");
        assertFalse(barriers.get(15, 15), "L'intérieur libre du territoire ne doit pas être une barrière.");
    }

    @Test
    @DisplayName("Colmatage des diagonales 8-connexes pour interdire les fuites 4-connexes")
    void testCloseDiagonalBarriers() {
        int w = 10;
        int h = 10;

        BinaryMask roads = new BinaryMask(w, h);
        // Deux pixels de barrière placés en diagonale (5, 5) et (6, 6)
        roads.set(5, 5, true);
        roads.set(6, 6, true);

        BinaryMask territory = new BinaryMask(w, h).not();

        BinaryMask barriers = consolidator.consolidate(roads, new BinaryMask(w, h), territory);

        assertTrue(barriers.get(5, 5), "Le pixel source (5,5) doit être une barrière.");
        assertTrue(barriers.get(6, 6), "Le pixel source (6,6) doit être une barrière.");
        // Le passage diagonal entre (5,6) et (6,5) doit être colmaté
        assertTrue(barriers.get(5, 6) || barriers.get(6, 5),
                "Au moins un des pixels adjacents (5,6) ou (6,5) doit être bloqué pour étanchéifier la diagonale descendante.");

        // Test également pour une diagonale montante : (2, 4) et (3, 3)
        BinaryMask risingRoads = new BinaryMask(w, h);
        risingRoads.set(3, 3, true);
        risingRoads.set(2, 4, true);

        BinaryMask risingBarriers = consolidator.consolidate(risingRoads, new BinaryMask(w, h), territory);
        assertTrue(risingBarriers.get(2, 3) || risingBarriers.get(3, 4),
                "Au moins un des pixels adjacents doit être bloqué pour étanchéifier la diagonale montante.");
    }

    @Test
    @DisplayName("Tolérance à une démarcation sombre nulle")
    void testNullDarkDemarcationsAccepted() {
        int w = 10;
        int h = 10;

        BinaryMask roads = new BinaryMask(w, h);
        roads.set(4, 4, true);

        BinaryMask territory = new BinaryMask(w, h).not();

        BinaryMask barriers = consolidator.consolidate(roads, null, territory);

        assertNotNull(barriers);
        assertTrue(barriers.get(4, 4));
        assertFalse(barriers.get(2, 2));
    }

    @Test
    @DisplayName("Validation des arguments null et des dimensions discordantes")
    void testValidationNullAndDimensions() {
        BinaryMask valid10 = new BinaryMask(10, 10);
        BinaryMask valid20 = new BinaryMask(20, 10);

        assertThrows(IllegalArgumentException.class,
                () -> consolidator.consolidate(null, valid10, valid10),
                "Un masque routier null doit lever IllegalArgumentException.");

        assertThrows(IllegalArgumentException.class,
                () -> consolidator.consolidate(valid10, valid10, null),
                "Un masque de territoire null doit lever IllegalArgumentException.");

        assertThrows(IllegalArgumentException.class,
                () -> consolidator.consolidate(valid10, valid20, valid10),
                "Des dimensions différentes pour demarcations doivent lever IllegalArgumentException.");

        assertThrows(IllegalArgumentException.class,
                () -> consolidator.consolidate(valid10, valid10, valid20),
                "Des dimensions différentes pour territory doivent lever IllegalArgumentException.");
    }
}
