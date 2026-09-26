package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import java.awt.image.BufferedImage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parseur d'arguments et orchestrateur d'exécution en mode ligne de commande.
 */
public final class CliRunner {

    private CliRunner() {
    }

    public static int run(String[] args) {
        if (args == null || args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp(System.out);
            return (args == null || args.length == 0) ? 1 : 0;
        }

        try {
            String mapPathStr = getRequiredOptionValue(args, "--map");
            String maskPathStr = getRequiredOptionValue(args, "--mask");
            String outputPathStr = getOptionValue(args, "--output", "clipped_output.png");
            String maskOutputPathStr = getOptionValue(args, "--mask-out", "mask_output.png");

            int snapDistance = getIntOption(args, "--snap-distance", 40);
            float roadSensitivity = getFloatOption(args, "--road-sensitivity", 1.0f);
            int smoothRadius = getIntOption(args, "--smooth", 1);

            SnappingConfig config = SnappingConfig.builder()
                    .snapDistance(snapDistance)
                    .roadSensitivity(roadSensitivity)
                    .smoothRadius(smoothRadius)
                    .build();

            System.out.println("-> Chargement des images...");
            BufferedImage mapImg = ImageLoader.load(Paths.get(mapPathStr));
            BufferedImage maskImg = ImageLoader.load(Paths.get(maskPathStr));

            ImageLoader.validateDimensions(mapImg, maskImg);

            System.out.println("-> Détection du masque vert grossier...");
            GreenMaskExtractor greenExtractor = new GreenMaskExtractor();
            BinaryMask roughMask = greenExtractor.extract(maskImg);
            int activePixels = roughMask.countActivePixels();
            System.out.printf("   %d pixels verts initiaux détectés.\n", activePixels);

            if (activePixels == 0) {
                System.err.println("Attention : Aucun pixel vert trouvé dans le masque.");
                return 1;
            }

            System.out.println("-> Détection des axes routiers Google Maps...");
            RoadDetector roadDetector = new RoadDetector();
            BinaryMask roadBarrier = roadDetector.detectRoads(mapImg, config);
            Path roadDebugPath = Paths.get("road-debug.png");

            ImageExporter.savePng(
                    ImageExporter.createMaskImage(roadBarrier),
                    roadDebugPath
            );

            System.out.println("   Masque routes debug : " + roadDebugPath.toAbsolutePath());
            System.out.printf("   %d pixels de barrière routière identifiés.\n", roadBarrier.countActivePixels());

            System.out.println("-> Recalage géodésique sur les routes...");
            RoadSnappingEngine engine = new RoadSnappingEngine();
            BinaryMask snappedMask = engine.snap(roughMask, roadBarrier, config);
            System.out.printf("   %d pixels conservés après recalage.\n", snappedMask.countActivePixels());

            System.out.println("-> Création et sauvegarde des exports PNG...");
            BufferedImage clippedImage = ImageExporter.createClippedImage(mapImg, snappedMask, config.smoothRadius());
            BufferedImage maskResultImg = ImageExporter.createMaskImage(snappedMask);

            Path outPath = Paths.get(outputPathStr);
            Path maskOutPath = Paths.get(maskOutputPathStr);
            ImageExporter.savePng(clippedImage, outPath);
            ImageExporter.savePng(maskResultImg, maskOutPath);

            System.out.printf("Succès ! Image détourée : %s | Masque : %s\n", outPath.toAbsolutePath(), maskOutPath.toAbsolutePath());
            return 0;

        } catch (IllegalArgumentException e) {
            System.err.println("Erreur de syntaxe ou de configuration : " + e.getMessage());
            printHelp(System.err);
            return 1;
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement : " + e.getMessage());
            return 1;
        }
    }

    private static boolean hasOption(String[] args, String... options) {
        for (String arg : args) {
            if (arg == null) continue;
            for (String opt : options) {
                if (opt.equalsIgnoreCase(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getRequiredOptionValue(String[] args, String option) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            throw new IllegalArgumentException("L'option obligatoire " + option + " est manquante.");
        }
        return val;
    }

    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Valeur manquante pour l'option " + option);
                }
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static int getIntOption(String[] args, String option, int defaultValue) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur entière invalide pour " + option + " : '" + val + "'");
        }
    }

    private static float getFloatOption(String[] args, String option, float defaultValue) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur flottante invalide pour " + option + " : '" + val + "'");
        }
    }

    public static void printHelp() {
        printHelp(System.out);
    }

    public static void printHelp(PrintStream out) {
        out.println("Usage:");
        out.println("  java -jar photoshop.jar --map <path> --mask <path> [options]");
        out.println();
        out.println("Options obligatoires :");
        out.println("  --map <path>              Chemin vers la capture Google Maps");
        out.println("  --mask <path>             Chemin vers le calque vert grossier");
        out.println();
        out.println("Options facultatives :");
        out.println("  --output <path>           Fichier PNG détouré final (défaut: clipped_output.png)");
        out.println("  --mask-out <path>         Fichier PNG du masque affiné (défaut: mask_output.png)");
        out.println("  --snap-distance <int>     Portée maximale d'ajustement en pixels (défaut: 40)");
        out.println("  --road-sensitivity <flt>  Sensibilité détection routes 0.5-2.0 (défaut: 1.0)");
        out.println("  --smooth <int>            Rayon de lissage des bords (défaut: 1)");
        out.println("  --gui                     Lancer l'interface graphique interactive Swing");
        out.println("  --help, -h                Afficher cette aide");
    }
}
