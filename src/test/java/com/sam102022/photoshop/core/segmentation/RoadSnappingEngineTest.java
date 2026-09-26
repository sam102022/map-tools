package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSnappingEngineTest {

    @Test
    @DisplayName("Aimantation du masque vert grossier sur les limites d'un rectangle routier")
    void testSnappingToEnclosingRoads() {
        int w = 60;
        int h = 60;

        // Barrière routière formant un cadre de (15,15) à (45,45)
        BinaryMask roadBarrier = new BinaryMask(w, h);
        for (int i = 15; i <= 45; i++) {
            roadBarrier.set(i, 15, true); // haut
            roadBarrier.set(i, 45, true); // bas
            roadBarrier.set(15, i, true); // gauche
            roadBarrier.set(45, i, true); // droite
        }

        // Masque vert grossier : centré mais plus petit (de 22 à 38)
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 22; y <= 38; y++) {
            for (int x = 22; x <= 38; x++) {
                roughMask.set(x, y, true);
            }
        }

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(20)
                .seedErosionRadius(2)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask snapped = engine.snap(roughMask, roadBarrier, config);

        // L'intérieur du cadre routier (ex: 20, 20 et 40, 40) doit être activé
        assertTrue(snapped.get(30, 30), "Le centre doit être conservé");
        assertTrue(snapped.get(18, 18), "Le masque doit s'être étendu jusqu'à la barrière");
        assertTrue(snapped.get(42, 42), "Le masque doit s'être étendu jusqu'à la barrière");

        // Au-delà de la barrière routière (ex: x=10 ou x=50), rien ne doit être actif
        assertFalse(snapped.get(10, 30), "L'extérieur de la barrière ne doit pas être envahi");
        assertFalse(snapped.get(50, 30), "L'extérieur de la barrière ne doit pas être envahi");
    }

    @Test
    @DisplayName("Rétraction des débordements extérieurs au cadre routier")
    void testRetractionOfOverflowingAreas() {
        int w = 60;
        int h = 60;

        // Barrière routière à x=30
        BinaryMask roadBarrier = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            roadBarrier.set(30, y, true);
        }

        // Masque vert grossier situé principalement à gauche (x de 10 à 40)
        // avec noyau à x=20 et débordement à x=35
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 15; y <= 45; y++) {
            for (int x = 10; x <= 40; x++) {
                roughMask.set(x, y, true);
            }
        }

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(25)
                .seedErosionRadius(4)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask snapped = engine.snap(roughMask, roadBarrier, config);

        // La partie gauche (noyau) est préservée
        assertTrue(snapped.get(20, 30));
        // Le débordement à droite (séparé par la route x=30) est éliminé
        assertFalse(snapped.get(35, 30), "La partie isolée derrière la route doit être rétractée");
    }

    @Test
    @DisplayName("Validation des arguments null ou dimensions discordantes")
    void testInvalidArguments() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask m1 = new BinaryMask(20, 20);
        BinaryMask m2 = new BinaryMask(20, 20);
        BinaryMask mDifferentSize = new BinaryMask(30, 30);
        SnappingConfig config = SnappingConfig.defaults();

        assertThrows(IllegalArgumentException.class, () -> engine.snap(null, m2, config));
        assertThrows(IllegalArgumentException.class, () -> engine.snap(m1, null, config));
        assertThrows(IllegalArgumentException.class, () -> engine.snap(m1, m2, null));
        assertThrows(IllegalArgumentException.class, () -> engine.snap(m1, mDifferentSize, config));
    }
}
