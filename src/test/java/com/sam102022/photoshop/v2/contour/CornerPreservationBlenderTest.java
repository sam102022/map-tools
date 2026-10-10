package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests du fondu de préservation des coins CornerPreservationBlender")
class CornerPreservationBlenderTest {

    @Test
    @DisplayName("Conserve le contour brut à 100% à distance <= R0 et applique le lissage à 100% à distance >= R1")
    void testBlendBetweenR0AndR1() {
        int n = 200;
        List<PixelPoint> raw = new ArrayList<>();
        List<PixelPoint> smoothed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            raw.add(new PixelPoint(i, 0.0));
            smoothed.add(new PixelPoint(i, 10.0));
        }

        // Coin à l'indice 0
        List<Integer> corners = List.of(0);
        CornerPreservationBlender blender = new CornerPreservationBlender();
        List<PixelPoint> blended = blender.blend(raw, smoothed, corners, 35.0, 95.0);

        // Au coin (i=0) et jusqu'à 35 px : brut à 100% (y = 0.0)
        assertEquals(0.0, blended.get(0).y(), 1e-6);
        assertEquals(0.0, blended.get(20).y(), 1e-6);
        assertEquals(0.0, blended.get(35).y(), 1e-6);

        // À distance >= 95 px (i=100) : lissé à 100% (y = 10.0)
        assertEquals(10.0, blended.get(100).y(), 1e-6);

        // À mi-chemin (d = 65 px), smoothstep = 0.5 (y = 5.0)
        assertEquals(5.0, blended.get(65).y(), 1e-4);
    }
}
