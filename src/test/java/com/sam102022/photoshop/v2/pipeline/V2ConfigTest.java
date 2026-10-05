package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.contour.ContourSmoothingConfig;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.refine.AlphaRefinementConfig;
import com.sam102022.photoshop.v2.vote.CellSelectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires du record immuable de configuration {@link V2Config}.
 */
@DisplayName("Tests unitaires du record immuable V2Config")
class V2ConfigTest {

    @Test
    @DisplayName("Configuration par défaut nominale conforme aux étalons Python V7")
    void testDefaultConfig() {
        V2Config config = V2Config.defaultConfig();

        assertNotNull(config);
        assertEquals(0.60, config.hi(), 1e-6);
        assertEquals(0.05, config.lo(), 1e-6);
        assertEquals(90, config.cropMargin());

        assertEquals(4.0, config.eps(), 1e-6);
        assertEquals(5, config.rho());
        assertEquals(15000L, config.residualHoleMaxArea());
        assertEquals(OperationMode.TERRITORY, config.operationMode());

        assertEquals(22.0, config.sig(), 1e-6);
        assertEquals(38.0, config.cornerAngle(), 1e-6);
        assertEquals(80, config.cornerWindowL());
        assertEquals(35.0, config.r0(), 1e-6);
        assertEquals(95.0, config.r1(), 1e-6);
        assertEquals(2.0, config.roundaboutRadiusZr(), 1e-6);

        assertEquals(2.7, config.straightFactor(), 1e-6);
        assertEquals(3.0, config.straightScale(), 1e-6);
        assertEquals(3.0, config.straightThreshold(), 1e-6);
        assertEquals(10, config.straightTransitionK());

        assertEquals(1.4, config.gaussianBlurSigma(), 1e-6);
        assertEquals(2.0, config.contrastStiffness(), 1e-6);
        assertEquals(2.3, config.roundaboutBufferInner(), 1e-6);
        assertEquals(3.0, config.roundaboutBufferOuter(), 1e-6);
        assertEquals(2000L, config.roadHoleMaxArea());

        assertEquals(4, config.supersamplingFactor());
    }

    @Test
    @DisplayName("Génération des sous-configurations modulaires")
    void testSubConfigConversions() {
        V2Config config = V2Config.defaultConfig();

        CellSelectionPolicy policy = config.toCellSelectionPolicy();
        assertEquals(0.60, policy.insideThreshold(), 1e-6);
        assertEquals(0.05, policy.partialThreshold(), 1e-6);

        ExpansionConfig expConfig = config.toExpansionConfig();
        assertEquals(4.0, expConfig.epsilon(), 1e-6);
        assertEquals(5, expConfig.openingDiskRadius());
        assertEquals(15000L, expConfig.maxHoleArea());

        ContourSmoothingConfig smoothConfig = config.toContourSmoothingConfig();
        assertEquals(22.0, smoothConfig.lqrSigma(), 1e-6);
        assertEquals(2.7, smoothConfig.straightFactor(), 1e-6);

        AlphaRefinementConfig refConfig = config.toAlphaRefinementConfig();
        assertEquals(1.4, refConfig.gaussianBlurSigma(), 1e-6);
        assertEquals(2000L, refConfig.roadHoleMaxArea());
    }

    @Test
    @DisplayName("Validation défensive des bornes")
    void testValidationBounds() {
        assertThrows(IllegalArgumentException.class, () -> new V2Config(
                -0.1, 0.05, 90, 4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0, 2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L, 4
        ));

        assertThrows(IllegalArgumentException.class, () -> new V2Config(
                0.60, 0.05, 90, 4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0, 2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L, 0
        ));
    }
}
