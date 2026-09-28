package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoadSnapperTest {

    @Test
    @DisplayName("Aimantation d'un bord vers une route parallèle continue")
    void testSnapToParallelRoad() {
        int w = 50;
        int h = 50;

        // Rectangle de (15,15) à (30,30) avec points d'échantillonnage le long des arêtes
        List<Point> contour = new ArrayList<>();
        // Bord supérieur (gauche à droite)
        for (int x = 15; x < 30; x += 3) contour.add(new Point(x, 15));
        // Bord droit (haut en bas)
        for (int y = 15; y < 30; y += 3) contour.add(new Point(30, y));
        // Bord inférieur (droite à gauche)
        for (int x = 30; x > 15; x -= 3) contour.add(new Point(x, 30));
        // Bord gauche (bas en haut)
        for (int y = 30; y > 15; y -= 3) contour.add(new Point(15, y));
        contour.add(new Point(15, 15)); // Fermeture

        // Route continue à x=36 (de y=10 à 35)
        BinaryMask roads = new BinaryMask(w, h);
        for (int y = 10; y <= 35; y++) {
            roads.set(36, y, true);
        }

        RoadSnapper snapper = new RoadSnapper();
        SnappingConfig config = SnappingConfig.builder().snapDistance(10).build();

        List<Point> result = snapper.snap(contour, roads, w, h, config);
        assertNotNull(result);
        assertEquals(contour.size(), result.size());

        // Le bord droit (x=30) doit avoir été attiré vers x=36
        boolean anyMovedRight = false;
        for (Point p : result) {
            if (p.x >= 35) {
                anyMovedRight = true;
                break;
            }
        }
        assertTrue(anyMovedRight, "Au moins un point du bord droit doit avoir été attiré vers la route à x=36");
    }

    @Test
    @DisplayName("Caler au bord intérieur (INNER) vs bord extérieur (OUTER) face à une route")
    void testSnapInnerVsOuterEdge() {
        int w = 50, h = 50;
        BinaryMask roads = new BinaryMask(w, h);
        // Route verticale de largeur 4 px à x=[25..28]
        for (int y = 0; y < h; y++) {
            for (int x = 25; x <= 28; x++) {
                roads.set(x, y, true);
            }
        }

        // Contour carré à gauche de la route : x=[10..20], y=[10..30]
        List<Point> contour = List.of(
                new Point(10, 10),
                new Point(20, 10),
                new Point(20, 30),
                new Point(10, 30)
        );

        RoadSnapper snapper = new RoadSnapper();

        // En mode OUTER : le bord droit à x=20 doit avancer jusqu'au bord extérieur x=29
        List<Point> snappedOuter = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.OUTER);
        assertEquals(29, snappedOuter.get(1).x, "Mode OUTER doit englober la route jusqu'à son bord extérieur (x=29)");

        // En mode INNER : le bord droit à x=20 doit s'arrêter juste avant la route à x=24
        List<Point> snappedInner = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.INNER);
        assertEquals(24, snappedInner.get(1).x, "Mode INNER doit s'arrêter juste avant la route (x=24)");
    }

    @Test
    @DisplayName("Rétraction en mode INNER si le sommet initial est déjà situé sur la chaussée")
    void testSnapInnerEdgeRetractWhenStartingOnRoad() {
        int w = 50, h = 50;
        BinaryMask roads = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 20; x <= 25; x++) {
                roads.set(x, y, true);
            }
        }

        // Contour dont le sommet droit est déjà sur la route à x=22
        List<Point> contour = List.of(
                new Point(10, 10),
                new Point(22, 10),
                new Point(22, 30),
                new Point(10, 30)
        );

        RoadSnapper snapper = new RoadSnapper();
        List<Point> snappedInner = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.INNER);
        // Doit être rétracté en arrière vers x=19 (hors de la route)
        assertEquals(19, snappedInner.get(1).x, "Mode INNER doit rétracter le sommet hors de la route à x=19");
    }

    @Test
    @DisplayName("Rejet des arguments invalides")
    void testValidation() {
        RoadSnapper snapper = new RoadSnapper();
        BinaryMask mask = new BinaryMask(20, 20);
        SnappingConfig config = SnappingConfig.defaults();

        assertThrows(IllegalArgumentException.class, () -> snapper.snap(null, mask, 20, 20, config));
        assertThrows(IllegalArgumentException.class, () -> snapper.snap(List.of(), null, 20, 20, config));
        assertThrows(IllegalArgumentException.class, () -> snapper.snap(List.of(), mask, 20, 20, null));
        assertThrows(IllegalArgumentException.class, () -> snapper.snap(List.of(new Point(0, 0)), mask, 30, 20, config));
    }
}
