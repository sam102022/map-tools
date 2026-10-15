package com.sam102022.photoshop.v2.vote;

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
 * Test d'intégration pivot du Sprint 4 validant le vote topologique déterministe
 * sur le territoire de référence CA01.
 * <p>
 * Valide l'orchestration complète :
 * <ul>
 *     <li>Avec la politique par défaut (0.60 / 0.05) : 21 INSIDE, 5 PARTIAL, 104 OUTSIDE.
 *         (La cellule 72 a une couverture de 4.75% &lt; 5.0%, donc légitimement OUTSIDE en rasterisation Java2D exacte).</li>
 *     <li>Avec le seuil de tolérance Python (0.60 / 0.045) : 21 INSIDE, 6 PARTIAL, 103 OUTSIDE
 *         (Concordance stricte avec l'étalon Python snap_cells_prototype_v5.py où la rasterisation PIL incluait les bords).</li>
 * </ul>
 * </p>
 */
@DisplayName("Test d'intégration Sprint 4 : Vote Topologique et Classification sur CA01")
class Sprint4IntegrationTest {

    /**
     * Valide l'exécution complète du vote topologique sur le territoire CA01
     * et s'assure de la conformité quantitative des décisions de classification.
     *
     * @throws IOException si l'un des fichiers de test (JSON ou PNG) ne peut pas être chargé.
     */
    @Test
    @DisplayName("Vote topologique déterministe CA01 sur les 130 cellules")
    void testTopologicalVoteOnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit être disponible.");

        Path roadPngPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPngPath), "L'image témoin maps/road.png doit exister.");

        long startTime = System.currentTimeMillis();

        // 1. Chargement et projection géométrique (Sprint 1)
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());
        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();

        // 2. Rasterisation du polygone global (Sprint 1)
        BufferedImage roadImage = ImageIO.read(roadPngPath.toFile());
        int width = roadImage.getWidth();
        int height = roadImage.getHeight();
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(
                width,
                height,
                List.of(polygonPoints),
                List.of()
        );

        // 3. Masque des routes (Sprint 2)
        BinaryMask fullRoadMask = BinaryMask.fromImage(roadImage, 128);

        // 4. Recadrage avec marge mg = 90 px (Sprint 3)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoadMask, cropWindow);
        BinaryMask croppedPolygon = cropper.crop(polygonMask.mask(), cropWindow);

        // 5. Segmentation en cellules 4-connexes (Sprint 3)
        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        assertEquals(130, labelingResult.cells().size(), "Le nombre de cellules CA01 doit être de 130.");

        // 6. Exécution du vote topologique avec politique par défaut (Sprint 4)
        TopologicalVoteEngine voteEngine = new TopologicalVoteEngine();
        CellSelection defaultSelection = voteEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPolygon
        );

        assertNotNull(defaultSelection, "La sélection par défaut ne doit pas être null.");
        assertEquals(21, defaultSelection.insideCellIds().size(),
                "Le nombre de cellules INSIDE doit être exactement de 21.");
        assertEquals(5, defaultSelection.partialCellIds().size(),
                "Le nombre de cellules PARTIAL avec seuil 5% doit être de 5.");
        assertEquals(104, defaultSelection.outsideCellIds().size(),
                "Le nombre de cellules OUTSIDE avec seuil 5% doit être de 104.");
        assertEquals(130, defaultSelection.insideCellIds().size()
                        + defaultSelection.partialCellIds().size()
                        + defaultSelection.outsideCellIds().size(),
                "La somme des décisions doit couvrir l'intégralité des 130 cellules.");

        // Vérification du masque d'amorçage T
        assertNotNull(defaultSelection.retainedMask(), "Le masque d'amorçage T ne doit pas être null.");
        assertEquals(1505, defaultSelection.retainedMask().getWidth(), "La largeur de T doit concorder avec CropWindow.");
        assertEquals(1783, defaultSelection.retainedMask().getHeight(), "La hauteur de T doit concorder avec CropWindow.");
        assertTrue(defaultSelection.retainedMask().countActivePixels() > 0, "Le masque d'amorçage T doit comporter des pixels actifs.");

        // 7. Exécution avec seuil ajusté (0.045) pour concordance exacte étalon Python snap_cells_prototype_v5.py
        CellSelection pythonAlignedSelection = voteEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPolygon,
                new CellSelectionPolicy(0.60, 0.045)
        );

        assertEquals(21, pythonAlignedSelection.insideCellIds().size(),
                "Les 21 cellules INSIDE restent strictement identiques.");
        assertEquals(6, pythonAlignedSelection.partialCellIds().size(),
                "Le nombre de cellules PARTIAL avec seuil 4.5% inclut la cellule 72 (total 6).");
        assertEquals(103, pythonAlignedSelection.outsideCellIds().size(),
                "Le nombre de cellules OUTSIDE passe à 103.");
        assertTrue(pythonAlignedSelection.partialCellIds().contains(72),
                "La cellule frontière 72 (couverture 4.75%) doit être qualifiée PARTIAL.");

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Sprint 4 exécuté sur CA01 en %d ms (défaut : 21 INSIDE, 5 PARTIAL, 104 OUTSIDE ; étalon Python : 21/6/103).%n",
                elapsed);
        assertTrue(elapsed < 1000, "Le temps de calcul total doit être inférieur à 1000 ms : " + elapsed + " ms");
    }
}
