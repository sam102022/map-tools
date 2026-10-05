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
}
