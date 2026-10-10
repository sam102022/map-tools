package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'énumération {@link SelectorColor} et {@link OperationMode}.
 */
@DisplayName("Tests unitaires pour SelectorColor et OperationMode")
class SelectorColorTest {

    @Test
    @DisplayName("Tester le parsing insensible à la casse et les alias français pour SelectorColor")
    void testParsingAndAliases() {
        assertEquals(SelectorColor.RED, SelectorColor.fromString("red"));
        assertEquals(SelectorColor.RED, SelectorColor.fromString("ROUGE"));
        assertEquals(SelectorColor.BLUE, SelectorColor.fromString("blue"));
        assertEquals(SelectorColor.BLUE, SelectorColor.fromString("bleu"));
        assertEquals(SelectorColor.MAGENTA, SelectorColor.fromString("magenta"));
        assertEquals(SelectorColor.CYAN, SelectorColor.fromString("cyan"));
        assertEquals(SelectorColor.AUTO, SelectorColor.fromString("auto"));
        assertThrows(IllegalArgumentException.class, () -> SelectorColor.fromString("inconnue"));
        assertThrows(IllegalArgumentException.class, () -> SelectorColor.fromString(null));
    }

    @Test
    @DisplayName("Tester le filtrage chromatique exact pour chaque couleur")
    void testColorMatching() {
        // Rouge vif
        assertTrue(SelectorColor.RED.matches(220, 20, 20));
        assertFalse(SelectorColor.RED.matches(255, 180, 50), "Ne doit pas matcher une route orange");
        assertFalse(SelectorColor.RED.matches(160, 240, 160), "Ne doit pas matcher le vert territoire");
        assertFalse(SelectorColor.RED.matches(50, 10, 10), "Ne doit pas matcher un rouge trop sombre");
        assertFalse(SelectorColor.RED.matches(200, 190, 190), "Ne doit pas matcher un rouge dé-saturé");

        // Bleu vif
        assertTrue(SelectorColor.BLUE.matches(20, 30, 220));
        assertFalse(SelectorColor.BLUE.matches(200, 200, 200), "Ne doit pas matcher du gris");
        assertFalse(SelectorColor.BLUE.matches(160, 240, 160), "Ne doit pas matcher le vert territoire");

        // Magenta
        assertTrue(SelectorColor.MAGENTA.matches(220, 20, 220));
        assertFalse(SelectorColor.MAGENTA.matches(200, 200, 200), "Ne doit pas matcher du gris");
        assertFalse(SelectorColor.MAGENTA.matches(255, 180, 50), "Ne doit pas matcher une route orange");

        // Cyan
        assertTrue(SelectorColor.CYAN.matches(20, 220, 220));
        assertFalse(SelectorColor.CYAN.matches(200, 200, 200), "Ne doit pas matcher du gris");
        assertFalse(SelectorColor.CYAN.matches(160, 240, 160), "Ne doit pas matcher le vert territoire");
    }

    @Test
    @DisplayName("Tester le matching dynamique du mode AUTO")
    void testAutoMatching() {
        assertTrue(SelectorColor.AUTO.matches(220, 20, 20), "AUTO doit matcher le rouge");
        assertTrue(SelectorColor.AUTO.matches(20, 30, 220), "AUTO doit matcher le bleu");
        assertTrue(SelectorColor.AUTO.matches(220, 20, 220), "AUTO doit matcher le magenta");
        assertTrue(SelectorColor.AUTO.matches(20, 220, 220), "AUTO doit matcher le cyan");
        assertFalse(SelectorColor.AUTO.matches(200, 200, 200), "AUTO ne doit pas matcher du gris neutre");
        assertFalse(SelectorColor.AUTO.matches(255, 180, 50), "AUTO ne doit pas matcher une route orange");
        assertFalse(SelectorColor.AUTO.matches(160, 240, 160), "AUTO ne doit pas matcher le vert territoire");
    }

    @Test
    @DisplayName("Tester la surcharge avec java.awt.Color")
    void testMatchesColorObject() {
        assertTrue(SelectorColor.RED.matches(new Color(220, 20, 20)));
        assertFalse(SelectorColor.RED.matches((Color) null));
    }

    @Test
    @DisplayName("Tester le parsing de OperationMode")
    void testOperationModeParsing() {
        assertEquals(OperationMode.AUTO, OperationMode.fromString("auto"));
        assertEquals(OperationMode.TERRITORY, OperationMode.fromString("territory"));
        assertEquals(OperationMode.TERRITORY, OperationMode.fromString("territoire"));
        assertEquals(OperationMode.ZONE, OperationMode.fromString("zone"));
        assertThrows(IllegalArgumentException.class, () -> OperationMode.fromString("autre"));
        assertThrows(IllegalArgumentException.class, () -> OperationMode.fromString(null));
    }
}
