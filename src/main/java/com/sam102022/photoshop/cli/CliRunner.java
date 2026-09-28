package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.detection.ColorRegionSelectorExtractor;
import com.sam102022.photoshop.core.detection.DarkDemarcationDetector;
import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.core.model.SelectorColor;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.core.segmentation.ZoneBarrierConsolidator;
import com.sam102022.photoshop.core.segmentation.ZoneSegmentationEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Parseur d'arguments et orchestrateur d'exécution en mode ligne de commande.
 * <p>
 * Prend en charge à la fois le détourage de territoire global et le découpage de zone ciblée
 * selon le mode d'opération sélectionné ou détecté.
 * </p>
 */
public final class CliRunner {

    /**
     * Constructeur privé pour empêcher l'instanciation de cette classe utilitaire.
     */
    private CliRunner() {
    }

    /**
     * Point d'entrée de l'exécution en ligne de commande.
     *
     * @param args Arguments passés sur la ligne de commande.
     * @return Code de retour (0 en cas de succès, 1 en cas d'erreur ou d'aide demandée sans options).
     */
    public static int run(String[] args) {
        if (args == null || args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp(System.out);
            return (args == null || args.length == 0) ? 1 : 0;
        }

        try {
            return executePipeline(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur de syntaxe ou de configuration : " + e.getMessage());
            printHelp(System.err);
            return 1;
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement : " + e.getMessage());
            return 1;
        }
    }

    /**
     * Exécute le pipeline complet de traitement cartographique.
     *
     * @param args Arguments de la ligne de commande.
     * @return 0 en cas de succès, 1 en cas d'échec métier.
     * @throws IOException              si la lecture ou l'écriture d'images échoue.
     * @throws IllegalArgumentException si les arguments ou fichiers d'entrée sont invalides.
     */
    private static int executePipeline(String[] args) throws IOException {
        String mapPathStr = getRequiredOptionValue(args, "--map");
        String maskPathStr = getRequiredOptionValue(args, "--mask");
        String outputPathStr = getOptionValue(args, "--output", "clipped_output.png");
        String maskOutputPathStr = getOptionValue(args, "--mask-out", "mask_output.png");
        String territoryMaskPathStr = getOptionValue(args, "--territory-mask", getOptionValue(args, "-tm", null));

        SnappingConfig config = parseSnappingConfig(args);

        System.out.println("-> Chargement des images...");
        BufferedImage mapImg = ImageLoader.load(Paths.get(mapPathStr));
        BufferedImage maskImg = ImageLoader.load(Paths.get(maskPathStr));

        ImageLoader.validateDimensions(mapImg, maskImg);

        BinaryMask territoryMask = resolveTerritoryMask(maskImg, territoryMaskPathStr);

        ColorRegionSelectorExtractor colorExtractor = new ColorRegionSelectorExtractor();
        List<SelectorColor> presentColors = colorExtractor.detectPresentColors(maskImg);

        CoverageMask finalCoverage;

        if (shouldExecuteZoneMode(config.mode(), config.zoneColor(), presentColors)) {
            SelectorColor targetColor = resolveTargetColor(config.zoneColor(), presentColors);
            finalCoverage = executeZoneWorkflow(mapImg, maskImg, territoryMask, colorExtractor, targetColor, config);
        } else {
            finalCoverage = executeTerritoryWorkflow(mapImg, territoryMask, config);
        }

        if (finalCoverage == null) {
            return 1;
        }

        saveOutputs(mapImg, finalCoverage, config.smoothRadius(), outputPathStr, maskOutputPathStr);
        return 0;
    }

    /**
     * Parse et assemble la configuration {@link SnappingConfig} à partir des arguments CLI.
     *
     * @param args Arguments CLI.
     * @return Configuration immuable initialisée.
     * @throws IllegalArgumentException si les valeurs des options sont invalides ou conflictuelles.
     */
    private static SnappingConfig parseSnappingConfig(String[] args) {
        int snapDistance = getIntOption(args, "--snap-distance", 40);
        float roadSensitivity = getFloatOption(args, "--road-sensitivity", 1.0f);
        int smoothRadius = getIntOption(args, "--smooth", 1);
        boolean antialiasing = getAntialiasingOption(args);

        String modeStr = getOptionValue(args, "--mode", "auto");
        OperationMode mode = OperationMode.fromString(modeStr);

        String zoneColorStr = getOptionValue(args, "--zone-color", getOptionValue(args, "-zc", "auto"));
        SelectorColor zoneColor = SelectorColor.fromString(zoneColorStr);

        return SnappingConfig.builder()
                .snapDistance(snapDistance)
                .roadSensitivity(roadSensitivity)
                .smoothRadius(smoothRadius)
                .antialiasing(antialiasing)
                .mode(mode)
                .zoneColor(zoneColor)
                .build();
    }

    /**
     * Résout ou extrait le masque du territoire global.
     *
     * @param maskImg              Image du calque de limites.
     * @param territoryMaskPathStr Chemin optionnel vers le fichier de masque du territoire.
     * @return Masque binaire du territoire global.
     * @throws IOException              si le chargement du fichier échoue.
     * @throws IllegalArgumentException si les dimensions du masque fourni diffèrent de maskImg.
     */
    private static BinaryMask resolveTerritoryMask(BufferedImage maskImg, String territoryMaskPathStr) throws IOException {
        if (territoryMaskPathStr != null) {
            System.out.println("-> Chargement du masque de territoire fourni...");
            BufferedImage territoryImg = ImageLoader.load(Paths.get(territoryMaskPathStr));
            ImageLoader.validateDimensions(maskImg, territoryImg);
            return BinaryMask.fromImage(territoryImg, 128);
        }

        System.out.println("-> Détection du masque vert du territoire...");
        GreenMaskExtractor greenExtractor = new GreenMaskExtractor();
        BinaryMask territoryMask = greenExtractor.extract(maskImg);
        int active = territoryMask.countActivePixels();
        System.out.printf("   %d pixels verts de territoire détectés.\n", active);
        return territoryMask;
    }

    /**
     * Détermine si le mode zone doit être activé selon la configuration et les annotations présentes.
     *
     * @param mode           Mode d'opération configuré.
     * @param requestedColor Couleur de zone demandée.
     * @param presentColors  Couleurs d'annotations détectées.
     * @return {@code true} si le mode zone doit être exécuté, {@code false} pour le mode territoire.
     * @throws IllegalArgumentException si le mode zone est forcé sans aucun cadre.
     * @throws IllegalStateException    si le mode zone est forcé avec plusieurs cadres sans couleur choisie.
     */
    private static boolean shouldExecuteZoneMode(OperationMode mode, SelectorColor requestedColor,
                                                 List<SelectorColor> presentColors) {
        if (mode == OperationMode.TERRITORY) {
            return false;
        }

        if (mode == OperationMode.ZONE) {
            if (requestedColor == SelectorColor.AUTO && presentColors.size() > 1) {
                throw new IllegalStateException("Plusieurs cadres de couleurs distinctes ont été détectés dans le calque : "
                        + presentColors + ". Veuillez préciser la zone à découper via l'option --zone-color <couleur>.");
            }
            if (requestedColor == SelectorColor.AUTO && presentColors.isEmpty()) {
                throw new IllegalArgumentException("Mode 'zone' spécifié mais aucun cadre d'annotation valide n'a été détecté dans le calque.");
            }
            return true;
        }

        // Mode AUTO : si une couleur spécifique est demandée, engager le flux de zone
        if (requestedColor != SelectorColor.AUTO) {
            return true;
        }

        // Mode AUTO par défaut :
        if (presentColors.size() > 1) {
            throw new IllegalStateException("Plusieurs cadres de couleurs distinctes ont été détectés dans le calque : "
                    + presentColors + ". Veuillez préciser la zone à découper via l'option --zone-color <couleur>.");
        }

        return presentColors.size() == 1;
    }

    /**
     * Résout la couleur d'annotation cible ou lève une exception descriptive si ambiguïté.
     *
     * @param requested     Couleur demandée dans la configuration.
     * @param presentColors Couleurs détectées dans l'image.
     * @return Couleur cible validée.
     * @throws IllegalArgumentException si la couleur demandée est absente ou aucun cadre n'existe.
     * @throws IllegalStateException    si plusieurs cadres coexistent en mode AUTO.
     */
    private static SelectorColor resolveTargetColor(SelectorColor requested, List<SelectorColor> presentColors) {
        if (requested != SelectorColor.AUTO) {
            if (!presentColors.contains(requested)) {
                throw new IllegalArgumentException("La couleur d'annotation demandée (" + requested
                        + ") n'a pas été détectée dans l'image de calque.");
            }
            return requested;
        }

        if (presentColors.isEmpty()) {
            throw new IllegalArgumentException("Mode 'zone' spécifié mais aucun cadre d'annotation valide n'a été détecté dans le calque.");
        }

        if (presentColors.size() > 1) {
            throw new IllegalStateException("Plusieurs cadres de couleurs distinctes ont été détectés dans le calque : "
                    + presentColors + ". Veuillez préciser la zone à découper via l'option --zone-color <couleur>.");
        }

        return presentColors.get(0);
    }

    /**
     * Exécute le flux de traitement de découpage de zone interne.
     *
     * @param mapImg        Image de carte.
     * @param maskImg       Image de calque.
     * @param territoryMask Masque du territoire.
     * @param extractor     Extracteur polychrome.
     * @param targetColor   Couleur de l'annotation ciblée.
     * @param config        Configuration de traitement.
     * @return Masque de couverture continue résultant.
     */
    private static CoverageMask executeZoneWorkflow(BufferedImage mapImg, BufferedImage maskImg,
                                                    BinaryMask territoryMask, ColorRegionSelectorExtractor extractor,
                                                    SelectorColor targetColor, SnappingConfig config) {
        System.out.printf("-> Détection du cadre d'annotation de couleur %s...\n", targetColor);
        BinaryMask interiorMask = extractor.extractInterior(maskImg, targetColor);
        System.out.printf("   Intérieur de zone identifié (%d pixels).\n", interiorMask.countActivePixels());

        System.out.println("-> Détection des axes routiers Google Maps...");
        RoadDetector roadDetector = new RoadDetector();
        BinaryMask roadCandidates = roadDetector.detectRoads(mapImg, config);
        System.out.printf("   %d pixels candidats routiers identifiés.\n", roadCandidates.countActivePixels());

        System.out.println("-> Détection des démarcations cartographiques sombres...");
        DarkDemarcationDetector darkDetector = new DarkDemarcationDetector();
        BinaryMask darkDemarcations = darkDetector.detect(mapImg, maskImg, territoryMask);
        System.out.printf("   %d pixels de démarcations sombres détectés.\n", darkDemarcations.countActivePixels());

        System.out.println("-> Consolidation étanche des barrières...");
        ZoneBarrierConsolidator barrierConsolidator = new ZoneBarrierConsolidator();
        BinaryMask consolidatedBarriers = barrierConsolidator.consolidate(roadCandidates, darkDemarcations, territoryMask);

        System.out.println("-> Découpage de zone et exclusion stricte de la chaussée...");
        ZoneSegmentationEngine zoneEngine = new ZoneSegmentationEngine();
        return zoneEngine.segmentZone(interiorMask, roadCandidates, consolidatedBarriers, territoryMask, config);
    }

    /**
     * Exécute le flux historique de détourage de territoire global.
     *
     * @param mapImg        Image de carte.
     * @param territoryMask Masque du territoire.
     * @param config        Configuration de recalage.
     * @return Masque de couverture résultant, ou null si territoire vide.
     */
    private static CoverageMask executeTerritoryWorkflow(BufferedImage mapImg,
                                                         BinaryMask territoryMask, SnappingConfig config) {
        if (territoryMask.countActivePixels() == 0) {
            System.err.println("Attention : Aucun pixel vert trouvé dans le masque.");
            return null;
        }

        System.out.println("-> Détection des axes routiers Google Maps...");
        RoadDetector roadDetector = new RoadDetector();
        BinaryMask roadCandidates = roadDetector.detectRoads(mapImg, config);
        System.out.printf("   %d pixels candidats routiers identifiés.\n", roadCandidates.countActivePixels());

        System.out.println("-> Recalage géodésique sur les routes...");
        RoadSnappingEngine engine = new RoadSnappingEngine();
        CoverageMask coverageMask = engine.snapCoverage(territoryMask, roadCandidates, config);
        BinaryMask snappedMask = coverageMask.toBinaryMask(128);
        System.out.printf("   %d pixels conservés après recalage.\n", snappedMask.countActivePixels());
        return coverageMask;
    }

    /**
     * Sauvegarde l'image détourée et le masque généré vers les chemins demandés.
     *
     * @param mapImg       Image de carte d'origine.
     * @param coverage     Masque de couverture continue.
     * @param smoothRadius Rayon d'adoucissement.
     * @param outPathStr   Chemin de l'image détourée.
     * @param maskPathStr  Chemin du masque de couverture.
     * @throws IOException si l'écriture échoue.
     */
    private static void saveOutputs(BufferedImage mapImg, CoverageMask coverage, int smoothRadius,
                                    String outPathStr, String maskPathStr) throws IOException {
        System.out.println("-> Création et sauvegarde des exports PNG...");
        BufferedImage clippedImage = ImageExporter.createClippedImage(mapImg, coverage, smoothRadius);
        BufferedImage maskResultImg = ImageExporter.createCoverageMaskImage(coverage);

        Path outPath = Paths.get(outPathStr);
        Path maskOutPath = Paths.get(maskPathStr);
        ImageExporter.savePng(clippedImage, outPath);
        ImageExporter.savePng(maskResultImg, maskOutPath);

        System.out.printf("Succès ! Image détourée : %s | Masque : %s\n",
                outPath.toAbsolutePath(), maskOutPath.toAbsolutePath());
    }

    /**
     * Vérifie si au moins une des options données figure dans les arguments CLI.
     *
     * @param args    Arguments passés.
     * @param options Noms d'options à tester.
     * @return {@code true} si l'option est présente, {@code false} sinon.
     */
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

    /**
     * Parse l'état d'activation de l'anti-aliasing en vérifiant l'absence de conflit.
     *
     * @param args Arguments CLI.
     * @return Vrai si l'anti-aliasing est actif (par défaut), faux sinon.
     * @throws IllegalArgumentException si les options contradictoires sont présentes simultanément.
     */
    private static boolean getAntialiasingOption(String[] args) {
        boolean enabled = hasOption(args, "--antialias", "--aa");
        boolean disabled = hasOption(args, "--no-antialias", "--no-aa");
        if (enabled && disabled) {
            throw new IllegalArgumentException("Conflit d'options : impossible de spécifier simultanément l'activation et la désactivation de l'anti-aliasing.");
        }
        return !disabled;
    }

    /**
     * Extrait la valeur d'une option obligatoire.
     *
     * @param args   Arguments CLI.
     * @param option Nom de l'option.
     * @return Valeur associée.
     * @throws IllegalArgumentException si l'option est manquante.
     */
    private static String getRequiredOptionValue(String[] args, String option) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            throw new IllegalArgumentException("L'option obligatoire " + option + " est manquante.");
        }
        return val;
    }

    /**
     * Extrait la valeur d'une option facultative ou retourne sa valeur par défaut.
     *
     * @param args         Arguments CLI.
     * @param option       Nom de l'option.
     * @param defaultValue Valeur par défaut.
     * @return Valeur renseignée ou valeur par défaut.
     * @throws IllegalArgumentException si l'option est présente mais qu'aucune valeur n'est fournie.
     */
    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                    throw new IllegalArgumentException("Valeur manquante pour l'option " + option);
                }
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    /**
     * Extrait la valeur entière d'une option.
     *
     * @param args         Arguments CLI.
     * @param option       Nom de l'option.
     * @param defaultValue Valeur par défaut.
     * @return Valeur convertie en entier.
     * @throws IllegalArgumentException si la valeur fournie n'est pas un entier valide.
     */
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

    /**
     * Extrait la valeur flottante d'une option.
     *
     * @param args         Arguments CLI.
     * @param option       Nom de l'option.
     * @param defaultValue Valeur par défaut.
     * @return Valeur convertie en flottant.
     * @throws IllegalArgumentException si la valeur fournie n'est pas un flottant valide.
     */
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

    /**
     * Affiche l'aide de l'application sur la sortie standard.
     */
    public static void printHelp() {
        printHelp(System.out);
    }

    /**
     * Affiche l'aide complète de l'application sur le flux spécifié.
     *
     * @param out Flux de sortie cible.
     */
    public static void printHelp(PrintStream out) {
        out.println("Usage:");
        out.println("  java -jar photoshop.jar --map <path> --mask <path> [options]");
        out.println();
        out.println("Options obligatoires :");
        out.println("  --map <path>              Chemin vers la capture Google Maps");
        out.println("  --mask <path>             Chemin vers le calque de limites / annotations");
        out.println();
        out.println("Options de mode et zone :");
        out.println("  --mode <auto|territory|zone>  Mode d'opération (défaut: auto)");
        out.println("  --zone-color, -zc <color>     Couleur de la zone ciblée : red, blue, magenta, cyan (défaut: auto)");
        out.println("  --territory-mask, -tm <path>  Masque optionnel du territoire global");
        out.println();
        out.println("Options facultatives :");
        out.println("  --output <path>           Fichier PNG détouré final (défaut: clipped_output.png)");
        out.println("  --mask-out <path>         Masque PNG en niveaux de gris avec couverture AA (défaut: mask_output.png)");
        out.println("  --snap-distance <int>     Portée maximale d'ajustement en pixels (défaut: 40)");
        out.println("  --road-sensitivity <flt>  Sensibilité détection routes 0.5-2.0 (défaut: 1.0)");
        out.println("  --smooth <int>            Rayon de lissage des bords (défaut: 1)");
        out.println("  --antialias, --aa         Activer l'anti-aliasing (défaut)");
        out.println("  --no-antialias, --no-aa   Désactiver l'anti-aliasing");
        out.println("  --gui                     Lancer l'interface graphique interactive Swing");
        out.println("  --help, -h                Afficher cette aide");
    }
}
