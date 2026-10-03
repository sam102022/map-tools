package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation d'intégration du Sprint 2 sur l'étalon Territoire CA01.
 * Compare la détection et le nettoyage du masque des routes avec l'image témoin de référence {@code maps/road.png}.
 */
@DisplayName("Validation d'intégration du Sprint 2 sur l'étalon Territoire CA01")
class Sprint2IntegrationTest {

    /**
     * Valide la conformité de la détection et du nettoyage de RoadMask avec l'image témoin Python maps/road.png.
     *
     * @throws IOException en cas d'erreur de lecture des ressources ou fichiers images.
     */
    @Test
    @DisplayName("Conformité de la détection et du nettoyage de RoadMask avec l'image témoin Python maps/road.png")
    void testRoadMaskPipelineConformity() throws IOException {
        // 1. Chargement de l'image source CA01
        InputStream mapStream = getClass().getResourceAsStream("/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        assertNotNull(mapStream, "L'image fixture 05_style_contraste_sans_rien.png doit être présente");
        BufferedImage mapImage = ImageIO.read(mapStream);

        // 2. Exécution du pipeline Sprint 2
        long t0 = System.currentTimeMillis();
        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask raw = detector.detect(mapImage);

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask roadMask = cleaner.clean(raw);
        long elapsed = System.currentTimeMillis() - t0;

        System.out.println("Pipeline Sprint 2 exécuté en " + elapsed + " ms");
        assertTrue(elapsed < 2000, "Le pipeline Sprint 2 doit s'exécuter en moins de 2 secondes");

        assertEquals(mapImage.getWidth(), roadMask.width());
        assertEquals(mapImage.getHeight(), roadMask.height());
        assertTrue(roadMask.closed().countActivePixels() > 0, "Le masque de routes fermé ne doit pas être vide");

        // 3. Comparaison avec l'image témoin maps/road.png si présente à la racine
        File witnessFile = new File("maps/road.png");
        if (witnessFile.exists()) {
            BufferedImage witnessImg = ImageIO.read(witnessFile);
            BinaryMask witnessMask = BinaryMask.fromImage(witnessImg, 128);

            double agreement = computeAgreement(roadMask.closed(), witnessMask);
            System.out.printf("Taux de concordance pixel avec maps/road.png : %.4f%%%n", agreement * 100.0);
            assertTrue(agreement >= 0.995, "Le taux de concordance avec le témoin Python doit dépasser 99.5%");
        }
    }

    /**
     * Calcule le taux de concordance pixel à pixel entre deux masques binaires de mêmes dimensions.
     *
     * @param actual   Masque binaire calculé par le pipeline.
     * @param expected Masque binaire témoin de référence.
     * @return Taux de concordance dans l'intervalle [0.0, 1.0].
     */
    private double computeAgreement(BinaryMask actual, BinaryMask expected) {
        int w = actual.getWidth();
        int h = actual.getHeight();
        long matchCount = 0;
        long total = (long) w * h;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (actual.get(x, y) == expected.get(x, y)) {
                    matchCount++;
                }
            }
        }

        return (double) matchCount / total;
    }
}
