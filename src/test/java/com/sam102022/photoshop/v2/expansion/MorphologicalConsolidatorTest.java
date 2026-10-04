package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'ouverture circulaire par disque et la sanctuarisation de T.
 */
@DisplayName("Tests du consolidateur morphologique avec sanctuarisation de T")
class MorphologicalConsolidatorTest {

    @Test
    @DisplayName("L'ouverture lisse les excroissances de l'extension sans altérer les coins authentiques de T")
    void testInteriorSanctuaryDuringOpening() {
        int width = 40;
        int height = 40;
        BinaryMask t = new BinaryMask(width, height);
        // T a un coin vif carré à (10, 10)
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) {
                t.set(x, y, true);
            }
        }

        BinaryMask ext = new BinaryMask(width, height);
        // Excroissance étroite de 1 pixel sur le côté
        ext.set(30, 20, true);
        // Excroissance isolée plus lointaine
        ext.set(35, 20, true);

        MorphologicalConsolidator consolidator = new MorphologicalConsolidator();
        BinaryMask result = consolidator.consolidate(t, ext, 5);

        // Le coin vif intérieur de T est préservé à 100%
        assertTrue(result.get(10, 10), "Le coin vif de T doit être sanctuarisé");
        assertTrue(result.get(10, 29), "Le coin inférieur de T doit être sanctuarisé");

        // T est préservé partout où t.get(x, y) est vrai
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) {
                assertTrue(result.get(x, y));
            }
        }

        // L'excroissance isolée lointaine doit être lissée par l'ouverture
        assertFalse(result.get(35, 20), "L'excroissance isolée à distance du bloc doit être éliminée");
    }
}
