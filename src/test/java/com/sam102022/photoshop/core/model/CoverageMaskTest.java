package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoverageMaskTest {

    @Test
    @DisplayName("Initialisation, lecture, écriture et clamping des valeurs entre 0 et 255")
    void testInitializationAndClamping() {
        CoverageMask mask = new CoverageMask(10, 20);
        assertEquals(10, mask.getWidth());
        assertEquals(20, mask.getHeight());
        assertEquals(0, mask.get(0, 0));

        mask.set(5, 5, 128);
        assertEquals(128, mask.get(5, 5));

        // Clamping sous 0 et au-dessus de 255
        mask.set(0, 0, -50);
        assertEquals(0, mask.get(0, 0));

        mask.set(9, 19, 300);
        assertEquals(255, mask.get(9, 19));
    }

    @Test
    @DisplayName("Débordement des index et dimensions invalides")
    void testBoundsAndValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CoverageMask(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CoverageMask(10, -1));

        CoverageMask mask = new CoverageMask(5, 5);
        assertThrows(IndexOutOfBoundsException.class, () -> mask.get(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> mask.get(0, 5));
        assertThrows(IndexOutOfBoundsException.class, () -> mask.set(-1, 0, 100));
        assertThrows(IndexOutOfBoundsException.class, () -> mask.set(0, 5, 100));
    }

    @Test
    @DisplayName("Conversion toBinaryMask avec seuil exact et validation du seuil")
    void testToBinaryMask() {
        CoverageMask mask = new CoverageMask(4, 1);
        mask.set(0, 0, 0);
        mask.set(1, 0, 127);
        mask.set(2, 0, 128);
        mask.set(3, 0, 255);

        BinaryMask binary = mask.toBinaryMask(128);
        assertFalse(binary.get(0, 0));
        assertFalse(binary.get(1, 0), "127 doit être false au seuil 128");
        assertTrue(binary.get(2, 0), "128 doit être true au seuil 128");
        assertTrue(binary.get(3, 0), "255 doit être true au seuil 128");

        assertThrows(IllegalArgumentException.class, () -> mask.toBinaryMask(-1));
        assertThrows(IllegalArgumentException.class, () -> mask.toBinaryMask(256));
    }

    @Test
    @DisplayName("Conversion fromBinaryMask vers CoverageMask")
    void testFromBinaryMask() {
        BinaryMask binary = new BinaryMask(2, 2);
        binary.set(0, 0, true);
        binary.set(1, 1, true);

        CoverageMask coverage = CoverageMask.fromBinaryMask(binary);
        assertEquals(255, coverage.get(0, 0));
        assertEquals(0, coverage.get(1, 0));
        assertEquals(0, coverage.get(0, 1));
        assertEquals(255, coverage.get(1, 1));

        assertThrows(IllegalArgumentException.class, () -> CoverageMask.fromBinaryMask(null));
    }

    @Test
    @DisplayName("Combinaison max entre masques de couverture")
    void testMaxCombination() {
        CoverageMask m1 = new CoverageMask(2, 2);
        m1.set(0, 0, 255);
        m1.set(1, 0, 100);
        m1.set(0, 1, 0);

        CoverageMask m2 = new CoverageMask(2, 2);
        m2.set(0, 0, 100);
        m2.set(1, 0, 200);
        m2.set(0, 1, 50);

        CoverageMask combined = m1.max(m2);
        assertEquals(255, combined.get(0, 0));
        assertEquals(200, combined.get(1, 0));
        assertEquals(50, combined.get(0, 1));

        CoverageMask wrongSize = new CoverageMask(3, 3);
        assertThrows(IllegalArgumentException.class, () -> m1.max(wrongSize));
        assertThrows(IllegalArgumentException.class, () -> m1.max(null));
    }
}
