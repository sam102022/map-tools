package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests unitaires validant l'orchestrateur de haut niveau RoadBoundaryConsolidator.
 */
@DisplayName("Tests de l'orchestrateur RoadBoundaryConsolidator")
class RoadBoundaryConsolidatorTest {

    @Test
    @DisplayName("Exécute la chaîne complète et retourne un ConsolidatedMask cohérent")
    void testEndToEndConsolidation() {
        int width = 50;
        int height = 50;
        BinaryMask retained = new BinaryMask(width, height);
        for (int y = 15; y < 35; y++) {
            for (int x = 15; x < 35; x++) {
                retained.set(x, y, true);
            }
        }
        BinaryMask road = new BinaryMask(width, height);
        for (int y = 10; y <= 14; y++) {
            for (int x = 10; x < 40; x++) {
                road.set(x, y, true);
            }
        }
        CropWindow crop = new CropWindow(100, 200, width, height);

        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask result = consolidator.consolidate(retained, road, crop, ExpansionConfig.defaultTerritory());

        assertNotNull(result);
        assertEquals(width, result.width());
        assertEquals(height, result.height());
        assertEquals(100, result.offsetX());
        assertEquals(200, result.offsetY());
    }
}
