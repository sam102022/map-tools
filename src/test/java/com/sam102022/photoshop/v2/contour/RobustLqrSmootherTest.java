package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests du lisseur quadratique robuste LQR")
class RobustLqrSmootherTest {

    @Test
    @DisplayName("Ignore une encoche étroite de carrefour (5 px) sans affaisser la ligne principale")
    void testRejetEncocheCarrefour() {
        int n = 200;
        List<PixelPoint> segment = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double y = 50.0;
            // Encoche transversale étroite de 5 px de large et 20 px de profondeur au centre (i=100)
            if (i >= 98 && i <= 102) {
                y += 20.0;
            }
            segment.add(new PixelPoint(i, y));
        }

        RobustLqrSmoother smoother = new RobustLqrSmoother();
        List<PixelPoint> smoothed = smoother.smoothSegment(segment, 22.0, 3.0, 6);

        assertEquals(n, smoothed.size());
        // Au centre (i=100), le lissage robuste doit rejeter l'encoche et rester très proche de y=50
        PixelPoint center = smoothed.get(100);
        assertEquals(50.0, center.y(), 2.0, "L'encoche doit être effacée comme valeur aberrante.");
    }

    /**
     * Vérifie que le lissage de résidus nuls produit des sorties nulles.
     */
    @Test
    @DisplayName("smoothResiduals préserve des résidus strictement nuls")
    void testSmoothResidualsPreservesZeroResiduals() {
        RobustLqrSmoother smoother = new RobustLqrSmoother();
        double[] zeros = new double[50];
        RobustLqrSmoother.LqrResidualResult result = smoother.smoothResiduals(zeros, zeros, 10.0, 3.0, 4);
        assertEquals(50, result.fitX().length);
        assertEquals(50, result.fitY().length);
        for (int i = 0; i < 50; i++) {
            assertEquals(0.0, result.fitX()[i], 1e-6);
            assertEquals(0.0, result.fitY()[i], 1e-6);
        }
    }
}
