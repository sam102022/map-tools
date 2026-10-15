package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du parseur JSON de métadonnées cartographiques et géométriques.
 */
@DisplayName("Validation du parseur JSON de territoire V2")
class JsonTerritoryLoaderTest {

    /**
     * Valide le chargement complet du fichier d'échantillon réel du Territoire CA01.
     *
     * @throws IOException en cas d'erreur de lecture.
     */
    @Test
    @DisplayName("Chargement complet du fichier d'échantillon réel Territoire CA01")
    void testLoadSampleTerritoryJson() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!java.nio.file.Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(jsonPath.toFile().exists(), "Le fichier de test réel doit être présent");

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        assertNotNull(loaded);
        MapContext ctx = loaded.mapContext();
        assertEquals(3810, ctx.width());
        assertEquals(2130, ctx.height());
        assertEquals(17, ctx.zoom());
        assertEquals(47.269135, ctx.center().latitude(), 1e-4);
        assertEquals(-1.506505, ctx.center().longitude(), 1e-4);

        TerritoryGeometry geom = loaded.geometry();
        assertEquals(1, geom.outerRings().size());
        assertFalse(geom.outerRings().get(0).isEmpty());
        // Vérifier le premier sommet du polygone
        GeoCoordinate firstPoint = geom.outerRings().get(0).get(0);
        assertEquals(47.272176, firstPoint.latitude(), 1e-4);
        assertEquals(-1.499395, firstPoint.longitude(), 1e-4);
    }

    /**
     * Valide la levée d'exception en cas de chemin de fichier null ou inexistant.
     */
    @Test
    @DisplayName("Rejet des fichiers inexistants ou null")
    void testNonExistentFileThrowsException() {
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        assertThrows(IllegalArgumentException.class, () -> loader.load(null));
        assertThrows(IllegalArgumentException.class, () -> loader.load(Paths.get("fichier_inexistant.json")));
    }
}
