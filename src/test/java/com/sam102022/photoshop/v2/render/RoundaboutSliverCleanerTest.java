package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du retrait des éclats de terrain autour des giratoires {@link RoundaboutSliverCleaner}.
 */
@DisplayName("Tests unitaires du nettoyeur d'éclats RoundaboutSliverCleaner")
class RoundaboutSliverCleanerTest {

    private static final int SIZE = 100;
    private static final int CENTER = 50;
    private static final int RING_OUTER = 20;
    private static final int RING_INNER = 10;

    /**
     * Un petit coin de terrain collé à l'anneau, au bord du territoire, devient transparent ; l'îlot central,
     * entouré par l'anneau, reste opaque.
     */
    @Test
    @DisplayName("Retire l'éclat de terrain au bord et conserve l'îlot central")
    void testRemovesSliverKeepsIsland() {
        CoverageMask coverage = new CoverageMask(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean disc = Math.hypot(x - CENTER, y - CENTER) <= RING_OUTER;
                boolean sliver = x >= CENTER + RING_OUTER - 2 && x <= CENTER + RING_OUTER + 4
                        && y >= CENTER - 3 && y <= CENTER + 3;
                coverage.set(x, y, disc || sliver ? 255 : 0);
            }
        }
        Roundabout roundabout = new Roundabout(new PixelPoint(CENTER, CENTER),
                new EllipseModel(CENTER, CENTER, RING_OUTER, RING_OUTER, 0.0), 300.0, 1.0);

        int removed = new RoundaboutSliverCleaner().clean(coverage, List.of(roundabout), ringRoads(),
                new CropWindow(0, 0, SIZE, SIZE));

        assertTrue(removed > 0, "L'éclat doit être retiré.");
        assertEquals(0, coverage.get(CENTER + RING_OUTER + 3, CENTER), "L'éclat de terrain doit être transparent.");
        assertEquals(255, coverage.get(CENTER, CENTER), "L'îlot central doit rester opaque.");
        assertEquals(255, coverage.get(CENTER + 15, CENTER), "L'anneau doit rester opaque.");
    }

    /**
     * Capture « Routes seules » synthétique : un anneau blanc sur fond noir.
     *
     * @return Champ de chaussée.
     */
    private GoogleRoadsImage ringRoads() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double r = Math.hypot(x - CENTER, y - CENTER);
                image.setRGB(x, y, r >= RING_INNER && r <= RING_OUTER ? 0xFFFFFF : 0x000000);
            }
        }
        return GoogleRoadsImage.from(image);
    }
}
