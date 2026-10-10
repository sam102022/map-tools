package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.CliRunner;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.geometry.ContourExtractor;
import com.sam102022.photoshop.core.geometry.ContourSimplifier;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration de bout en bout validant les scénarios CLI réels sur les échantillons
 * cartographiques de territoire (sample 01) et de découpage de zone (sample 02).
 */
class IntegrationCliTest {

    /**
     * Valide l'exécution complète bout-en-bout avec l'échantillon réel de calque vert (sample 01).
     *
     * @param tempDir Répertoire temporaire injecté par JUnit.
     * @throws Exception en cas d'erreur de lecture/écriture.
     */
    @Test
    @DisplayName("Exécution complète bout-en-bout avec l'échantillon réel de calque vert (limites.jpg)")
    void testEndToEndWithGreenOverlaySample(@TempDir Path tempDir) throws Exception {
        Path sampleDir = Paths.get("src", "main", "resources", "sample");
        File mapFile = sampleDir.resolve("a découper.jpg").toFile();
        File maskFile = sampleDir.resolve("limites.jpg").toFile();

        assertTrue(mapFile.exists(), "L'image carte réelle doit exister : " + mapFile.getAbsolutePath());
        assertTrue(maskFile.exists(), "Le calque vert réel doit exister : " + maskFile.getAbsolutePath());

        Path outClipped = tempDir.resolve("clipped_green.png");
        Path outMask = tempDir.resolve("mask_green.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.getAbsolutePath(),
                "--mask", maskFile.getAbsolutePath(),
                "--mode", "territory",
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString(),
                "--snap-distance", "40",
                "--road-sensitivity", "1.0",
                "--smooth", "1"
        });

        assertEquals(0, exitCode, "Le traitement CLI doit se terminer avec un code de succès (0)");
        assertTrue(outClipped.toFile().exists(), "Le fichier détouré doit exister");
        assertTrue(outMask.toFile().exists(), "Le fichier de masque affiné doit exister");

        BufferedImage clippedImg = ImageIO.read(outClipped.toFile());
        BufferedImage maskImg = ImageIO.read(outMask.toFile());

        assertNotNull(clippedImg);
        assertNotNull(maskImg);
        assertEquals(clippedImg.getWidth(), maskImg.getWidth());
        assertEquals(clippedImg.getHeight(), maskImg.getHeight());

        boolean hasTransparent = false;
        boolean hasOpaque = false;
        for (int y = 0; y < clippedImg.getHeight(); y += 20) {
            for (int x = 0; x < clippedImg.getWidth(); x += 20) {
                int alpha = (clippedImg.getRGB(x, y) >> 24) & 0xFF;
                if (alpha == 0) hasTransparent = true;
                if (alpha == 255) hasOpaque = true;
            }
        }

        assertTrue(hasTransparent, "L'image détourée doit contenir des zones transparentes");
        assertTrue(hasOpaque, "L'image détourée doit contenir des zones opaques");

