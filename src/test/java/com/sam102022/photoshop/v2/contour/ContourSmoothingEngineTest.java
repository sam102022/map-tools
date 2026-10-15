package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de la façade d'orchestration vectorielle {@link ContourSmoothingEngine}.
 */
@DisplayName("Tests unitaires de la façade ContourSmoothingEngine")
class ContourSmoothingEngineTest {

    /**
     * Vérifie la validation défensive contre les arguments nuls.
     */
    @Test
    @DisplayName("Lance une exception si un argument obligatoire est nul")
    void testValidationArguments() {
        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        assertThrows(NullPointerException.class, () -> engine.process(null, null, null, null, null, null));
    }

    /**
     * Vérifie que le constructeur par injection valide les dépendances non nulles.
     */
    @Test
    @DisplayName("Le constructeur par injection valide que les modules spécialisés sont non nuls")
    void testConstructorDependencyInjection() {
        assertThrows(NullPointerException.class, () -> new ContourSmoothingEngine(
                null, new CornerDetector(), new StraightSegmentSmoother(),
                new CornerPreservationBlender(), new RoundaboutDetector(), new HermiteSplineConnector()
        ));
        assertThrows(NullPointerException.class, () -> new ContourSmoothingEngine(
                new SubpixelContourExtractor(), null, new StraightSegmentSmoother(),
                new CornerPreservationBlender(), new RoundaboutDetector(), new HermiteSplineConnector()
        ));
        assertThrows(NullPointerException.class, () -> new ContourSmoothingEngine(
                new SubpixelContourExtractor(), new CornerDetector(), null,
                new CornerPreservationBlender(), new RoundaboutDetector(), new HermiteSplineConnector()
        ));
    }

    /**
     * Vérifie le traitement nominal complet sur un masque carré.
     */
    @Test
    @DisplayName("Traite un masque carré consolidé avec succès")
    void testProcessSquareMask() {
        int w = 80;
        int h = 80;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 20; y < 60; y++) {
            for (int x = 20; x < 60; x++) {
                mask.set(x, y, true);
            }
        }
        CropWindow crop = new CropWindow(0, 0, w, h);
        ConsolidatedMask consolidated = new ConsolidatedMask(w, h, mask, crop);
        BinaryMask roads = new BinaryMask(w, h);
        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);

        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        SmoothVectorContour result = engine.process(
                consolidated,
                roads,
                labelMap,
                List.of(),
                mask,
                ContourSmoothingConfig.defaultConfig()
        );

        assertNotNull(result);
        assertFalse(result.points().isEmpty());
    }
}
