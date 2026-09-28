package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires et d'intégration pour {@link CliRunner}.
 * <p>
 * Valide le parsing d'options, les modes d'opération (auto, territory, zone),
 * les sélecteurs de couleur, les alias courts et la gestion robuste des erreurs.
 * </p>
 */
class CliRunnerTest {

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
        int exitCode1 = CliRunner.run(new String[]{"--help"});
        assertEquals(0, exitCode1);
        assertTrue(outContent.toString().contains("Usage:"));
        assertTrue(outContent.toString().contains("--map"));

        outContent.reset();
        int exitCode2 = CliRunner.run(new String[]{"-h"});
        assertEquals(0, exitCode2);
        assertTrue(outContent.toString().contains("Usage:"));
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants ou null")
    void testMissingArguments() {
        assertEquals(1, CliRunner.run(new String[]{}));
        assertEquals(1, CliRunner.run(null));
        assertEquals(1, CliRunner.run(new String[]{"--map", "only_map.png"}));
    }

    @Test
    @DisplayName("Échec si une option attendue n'a pas de valeur")
    void testMissingOptionValue() {
        int exitCode = CliRunner.run(new String[]{"--map", "map.png", "--mask"});
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Valeur manquante"));
    }

    @Test
    @DisplayName("Échec si un paramètre numérique est invalide")
    void testInvalidNumericArgument() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--snap-distance", "invalid_int"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Valeur entière invalide"));
    }

    @Test
    @DisplayName("Échec si un paramètre de configuration est hors bornes")
    void testInvalidConfigBounds() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--snap-distance", "0"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Erreur de syntaxe ou de configuration"));
    }

    @Test
    @DisplayName("Échec si un mode inconnu est spécifié")
    void testInvalidModeOption() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--mode", "inconnu"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Mode d'opération non reconnu"));
    }

    @Test
    @DisplayName("Échec si une couleur de zone inconnue est spécifiée")
    void testInvalidZoneColorOption() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--zone-color", "jaune"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Couleur de sélection non reconnue"));
    }

    @Test
    @DisplayName("Échec si la couleur demandée est absente du calque en mode zone")
    void testRequestedZoneColorMissingFails(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");

        BufferedImage map = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(10, 10, 60, 60);
        g.setColor(new Color(230, 20, 20)); // Cadre rouge
        g.drawRect(20, 20, 30, 30);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--mode", "zone",
                "--zone-color", "blue"
        });

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("n'a pas été détectée"));
    }

    @Test
    @DisplayName("Exécution complète d'un scénario CLI avec anti-aliasing par défaut et contrôle des nuances de gris dans mask-out")
    void testEndToEndCliRun(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out.png");
        Path maskOutFile = tempDir.resolve("mask_out.png");

        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        // Triangle oblique pour générer des bords sub-pixel anti-aliasés
        Polygon poly = new Polygon(new int[]{5, 35, 15}, new int[]{5, 10, 35}, 3);
        g.fillPolygon(poly);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--snap-distance", "15",
                "--aa"
        });

        assertEquals(0, exitCode);
        assertTrue(outFile.toFile().exists());
        assertTrue(maskOutFile.toFile().exists());

        // 1. Vérifier que mask-out contient des pixels gris intermédiaires ]0, 255[
        BufferedImage maskOutImg = ImageIO.read(maskOutFile.toFile());
        assertNotNull(maskOutImg);
        int grayIntermediates = 0;
        for (int y = 0; y < maskOutImg.getHeight(); y++) {
            for (int x = 0; x < maskOutImg.getWidth(); x++) {
                int sample = maskOutImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    grayIntermediates++;
                }
            }
        }
        assertTrue(grayIntermediates > 5, "mask-out doit contenir des pixels gris sub-pixel intermédiaires ]0, 255[, trouvé: " + grayIntermediates);

        // 2. Vérifier que l'image détourée contient des valeurs alpha progressives
        BufferedImage clippedImg = ImageIO.read(outFile.toFile());
        assertNotNull(clippedImg);
        int alphaIntermediates = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    alphaIntermediates++;
                }
            }
        }
        assertTrue(alphaIntermediates > 5, "L'image détourée doit contenir un dégradé alpha progressif, trouvé: " + alphaIntermediates);
    }

    @Test
    @DisplayName("Option --no-aa / --no-antialias désactive l'anti-aliasing (sorties strictement binaires)")
    void testCliWithNoAntialiasingOption(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out_no_aa.png");
        Path maskOutFile = tempDir.resolve("mask_no_aa.png");

        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        Polygon poly = new Polygon(new int[]{5, 35, 15}, new int[]{5, 10, 35}, 3);
        g.fillPolygon(poly);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--smooth", "0",
                "--no-aa"
        });

        assertEquals(0, exitCode);

        // mask-out doit être strictement binaire (0 ou 255)
        BufferedImage maskOutImg = ImageIO.read(maskOutFile.toFile());
        for (int y = 0; y < maskOutImg.getHeight(); y++) {
            for (int x = 0; x < maskOutImg.getWidth(); x++) {
                int sample = maskOutImg.getRaster().getSample(x, y, 0);
                assertTrue(sample == 0 || sample == 255, "Sans AA, mask-out doit être strictement 0 ou 255, trouvé: " + sample);
            }
        }

        // image détourée doit avoir un canal alpha strictement binaire
        BufferedImage clippedImg = ImageIO.read(outFile.toFile());
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                assertTrue(alpha == 0 || alpha == 255, "Sans AA, l'alpha doit être strictement 0 ou 255, trouvé: " + alpha);
            }
        }
    }

    @Test
    @DisplayName("Échec si conflit entre --aa et --no-aa")
    void testCliConflictingAntialiasingOptions() {
        int exitCode = CliRunner.run(new String[]{
                "--map", "map.png",
                "--mask", "mask.png",
                "--aa",
                "--no-aa"
        });
        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Conflit"));
    }

    @Test
    @DisplayName("Exécution réussie en mode zone avec sélecteur rouge")
    void testModeZoneWithRedSelector(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("zone_out.png");
        Path maskOutFile = tempDir.resolve("mask_zone_out.png");

        int w = 80, h = 80;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // Fond vert territoire
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(10, 10, 60, 60);

        // Tracé rouge fermé [20..50, 20..50]
        g.setColor(new Color(230, 20, 20));
        g.drawRect(20, 20, 30, 30);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--mode", "zone",
                "--zone-color", "red"
        });

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Détection du cadre d'annotation"));
        assertTrue(outContent.toString().contains("Découpage de zone et exclusion stricte"));
        assertTrue(outFile.toFile().exists());
    }

    @Test
    @DisplayName("Échec si le mode zone est forcé mais aucun cadre coloré n'est présent")
    void testModeZoneWithoutFrameFails(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");

        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 230, 180));
        g.fillRect(5, 5, 30, 30); // Vert uniquement, aucun cadre rouge
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--mode", "zone"
        });

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("aucun cadre"));
    }

    @Test
    @DisplayName("Échec si plusieurs couleurs coexistent en mode AUTO sans précision de --zone-color")
    void testMultiColorAmbiguityUnderAutoFails(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");

        int w = 120, h = 80;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(5, 5, 110, 70);

        // Cadre rouge à gauche
        g.setColor(new Color(230, 20, 20));
        g.drawRect(10, 10, 30, 30);

        // Cadre bleu à droite
        g.setColor(new Color(20, 30, 230));
        g.drawRect(60, 10, 30, 30);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--mode", "auto"
        });

        assertEquals(1, exitCode);
        assertTrue(errContent.toString().contains("Plusieurs cadres"));
    }

    @Test
    @DisplayName("Prise en charge réussie du masque de territoire via --territory-mask")
    void testTerritoryMaskOption(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path tmFile = tempDir.resolve("territory.png");
        Path outFile = tempDir.resolve("out_tm.png");
        Path maskOutFile = tempDir.resolve("mask_tm.png");

        int w = 60, h = 60;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage tm = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = tm.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(10, 10, 40, 40);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());
        ImageIO.write(tm, "PNG", tmFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--territory-mask", tmFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--mode", "territory"
        });

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Chargement du masque de territoire fourni"));
    }

    @Test
    @DisplayName("Mode territory force le traitement territoire même si une annotation est présente")
    void testModeTerritoryForcesTerritoryEvenWithAnnotation(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out_ter.png");
        Path maskOutFile = tempDir.resolve("mask_ter.png");

        int w = 60, h = 60;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(5, 5, 50, 50);
        g.setColor(new Color(230, 20, 20)); // Tracé rouge d'annotation présent
        g.drawRect(15, 15, 20, 20);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--mode", "territory"
        });

        assertEquals(0, exitCode);
        // Doit exécuter le recalage géodésique de territoire et non le découpage de zone
        assertTrue(outContent.toString().contains("Recalage géodésique sur les routes"));
    }

    @Test
    @DisplayName("Prise en charge des alias courts -zc et -tm")
    void testShortAliasesZcAndTm(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path tmFile = tempDir.resolve("tm.png");
        Path outFile = tempDir.resolve("out_short.png");
        Path maskOutFile = tempDir.resolve("mask_short.png");

        int w = 80, h = 80;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage tm = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(10, 10, 60, 60);
        g.setColor(new Color(20, 30, 230)); // Tracé bleu fermé
        g.drawRect(20, 20, 30, 30);
        g.dispose();

        Graphics2D gtm = tm.createGraphics();
        gtm.setColor(Color.WHITE);
        gtm.fillRect(10, 10, 60, 60);
        gtm.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());
        ImageIO.write(tm, "PNG", tmFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--mode", "zone",
                "-zc", "blue",
                "-tm", tmFile.toString()
        });

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Détection du cadre d'annotation de couleur BLUE"));
        assertTrue(outFile.toFile().exists());
    }

    @Test
    @DisplayName("Mode AUTO bascule automatiquement en zone si un cadre unique est présent sans --zone-color")
    void testModeAutoWithUniqueFrameSwitchesToZone(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out_auto_zone.png");
        Path maskOutFile = tempDir.resolve("mask_auto_zone.png");

        int w = 80, h = 80;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175));
        g.fillRect(10, 10, 60, 60);
        g.setColor(new Color(230, 20, 20)); // Tracé rouge fermé unique
        g.drawRect(20, 20, 30, 30);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--mode", "auto"
        });

        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Détection du cadre d'annotation de couleur RED"));
        assertTrue(outContent.toString().contains("Découpage de zone et exclusion stricte"));
    }

    @Test
    @DisplayName("Vérifier que le masque de territoire résolu est automatiquement exporté en image territory_mask_detected.png")
    void testResolveTerritoryMaskSavesVerificationImage(@TempDir Path tempDir) throws Exception {
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out.png");
        Path maskOutFile = tempDir.resolve("mask_out.png");

        int w = 50, h = 50;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(180, 235, 175)); // Vert clair territoire
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString()
        });

        assertEquals(0, exitCode);
        Path debugMaskPath = tempDir.resolve("territory_mask_detected.png");
        assertTrue(debugMaskPath.toFile().exists(), "Le fichier territory_mask_detected.png doit exister dans le dossier de sortie");

        BufferedImage savedDebugMask = ImageIO.read(debugMaskPath.toFile());
        assertNotNull(savedDebugMask);
        assertEquals(w, savedDebugMask.getWidth());
        assertEquals(h, savedDebugMask.getHeight());
        // Au centre (25, 25), le pixel doit être blanc (0xFFFFFF)
        assertEquals(0xFFFFFF, savedDebugMask.getRGB(25, 25) & 0xFFFFFF, "Le pixel de territoire détecté doit être blanc");
    }
}
