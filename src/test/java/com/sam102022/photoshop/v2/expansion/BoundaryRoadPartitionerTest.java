package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant le partitionnement équitable de chaussée en mode ZONE sans chevauchement.
 */
@DisplayName("Tests du partitionneur médian de chaussée en mode ZONE")
class BoundaryRoadPartitionerTest {

    @Test
    @DisplayName("Partitionne équitablement une route entre Zone 1 et Zone 2 sans aucun chevauchement")
    void testEquitablePartitionWithoutOverlap() {
        int width = 30;
        int height = 10;
        // Route horizontale entre y = 3 et y = 6 (4 pixels de haut : 3, 4, 5, 6)
        BinaryMask road = new BinaryMask(width, height);
        for (int y = 3; y <= 6; y++) {
            for (int x = 0; x < width; x++) {
                road.set(x, y, true);
            }
        }

        // Zone 1 située au-dessus (y < 3)
        BinaryMask zone1 = new BinaryMask(width, height);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < width; x++) {
                zone1.set(x, y, true);
            }
        }

        // Zone 2 située en dessous (y > 6)
        BinaryMask zone2 = new BinaryMask(width, height);
        for (int y = 7; y < height; y++) {
            for (int x = 0; x < width; x++) {
                zone2.set(x, y, true);
            }
        }

        BoundaryRoadPartitioner partitioner = new BoundaryRoadPartitioner();
        BinaryMask allocatedToZone1 = partitioner.partition(road, zone1, zone2);
        BinaryMask allocatedToZone2 = partitioner.partition(road, zone2, zone1);

        // Les lignes les plus proches de Zone 1 (y = 3 et 4) sont attribuées à Zone 1
        assertTrue(allocatedToZone1.get(5, 3));
        assertTrue(allocatedToZone1.get(5, 4));
        assertFalse(allocatedToZone1.get(5, 5));
        assertFalse(allocatedToZone1.get(5, 6));

        // Les lignes les plus proches de Zone 2 (y = 5 et 6) sont attribuées à Zone 2
        assertTrue(allocatedToZone2.get(5, 5));
        assertTrue(allocatedToZone2.get(5, 6));
        assertFalse(allocatedToZone2.get(5, 3));
        assertFalse(allocatedToZone2.get(5, 4));

        // Invariance absolue : aucun pixel ne peut être alloué aux deux zones simultanément
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertFalse(allocatedToZone1.get(x, y) && allocatedToZone2.get(x, y),
                        "Aucun pixel ne doit être partagé entre Zone 1 et Zone 2");
            }
        }
    }
}
