package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la rectification du bord de zone le long du tracé {@link TraceAlignedOutline}.
 */
@DisplayName("Tests unitaires de TraceAlignedOutline")
class TraceAlignedOutlineTest {

    /**
     * Tracé carré [20, 180[ ; le masque de zone s'arrête 10 px à l'intérieur (bord de chaussée), avec deux
     * renfoncements de 6 px (débouchés de rues) sur le côté haut et un grand renfoncement circulaire de rayon 30
     * (giratoire exclu) sur le côté bas. Le côté haut devient rectiligne à 10 px du tracé ; le giratoire reste exclu.
     */
    @Test
    @DisplayName("Bord rectiligne parallèle au tracé, giratoire conservé")
    void testStraightEdgeAndRoundaboutKept() {
        BinaryMask trace = new BinaryMask(200, 200);
        BinaryMask zone = new BinaryMask(200, 200);
        for (int y = 0; y < 200; y++) {
            for (int x = 0; x < 200; x++) {
                trace.set(x, y, x >= 20 && x < 180 && y >= 20 && y < 180);
                boolean inner = x >= 30 && x < 170 && y >= 30 && y < 170;
                boolean notch = y < 36 && ((x >= 60 && x < 75) || (x >= 120 && x < 135));
                boolean roundabout = Math.hypot(x - 100, y - 172) < 30;
                zone.set(x, y, inner && !notch && !roundabout);
            }
        }

        BinaryMask aligned = new TraceAlignedOutline().align(zone, trace);

        assertTrue(aligned.get(67, 32), "renfoncement comblé");
        assertTrue(aligned.get(127, 32), "renfoncement comblé");
        assertFalse(aligned.get(67, 27), "le bord reste au bord de chaussée");
        assertTrue(aligned.get(100, 100));
        assertFalse(aligned.get(100, 160), "giratoire toujours exclu");
        assertFalse(aligned.get(10, 10));
    }
}
