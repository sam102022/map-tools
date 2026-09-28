package com.sam102022.photoshop.core.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests unitaires pour {@link SnapTargetEdge}.
 */
@DisplayName("Tests unitaires pour SnapTargetEdge")
class SnapTargetEdgeTest {

    @Test
    @DisplayName("Vérifier les valeurs de l'énumération SnapTargetEdge")
    void testEnumValues() {
        assertEquals(2, SnapTargetEdge.values().length);
        assertNotNull(SnapTargetEdge.valueOf("INNER"));
        assertNotNull(SnapTargetEdge.valueOf("OUTER"));
    }
}
