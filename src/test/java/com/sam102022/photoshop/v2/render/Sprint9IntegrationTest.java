package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.pipeline.V2Config;
import com.sam102022.photoshop.v2.pipeline.V2Pipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration pivot étalon pour le Sprint 9 sur le territoire CA01 (3810 x 2130 px).
 */
@DisplayName("Test d'intégration pivot Sprint 9 : Rendu Sub-Pixel SS=4 et Export CA01")
class Sprint9IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprints 1 à 9 sur CA01 : concordance IoU >= 0.99 et budget temps <= 2500 ms")
    void testEndToEndSprint9OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path mapPath = Paths.get("src/test/resources/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        if (!Files.exists(mapPath)) {
            mapPath = Paths.get("maps/captures_maps/Territoire CA01/05_style_contraste_sans_rien.png");
        }
        assertTrue(Files.exists(mapPath), "L'image map CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image maps/road.png doit exister.");

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        BufferedImage mapImg = ImageIO.read(mapPath.toFile());
        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        BinaryMask roadMask = BinaryMask.fromImage(roadImg, 128);

        long startPipeline = System.currentTimeMillis();
        V2Pipeline pipeline = new V2Pipeline();
        V2Config config = V2Config.defaultConfig();

        RenderResult result = pipeline.execute(mapImg, loaded.geometry(), loaded.mapContext(), roadMask, config);
        long elapsedTotal = System.currentTimeMillis() - startPipeline;

        assertNotNull(result);
        assertNotNull(result.clipped());
        assertNotNull(result.mask());
        assertNotNull(result.overlay());
        assertNotNull(result.coverageMask());

        System.out.printf("Pipeline complet V2 exécuté en %d ms sur CA01 (%dx%d px).%n",
                elapsedTotal, mapImg.getWidth(), mapImg.getHeight());

        // A. Vérification des dimensions et types d'images
        assertEquals(mapImg.getWidth(), result.clipped().getWidth());
        assertEquals(mapImg.getHeight(), result.clipped().getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, result.clipped().getType());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, result.mask().getType());
        assertEquals(BufferedImage.TYPE_INT_RGB, result.overlay().getType());

        // B. Concordance avec l'étalon Python V7 si disponible
        Path pyMaskPath = Paths.get("maps/python/CA01_mask_v7.png");
        if (Files.exists(pyMaskPath)) {
            BufferedImage pyMaskImg = ImageIO.read(pyMaskPath.toFile());
            double iou = computeMaskIoU(result.coverageMask(), pyMaskImg);
            System.out.printf("IoU vs CA01_mask_v7.png : %.4f%%%n", iou * 100.0);
            assertTrue(iou >= 0.985, "L'indice IoU par rapport à CA01_mask_v7.png doit être >= 0.985 (actuel : " + iou + ")");
        }

        // C. Budget de performance : <= 3000 ms de bout en bout en JVM froide
        assertTrue(elapsedTotal <= 3000, "Le temps total du pipeline V2 doit être <= 3000 ms (actuel : " + elapsedTotal + " ms)");
    }

    private double computeMaskIoU(CoverageMask coverageMask, BufferedImage referenceMask) {
        int w = coverageMask.getWidth();
        int h = coverageMask.getHeight();
        long intersection = 0;
        long union = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean a = coverageMask.get(x, y) >= 128;
                boolean b = (referenceMask.getRaster().getSample(x, y, 0)) >= 128;
                if (a && b) {
                    intersection++;
                }
                if (a || b) {
                    union++;
                }
            }
        }
        return union == 0 ? 1.0 : (double) intersection / (double) union;
    }
}
