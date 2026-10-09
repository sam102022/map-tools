package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de l'accrochage du contour sur le bord vectoriel des routes {@link RoadEdgeSnapper}.
 */
@DisplayName("Tests unitaires de l'accrocheur de bord de route RoadEdgeSnapper")
class RoadEdgeSnapperTest {

    private static final Color BACKGROUND = new Color(248, 247, 247);
    private static final Color ROAD = new Color(203, 217, 230);

    /**
     * Construit une carte synthétique : fond clair, carré intérieur [60, 140]² bordé d'une route anti-crénelée
     * dont le bord extérieur est à x = 150.3 (côté droit uniquement).
     *
     * @return Image ARGB 200 x 200.
     */
    private static BufferedImage syntheticMap() {
        BufferedImage img = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BACKGROUND);
            g.fillRect(0, 0, 200, 200);
            g.setColor(ROAD);
            // Route verticale de x = 140 à x = 150.3 (bord extérieur sub-pixel), de y = 20 à y = 180
            g.fill(new Rectangle2D.Double(140.0, 20.0, 10.3, 160.0));
        } finally {
            g.dispose();
        }
        return img;
    }

    /**
     * Contour carré fermé (sens horaire en repère image) au pas de 1 px dont le côté droit est à x = rightX.
     *
     * @param rightX Abscisse du côté droit.
     * @return Contour fermé.
     */
    private static List<PixelPoint> square(double rightX) {
        List<PixelPoint> pts = new ArrayList<>();
        for (int x = 60; x < (int) rightX; x++) {
            pts.add(new PixelPoint(x, 60));
        }
        for (int y = 60; y < 140; y++) {
            pts.add(new PixelPoint(rightX, y));
        }
        for (int x = (int) rightX; x > 60; x--) {
            pts.add(new PixelPoint(x, 140));
        }
        for (int y = 140; y > 60; y--) {
            pts.add(new PixelPoint(60, y));
        }
        return pts;
    }

    /**
     * Le côté droit du contour, à 3 px à l'intérieur de la route, est ramené sur son bord extérieur ; les côtés
     * sans route sont inchangés.
     */
    @Test
    @DisplayName("Ramène sur le bord extérieur de la route le tronçon qui la longe, sans toucher les autres")
    void testSnapsAlongRoadOnly() {
        CropWindow window = new CropWindow(0, 0, 200, 200);
        SmoothVectorContour contour = new SmoothVectorContour(square(147.0), List.of(), 200, 200, window);

        SmoothVectorContour snapped = new RoadEdgeSnapper().snap(contour, syntheticMap(), RoadEdgeSnapConfig.defaultConfig());

        assertEquals(contour.points().size(), snapped.points().size());
        PixelPoint middleRight = snapped.points().get(87 + 40);
        // Bord à x = 150.3 en coordonnées de bord de pixel, soit 149.8 en coordonnées de centre de pixel
        assertEquals(149.8, middleRight.x(), 0.35, "Le côté droit doit être accroché au bord de la route.");
        PixelPoint middleLeft = snapped.points().get(snapped.points().size() - 40);
        assertEquals(60.0, middleLeft.x(), 1e-9, "Le côté gauche, sans route, doit rester inchangé.");
    }

    /**
     * Configuration désactivée : le contour est retourné tel quel.
     */
    @Test
    @DisplayName("L'accrochage désactivé retourne le contour d'origine")
    void testDisabledReturnsSameContour() {
        CropWindow window = new CropWindow(0, 0, 200, 200);
        SmoothVectorContour contour = new SmoothVectorContour(square(147.0), List.of(), 200, 200, window);

        SmoothVectorContour result = new RoadEdgeSnapper().snap(contour, syntheticMap(), RoadEdgeSnapConfig.disabled());

        assertSame(contour, result);
    }

    /**
     * Sur une carte sans route, aucun point ne bouge.
     */
    @Test
    @DisplayName("Sans route à proximité, le contour n'est pas déplacé")
    void testNoRoadNoMove() {
        BufferedImage blank = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blank.createGraphics();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        CropWindow window = new CropWindow(0, 0, 200, 200);
        SmoothVectorContour contour = new SmoothVectorContour(square(147.0), List.of(), 200, 200, window);

        SmoothVectorContour result = new RoadEdgeSnapper().snap(contour, blank, RoadEdgeSnapConfig.defaultConfig());

        for (int i = 0; i < contour.points().size(); i++) {
            assertEquals(contour.points().get(i).x(), result.points().get(i).x(), 1e-9);
            assertEquals(contour.points().get(i).y(), result.points().get(i).y(), 1e-9);
        }
        assertTrue(result.points().size() > 0);
    }
}
