package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires des modèles de domaine du Sprint 8 : {@link AlphaRefinementConfig}
 * et {@link AlphaRefinementMap}.
 */
@DisplayName("Tests des modèles de domaine du Sprint 8 (Alpha Refinement)")
class AlphaRefinementModelTest {

    @Test
    @DisplayName("AlphaRefinementConfig expose les hyperparamètres par défaut conformes à la spécification")
    void testAlphaRefinementConfigDefaults() {
        AlphaRefinementConfig config = AlphaRefinementConfig.defaultConfig();
        assertEquals(1.4, config.gaussianBlurSigma(), 1e-6);
        assertEquals(2.0, config.contrastStiffness(), 1e-6);
        assertEquals(2.3, config.roundaboutBufferInner(), 1e-6);
        assertEquals(3.0, config.roundaboutBufferOuter(), 1e-6);
        assertEquals(2000L, config.roadHoleMaxArea());
    }

    @Test
    @DisplayName("AlphaRefinementConfig rejette les hyperparamètres hors bornes")
    void testAlphaRefinementConfigValidation() {
        // gaussianBlurSigma <= 0.0
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                0.0, 2.0, 2.3, 3.0, 2000L
        ));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                -1.0, 2.0, 2.3, 3.0, 2000L
        ));

        // contrastStiffness < 1.0
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 0.99, 2.3, 3.0, 2000L
        ));

        // roundaboutBufferInner <= 0.0
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 2.0, 0.0, 3.0, 2000L
        ));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 2.0, -0.5, 3.0, 2000L
        ));

        // roundaboutBufferOuter <= roundaboutBufferInner
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 2.0, 2.3, 2.3, 2000L
        ));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 2.0, 2.3, 2.0, 2000L
        ));

        // roadHoleMaxArea < 0
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementConfig(
                1.4, 2.0, 2.3, 3.0, -1L
        ));
    }

    @Test
    @DisplayName("AlphaRefinementMap instancie correctement et expose ses accesseurs")
    void testAlphaRefinementMapCreationAndAccessors() {
        CropWindow crop = new CropWindow(10, 20, 3, 2);
        float[][] factor = {
                {0.1f, 0.2f, 0.3f},
                {0.4f, 0.5f, 0.6f}
        };

        AlphaRefinementMap map = new AlphaRefinementMap(3, 2, factor, crop);

        assertEquals(3, map.width());
        assertEquals(2, map.height());
        assertEquals(crop, map.cropWindow());
        assertNotNull(map.factor());
        assertEquals(0.1f, map.factorAt(0, 0), 1e-6f);
        assertEquals(0.2f, map.factorAt(1, 0), 1e-6f);
        assertEquals(0.3f, map.factorAt(2, 0), 1e-6f);
        assertEquals(0.4f, map.factorAt(0, 1), 1e-6f);
        assertEquals(0.5f, map.factorAt(1, 1), 1e-6f);
        assertEquals(0.6f, map.factorAt(2, 1), 1e-6f);
    }

    @Test
    @DisplayName("AlphaRefinementMap renvoie 0.0f pour les coordonnées hors limites dans factorAt")
    void testAlphaRefinementMapOutOfBounds() {
        CropWindow crop = new CropWindow(0, 0, 2, 2);
        float[][] factor = {
                {0.5f, 0.5f},
                {0.5f, 0.5f}
        };

        AlphaRefinementMap map = new AlphaRefinementMap(2, 2, factor, crop);

        assertEquals(0.0f, map.factorAt(-1, 0), 1e-6f);
        assertEquals(0.0f, map.factorAt(0, -1), 1e-6f);
        assertEquals(0.0f, map.factorAt(2, 0), 1e-6f);
        assertEquals(0.0f, map.factorAt(0, 2), 1e-6f);
        assertEquals(0.0f, map.factorAt(-10, -10), 1e-6f);
        assertEquals(0.0f, map.factorAt(100, 100), 1e-6f);
    }

    @Test
    @DisplayName("AlphaRefinementMap garantit l'immutabilité défensive du tableau factor")
    void testAlphaRefinementMapImmutability() {
        CropWindow crop = new CropWindow(0, 0, 2, 2);
        float[][] factor = {
                {0.2f, 0.4f},
                {0.6f, 0.8f}
        };

        AlphaRefinementMap map = new AlphaRefinementMap(2, 2, factor, crop);

        // Mutation du tableau source original
        factor[0][0] = 0.99f;
        assertEquals(0.2f, map.factorAt(0, 0), 1e-6f);

        // Mutation du tableau retourné par l'accesseur
        float[][] factorCopy = map.factor();
        factorCopy[0][0] = 0.99f;
        assertEquals(0.2f, map.factorAt(0, 0), 1e-6f);
    }

    @Test
    @DisplayName("AlphaRefinementMap valide strictement ses arguments")
    void testAlphaRefinementMapValidation() {
        CropWindow crop = new CropWindow(0, 0, 2, 2);
        float[][] factor = {
                {0.1f, 0.2f},
                {0.3f, 0.4f}
        };

        // Dimensions invalides
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(0, 2, factor, crop));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(-1, 2, factor, crop));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, 0, factor, crop));
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, -1, factor, crop));

        // Références nulles
        assertThrows(NullPointerException.class, () -> new AlphaRefinementMap(2, 2, null, crop));
        assertThrows(NullPointerException.class, () -> new AlphaRefinementMap(2, 2, factor, null));

        // Dimensions de matrice incohérentes
        float[][] badHeight = {
                {0.1f, 0.2f}
        };
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, 2, badHeight, crop));

        float[][] badWidth = {
                {0.1f, 0.2f, 0.3f},
                {0.4f, 0.5f, 0.6f}
        };
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, 2, badWidth, crop));

        float[][] jagged = {
                {0.1f, 0.2f},
                {0.3f}
        };
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, 2, jagged, crop));

        float[][] nullRow = {
                null,
                {0.3f, 0.4f}
        };
        assertThrows(IllegalArgumentException.class, () -> new AlphaRefinementMap(2, 2, nullRow, crop));
    }
}
