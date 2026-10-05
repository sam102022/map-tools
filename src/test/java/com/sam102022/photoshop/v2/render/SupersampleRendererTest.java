package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du rastériseur sub-pixel {@link SupersampleRenderer}.
 */
@DisplayName("Tests unitaires du rastériseur sub-pixel SupersampleRenderer")
class SupersampleRendererTest {

    @Test
    @DisplayName("Rastérisation sub-pixel d'un carré simple et vérification du box filter")
    void testRenderSimpleSquare() {
        // CropWindow 10x10 avec origine (5, 5) sur canevas global 20x20
        CropWindow cropWindow = new CropWindow(5, 5, 10, 10);

        // Polygone carré local centré de (2, 2) à (8, 8)
        List<PixelPoint> points = List.of(
                new PixelPoint(2.0, 2.0),
                new PixelPoint(8.0, 2.0),
                new PixelPoint(8.0, 8.0),
                new PixelPoint(2.0, 8.0)
        );
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), 10, 10, cropWindow);

        // Facteur d'affinage 1.0f partout
        float[][] factor = new float[10][10];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                factor[y][x] = 1.0f;
            }
        }
        AlphaRefinementMap refinementMap = new AlphaRefinementMap(10, 10, factor, cropWindow);

        SupersampleRenderer renderer = new SupersampleRenderer();
        CoverageMask coverage = renderer.render(contour, refinementMap, 4, 20, 20);

        assertNotNull(coverage);
        assertEquals(20, coverage.getWidth());
        assertEquals(20, coverage.getHeight());

        // Pixel intérieur plein (global 10, 10 -> local 5, 5) doit avoir couverture 255
        assertEquals(255, coverage.get(10, 10));

        // Pixel extérieur hors cropWindow (global 0, 0) doit avoir couverture 0
        assertEquals(0, coverage.get(0, 0));

        // Pixel extérieur dans cropWindow (global 6, 6 -> local 1, 1) doit avoir couverture 0
        assertEquals(0, coverage.get(6, 6));

        // Pixel sur la frontière doit avoir une valeur anti-aliasée intermédiaire
        int edgeCov = coverage.get(7, 7); // local (2, 2)
        assertTrue(edgeCov > 0 && edgeCov <= 255, "La couverture sur le bord doit être comprise entre 0 et 255");
    }

    @Test
    @DisplayName("Modulation effective par le facteur d'affinage spectral")
    void testModulationWithRefinementMap() {
        CropWindow cropWindow = new CropWindow(0, 0, 4, 4);
        List<PixelPoint> points = List.of(
                new PixelPoint(0.0, 0.0),
                new PixelPoint(4.0, 0.0),
                new PixelPoint(4.0, 4.0),
                new PixelPoint(0.0, 4.0)
        );
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), 4, 4, cropWindow);

        // Facteur 0.5f au centre
        float[][] factor = new float[4][4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                factor[y][x] = 0.5f;
            }
        }
        AlphaRefinementMap refinementMap = new AlphaRefinementMap(4, 4, factor, cropWindow);

        SupersampleRenderer renderer = new SupersampleRenderer();
        CoverageMask coverage = renderer.render(contour, refinementMap, 4, 4, 4);

        // Pixel au centre pleinement couvert (alpha_ss = 1.0) modulé à 0.5 -> ~128
        int covCenter = coverage.get(2, 2);
        assertTrue(Math.abs(covCenter - 128) <= 2, "La couverture modulée doit être d'environ 128 (actuel : " + covCenter + ")");
    }

    @Test
    @DisplayName("Rejet des arguments invalides ou nuls")
    void testInvalidInputs() {
        SupersampleRenderer renderer = new SupersampleRenderer();
        CropWindow cropWindow = new CropWindow(0, 0, 4, 4);
        SmoothVectorContour contour = new SmoothVectorContour(List.of(new PixelPoint(0, 0)), List.of(), 4, 4, cropWindow);
        AlphaRefinementMap map = new AlphaRefinementMap(4, 4, new float[4][4], cropWindow);

        assertThrows(NullPointerException.class, () -> renderer.render(null, map, 4, 10, 10));
        assertThrows(NullPointerException.class, () -> renderer.render(contour, null, 4, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(contour, map, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(contour, map, 4, 0, 10));
    }
}
