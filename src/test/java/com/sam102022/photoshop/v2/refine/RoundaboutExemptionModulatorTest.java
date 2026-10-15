package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du modulateur d'exemption des ronds-points {@link RoundaboutExemptionModulator}.
 * Valide la modulation continue smoothstep cubique C1 et la sanctuarisation de la zone de giratoire.
 */
@DisplayName("Tests du composant RoundaboutExemptionModulator (Sprint 8)")
class RoundaboutExemptionModulatorTest {

    @Test
    @DisplayName("Validation des paramètres d'entrée")
    void testParameterValidation() {
        RoundaboutExemptionModulator modulator = new RoundaboutExemptionModulator();
        float[][] raw = new float[10][10];

        assertThrows(NullPointerException.class, () -> modulator.modulate(null, List.of(), 2.3, 3.0));
        assertThrows(NullPointerException.class, () -> modulator.modulate(raw, null, 2.3, 3.0));
        assertThrows(IllegalArgumentException.class, () -> modulator.modulate(new float[0][0], List.of(), 2.3, 3.0));
        assertThrows(IllegalArgumentException.class, () -> modulator.modulate(raw, List.of(), 0.0, 3.0));
        assertThrows(IllegalArgumentException.class, () -> modulator.modulate(raw, List.of(), 2.5, 2.0));
    }

    @Test
    @DisplayName("Liste de ronds-points vide : le facteur retourné est identique au facteur brut")
    void testEmptyRoundaboutsLeavesFactorUnchanged() {
        RoundaboutExemptionModulator modulator = new RoundaboutExemptionModulator();
        int w = 20;
        int h = 20;
        float[][] raw = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                raw[y][x] = 0.35f;
            }
        }

        float[][] modulated = modulator.modulate(raw, List.of(), 2.3, 3.0);
        assertNotNull(modulated);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                assertEquals(0.35f, modulated[y][x], 1e-6f);
            }
        }
    }

    @Test
    @DisplayName("Protection d'un giratoire : zw=1.0 à l'intérieur (rho <= 2.3), transition smoothstep sur ]2.3..3.0[ et zw=0.0 au-delà (rho >= 3.0)")
    void testRoundaboutProtectionAndSmoothstepProfile() {
        RoundaboutExemptionModulator modulator = new RoundaboutExemptionModulator();
        int w = 150;
        int h = 150;
        // Facteur brut initialisé à 0.0f partout (extérieur non autorisé)
        float[][] raw = new float[h][w];

        // Cercle centré en (75, 75) avec rayon a = 20, b = 20
        double xc = 75.0;
        double yc = 75.0;
        double a = 20.0;
        double b = 20.0;
        EllipseModel ell = new EllipseModel(xc, yc, a, b, 0.0);
        Roundabout rb = new Roundabout(1, new PixelPoint(xc, yc), ell, 300.0, 0.95);

        double fz0 = 2.3;
        double fz1 = 3.0;

        float[][] modulated = modulator.modulate(raw, List.of(rb), fz0, fz1);

        // 1. Au centre (75, 75) : rho = 0 <= 2.3 => le facteur doit être sanctuarisé à 1.0f
        assertEquals(1.0f, modulated[75][75], 1e-4f, "Le centre du rond-point doit être à 1.0f");

        // 2. Sur l'ellipse intérieure rho = 2.0 (distance euclidienne 40 px : x = 75 + 40 = 115)
        // rho = 40 / 20 = 2.0 <= 2.3 => facteur à 1.0f
        assertEquals(1.0f, modulated[75][115], 1e-4f, "La zone intérieure rho <= 2.3 doit être à 1.0f");

        // 3. Juste à la frontière interne fz0 = 2.3 (distance = 2.3 * 20 = 46 px : x = 75 + 46 = 121)
        assertEquals(1.0f, modulated[75][121], 1e-2f, "La frontière rho = 2.3 doit être à 1.0f");

        // 4. Au-delà de la zone tampon fz1 = 3.0 (distance >= 3.0 * 20 = 60 px : x = 75 + 65 = 140)
        // rho = 65 / 20 = 3.25 >= 3.0 => zw = 0 => facteur préservé à 0.0f
        assertEquals(0.0f, modulated[75][140], 1e-4f, "Au-delà de rho = 3.0, le facteur reste identique à l'entrée");

        // 5. Profil de décroissance continue C1 dans la zone de transition ]2.3..3.0[
        // Test le long du rayon horizontal y = 75, de x = 121 (rho ~ 2.3) à x = 135 (rho ~ 3.0)
        for (int x = 121; x < 135; x++) {
            assertTrue(modulated[75][x] >= modulated[75][x + 1],
                    "Le facteur doit décroître de façon monotone dans la zone de transition smoothstep à x=" + x);
            assertTrue(modulated[75][x] >= 0.0f && modulated[75][x] <= 1.0f,
                    "Le facteur doit être borné dans [0.0, 1.0]");
        }
    }
}
