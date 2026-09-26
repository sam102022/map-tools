package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parseur d'arguments et orchestrateur d'exécution en mode ligne de commande.
 */
public final class CliRunner {

    private CliRunner() {
    }

    public static int run(String[] args) {
        if (args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp();
            return args.length == 0 ? 1 : 0;
        }

        String mapPathStr = getOptionValue(args, "--map");
        String maskPathStr = getOptionValue(args, "--mask");
        String outputPathStr = getOptionValue(args, "--output", "clipped_output.png");
        String maskOutputPathStr = getOptionValue(args, "--mask-out", "mask_output.png");

        if (mapPathStr == null || maskPathStr == null) {
            System.err.println("Erreur : Les options --map et --mask sont obligatoires.");
            printHelp();
            return 1;
        }

        int snapDistance = getIntOption(args, "--snap-distance", 40);
        float roadSensitivity = getFloatOption(args, "--road-sensitivity", 1.0f);
        int smoothRadius = getIntOption(args, "--smooth", 1);

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(snapDistance)
                .roadSensitivity(roadSensitivity)
                .smoothRadius(smoothRadius)
                .build();

        try {
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

        } catch (Exception e) {
            System.err.println("Erreur lors du traitement : " + e.getMessage());
            return 1;
        }
    }

    private static boolean hasOption(String[] args, String... options) {
        for (String arg : args) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getOptionValue(String[] args, String option) {
        return getOptionValue(args, option, null);
    }

    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static int getIntOption(String[] args, String option, int defaultValue) {
        String val = getOptionValue(args, option);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            System.err.printf("Avertissement : valeur entière invalide pour %s : %s. Valeur par défaut utilisée (%d).\n", option, val, defaultValue);
            return defaultValue;
        }
    }

    private static float getFloatOption(String[] args, String option, float defaultValue) {
        String val = getOptionValue(args, option);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            System.err.printf("Avertissement : valeur flottante invalide pour %s : %s. Valeur par défaut utilisée (%.1f).\n", option, val, defaultValue);
            return defaultValue;
        }
    }

    public static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -jar photoshop.jar --map <path> --mask <path> [options]");
        System.out.println();
        System.out.println("Options obligatoires :");
        System.out.println("  --map <path>              Chemin vers la capture Google Maps");
        System.out.println("  --mask <path>             Chemin vers le calque vert grossier");
        System.out.println();
        System.out.println("Options facultatives :");
        System.out.println("  --output <path>           Fichier PNG détouré final (défaut: clipped_output.png)");
        System.out.println("  --mask-out <path>         Fichier PNG du masque affiné (défaut: mask_output.png)");
        System.out.println("  --snap-distance <int>     Portée maximale d'ajustement en pixels (défaut: 40)");
        System.out.println("  --road-sensitivity <flt>  Sensibilité détection routes 0.5-2.0 (défaut: 1.0)");
        System.out.println("  --smooth <int>            Rayon de lissage des bords (défaut: 1)");
        System.out.println("  --gui                     Lancer l'interface graphique interactive Swing");
        System.out.println("  --help, -h                Afficher cette aide");
    }
}
