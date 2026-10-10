package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests unitaires de la bande de protection du bord vectoriel {@link EdgeBandGuard}.
 */
@DisplayName("Tests unitaires de la bande de protection EdgeBandGuard")
class EdgeBandGuardTest {

    /**
     * Le facteur est forcé à 1 à moins de 2 px du contour et conservé ailleurs.
     */
    @Test
    @DisplayName("Force le facteur à 1 près du contour et le conserve ailleurs")
    void testProtectsBandOnly() {
        CropWindow window = new CropWindow(0, 0, 50, 50);
        float[][] factor = new float[50][50];
        AlphaRefinementMap map = new AlphaRefinementMap(50, 50, factor, window);
        List<PixelPoint> square = List.of(new PixelPoint(10, 10), new PixelPoint(40, 10),
                new PixelPoint(40, 40), new PixelPoint(10, 40));
        SmoothVectorContour contour = new SmoothVectorContour(square, List.of(), 50, 50, window);

        AlphaRefinementMap guarded = new EdgeBandGuard().protect(map, contour, 2.0);

        assertEquals(1.0f, guarded.factorAt(25, 10), 1e-6f, "Sur le contour.");
        assertEquals(1.0f, guarded.factorAt(25, 12), 1e-6f, "À 2 px du contour.");
        assertEquals(0.0f, guarded.factorAt(25, 25), 1e-6f, "Au centre, le facteur d'origine est conservé.");
        assertEquals(0.0f, guarded.factorAt(25, 14), 1e-6f, "À 4 px, le facteur d'origine est conservé.");
    }

    /**
     * Rayon nul : la carte est retournée telle quelle.
     */
    @Test
    @DisplayName("Un rayon nul retourne la carte d'origine")
    void testZeroRadiusReturnsSameMap() {
        CropWindow window = new CropWindow(0, 0, 10, 10);
        AlphaRefinementMap map = new AlphaRefinementMap(10, 10, new float[10][10], window);
        SmoothVectorContour contour = new SmoothVectorContour(
                List.of(new PixelPoint(1, 1), new PixelPoint(8, 1), new PixelPoint(8, 8)), List.of(), 10, 10, window);

        assertSame(map, new EdgeBandGuard().protect(map, contour, 0.0));
    }
}
