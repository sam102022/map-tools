package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la façade d'orchestration {@link AlphaRefiner}.
 * Valide l'enchaînement de l'union binaire, du flou gaussien à seuil raide
 * et de la modulation par bouclier des giratoires.
 */
@DisplayName("Tests du composant AlphaRefiner (Sprint 8)")
class AlphaRefinerTest {

    @Test
    @DisplayName("Validation des paramètres d'entrée : rejet systématique des arguments nuls")
    void testParameterValidation() {
        AlphaRefiner refiner = new AlphaRefiner();
        CropWindow cropWindow = new CropWindow(10, 10, 30, 30);
        ConsolidatedMask consolidatedMask = new ConsolidatedMask(30, 30, new BinaryMask(30, 30), cropWindow);
        BinaryMask road = new BinaryMask(30, 30);
        CellLabelMap labelMap = new CellLabelMap(30, 30, new int[30 * 30], 0);
        AlphaRefinementConfig config = AlphaRefinementConfig.defaultConfig();

        assertThrows(NullPointerException.class, () -> refiner.refine(null, road, labelMap, List.of(), config));
        assertThrows(NullPointerException.class, () -> refiner.refine(consolidatedMask, null, labelMap, List.of(), config));
        assertThrows(NullPointerException.class, () -> refiner.refine(consolidatedMask, road, null, List.of(), config));
        assertThrows(NullPointerException.class, () -> refiner.refine(consolidatedMask, road, labelMap, null, config));
        assertThrows(NullPointerException.class, () -> refiner.refine(consolidatedMask, road, labelMap, List.of(), null));
    }

    @Test
    @DisplayName("Orchestration nominale : production d'une AlphaRefinementMap cohérente")
    void testNominalRefinement() {
        AlphaRefiner refiner = new AlphaRefiner();
        int w = 40;
        int h = 40;
        CropWindow cropWindow = new CropWindow(5, 5, w, h);

        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) {
                mask.set(x, y, true);
            }
        }
        ConsolidatedMask consolidatedMask = new ConsolidatedMask(w, h, mask, cropWindow);
        BinaryMask road = new BinaryMask(w, h);
        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);

        // Appel avec surcharge de commodité
        AlphaRefinementMap refinementMap = refiner.refine(consolidatedMask, road, labelMap, List.of());

        assertNotNull(refinementMap);
        assertEquals(w, refinementMap.width());
        assertEquals(h, refinementMap.height());
        assertEquals(cropWindow, refinementMap.cropWindow());

        // Au cœur de la zone consolidée (x=20, y=20), le facteur doit valoir 1.0
        assertEquals(1.0f, refinementMap.factorAt(20, 20), 1e-4f);
        // Hors de la zone consolidée (x=2, y=2), le facteur doit valoir 0.0
        assertEquals(0.0f, refinementMap.factorAt(2, 2), 1e-4f);

        // Tous les facteurs doivent être strictement bornés dans [0.0..1.0]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float f = refinementMap.factorAt(x, y);
                assertTrue(f >= 0.0f && f <= 1.0f, "Facteur hors bornes au pixel (" + x + "," + y + "): " + f);
            }
        }
    }

    @Test
    @DisplayName("Orchestration avec intégration de rond-point substitué")
    void testRefinementWithRoundabout() {
        AlphaRefiner refiner = new AlphaRefiner();
        int w = 60;
        int h = 60;
        CropWindow cropWindow = new CropWindow(0, 0, w, h);
        ConsolidatedMask consolidatedMask = new ConsolidatedMask(w, h, new BinaryMask(w, h), cropWindow);
        BinaryMask road = new BinaryMask(w, h);
        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);

        EllipseModel ell = new EllipseModel(30.0, 30.0, 10.0, 10.0, 0.0);
        Roundabout rb = new Roundabout(1, new PixelPoint(30.0, 30.0), ell, 80.0, 0.95);

        AlphaRefinementConfig config = new AlphaRefinementConfig(1.4, 2.0, 2.3, 3.0, 2000L, true);
        AlphaRefinementMap map = refiner.refine(consolidatedMask, road, labelMap, List.of(rb), config);

        assertNotNull(map);
        // Bouclier activé : le centre du rond-point est protégé à 1.0f
        assertEquals(1.0f, map.factorAt(30, 30), 1e-4f);
    }

    /**
     * Par défaut, le bouclier des giratoires est désactivé : le fond situé hors de la zone autorisée
     * (ni chaussée, ni territoire consolidé, ni îlot substitué) est retiré même près d'un giratoire.
     */
    @Test
    @DisplayName("Par défaut, le fond hors zone autorisée est retiré même près d'un giratoire substitué")
    void testDefaultConfigDoesNotShieldRoundaboutSurroundings() {
        AlphaRefiner refiner = new AlphaRefiner();
        int w = 60;
        int h = 60;
        CropWindow cropWindow = new CropWindow(0, 0, w, h);
        ConsolidatedMask consolidatedMask = new ConsolidatedMask(w, h, new BinaryMask(w, h), cropWindow);
        BinaryMask road = new BinaryMask(w, h);
        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);
        EllipseModel ell = new EllipseModel(30.0, 30.0, 10.0, 10.0, 0.0);
        Roundabout rb = new Roundabout(1, new PixelPoint(30.0, 30.0), ell, 80.0, 0.95);

        AlphaRefinementMap map = refiner.refine(consolidatedMask, road, labelMap, List.of(rb),
                AlphaRefinementConfig.defaultConfig());

        assertEquals(0.0f, map.factorAt(30, 45), 1e-4f, "Le fond blanc proche du giratoire doit être retiré.");
    }
}
