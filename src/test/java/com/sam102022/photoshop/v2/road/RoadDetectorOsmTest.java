package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation du détecteur vectoriel RoadDetectorOsm")
class RoadDetectorOsmTest {

    @Test
    @DisplayName("Rasterisation d'un tronçon OSM simple sur un contexte de carte")
    void testRasterizeOsmRoad() throws IOException {
        String json = """
        {
          "map": {
            "center": { "lat": 47.2691, "lng": -1.5065 },
            "zoom": 17,
            "container": { "pixelWidth": 200, "pixelHeight": 200 }
          },
          "roads": [
            {
              "tags": { "highway": "primary", "lanes": "2" },
              "geometry": [
                { "lat": 47.2691, "lon": -1.5065 },
                { "lat": 47.2695, "lon": -1.5065 }
              ]
            }
          ]
        }
        """;

        Path tempFile = Files.createTempFile("osm_test", ".json");
        Files.writeString(tempFile, json);

        MapContext context = new MapContext(200, 200, 17, new GeoCoordinate(47.2691, -1.5065));
        RoadDetectorOsm detector = new RoadDetectorOsm();
        BinaryMask mask = detector.detect(tempFile, context);

        assertNotNull(mask);
        // Le centre (100, 100) doit être couvert par la route tracée
        assertTrue(mask.get(100, 100), "Le centre de l'image traversé par la route doit être actif");
        assertFalse(mask.get(10, 10), "Un pixel éloigné ne doit pas être actif");

        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("Rejet des paramètres invalides ou manquants")
    void testInvalidParameters() {
        RoadDetectorOsm detector = new RoadDetectorOsm();
        MapContext context = new MapContext(100, 100, 15, new GeoCoordinate(47.0, -1.0));

        assertThrows(IllegalArgumentException.class, () -> detector.detect((Path) null, context));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(Path.of("nonexistent.json"), null));
        assertThrows(IOException.class, () -> detector.detect(Path.of("nonexistent_file_xyz.json"), context));
    }

    @Test
    @DisplayName("Filtrage des voies non autorisées ou des dessertes mineures")
    void testIgnoreMinorServiceAndExcludedHighways() throws IOException {
        String json = """
        {
          "roads": [
            {
              "tags": { "highway": "footway" },
              "geometry": [
                { "lat": 47.2691, "lon": -1.5065 },
                { "lat": 47.2695, "lon": -1.5065 }
              ]
            },
            {
              "tags": { "highway": "service", "service": "driveway" },
              "geometry": [
                { "lat": 47.2691, "lon": -1.5065 },
                { "lat": 47.2695, "lon": -1.5065 }
              ]
            },
            {
              "tags": { "highway": "primary" },
              "geometry": [
                { "lat": 47.2691, "lon": -1.5065 }
              ]
            }
          ]
        }
        """;

        Path tempFile = Files.createTempFile("osm_ignore_test", ".json");
        Files.writeString(tempFile, json);

        MapContext context = new MapContext(200, 200, 17, new GeoCoordinate(47.2691, -1.5065));
        RoadDetectorOsm detector = new RoadDetectorOsm();
        BinaryMask mask = detector.detect(tempFile, context);

        assertNotNull(mask);
        assertEquals(0, mask.countActivePixels(), "Aucune route ne doit être tracée pour ces configurations");

        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("Prise en compte d'une largeur explicite en mètres")
    void testExplicitWidthRoad() throws IOException {
        String json = """
        {
          "roads": [
            {
              "tags": { "highway": "secondary", "width": "12.5" },
              "geometry": [
                { "lat": 47.2691, "lng": -1.5065 },
                { "lat": 47.2695, "lng": -1.5065 }
              ]
            }
          ]
        }
        """;

        Path tempFile = Files.createTempFile("osm_width_test", ".json");
        Files.writeString(tempFile, json);

        MapContext context = new MapContext(200, 200, 17, new GeoCoordinate(47.2691, -1.5065));
        RoadDetectorOsm detector = new RoadDetectorOsm();
        BinaryMask mask = detector.detect(tempFile, context);

        assertNotNull(mask);
        assertTrue(mask.get(100, 100));

        Files.deleteIfExists(tempFile);
    }
}
