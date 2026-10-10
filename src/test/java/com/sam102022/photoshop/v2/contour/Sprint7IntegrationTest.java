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

/**
 * Test d'intégration pivot étalon pour le Sprint 7 sur le cas réel CA01.
 * Valide le redressement multi-échelle des tronçons droits, la préservation des virages authentiques,
 * la stabilité du tracé vectoriel (~5215 points) et le respect du budget de performance.
 */
@DisplayName("Test d'intégration pivot Sprint 7 : Lissage Multi-Échelle des Tronçons Droits CA01")
class Sprint7IntegrationTest {

    /**
     * Exécute le pipeline complet de vectorisation et de lissage multi-échelle adaptatif sur le territoire CA01.
     *
     * @throws IOException En cas d'erreur de lecture des ressources graphiques ou vectorielles.
     */
    @Test
    @DisplayName("Pipeline complet Sprint 7 sur CA01 : 4 à 16 coins, 7 ronds-points substitués (étalon Python), budget < 1000 ms")
    void testEndToEndSprint7OnCA01() throws IOException {
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

        // 2. Cellules & Vote topologique (Sprints 3 & 4)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoad, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        TopologicalVoteEngine votingEngine = new TopologicalVoteEngine();
        CellSelection selection = votingEngine.execute(labelingResult.labelMap(), labelingResult.cells(), croppedPoly);

        // 3. Masque consolidé géodésique (Sprint 5)
        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                ExpansionConfig.defaultTerritory()
        );

        // 4. Lissage vectoriel multi-échelle (Sprints 6 & 7)
        long startSprint7 = System.currentTimeMillis();
        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        SmoothVectorContour result = engine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                config
        );
        long elapsedSprint7 = System.currentTimeMillis() - startSprint7;
        long elapsedTotal = System.currentTimeMillis() - startTime;

        assertNotNull(result, "Le résultat du lissage vectoriel ne doit pas être nul.");
        assertFalse(result.points().isEmpty(), "Le contour vectoriel lissé ne doit pas être vide.");

        System.out.printf("Sprint 7 exécuté en %d ms (total pipeline : %d ms). Points : %d, Coins : %d%n",
                elapsedSprint7, elapsedTotal, result.points().size(), result.cornerIndices().size());

        // 1. Nombre de points du contour : environ 5215 points
        int pointCount = result.points().size();
        assertTrue(pointCount >= 5000 && pointCount <= 5400,
                "Le nombre de points du tracé final doit être compris entre 5000 et 5400 (actuel : " + pointCount + ")");

        // 2. Préservation des coins majeurs : entre 4 et 16 coins
        // Les 5 sommets majeurs du polygone CA01 sont situés sur des giratoires : l'étalon Python
        // (snap_cells_prototype_v7.py) y substitue 7 arcs d'ellipse sur 12 giratoires détectés. Un sommet
        // remplacé par un arc tangent peut ne plus être vu comme un angle vif (L=80) sur le tracé final.
        assertEquals(7, result.substitutedRoundabouts().size(),
                "Nombre de ronds-points substitués attendu : 7 (concordance étalon Python)");
        int cornerCount = result.cornerIndices().size();
        assertTrue(cornerCount >= 4 && cornerCount <= 16,
                "Le nombre de coins détectés doit être compris entre 4 et 16 (actuel : " + cornerCount + ")");

        // 3. Pas moyen de rééchantillonnage proche de 1.0 px
        double avgStep = 0.0;
        int n = result.points().size();
        for (int i = 0; i < n; i++) {
            PixelPoint p1 = result.points().get(i);
            PixelPoint p2 = result.points().get((i + 1) % n);
            avgStep += Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
        }
        avgStep /= n;
        assertEquals(1.0, avgStep, 0.1, "Le pas moyen entre points du contour doit être proche de 1.0 px");

        // 4. Budget de performance <= 1000 ms en Java standard
        assertTrue(elapsedSprint7 <= 1000,
                "Le temps de calcul du lissage Sprint 7 doit être inférieur à 1000 ms (actuel : " + elapsedSprint7 + " ms)");
    }
}
