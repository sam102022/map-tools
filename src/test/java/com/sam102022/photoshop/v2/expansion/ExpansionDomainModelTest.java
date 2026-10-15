package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires validant l'intégrité des modèles de domaine immuables du Sprint 5.
 */
@DisplayName("Tests des modèles de domaine du Sprint 5")
class ExpansionDomainModelTest {

    @Test
    @DisplayName("ExpansionConfig expose les valeurs par défaut pour TERRITORY et ZONE")
    void testExpansionConfigDefaults() {
        ExpansionConfig territory = ExpansionConfig.defaultTerritory();
        assertEquals(4.0, territory.epsilon());
        assertEquals(41, territory.maxFilterSize());
        assertEquals(5, territory.openingDiskRadius());
        assertEquals(15000L, territory.maxHoleArea());
        assertEquals(2, territory.dilationSeedSteps());
        assertEquals(OperationMode.TERRITORY, territory.operationMode());

        ExpansionConfig zone = ExpansionConfig.defaultZone();
        assertEquals(OperationMode.ZONE, zone.operationMode());
    }

    @Test
    @DisplayName("DistanceMap alloue et manipule les valeurs sans allocation d'objets")
    void testDistanceMapOperations() {
        DistanceMap map = new DistanceMap(10, 10, new float[100]);
        map.set(2, 3, 4.5f);
        assertEquals(4.5f, map.get(2, 3));
        assertThrows(IllegalArgumentException.class, () -> new DistanceMap(10, 10, new float[50]));
    }

    @Test
    @DisplayName("ConsolidatedMask valide les dimensions et calcule les offsets de CropWindow")
    void testConsolidatedMaskProperties() {
        BinaryMask mask = new BinaryMask(100, 80);
        CropWindow crop = new CropWindow(15, 25, 100, 80);
        ConsolidatedMask consolidated = new ConsolidatedMask(100, 80, mask, crop);

        assertEquals(100, consolidated.width());
        assertEquals(80, consolidated.height());
        assertEquals(15, consolidated.offsetX());
        assertEquals(25, consolidated.offsetY());
    }
}
