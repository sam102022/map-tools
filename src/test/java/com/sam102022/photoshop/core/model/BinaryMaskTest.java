package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinaryMaskTest {

    @Test
    @DisplayName("Initialisation d'un masque vide avec dimensions valides")
    void testInitialization() {
        BinaryMask mask = new BinaryMask(10, 20);
        assertEquals(10, mask.getWidth());
        assertEquals(20, mask.getHeight());
        assertEquals(0, mask.countActivePixels());
        assertFalse(mask.get(0, 0));
        assertFalse(mask.get(9, 19));
    }

    @Test
    @DisplayName("Gestion des coordonnées et des limites")
    void testBoundsAndSet() {
        BinaryMask mask = new BinaryMask(5, 5);
        assertTrue(mask.isInBounds(0, 0));
        assertTrue(mask.isInBounds(4, 4));
        assertFalse(mask.isInBounds(-1, 0));
        assertFalse(mask.isInBounds(0, 5));

        mask.set(2, 3, true);
        assertTrue(mask.get(2, 3));
        assertEquals(1, mask.countActivePixels());

        mask.set(2, 3, false);
        assertFalse(mask.get(2, 3));
        assertEquals(0, mask.countActivePixels());
    }

    @Test
    @DisplayName("Opérations logiques : OR, AND, NOT et copie")
    void testBitwiseOperations() {
        BinaryMask m1 = new BinaryMask(2, 2);
        m1.set(0, 0, true);
        m1.set(1, 0, true);

        BinaryMask m2 = new BinaryMask(2, 2);
        m2.set(1, 0, true);
        m2.set(1, 1, true);

        BinaryMask orMask = m1.or(m2);
        assertEquals(3, orMask.countActivePixels());
        assertTrue(orMask.get(0, 0));
        assertTrue(orMask.get(1, 0));
        assertTrue(orMask.get(1, 1));
        assertFalse(orMask.get(0, 1));

        BinaryMask andMask = m1.and(m2);
        assertEquals(1, andMask.countActivePixels());
        assertTrue(andMask.get(1, 0));

        BinaryMask notMask = m1.not();
        assertEquals(2, notMask.countActivePixels());
        assertFalse(notMask.get(0, 0));
        assertFalse(notMask.get(1, 0));
        assertTrue(notMask.get(0, 1));
        assertTrue(notMask.get(1, 1));

        BinaryMask clone = m1.copy();
        assertEquals(m1.countActivePixels(), clone.countActivePixels());
        clone.set(0, 1, true);
        assertFalse(m1.get(0, 1));
    }

    @Test
    @DisplayName("Rejet des dimensions négatives ou nulles")
    void testInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new BinaryMask(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new BinaryMask(10, -5));
    }
}
