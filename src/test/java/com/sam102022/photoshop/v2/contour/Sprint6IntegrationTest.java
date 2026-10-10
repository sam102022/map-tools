package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.expansion.RoadBoundaryConsolidator;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import com.sam102022.photoshop.v2.vote.CellSelection;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test d'intégration pivot Sprint 6 : Lissage Sub-Pixel & Ronds-points CA01")
class Sprint6IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprint 6 sur CA01 : 4 à 16 coins, 7 ronds-points substitués (étalon Python), budget < 1000 ms")
    void testEndToEndSprint6OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image maps/road.png doit exister.");

        long startTime = System.currentTimeMillis();

        // 1. Polygone P (Sprint 1)
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
        PolygonMask polygonMask = rasterizer.rasterize(width, height, List.of(polygonPoints), List.of());
        BinaryMask fullRoad = BinaryMask.fromImage(roadImg, 128);

        // 2. Cellules & Vote (Sprints 3 & 4)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoad, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        TopologicalVoteEngine votingEngine = new TopologicalVoteEngine();
        CellSelection selection = votingEngine.execute(labelingResult.labelMap(), labelingResult.cells(), croppedPoly);

        // 3. Masque consolidé (Sprint 5)
        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                ExpansionConfig.defaultTerritory()
        );

        // 4. Exécution du Sprint 6
        long startSprint6 = System.currentTimeMillis();
        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        SmoothVectorContour result = engine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                ContourSmoothingConfig.defaultConfig()
        );
        long elapsedSprint6 = System.currentTimeMillis() - startSprint6;
        long elapsedTotal = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertFalse(result.points().isEmpty());

        System.out.printf("Sprint 6 exécuté en %d ms (total pipeline : %d ms). Points de contour : %d, Coins détectés : %d%n",
                elapsedSprint6, elapsedTotal, result.points().size(), result.cornerIndices().size());

        // 1. Coins authentiques : 4 à 16 coins
        // Les 5 sommets majeurs du polygone CA01 sont situés sur des giratoires : l'étalon Python
        // (snap_cells_prototype_v7.py) y substitue 7 arcs d'ellipse sur 12 giratoires détectés. Un sommet
        // remplacé par un arc tangent peut ne plus être vu comme un angle vif (L=80) sur le tracé final.
        assertEquals(7, result.substitutedRoundabouts().size(),
                "Nombre de ronds-points substitués attendu : 7 (concordance étalon Python)");
        int cornerCount = result.cornerIndices().size();
        assertTrue(cornerCount >= 4 && cornerCount <= 16,
                "Le nombre de coins détectés doit être compris entre 4 et 16 (actuel : " + cornerCount + ")");

        // 2. Pas moyen de rééchantillonnage proche de 1.0 px
        double avgStep = 0.0;
        int n = result.points().size();
        for (int i = 0; i < n; i++) {
            PixelPoint p1 = result.points().get(i);
            PixelPoint p2 = result.points().get((i + 1) % n);
            avgStep += Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
        }
        avgStep /= n;
        assertEquals(1.0, avgStep, 0.1, "Le pas moyen entre points du contour doit être proche de 1.0 px");

        // 3. Budget de performance <= 1000 ms en Java standard
        assertTrue(elapsedSprint6 <= 1000, "Le temps de calcul du Sprint 6 doit être inférieur à 1000 ms");
    }
}
