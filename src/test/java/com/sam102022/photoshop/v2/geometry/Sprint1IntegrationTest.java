package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration bout-en-bout du Sprint 1 validant la chaîne complète
 * de conversion d'un fichier JSON vers le polygone raster P en passant par
 * la projection cartographique Web Mercator.
 */
@DisplayName("Test d'intégration Sprint 1 : JSON -> Mercator -> Polygone Raster P")
class Sprint1IntegrationTest {

    /**
     * Valide la génération complète du polygone pixel P sur le Territoire CA01
     * et vérifie sa conformité quantitative avec l'aire mesurée dans le prototype Python.
     *
     * @throws IOException en cas d'erreur de lecture du fichier JSON.
     */
    @Test
    @DisplayName("Génération complète du polygone pixel P sur Territoire CA01 conforme au prototype Python")
    void testEndToEndSprint1Pipeline() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!java.nio.file.Files.exists(jsonPath)) {
            // Repli sur le dossier local si exécuté depuis un autre répertoire de travail
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }

        // 1. Chargement JSON
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        // 2. Projection Web Mercator
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());

        List<List<PixelPoint>> projectedOuterRings = new ArrayList<>();
        for (List<GeoCoordinate> ring : loaded.geometry().outerRings()) {
            List<PixelPoint> projectedRing = ring.stream().map(projection::toPixel).toList();
            projectedOuterRings.add(projectedRing);
        }

        List<List<PixelPoint>> projectedInnerRings = new ArrayList<>();
        for (List<GeoCoordinate> ring : loaded.geometry().innerRings()) {
            List<PixelPoint> projectedRing = ring.stream().map(projection::toPixel).toList();
            projectedInnerRings.add(projectedRing);
        }

        // 3. Rasterisation en contrat officiel PolygonMask P
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(
                loaded.mapContext().width(),
                loaded.mapContext().height(),
                projectedOuterRings,
                projectedInnerRings
        );

        // 4. Vérifications quantitatives
        assertEquals(3810, polygonMask.width());
        assertEquals(2130, polygonMask.height());

        // L'aire du polygone projeté P dans le prototype Python script.py est de 1 184 004 pixels
        int activePixels = polygonMask.countActivePixels();
        assertTrue(activePixels > 1_170_000 && activePixels < 1_200_000,
                "L'aire du polygone raster P (" + activePixels + ") doit concorder avec l'aire mesurée dans script.py (~1 184 000 px)");
    }
}
