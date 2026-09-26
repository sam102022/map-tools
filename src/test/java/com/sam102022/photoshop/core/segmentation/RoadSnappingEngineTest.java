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

    @Test
    @DisplayName("Un masque grossier vide retourne un résultat vide sans lever d'exception")
    void testEmptyRoughMaskReturnsEmpty() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask emptyRough = new BinaryMask(30, 30);
        BinaryMask emptyRoad = new BinaryMask(30, 30);
        BinaryMask result = engine.snap(emptyRough, emptyRoad, SnappingConfig.defaults());

        assertEquals(0, result.countActivePixels());
    }

    @Test
    @DisplayName("Languette fine : repli adaptatif de l'érosion")
    void testThinSliverErosionFallback() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask thinMask = new BinaryMask(40, 40);
        // Languette de 3 pixels de large (alors que seedErosionRadius par défaut = 8)
        for (int y = 10; y <= 30; y++) {
            for (int x = 18; x <= 20; x++) {
                thinMask.set(x, y, true);
            }
        }

        BinaryMask roadBarrier = new BinaryMask(40, 40);
        BinaryMask result = engine.snap(thinMask, roadBarrier, SnappingConfig.defaults());

        assertTrue(result.countActivePixels() > 0, "La languette fine doit être préservée grâce au repli adaptatif");
        assertTrue(result.get(19, 20));
    }

    @Test
    @DisplayName("Arrêt de la propagation au-delà de snapDistance en l'absence de route")
    void testSnapDistanceHaltsPropagationWithoutRoad() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask roughMask = new BinaryMask(50, 50);
        roughMask.set(25, 25, true);

        BinaryMask noRoads = new BinaryMask(50, 50);
        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(5)
                .seedErosionRadius(1)
                .build();

        BinaryMask result = engine.snap(roughMask, noRoads, config);

        assertTrue(result.get(25, 25));
        assertTrue(result.get(25, 30), "À 5px de distance, doit être inclus");
        assertFalse(result.get(25, 35), "À 10px de distance (> snapDistance 5), doit être exclu");
    }

    @Test
    @DisplayName("Collision graine et route : repli sur le masque initial hors route")
    void testSeedRoadCollisionFallback() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask rough = new BinaryMask(30, 30);
        // Carré grossier de 10 à 20
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) {
                rough.set(x, y, true);
            }
        }

        // Route qui coupe précisément au centre érodé (x=15)
        BinaryMask road = new BinaryMask(30, 30);
        for (int y = 0; y < 30; y++) {
            road.set(15, y, true);
        }

        SnappingConfig config = SnappingConfig.builder()
                .seedErosionRadius(4)
                .build();

        BinaryMask result = engine.snap(rough, road, config);
        assertTrue(result.countActivePixels() > 0, "Le repli doit permettre de conserver le secteur hors route");
        assertFalse(result.get(15, 15), "La route elle-même ne doit pas être incluse");
    }
}