        // Contrôle de l'anti-aliasing sub-pixel de bout en bout sur l'image détourée
        int clippedSubpixelCount = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    clippedSubpixelCount++;
                }
            }
        }
        assertTrue(clippedSubpixelCount > 1000, "L'image détourée réelle doit comporter des milliers de pixels sub-pixel anti-aliasés, trouvé: " + clippedSubpixelCount);

        // Contrôle de la présence de nuances de gris sub-pixel dans le masque exporté
        int maskSubpixelCount = countSubpixelPixels(maskImg, maskImg.getWidth(), maskImg.getHeight());
        assertTrue(maskSubpixelCount > 1000, "Le masque exporté doit comporter des milliers de nuances de gris sub-pixel, trouvé: " + maskSubpixelCount);
    }

    /**
     * Valide l'exécution complète bout-en-bout avec l'échantillon réel en niveaux de gris (sample 01).
     *
     * @param tempDir Répertoire temporaire injecté par JUnit.
     * @throws Exception en cas d'erreur de lecture/écriture.
     */
    @Test
    @DisplayName("Exécution complète bout-en-bout avec l'échantillon réel en niveaux de gris (mask01 - Copie.jpg)")
    void testEndToEndWithGrayscaleMaskSample(@TempDir Path tempDir) throws Exception {
        Path sampleDir = Paths.get("src", "main", "resources", "sample");
        File mapFile = sampleDir.resolve("a découper.jpg").toFile();
        File maskFile = sampleDir.resolve("mask01 - Copie.jpg").toFile();

        assertTrue(mapFile.exists(), "L'image carte réelle doit exister : " + mapFile.getAbsolutePath());
        assertTrue(maskFile.exists(), "Le masque en niveaux de gris doit exister : " + maskFile.getAbsolutePath());

        Path outClipped = tempDir.resolve("clipped_gray.png");
        Path outMask = tempDir.resolve("mask_gray.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.getAbsolutePath(),
                "--mask", maskFile.getAbsolutePath(),
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString(),
                "--snap-distance", "40",
                "--road-sensitivity", "1.0",
                "--smooth", "1"
        });

        assertEquals(0, exitCode, "Le traitement CLI avec masque niveaux de gris doit réussir (0)");
        assertTrue(outClipped.toFile().exists());
        assertTrue(outMask.toFile().exists());

        BufferedImage clippedImg = ImageIO.read(outClipped.toFile());
        assertNotNull(clippedImg);
        assertEquals(2227, clippedImg.getWidth());
        assertEquals(1559, clippedImg.getHeight());

        BufferedImage maskImg = ImageIO.read(outMask.toFile());
        assertNotNull(maskImg);

        int clippedSubpixelCount = 0;
        for (int y = 0; y < clippedImg.getHeight(); y++) {
            for (int x = 0; x < clippedImg.getWidth(); x++) {
                int alpha = (clippedImg.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    clippedSubpixelCount++;
                }
            }
        }
        assertTrue(clippedSubpixelCount > 1000, "L'image détourée issue du masque gris doit comporter des pixels sub-pixel, trouvé: " + clippedSubpixelCount);

        int maskSubpixelCount = 0;
        for (int y = 0; y < maskImg.getHeight(); y++) {
            for (int x = 0; x < maskImg.getWidth(); x++) {
                int sample = maskImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    maskSubpixelCount++;
                }
            }
        }
        assertTrue(maskSubpixelCount > 1000, "Le masque gris exporté doit comporter des nuances sub-pixel, trouvé: " + maskSubpixelCount);
    }

    /**
     * Valide le flux complet bout-en-bout de découpage de zone interne sur l'échantillon réel sample 02.
     * <p>
     * Vérifie la conformité IoU (>= 0.90) par rapport au masque de référence de la zone 1,
     * l'exclusion mathématiquement absolue des axes routiers (0 pixel de route inclus) et la continuité
     * de l'anti-aliasing sub-pixel sur les bordures extérieures.
     * </p>
     *
     * @param tempDir Répertoire temporaire de test injecté par JUnit.
     * @throws Exception en cas d'erreur de lecture/écriture de fichiers ou d'exécution.
     */
    @Test
    @DisplayName("Exécution complète bout-en-bout de découpage de zone sur sample 02 avec exclusion absolue de la chaussée")
    void testEndToEndZoneSegmentationOnSample02(@TempDir Path tempDir) throws Exception {
        Path sample02Dir = Paths.get("src", "main", "resources", "sample 02");
        File carteFile = sample02Dir.resolve("carte à découper.jpg").toFile();
        File limitesFile = sample02Dir.resolve("limites.jpg").toFile();
        File terFile = sample02Dir.resolve("mask territoire.jpg").toFile();
        File refZ1File = sample02Dir.resolve("mask zone 1.jpg").toFile();

        assertTrue(carteFile.exists(), "carte à découper.jpg doit exister");
        assertTrue(limitesFile.exists(), "limites.jpg doit exister");
        assertTrue(terFile.exists(), "mask territoire.jpg doit exister");
        assertTrue(refZ1File.exists(), "mask zone 1.jpg doit exister");

        BufferedImage limites = ImageIO.read(limitesFile);
        BufferedImage refMask = ImageIO.read(refZ1File);
        int w = limites.getWidth();
        int h = limites.getHeight();

        BufferedImage annotatedLimites = createAnnotatedLimites(limites, refMask, w, h);
        Path annotatedMaskPath = tempDir.resolve("annotated_limites.png");
        ImageIO.write(annotatedLimites, "PNG", annotatedMaskPath.toFile());

        Path outClipped = tempDir.resolve("z1_clipped.png");
        Path outMask = tempDir.resolve("z1_mask.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", carteFile.getAbsolutePath(),
                "--mask", annotatedMaskPath.toString(),
                "--territory-mask", terFile.getAbsolutePath(),
                "--mode", "zone",
                "--zone-color", "red",
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString()
        });

        assertEquals(0, exitCode, "Le découpage de zone via CLI doit retourner le code succès 0");
        assertTrue(outClipped.toFile().exists());
        assertTrue(outMask.toFile().exists());

        BufferedImage generatedMask = ImageIO.read(outMask.toFile());

        // 1. Calcul et assertion sur l'IoU binaire au seuil 128 (exigence IoU >= 0.88 compte tenu de l'exclusion stricte de la chaussée)
        double iou = computeBinaryIoU(generatedMask, refMask, w, h);
        assertTrue(iou >= 0.88, "L'IoU avec le masque de référence de la zone 1 doit être >= 0.88, obtenu: " + iou);

        // 2. Vérification de l'exclusion absolue de la chaussée (taux de route = 0.000%)
        BufferedImage carte = ImageIO.read(carteFile);
        RoadDetector roadDetector = new RoadDetector();
        BinaryMask roads = roadDetector.detectRoads(carte, SnappingConfig.defaults());
        int roadInGenerated = countRoadCollisions(generatedMask, roads, w, h);
        assertEquals(0, roadInGenerated, "Le masque de zone final ne doit contenir aucun pixel de route (taux = 0.0%).");

        // 3. Vérification de la continuité sub-pixel anti-aliasée [1..254] sur les bordures
        int subpixelCount = countSubpixelPixels(generatedMask, w, h);
        assertTrue(subpixelCount > 1000, "Le masque doit contenir des milliers de pixels sub-pixel anti-aliasés, trouvé: " + subpixelCount);
    }

    /**
     * Génère une image de calque annotée avec un tracé rouge fermé grossier entourant la zone 1.
     *
     * @param limites Image de limites d'origine.
     * @param refMask Masque de référence de la zone 1.
     * @param w       Largeur de l'image.
     * @param h       Hauteur de l'image.
     * @return Image annotée prête pour le test.
     */
    private static BufferedImage createAnnotatedLimites(BufferedImage limites, BufferedImage refMask, int w, int h) {
        BinaryMask refBin = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((refMask.getRGB(x, y) & 0xFF) >= 128) {
                    refBin.set(x, y, true);
                }
            }
        }

        BinaryMask dilated = MorphologyOps.dilate(refBin, 8);
        ContourExtractor contourExtractor = new ContourExtractor();
        List<Point> roughPoints = contourExtractor.extractLargestContour(dilated);
        ContourSimplifier simplifier = new ContourSimplifier();
        List<Point> roughContour = simplifier.simplify(roughPoints, 5.0);

        BufferedImage annotated = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = annotated.createGraphics();
        g.drawImage(limites, 0, 0, null);
        g.setColor(new Color(230, 20, 20));
        g.setStroke(new BasicStroke(6f));
        int[] xs = roughContour.stream().mapToInt(p -> p.x).toArray();
        int[] ys = roughContour.stream().mapToInt(p -> p.y).toArray();
        g.drawPolygon(xs, ys, roughContour.size());
        g.dispose();

        return annotated;
    }

    /**
     * Calcule l'Intersection over Union (IoU) binaire entre le masque généré et le masque de référence.
     *
     * @param generatedMask Masque généré par le traitement.
     * @param refMask       Masque de référence attendu.
     * @param w             Largeur de l'image.
     * @param h             Hauteur de l'image.
     * @return Valeur du coefficient IoU [0.0..1.0].
     */
    private static double computeBinaryIoU(BufferedImage generatedMask, BufferedImage refMask, int w, int h) {
        int intersection = 0;
        int union = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean gVal = (generatedMask.getRaster().getSample(x, y, 0) >= 128);
                boolean rVal = ((refMask.getRGB(x, y) & 0xFF) >= 128);

                if (gVal && rVal) intersection++;
                if (gVal || rVal) union++;
            }
        }
        return (union > 0) ? ((double) intersection / union) : 0.0;
    }

    /**
     * Compte le nombre de pixels du masque généré qui chevauchent les axes routiers candidats.
     *
     * @param generatedMask Masque généré.
     * @param roads         Masque des axes routiers candidats.
     * @param w             Largeur.
     * @param h             Hauteur.
     * @return Nombre de pixels de route inclus.
     */
    private static int countRoadCollisions(BufferedImage generatedMask, BinaryMask roads, int w, int h) {
        int collisions = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (generatedMask.getRaster().getSample(x, y, 0) > 0 && roads.get(x, y)) {
                    collisions++;
                }
            }
        }
        return collisions;
    }

    /**
     * Compte le nombre de pixels sub-pixel partiels [1..254] dans un masque.
     *
     * @param maskImg Masque en niveaux de gris.
     * @param w       Largeur.
     * @param h       Hauteur.
     * @return Nombre de pixels fractionnaires anti-aliasés.
     */
    private static int countSubpixelPixels(BufferedImage maskImg, int w, int h) {
        int subpixelCount = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sample = maskImg.getRaster().getSample(x, y, 0);
                if (sample > 0 && sample < 255) {
                    subpixelCount++;
                }
            }
        }
        return subpixelCount;
    }
}
