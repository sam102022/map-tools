package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du détecteur de ronds-points")
class RoundaboutDetectorTest {

    @Test
    @DisplayName("Détecte un rond-point synthétique constitué d'un îlot circulaire entouré d'un anneau routier")
    void testDetectSyntheticRoundabout() {
        int w = 120;
        int h = 120;
        int cx = 60;
        int cy = 60;
        int islandR = 10;
        int roadR = 20;

        // Chaussée Rc (anneau entre islandR et roadR)
        BinaryMask rc = new BinaryMask(w, h);
        int[] labels = new int[w * h];
        long islandArea = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dist = Math.hypot(x - cx, y - cy);
                if (dist <= islandR) {
                    labels[y * w + x] = 1; // Îlot central
                    islandArea++;
                } else if (dist <= roadR) {
                    rc.set(x, y, true); // Anneau de route
                    labels[y * w + x] = 0;
                } else {
                    labels[y * w + x] = 2; // Extérieur
                }
            }
        }

        CellLabelMap labelMap = new CellLabelMap(w, h, labels, 2);
        Cell island = new Cell(1, islandArea, cx - islandR, cy - islandR, cx + islandR, cy + islandR, new PixelPoint(cx, cy));

        RoundaboutDetector detector = new RoundaboutDetector();
        List<Roundabout> detected = detector.detect(rc, labelMap, List.of(island), 90, 240);

        assertEquals(1, detected.size(), "Le rond-point synthétique doit être détecté.");
        Roundabout rb = detected.get(0);
        assertEquals(cx, rb.center().x(), 1.0);
        assertEquals(cy, rb.center().y(), 1.0);
        assertEquals(roadR, rb.exteriorEllipse().a(), 2.0);
        assertTrue(rb.inlierRatio() >= 0.5);
    }
}
