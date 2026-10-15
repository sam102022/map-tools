package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.CellSelectionPolicy;
import com.sam102022.photoshop.v2.vote.TopologicalVoteEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration de référence pour le Sprint 5 : Expansion Géodésique & ConsolidatedMask sur CA01.
 */
@DisplayName("Test d'intégration Sprint 5 : Expansion Géodésique & ConsolidatedMask CA01")
class Sprint5IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprint 5 sur CA01 : surface cible de 1 215 383 px et budget < 1500 ms")
    void testEndToEndSprint5OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image témoin maps/road.png doit exister.");

        long startTime = System.currentTimeMillis();

        // 1. Chargement & rasterisation du polygone P (Sprint 1)
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());
        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();

        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        int width = roadImg.getWidth();
        int height = roadImg.getHeight();

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(
                width,
                height,
                List.of(polygonPoints),
                List.of()
        );

        // 2. Chargement du masque de routes (Sprint 2)
        BinaryMask fullRoad = BinaryMask.fromImage(roadImg, 128);

        // 3. Recadrage & segmentation des 130 cellules (Sprint 3)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoad, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        assertEquals(130, labelingResult.cells().size(), "CA01 doit comporter 130 cellules.");

        // 4. Vote topologique et masque retenu T (Sprint 4)
        TopologicalVoteEngine votingEngine = new TopologicalVoteEngine();
        CellSelection selection = votingEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPoly
        );
        assertNotNull(selection.retainedMask());

        // 5. Exécution du Sprint 5 : Expansion géodésique & consolidation
        long startConsolidation = System.currentTimeMillis();
        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                ExpansionConfig.defaultTerritory()
        );
        long elapsedConsolidation = System.currentTimeMillis() - startConsolidation;
        long elapsedTotal = System.currentTimeMillis() - startTime;
        assertNotNull(consolidated);

        // Validation de la surface consolidée (cible Python : 1 215 383 px à +/- 1.5%)
        long area = consolidated.mask().countActivePixels();
        double matchRate = 1.0 - Math.abs(area - 1_215_383L) / 1_215_383.0;
        System.out.printf("Sprint 5 exécuté en %d ms (total pipeline : %d ms). Surface Mc = %d px (concordance étalon Python : %.3f%%)%n",
                elapsedConsolidation, elapsedTotal, area, matchRate * 100.0);

        assertTrue(matchRate >= 0.985, "Le taux de concordance avec l'étalon Python doit être >= 98.5%");
        assertTrue(elapsedConsolidation < 1500, "Le temps de calcul du Sprint 5 doit être inférieur à 1500 ms");
    }
}
