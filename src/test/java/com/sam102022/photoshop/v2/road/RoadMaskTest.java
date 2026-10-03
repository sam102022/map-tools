package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires validant le contrat immuable RoadMask (Sprint 2).
 */
@DisplayName("Validation du contrat officiel RoadMask (Sprint 2)")
class RoadMaskTest {

    /**
     * Vérifie l'instanciation valide d'un RoadMask conforme.
     */
    @Test
    @DisplayName("Instanciation valide d'un RoadMask conforme")
    void testValidRoadMask() {
        BinaryMask raw = new BinaryMask(100, 50);
        BinaryMask closed = new BinaryMask(100, 50);
        raw.set(10, 10, true);
        closed.set(10, 10, true);

        RoadMask roadMask = new RoadMask(100, 50, raw, closed);

        assertEquals(100, roadMask.width());
        assertEquals(50, roadMask.height());
        assertNotNull(roadMask.raw());
        assertNotNull(roadMask.closed());
        assertTrue(roadMask.raw().get(10, 10));
        assertTrue(roadMask.closed().get(10, 10));
    }

    /**
     * Vérifie le rejet des dimensions négatives ou nulles avec IllegalArgumentException.
     */
    @Test
    @DisplayName("Rejet des dimensions négatives ou nulles")
    void testInvalidDimensions() {
        BinaryMask mask = new BinaryMask(10, 10);
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(0, 10, mask, mask));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, -1, mask, mask));
    }

    /**
     * Vérifie le rejet des masques null avec IllegalArgumentException.
     */
    @Test
    @DisplayName("Rejet des masques null")
    void testNullMasks() {
        BinaryMask mask = new BinaryMask(10, 10);
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, null, mask));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask, null));
    }

    /**
     * Vérifie le rejet des masques de dimensions incohérentes avec IllegalArgumentException.
     */
    @Test
    @DisplayName("Rejet des masques de dimensions incohérentes")
    void testInconsistentDimensions() {
        BinaryMask mask10x10 = new BinaryMask(10, 10);
        BinaryMask mask20x10 = new BinaryMask(20, 10);

        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask20x10, mask10x10));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask10x10, mask20x10));
    }
}
