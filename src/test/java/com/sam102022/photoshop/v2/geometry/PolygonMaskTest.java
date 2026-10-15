package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du record de contrat PolygonMask.
 */
@DisplayName("Validation du contrat officiel PolygonMask V2")
class PolygonMaskTest {

    @Test
    @DisplayName("PolygonMask valide les dimensions et la cohérence avec le BinaryMask")
    void testPolygonMaskValidation() {
        BinaryMask mask = new BinaryMask(100, 50);
        mask.set(10, 10, true);

        PolygonMask polygonMask = new PolygonMask(100, 50, mask);
        assertEquals(100, polygonMask.width());
        assertEquals(50, polygonMask.height());
        assertEquals(mask, polygonMask.mask());
        assertTrue(polygonMask.get(10, 10));
        assertFalse(polygonMask.get(0, 0));
        assertEquals(1, polygonMask.countActivePixels());

        // Rejets
        assertThrows(IllegalArgumentException.class, () -> new PolygonMask(0, 50, mask));
        assertThrows(IllegalArgumentException.class, () -> new PolygonMask(100, -1, mask));
        assertThrows(IllegalArgumentException.class, () -> new PolygonMask(100, 50, null));
        assertThrows(IllegalArgumentException.class, () -> new PolygonMask(80, 50, mask));
        assertThrows(IllegalArgumentException.class, () -> new PolygonMask(100, 40, mask));
    }
}
