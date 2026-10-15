package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la réduction du contour en polygone à grands segments {@link PolygonalSimplifier}.
 */
@DisplayName("Tests unitaires du simplificateur polygonal PolygonalSimplifier")
class PolygonalSimplifierTest {

    /**
     * Contour carré de côté 100 au pas de 1 px, bruité de ±0,3 px (alternance), coins en (50,50) et (150,150).
     *
     * @return Contour fermé bruité.
     */
    private static List<PixelPoint> noisySquare() {
        List<PixelPoint> pts = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            int side = i / 100;
            int k = i % 100;
            double noise = (k % 2 == 0) ? 0.3 : -0.3;
            switch (side) {
                case 0 -> pts.add(new PixelPoint(50 + k, 50 + noise));
                case 1 -> pts.add(new PixelPoint(150 + noise, 50 + k));
                case 2 -> pts.add(new PixelPoint(150 - k, 150 + noise));
                default -> pts.add(new PixelPoint(50 + noise, 150 - k));
            }
        }
        return pts;
    }

    /**
     * Un carré bruité est réduit à ses 4 sommets, placés à l'intersection des droites ajustées.
     */
    @Test
    @DisplayName("Un carré bruité est réduit à 4 sommets exacts")
    void testNoisySquareReducedToFourVertices() {
        SmoothVectorContour contour = new SmoothVectorContour(noisySquare(), List.of(0, 100, 200, 300),
                300, 300, new CropWindow(0, 0, 300, 300));

        SmoothVectorContour polygon = new PolygonalSimplifier().simplify(contour, 1.0);

        assertEquals(4, polygon.points().size(), "Le carré doit être réduit à 4 sommets.");
        for (PixelPoint v : polygon.points()) {
            boolean nearCorner = (Math.abs(v.x() - 50) < 0.5 || Math.abs(v.x() - 150) < 0.5)
                    && (Math.abs(v.y() - 50) < 0.5 || Math.abs(v.y() - 150) < 0.5);
            assertTrue(nearCorner, "Sommet inattendu : " + v);
        }
        assertEquals(4, polygon.cornerIndices().size(), "Les 4 coins doivent être réindexés sur les sommets.");
    }

    /**
     * Un cercle est restitué par des facettes dont la flèche reste inférieure à la tolérance.
     */
    @Test
    @DisplayName("Un cercle est facetté avec une flèche inférieure à la tolérance")
    void testCircleFacetsWithinTolerance() {
        List<PixelPoint> pts = new ArrayList<>();
        for (int i = 0; i < 315; i++) {
            double t = 2.0 * Math.PI * i / 315;
            pts.add(new PixelPoint(100 + 50 * Math.cos(t), 100 + 50 * Math.sin(t)));
        }
        SmoothVectorContour contour = new SmoothVectorContour(pts, List.of(), 200, 200, new CropWindow(0, 0, 200, 200));

        SmoothVectorContour polygon = new PolygonalSimplifier().simplify(contour, 1.0);

        int m = polygon.points().size();
        assertTrue(m >= 10 && m <= 30, "Nombre de facettes inattendu : " + m);
        for (int i = 0; i < m; i++) {
            PixelPoint a = polygon.points().get(i);
            PixelPoint b = polygon.points().get((i + 1) % m);
            double midR = Math.hypot((a.x() + b.x()) / 2 - 100, (a.y() + b.y()) / 2 - 100);
            assertTrue(Math.abs(midR - 50) < 1.6, "Flèche excessive sur la facette " + i + " : " + (50 - midR));
        }
    }

    /**
     * Tolérance nulle : le contour est retourné tel quel.
     */
    @Test
    @DisplayName("Une tolérance nulle désactive la simplification")
    void testZeroToleranceDisables() {
        SmoothVectorContour contour = new SmoothVectorContour(noisySquare(), List.of(), 300, 300,
                new CropWindow(0, 0, 300, 300));

        assertSame(contour, new PolygonalSimplifier().simplify(contour, 0.0));
    }
}
