package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSnappingEngineTest {

    @Test
    @DisplayName("1. Recaler un contour synthétique vers des axes routiers candidats continus")
    void testRecalerContourVersAxesRoutiersContinus() {
        int w = 60;
        int h = 60;

        // Masque carré initial centré : de (20,20) à (35,35)
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 20; y <= 35; y++) {
            for (int x = 20; x <= 35; x++) {
                roughMask.set(x, y, true);
            }
        }

        // Axe routier continu à droite à x=42 (de y=10 à 45)
        BinaryMask roads = new BinaryMask(w, h);
        for (int y = 10; y <= 45; y++) {
            roads.set(42, y, true);
        }

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(15)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask snapped = engine.snap(roughMask, roads, config);

        // Le contour droit doit s'être étendu vers la route à x=42
        assertTrue(snapped.get(40, 28), "Le masque doit s'être étendu vers l'axe routier");
        // Le centre initial doit toujours être préservé
        assertTrue(snapped.get(25, 25), "Le centre doit rester actif");
        // Au-delà de la route (ex: x=50), rien ne doit être activé
        assertFalse(snapped.get(50, 28), "L'extérieur de la route ne doit pas être activé");
    }

    @Test
    @DisplayName("2. Vérifier qu’aucune route candidate ne renvoie le masque initial")
    void testAucuneRouteCandidateRenvoieMasqueInitial() {
        int w = 30;
        int h = 30;
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) {
                roughMask.set(x, y, true);
            }
        }

        BinaryMask emptyRoads = new BinaryMask(w, h);
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask result = engine.snap(roughMask, emptyRoads, SnappingConfig.defaults());

        assertEquals(roughMask.countActivePixels(), result.countActivePixels());
        assertTrue(result.get(15, 15));
    }

    @Test
    @DisplayName("3. Vérifier qu’un candidat isolé ne déplace pas le contour")
    void testCandidatIsoleNeDeplacePasContour() {
        int w = 50;
        int h = 50;
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 15; y <= 30; y++) {
            for (int x = 15; x <= 30; x++) {
                roughMask.set(x, y, true);
            }
        }

        // Un seul pixel isolé hors du masque (ex: texte ou pictogramme)
        BinaryMask roadWithNoise = new BinaryMask(w, h);
        roadWithNoise.set(38, 22, true);

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask result = engine.snap(roughMask, roadWithNoise, SnappingConfig.defaults());

        // Le pixel isolé n'a aucun support tangentiel -> le contour ne doit pas bouger
        assertFalse(result.get(38, 22), "Un pixel isolé ne doit pas attirer le contour");
        assertEquals(roughMask.countActivePixels(), result.countActivePixels());
    }

    @Test
    @DisplayName("4. Vérifier que les candidats au-delà de snapDistance sont ignorés")
    void testCandidatsAuDelaDeSnapDistanceSontIgnores() {
        int w = 80;
        int h = 80;
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 20; y <= 40; y++) {
            for (int x = 20; x <= 40; x++) {
                roughMask.set(x, y, true);
            }
        }

        // Route continue mais située à x=70 (distance = 30px par rapport à x=40)
        BinaryMask farRoad = new BinaryMask(w, h);
        for (int y = 10; y <= 50; y++) {
            farRoad.set(70, y, true);
        }

        // snapDistance = 15 (< distance 30)
        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(15)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask result = engine.snap(roughMask, farRoad, config);

        assertFalse(result.get(65, 30), "La route trop éloignée ne doit pas être atteinte");
        assertFalse(result.get(70, 30));
    }

    @Test
    @DisplayName("5. Vérifier que les portions sans candidat fiable conservent leur contour initial")
    void testPortionsSansCandidatFiableConserventContour() {
        int w = 60;
        int h = 60;
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 20; y <= 35; y++) {
            for (int x = 20; x <= 35; x++) {
                roughMask.set(x, y, true);
            }
        }

        // Route uniquement sur le bord droit x=40
        BinaryMask rightRoad = new BinaryMask(w, h);
        for (int y = 15; y <= 40; y++) {
            rightRoad.set(40, y, true);
        }

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask result = engine.snap(roughMask, rightRoad, SnappingConfig.defaults());

        // Le bord gauche (x=20) n'a pas de route en face et doit conserver sa position
        assertTrue(result.get(21, 28));
        assertFalse(result.get(15, 28), "Le côté gauche sans route ne doit pas s'étendre");
    }

    @Test
    @DisplayName("6. Vérifier que le noyau intérieur du masque est préservé")
    void testNoyauInterieurPreserve() {
        int w = 50;
        int h = 50;
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 15; y <= 35; y++) {
            for (int x = 15; x <= 35; x++) {
                roughMask.set(x, y, true);
            }
        }

        BinaryMask road = new BinaryMask(w, h);
        for (int y = 10; y <= 40; y++) {
            road.set(42, y, true);
        }

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask result = engine.snap(roughMask, road, SnappingConfig.defaults());

        // Tous les pixels du noyau intérieur (ex: (25,25)) doivent rester actifs
        assertTrue(result.get(25, 25));
        assertTrue(result.get(20, 20));
        assertTrue(result.get(30, 30));
    }

    @Test
    @DisplayName("7. Vérifier le repli sur le masque initial en cas de géométrie invalide ou vide")
    void testRepliEnCasDeGeometrieInvalideOuVide() {
        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask emptyRough = new BinaryMask(30, 30);
        BinaryMask roads = new BinaryMask(30, 30);
        BinaryMask resEmpty = engine.snap(emptyRough, roads, SnappingConfig.defaults());
        assertEquals(0, resEmpty.countActivePixels());

        // Masque de 2 pixels (pas un polygone)
        BinaryMask twoPixels = new BinaryMask(30, 30);
        twoPixels.set(5, 5, true);
        twoPixels.set(5, 6, true);
        BinaryMask resTwo = engine.snap(twoPixels, roads, SnappingConfig.defaults());
        assertEquals(2, resTwo.countActivePixels());
    }

    @Test
    @DisplayName("8. Validation des arguments null ou dimensions discordantes")
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
