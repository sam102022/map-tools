package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnappingConfigTest {

    @Test
    @DisplayName("Valeurs par défaut conformes à la spécification")
    void testDefaults() {
        SnappingConfig config = SnappingConfig.defaults();
        assertEquals(40, config.snapDistance());
        assertEquals(1.0f, config.roadSensitivity(), 0.001f);
        assertEquals(1, config.smoothRadius());
        assertEquals(8, config.seedErosionRadius());
        assertEquals(2, config.closingRadius());
    }

    @Test
    @DisplayName("Validation des paramètres invalides")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(0, 1.0f, 1, 8, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 0.0f, 1, 8, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, -1, 8, 2));
    }

    @Test
    @DisplayName("Création avec le Builder")
    void testBuilder() {
        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(50)
                .roadSensitivity(1.5f)
                .smoothRadius(2)
                .seedErosionRadius(6)
                .closingRadius(3)
                .build();

        assertEquals(50, config.snapDistance());
        assertEquals(1.5f, config.roadSensitivity(), 0.001f);
        assertEquals(2, config.smoothRadius());
        assertEquals(6, config.seedErosionRadius());
        assertEquals(3, config.closingRadius());
    }
}
