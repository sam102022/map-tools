package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires de la garde du terrain des îlots retenus {@link PartialCellGuard}.
 */
@DisplayName("Tests unitaires de la garde des îlots retenus PartialCellGuard")
class PartialCellGuardTest {

    private static final int SIZE = 100;
    private static final int ROAD_Y = 50;

    /**
     * Îlot 1 (y &lt; 50) retenu sauf une encoche ; îlot 2 (y &gt; 50) hors du territoire. Sous le contour, l'encoche
     * de l'îlot 1 redevient opaque, le terrain de l'îlot 2 reste effacé.
     */
    @Test
    @DisplayName("Garde l'encoche de l'îlot retenu et laisse l'îlot voisin à l'affinage")
    void testKeepsNotchOfOccupiedCellOnly() {
        int[] labels = new int[SIZE * SIZE];
        BinaryMask consolidated = new BinaryMask(SIZE, SIZE);
        float[][] factor = new float[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                labels[y * SIZE + x] = y < ROAD_Y ? 1 : y > ROAD_Y ? 2 : 0;
                boolean notch = x >= 40 && x < 50 && y >= 30 && y < ROAD_Y;
                boolean kept = y < ROAD_Y && !notch;
                consolidated.set(x, y, kept);
                factor[y][x] = kept ? 1.0f : 0.0f;
            }
        }
        CropWindow window = new CropWindow(0, 0, SIZE, SIZE);
        SmoothVectorContour contour = new SmoothVectorContour(List.of(new PixelPoint(5, 5), new PixelPoint(95, 5),
                new PixelPoint(95, 70), new PixelPoint(5, 70)), List.of(), SIZE, SIZE, window);

        AlphaRefinementMap guarded = new PartialCellGuard().protect(new AlphaRefinementMap(SIZE, SIZE, factor, window),
                contour, consolidated, new CellLabelMap(SIZE, SIZE, labels, 2));

        assertEquals(1.0f, guarded.factorAt(45, 40), "L'encoche de l'îlot retenu doit être gardée.");
        assertEquals(0.0f, guarded.factorAt(45, 60), "Le terrain de l'îlot voisin doit rester effacé.");
        assertEquals(0.0f, guarded.factorAt(45, ROAD_Y), "La chaussée reste soumise à l'affinage.");
    }
}
