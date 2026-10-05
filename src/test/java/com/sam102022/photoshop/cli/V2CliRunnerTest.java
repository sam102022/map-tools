package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du runner CLI V2 {@link V2CliRunner}.
 */
@DisplayName("Tests unitaires du runner CLI V2")
class V2CliRunnerTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Affichage de l'aide avec --help ou -h")
    void testHelpOption() {
        int exitCode = V2CliRunner.run(new String[]{"--help"});
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Pipeline V2"));
        assertTrue(outContent.toString().contains("--map"));
        assertTrue(outContent.toString().contains("--json"));
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants")
    void testMissingArguments() {
        int exitCode = V2CliRunner.run(new String[]{});
        assertEquals(1, exitCode);

        int exitCode2 = V2CliRunner.run(new String[]{"--map", "carte.png"});
        assertEquals(1, exitCode2);
        assertTrue(errContent.toString().contains("Erreur"));
    }

    @Test
    @DisplayName("Exécution complète en ligne de commande avec fichiers de test CA01")
    void testExecutionWithCA01Files(@TempDir Path tempDir) {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        Path mapPath = Paths.get("src/test/resources/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        Path roadPath = Paths.get("maps/road.png");

        if (!Files.exists(jsonPath) || !Files.exists(mapPath) || !Files.exists(roadPath)) {
            return;
        }

        String[] args = new String[]{
                "--v2",
                "--map", mapPath.toString(),
                "--json", jsonPath.toString(),
                "--road", roadPath.toString(),
                "--out-dir", tempDir.toString()
        };

        int exitCode = V2CliRunner.run(args);
        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve("clipped.png")));
        assertTrue(Files.exists(tempDir.resolve("mask.png")));
        assertTrue(Files.exists(tempDir.resolve("overlay.png")));
    }

    @Test
    @DisplayName("Exécution avec détection et rasterisation automatique d'osm_roads.json adjacent")
    void testExecutionWithAdjacentOsmRoads(@TempDir Path tempDir) {
        Path jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        Path mapPath = Paths.get("maps/captures_maps/Territoire CA01/05_style_contraste_sans_rien.png");
        Path osmPath = Paths.get("maps/captures_maps/Territoire CA01/osm_roads.json");

        if (!Files.exists(jsonPath) || !Files.exists(mapPath) || !Files.exists(osmPath)) {
            return;
        }

        String[] args = new String[]{
                "--v2",
                "--map", mapPath.toString(),
                "--json", jsonPath.toString(),
                "--out-dir", tempDir.toString()
        };

        int exitCode = V2CliRunner.run(args);
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Rasterisation automatique des axes routiers OSM")
                || outContent.toString().contains("OpenStreetMap"));
        assertTrue(Files.exists(tempDir.resolve("clipped.png")));
        assertTrue(Files.exists(tempDir.resolve("mask.png")));
        assertTrue(Files.exists(tempDir.resolve("overlay.png")));
    }

    @Test
    @DisplayName("Rejet si le masque routier fourni via --road ne correspond pas aux dimensions de la carte")
    void testRejectsMismatchedRoadMaskDimensions(@TempDir Path tempDir) throws Exception {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        Path mapPath = Paths.get("src/test/resources/v2/fixtures/CA01/05_style_contraste_sans_rien.png");

        if (!Files.exists(jsonPath) || !Files.exists(mapPath)) {
            return;
        }

        Path badRoad = tempDir.resolve("bad_road.png");
        java.awt.image.BufferedImage smallImg = new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_BYTE_BINARY);
        javax.imageio.ImageIO.write(smallImg, "png", badRoad.toFile());

        String[] args = new String[]{
                "--v2",
                "--map", mapPath.toString(),
                "--json", jsonPath.toString(),
                "--road", badRoad.toString(),
                "--out-dir", tempDir.toString()
        };

        int exitCode = V2CliRunner.run(args);
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Dimensions incompatibles"));
    }
}
