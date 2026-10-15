package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du composant {@link SoftThresholdFilter}.
 * Valide le filtrage gaussien 2D séparable, la symétrie et la transition
 * continue à seuil raide [0.0..1.0].
 */
@DisplayName("Tests du composant SoftThresholdFilter (Sprint 8)")
class SoftThresholdFilterTest {

    @Test
    @DisplayName("Validation des paramètres d'entrée")
    void testParameterValidation() {
        SoftThresholdFilter filter = new SoftThresholdFilter();
        BinaryMask mask = new BinaryMask(20, 20);

        assertThrows(NullPointerException.class, () -> filter.filter(null, 1.4, 2.0));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(mask, 0.0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(mask, -1.0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(mask, 1.4, 0.5));
    }

    @Test
    @DisplayName("Masque plein unitaire : le facteur doit être uniforme à 1.0f")
    void testFullMaskProducesUniformOne() {
        SoftThresholdFilter filter = new SoftThresholdFilter();
        int w = 30;
        int h = 30;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask.set(x, y, true);
            }
        }

        float[][] factor = filter.filter(mask, 1.4, 2.0);
        assertNotNull(factor);
        assertEquals(h, factor.length);
        assertEquals(w, factor[0].length);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals(1.0f, factor[y][x], 1e-5f, "Chaque pixel plein doit valoir 1.0f");
            }
        }
    }

    @Test
    @DisplayName("Masque vide : le facteur doit être uniforme à 0.0f")
    void testEmptyMaskProducesUniformZero() {
        SoftThresholdFilter filter = new SoftThresholdFilter();
        int w = 30;
        int h = 30;
        BinaryMask mask = new BinaryMask(w, h);

        float[][] factor = filter.filter(mask, 1.4, 2.0);
        assertNotNull(factor);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals(0.0f, factor[y][x], 1e-5f, "Chaque pixel vide doit valoir 0.0f");
            }
        }
    }

    @Test
    @DisplayName("Marche d'escalier verticale : décroissance monotone, symétrie à 0.5 et bornes [0..1]")
    void testStepFunctionTransition() {
        SoftThresholdFilter filter = new SoftThresholdFilter();
        int w = 40;
        int h = 40;
        BinaryMask mask = new BinaryMask(w, h);

        // x < 20 => true, x >= 20 => false
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < 20; x++) {
                mask.set(x, y, true);
            }
        }

        float[][] factor = filter.filter(mask, 1.4, 2.0);

        // Test au centre vertical y = 20
        int y = 20;
        // Dans le cœur du masque (x = 5), le facteur doit valoir 1.0
        assertEquals(1.0f, factor[y][5], 1e-4f);
        // Loin à l'extérieur (x = 35), le facteur doit valoir 0.0
        assertEquals(0.0f, factor[y][35], 1e-4f);

        // Symétrie autour de l'interface entre x=19 et x=20 :
        // factor[y][19] + factor[y][20] doit être égal à 1.0f
        assertEquals(1.0f, factor[y][19] + factor[y][20], 1e-4f,
                "La somme des facteurs de part et d'autre de la frontière doit valoir 1.0 (symétrie)");

        // Décroissance monotone en traversant l'arête
        for (int x = 0; x < w - 1; x++) {
            assertTrue(factor[y][x] >= factor[y][x + 1],
                    "Le facteur doit décroître de façon monotone le long de l'arête à x=" + x);
            assertTrue(factor[y][x] >= 0.0f && factor[y][x] <= 1.0f,
                    "Le facteur doit être borné dans [0.0, 1.0]");
        }
    }
}
