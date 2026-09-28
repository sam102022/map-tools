package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant la configuration immuable {@link SnappingConfig}.
 */
@DisplayName("Tests unitaires pour SnappingConfig")
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
        assertTrue(config.antialiasing());
        assertEquals(OperationMode.AUTO, config.mode());
        assertEquals(SelectorColor.AUTO, config.zoneColor());
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
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, 1, -1, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, 1, 8, -1));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, 1, 8, 2, true, null, SelectorColor.AUTO));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, 1, 8, 2, true, OperationMode.AUTO, null));
    }

    @Test
    @DisplayName("Création avec le Builder incluant mode et zoneColor")
    void testBuilder() {
        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(50)
                .roadSensitivity(1.5f)
                .smoothRadius(2)
                .seedErosionRadius(6)
                .closingRadius(3)
                .antialiasing(false)
                .mode(OperationMode.ZONE)
                .zoneColor(SelectorColor.BLUE)
                .build();

        assertEquals(50, config.snapDistance());
        assertEquals(1.5f, config.roadSensitivity(), 0.001f);
        assertEquals(2, config.smoothRadius());
        assertEquals(6, config.seedErosionRadius());
        assertEquals(3, config.closingRadius());
        assertEquals(false, config.antialiasing());
        assertEquals(OperationMode.ZONE, config.mode());
        assertEquals(SelectorColor.BLUE, config.zoneColor());
    }

    @Test
    @DisplayName("Évolution immuable via withMode et withZoneColor")
    void testWithers() {
        SnappingConfig base = SnappingConfig.defaults();
        SnappingConfig updated = base.withMode(OperationMode.ZONE).withZoneColor(SelectorColor.RED);

        assertEquals(OperationMode.AUTO, base.mode(), "L'instance de base doit rester inchangée");
        assertEquals(SelectorColor.AUTO, base.zoneColor(), "L'instance de base doit rester inchangée");

        assertEquals(OperationMode.ZONE, updated.mode());
        assertEquals(SelectorColor.RED, updated.zoneColor());
        assertEquals(base.snapDistance(), updated.snapDistance());
    }
}
