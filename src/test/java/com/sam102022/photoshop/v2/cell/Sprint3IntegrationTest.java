package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration pivot validant le pipeline complet du Sprint 3 sur le Territoire étalon CA01.
 * Chaîne validée : BoundingBoxCropper -> CellLabeler (4-connexité) -> RoadInterfaceExtractor -> CellGraph.
 */
@DisplayName("Test d'intégration Sprint 3 : Découpe, Segmentation en 130 cellules & CellGraph CA01")
class Sprint3IntegrationTest {

    /**
     * Valide de bout en bout la chaîne algorithmique du Sprint 3 sur le territoire étalon CA01.
     * Vérifie la dimension de la fenêtre de recadrage, le nombre exact de 130 cellules obtenues
     * en 4-connexité stricte, ainsi que l'extraction des interfaces routières et la complétude du CellGraph.
     *
     * @throws IOException si un fichier de données de test ne peut pas être lu.
     */
    @Test
    @DisplayName("Pipeline complet Sprint 3 sur CA01 : 130 cellules et graphe topologique conforme à l'étalon Python")
    void testEndToEndSprint3PipelineOnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit être disponible.");

        Path roadPngPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPngPath), "L'image témoin des routes maps/road.png doit exister.");

        // 1. Chargement et projection de la géométrie du territoire
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());

        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();

        // 2. Chargement du masque routier étalon
        BufferedImage roadImage = ImageIO.read(roadPngPath.toFile());
        int width = roadImage.getWidth();
        int height = roadImage.getHeight();
        BinaryMask fullRoadMask = BinaryMask.fromImage(roadImage, 128);

        // 3. Mesure de performance du pipeline algorithmique Sprint 3
        long startTime = System.currentTimeMillis();

        // 3.1 Recadrage de la zone d'intérêt avec marge mg = 90 px
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoadMask, cropWindow);

        assertEquals(1505, cropWindow.width());
        assertEquals(1783, cropWindow.height());

        // 3.2 Segmentation en 4-connexité stricte des cellules
        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);

        // Conformité déterministe avec l'étalon Python ndi.label(~Rc) = 130 cellules
        assertEquals(130, labelingResult.cells().size(),
                "Le nombre de cellules sur la zone rognée CA01 doit être exactement de 130.");
        assertEquals(130, labelingResult.labelMap().cellCount());

        // 3.3 Extraction des interfaces routières et assemblage du CellGraph
        RoadInterfaceExtractor extractor = new RoadInterfaceExtractor();
        CellGraph graph = extractor.buildGraph(labelingResult, croppedRoad);

        long elapsed = System.currentTimeMillis() - startTime;

        assertNotNull(graph);
        assertEquals(130, graph.cells().size());
        assertFalse(graph.roadInterfaces().isEmpty(), "Le graphe doit contenir des interfaces routières.");
        assertTrue(graph.roadInterfaces().size() >= 200,
                "Le réseau routier CA01 doit comporter au moins 200 tronçons d'interfaces entre cellules.");

        System.out.printf("Pipeline algorithmique Sprint 3 exécuté avec succès en %d ms (130 cellules, %d interfaces routières).%n",
                elapsed, graph.roadInterfaces().size());

        // Budget de performance : exécution algorithmique complète en moins de 1000 ms (critère nominal <= 1000 ms)
        assertTrue(elapsed < 1000, "Le temps de calcul algorithmique doit être inférieur à 1000 ms : " + elapsed + " ms");
    }
}
