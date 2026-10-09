package com.sam102022.photoshop.v2.zone;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires des couleurs de zones {@link ZoneColor}.
 */
@DisplayName("Tests unitaires de ZoneColor")
class ZoneColorTest {

    /**
     * Les zones se retrouvent par numéro ou par nom, sans tenir compte de la casse.
     */
    @Test
    @DisplayName("Résolution par numéro et par nom")
    void testParse() {
        assertEquals(ZoneColor.MAUVE, ZoneColor.parse("3"));
        assertEquals(ZoneColor.NOIR, ZoneColor.parse(" Noir "));
        assertEquals("zone2_verte", ZoneColor.VERTE.fileLabel());
        assertThrows(IllegalArgumentException.class, () -> ZoneColor.parse("violet"));
    }

    /**
     * La distance RVB est nulle sur la couleur de référence et dépasse la tolérance entre deux zones différentes.
     */
    @Test
    @DisplayName("Distance colorimétrique")
    void testDistance() {
        assertEquals(0.0, ZoneColor.ROUGE.distance(ZoneColor.ROUGE.rgb()), 1e-9);
        for (ZoneColor a : ZoneColor.values()) {
            for (ZoneColor b : ZoneColor.values()) {
                if (a != b) {
                    assertTrue(a.distance(b.rgb()) > ZoneExtractor.DEFAULT_COLOR_TOLERANCE,
                            a + " et " + b + " doivent être séparables");
                }
            }
        }
    }
}
