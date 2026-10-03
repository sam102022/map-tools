package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires pour le composant {@link RoadMaskCleaner}.
 */
@DisplayName("Validation du nettoyeur et fermeur topologique RoadMaskCleaner")
class RoadMaskCleanerTest {

    /**
     * Vérifie le colmatage d'une micro-coupure de 1 pixel par fermeture morphologique minimale.
     */
    @Test
    @DisplayName("Colmatage d'une micro-coupure de 1 pixel par fermeture morphologique minimale")
    void testCloseOnePixelGap() {
        BinaryMask raw = new BinaryMask(10, 10);
        // Deux tronçons de chaussée de 4 pixels de large séparés par une coupure d'un pixel en x=5
        for (int y = 3; y <= 6; y++) {
            raw.set(3, y, true);
            raw.set(4, y, true);
            // x = 5 est la coupure
            raw.set(6, y, true);
            raw.set(7, y, true);
        }

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask result = cleaner.clean(raw);

        assertNotNull(result);
        assertFalse(result.raw().get(5, 5), "Le masque raw conserve la discontinuité d'origine");
        assertTrue(result.closed().get(5, 5), "Le masque closed a colmaté la discontinuité de 1 pixel");
    }

    /**
     * Vérifie la suppression des bruits isolés 1x1 par ouverture morphologique 2x2.
     */
    @Test
    @DisplayName("Suppression des bruits isolés 1x1 par ouverture morphologique 2x2")
    void testRemoveIsolatedNoise() {
        BinaryMask raw = new BinaryMask(20, 20);
        // Pixel de bruit isolé
        raw.set(2, 2, true);

        // Bloc continu de route (au moins 2x2)
        raw.set(10, 10, true);
        raw.set(11, 10, true);
        raw.set(10, 11, true);
        raw.set(11, 11, true);

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask result = cleaner.clean(raw);

        assertFalse(result.closed().get(2, 2), "Le pixel de bruit isolé doit être supprimé");
        assertTrue(result.closed().get(10, 10), "Le bloc continu 2x2 doit être préservé");
    }

    /**
     * Vérifie le rejet d'un masque raw null avec IllegalArgumentException.
     */
    @Test
    @DisplayName("Rejet d'un masque raw null")
    void testNullRawMask() {
        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        assertThrows(IllegalArgumentException.class, () -> cleaner.clean(null));
    }
}
