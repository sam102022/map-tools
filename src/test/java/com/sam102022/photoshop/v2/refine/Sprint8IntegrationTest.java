package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.ContourSmoothingConfig;
import com.sam102022.photoshop.v2.contour.ContourSmoothingEngine;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
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
 * Test d'intégration étalon de bout en bout pour le Sprint 8 sur le territoire CA01.
 * Exécute l'intégralité du pipeline des Sprints 1 à 8 :
 * 1. Géométrie & Projection (Sprint 1)
 * 2. Détection du masque routier (Sprint 2)
 * 3. Segmentation des cellules urbaines (Sprint 3)
 * 4. Vote topologique et classification (Sprint 4)
 * 5. Expansion géodésique et consolidation frontière (Sprint 5)
 * 6. Lissage sub-pixel & substitution des giratoires (Sprints 6 & 7)
 * 7. Affinage spectral et modulation alpha (Sprint 8).
 */
@DisplayName("Test d'intégration pivot Sprint 8 : Affinage Spectral et Modulation Alpha CA01")
class Sprint8IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprints 1 à 8 sur CA01 : neutralisation des pixels résiduels et sanctuarisation giratoire")
    void testEndToEndSprint8OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image maps/road.png doit exister.");

        long totalStartTime = System.currentTimeMillis();

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

        // 4. Lissage vectoriel multi-échelle & substitution giratoires (Sprints 6 & 7)
        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        ContourSmoothingConfig smoothingConfig = ContourSmoothingConfig.defaultConfig();
        SmoothVectorContour smoothContour = engine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                smoothingConfig
        );

        assertNotNull(smoothContour, "Le tracé vectoriel lissé ne doit pas être nul.");
        assertFalse(smoothContour.points().isEmpty(), "Le tracé vectoriel lissé ne doit pas être vide.");

        // 5. Affinage spectral et modulation alpha (Sprint 8)
        long startSprint8 = System.currentTimeMillis();
        AlphaRefiner refiner = new AlphaRefiner();
        AlphaRefinementConfig refinementConfig = AlphaRefinementConfig.defaultConfig();

        AlphaRefinementMap refinementMap = refiner.refine(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                smoothContour.substitutedRoundabouts(),
                refinementConfig
        );
        long elapsedSprint8 = System.currentTimeMillis() - startSprint8;
        long elapsedTotal = System.currentTimeMillis() - totalStartTime;

        assertNotNull(refinementMap, "Le contrat AlphaRefinementMap ne doit pas être nul.");
        assertEquals(cropWindow.width(), refinementMap.width());
        assertEquals(cropWindow.height(), refinementMap.height());
        assertEquals(cropWindow, refinementMap.cropWindow());

        System.out.printf(
                "Sprint 8 exécuté en %d ms (total pipeline : %d ms). Matrice factor : %dx%d px, giratoires : %d%n",
                elapsedSprint8,
                elapsedTotal,
                refinementMap.width(),
                refinementMap.height(),
                smoothContour.substitutedRoundabouts().size()
        );

        // A. Sanctuarisation du rond-point substitué
        assertFalse(smoothContour.substitutedRoundabouts().isEmpty(), "Au moins un rond-point doit être substitué sur CA01.");
        for (Roundabout rb : smoothContour.substitutedRoundabouts()) {
            int cx = (int) Math.round(rb.exteriorEllipse().xc());
            int cy = (int) Math.round(rb.exteriorEllipse().yc());
            assertEquals(1.0f, refinementMap.factorAt(cx, cy), 1e-3f,
                    "Le centre du giratoire substitué (" + cx + "," + cy + ") doit avoir factor = 1.0f");
        }

        // B. Neutralisation effective des pixels de fuite résiduels en bordure
        // Rastérisation du contour lissé local
        PolygonMask localContourRaster = rasterizer.rasterize(
                cropWindow.width(),
                cropWindow.height(),
                List.of(smoothContour.points()),
                List.of()
        );

        int neutralizedPixels = 0;
        int totalContourInterior = 0;
        for (int y = 0; y < cropWindow.height(); y++) {
            for (int x = 0; x < cropWindow.width(); x++) {
                if (localContourRaster.mask().get(x, y)) {
                    totalContourInterior++;
                    if (refinementMap.factorAt(x, y) < 0.5f) {
                        neutralizedPixels++;
                    }
                }
            }
        }

        System.out.printf("Pixels intérieurs contour : %d, pixels résiduels neutralisés en lisière (factor < 0.5) : %d%n",
                totalContourInterior, neutralizedPixels);

        // Sur le cas étalon CA01, les pixels résiduels en débordement de chaussée sont neutralisés par le masque autorisé
        assertTrue(neutralizedPixels >= 500 && neutralizedPixels <= 2000,
                "Le nombre de pixels résiduels neutralisés en lisière doit être compris entre 500 et 2000 (actuel : " + neutralizedPixels + ")");

        // C. Budget de performance Sprint 8 : <= 1000 ms en environnement standard
        assertTrue(elapsedSprint8 <= 1000,
                "L'exécution du Sprint 8 doit respecter le budget de performance <= 1000 ms (actuel : " + elapsedSprint8 + " ms)");
    }
}
