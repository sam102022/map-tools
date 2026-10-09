package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires de la découpe exacte le long des routes frontières {@link RoadExactCoverage}.
 */
@DisplayName("Tests unitaires de RoadExactCoverage")
class RoadExactCoverageTest {

    /**
     * Zone à gauche (colonnes 0 à 49), route frontière verticale (colonnes 50 à 59, bord gauche anti-crénelé à
     * mi-gris en colonne 50), terrain au-delà ; la couverture vectorielle déborde de 3 px sur la route et un
     * fragment isolé subsiste au-delà de la route.
     */
    @Test
    @DisplayName("Bord de chaussée exact, fragment au-delà de la route retiré")
    void testExactEdgeAndFragmentRemoval() {
        int w = 100;
        int h = 60;
        BufferedImage capture = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BinaryMask zone = new BinaryMask(w, h);
        BinaryMask road = new BinaryMask(w, h);
        CoverageMask coverage = new CoverageMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = x == 50 ? 128 : (x > 50 && x < 60 ? 255 : 0);
                capture.setRGB(x, y, (v << 16) | (v << 8) | v);
                road.set(x, y, x >= 50 && x < 60);
                zone.set(x, y, x < 50);
                coverage.set(x, y, x < 53 || (x >= 70 && x < 75 && y >= 20 && y < 25) ? 255 : 0);
            }
        }
        GoogleRoadsImage roads = GoogleRoadsImage.from(capture);

        new RoadExactCoverage().refine(coverage, zone, road, roads, new CropWindow(0, 0, w, h));

        assertEquals(255, coverage.get(30, 30), "cœur de la zone opaque");
        assertEquals(255, coverage.get(49, 30), "terrain de la zone jusqu'au bord");
        assertEquals(127, coverage.get(50, 30), 2, "liseré anti-crénelé de la chaussée");
        assertEquals(0, coverage.get(52, 30), "chaussée frontière exclue malgré le débord du contour");
        assertEquals(0, coverage.get(72, 22), "fragment isolé au-delà de la route retiré");
    }
}
