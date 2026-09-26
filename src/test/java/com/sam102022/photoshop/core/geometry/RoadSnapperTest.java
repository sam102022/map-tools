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
