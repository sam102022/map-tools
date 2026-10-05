package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.pipeline.V2Config;
import com.sam102022.photoshop.v2.pipeline.V2Pipeline;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.road.RoadDetectorOsm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Point d'entrée d'exécution en ligne de commande pour le pipeline V2 haute-fidélité (Sprint 9).
 */
public final class V2CliRunner {

    private V2CliRunner() {
    }

    /**
     * Point d'entrée principal pour la commande CLI V2.
     *
     * @param args Arguments de la ligne de commande.
     * @return Code de sortie (0 en cas de succès, 1 en cas d'erreur ou d'aide sans exécution).
     */
    public static int run(String[] args) {
        if (args == null || args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp(System.out);
            return (args == null || args.length == 0) ? 1 : 0;
        }

        try {
            return executePipeline(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur de syntaxe ou de configuration V2 : " + e.getMessage());
            printHelp(System.err);
            return 1;
        } catch (Exception e) {
            System.err.println("Erreur lors de l'exécution du pipeline V2 : " + e.getMessage());
            return 1;
        }
    }

    /**
     * Exécute le pipeline V2 complet à partir des arguments CLI.
     *
     * @param args Arguments CLI.
     * @return Code de sortie 0.
     * @throws IOException en cas d'erreur de lecture/écriture.
     */
    private static int executePipeline(String[] args) throws IOException {
        Path mapPath = resolveMapPath(args);
        Path jsonPath = resolveJsonPath(args);

        System.out.println("-> [V2] Chargement des données sources...");
        BufferedImage mapImg = ImageLoader.load(mapPath);

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        MapContext mapContext = loaded.mapContext();
        if (mapContext.width() != mapImg.getWidth() || mapContext.height() != mapImg.getHeight()) {
            mapContext = new MapContext(mapImg.getWidth(), mapImg.getHeight(), mapContext.zoom(), mapContext.center());
        }

        BinaryMask roadMask = resolveRoadMask(args, mapPath, jsonPath, mapContext, mapImg.getWidth(), mapImg.getHeight());

        V2Config config = parseConfig(args);
        V2Pipeline pipeline = new V2Pipeline();
        RenderResult result = pipeline.execute(mapImg, loaded.geometry(), mapContext, roadMask, config);

        saveOutputs(result, args, mapPath);
        System.out.println("Succès ! Pipeline V2 achevé avec succès.");
        return 0;
    }

    /**
     * Résout et valide le chemin de l'image cartographique source.
     */
    private static Path resolveMapPath(String[] args) {
        String mapPathStr = getRequiredOptionValue(args, "--map");
        Path mapPath = Paths.get(mapPathStr);
        if (!Files.exists(mapPath)) {
            throw new IllegalArgumentException("Le fichier image cartographique n'existe pas : " + mapPath);
        }
        return mapPath;
    }

    /**
     * Résout et valide le chemin du fichier JSON de géométrie.
     */
    private static Path resolveJsonPath(String[] args) {
        String jsonPathStr = getRequiredOptionValue(args, "--json");
        Path jsonPath = Paths.get(jsonPathStr);
        if (!Files.exists(jsonPath)) {
            throw new IllegalArgumentException("Le fichier JSON de géométrie n'existe pas : " + jsonPath);
        }
        return jsonPath;
    }

    /**
     * Résout et prépare le masque routier à partir des options CLI, d'OpenStreetMap ou du fallback.
     *
     * @param args           Arguments CLI.
     * @param mapPath        Chemin de l'image source cartographique.
     * @param jsonPath       Chemin du fichier JSON de cadrage du territoire.
     * @param mapContext     Contexte géométrique et projection cartographique.
     * @param expectedWidth  Largeur attendue en pixels.
     * @param expectedHeight Hauteur attendue en pixels.
     * @return Masque binaire des axes routiers aux dimensions de la carte.
     * @throws IOException              en cas d'erreur d'entrée/sortie.
     * @throws IllegalArgumentException si aucun masque valide n'est trouvé ou si les dimensions sont divergentes.
     */
    private static BinaryMask resolveRoadMask(
            String[] args,
            Path mapPath,
            Path jsonPath,
            MapContext mapContext,
            int expectedWidth,
            int expectedHeight
    ) throws IOException {
        BinaryMask explicitMask = resolveExplicitRoadMask(args, expectedWidth, expectedHeight);
        if (explicitMask != null) {
            return explicitMask;
        }

        BinaryMask osmMask = resolveOsmRoadMask(args, mapPath, jsonPath, mapContext);
        if (osmMask != null) {
            return osmMask;
        }

        BinaryMask fallbackMask = resolveFallbackRoadMask(expectedWidth, expectedHeight);
        if (fallbackMask != null) {
            return fallbackMask;
        }

        throw new IllegalArgumentException(String.format(
                "Aucun masque routier compatible trouvé pour les dimensions %dx%d. "
                        + "Veuillez spécifier un masque bitmap via --road, ou fournir un fichier osm_roads.json via --osm-roads "
                        + "ou dans le répertoire de la carte.",
                expectedWidth, expectedHeight
        ));
    }

    /**
     * Tente de charger le masque routier bitmap explicitement passé en argument via --road.
     */
    private static BinaryMask resolveExplicitRoadMask(String[] args, int expectedWidth, int expectedHeight) throws IOException {
        String roadPathStr = getOptionValue(args, "--road", null);
        if (roadPathStr == null) {
            return null;
        }

        Path roadPath = Paths.get(roadPathStr);
        if (!Files.exists(roadPath)) {
            throw new IllegalArgumentException("Le masque routier spécifié n'existe pas : " + roadPath);
        }

        System.out.println("-> [V2] Chargement du masque routier : " + roadPath);
        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        if (roadImg == null) {
            throw new IllegalArgumentException("Impossible de décoder l'image du masque routier : " + roadPath);
        }

        if (roadImg.getWidth() != expectedWidth || roadImg.getHeight() != expectedHeight) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions incompatibles pour le masque routier fourni (%dx%d vs %dx%d attendu pour la carte).",
                    roadImg.getWidth(), roadImg.getHeight(), expectedWidth, expectedHeight
            ));
        }

        return BinaryMask.fromImage(roadImg, 128);
    }

    /**
     * Tente de détecter et rasteriser les données OpenStreetMap depuis --osm-roads ou un fichier adjacent.
     */
    private static BinaryMask resolveOsmRoadMask(
            String[] args,
            Path mapPath,
            Path jsonPath,
            MapContext mapContext
    ) throws IOException {
        Path osmPath = resolveOsmRoadsPath(args, mapPath, jsonPath);
        if (osmPath == null) {
            return null;
        }

        System.out.println("-> [V2] Rasterisation automatique des axes routiers OSM : " + osmPath.toAbsolutePath());
        RoadDetectorOsm detector = new RoadDetectorOsm();
        BinaryMask osmMask = detector.detect(osmPath, mapContext);
        System.out.printf("   %d pixels routiers rasterisés depuis OpenStreetMap.%n", osmMask.countActivePixels());
        return osmMask;
    }

    /**
     * Résout l'emplacement du fichier osm_roads.json.
     */
    private static Path resolveOsmRoadsPath(String[] args, Path mapPath, Path jsonPath) {
        String osmRoadsOption = getOptionValue(args, "--osm-roads", null);
        if (osmRoadsOption != null) {
            Path explicitPath = Paths.get(osmRoadsOption);
            if (!Files.exists(explicitPath)) {
                throw new IllegalArgumentException("Le fichier OSM spécifié n'existe pas : " + explicitPath);
            }
            return explicitPath;
        }

        Path candidateMap = mapPath.getParent() != null ? mapPath.getParent().resolve("osm_roads.json") : null;
        if (candidateMap != null && Files.exists(candidateMap)) {
            return candidateMap;
        }

        Path candidateJson = jsonPath.getParent() != null ? jsonPath.getParent().resolve("osm_roads.json") : null;
        if (candidateJson != null && Files.exists(candidateJson)) {
            return candidateJson;
        }

        return null;
    }

    /**
     * Tente d'utiliser le masque routier par défaut maps/road.png s'il correspond aux dimensions attendues.
     */
    private static BinaryMask resolveFallbackRoadMask(int expectedWidth, int expectedHeight) throws IOException {
        Path defaultRoad = Paths.get("maps/road.png");
        if (!Files.exists(defaultRoad)) {
            return null;
        }

        BufferedImage roadImg = ImageIO.read(defaultRoad.toFile());
        if (roadImg != null && roadImg.getWidth() == expectedWidth && roadImg.getHeight() == expectedHeight) {
            System.out.println("-> [V2] Utilisation du masque routier par défaut : " + defaultRoad);
            return BinaryMask.fromImage(roadImg, 128);
        }

        return null;
    }

    /**
     * Parse et assemble la configuration V2 à partir des options fournies.
     */
    private static V2Config parseConfig(String[] args) {
        V2Config def = V2Config.defaultConfig();
        int ss = getIntOption(args, "--ss", def.supersamplingFactor());
        int cropMargin = getIntOption(args, "--crop-margin", def.cropMargin());
        double eps = getDoubleOption(args, "--eps", def.eps());
        int rho = getIntOption(args, "--rho", def.rho());
        double sig = getDoubleOption(args, "--sig", def.sig());
        double cornerAngle = getDoubleOption(args, "--corner-angle", def.cornerAngle());
        String modeStr = getOptionValue(args, "--mode", "territory");
        OperationMode mode = OperationMode.fromString(modeStr);

        return new V2Config(
                def.hi(), def.lo(), cropMargin, eps, rho, def.residualHoleMaxArea(),
                mode, sig, cornerAngle, def.cornerWindowL(), def.r0(), def.r1(),
                def.roundaboutRadiusZr(), def.straightFactor(), def.straightScale(),
                def.straightThreshold(), def.straightTransitionK(), def.gaussianBlurSigma(),
                def.contrastStiffness(), def.roundaboutBufferInner(), def.roundaboutBufferOuter(),
                def.roadHoleMaxArea(), ss
        );
    }

    /**
     * Sauvegarde les images produites selon les chemins configurés.
     */
    private static void saveOutputs(RenderResult result, String[] args, Path sourceMapPath) throws IOException {
        String outDirStr = getOptionValue(args, "--out-dir", null);
        String clippedOutStr = getOptionValue(args, "--output", null);
        String maskOutStr = getOptionValue(args, "--mask-out", null);
        String overlayOutStr = getOptionValue(args, "--overlay-out", null);

        Path outDir = outDirStr != null ? Paths.get(outDirStr) : sourceMapPath.getParent();
        if (outDir == null) {
            outDir = Paths.get(".");
        }
        if (!Files.exists(outDir)) {
            Files.createDirectories(outDir);
        }

        Path clippedPath = clippedOutStr != null ? Paths.get(clippedOutStr) : outDir.resolve("clipped.png");
        Path maskPath = maskOutStr != null ? Paths.get(maskOutStr) : outDir.resolve("mask.png");
        Path overlayPath = overlayOutStr != null ? Paths.get(overlayOutStr) : outDir.resolve("overlay.png");

        System.out.println("-> [V2] Enregistrement des livrables graphiques...");
        ImageExporter.savePng(result.clipped(), clippedPath);
        System.out.println("   [Livrable] Image détourée ARGB : " + clippedPath.toAbsolutePath());
        ImageExporter.savePng(result.mask(), maskPath);
        System.out.println("   [Livrable] Masque monochrome 8-bit : " + maskPath.toAbsolutePath());
        ImageExporter.savePng(result.overlay(), overlayPath);
        System.out.println("   [Livrable] Image de contrôle overlay : " + overlayPath.toAbsolutePath());
    }

    /**
     * Affiche le manuel d'aide utilisateur.
     */
    private static void printHelp(PrintStream out) {
        out.println("Usage: java -jar photoshop-1.0.jar --v2 [options]");
        out.println("Pipeline V2 de détourage cartographique sub-pixel haute-fidélité.");
        out.println();
        out.println("Options obligatoires :");
        out.println("  --map <chemin>         Image cartographique source (ex: 05_style_contraste_sans_rien.png)");
        out.println("  --json <chemin>        Fichier JSON contenant les polygones et coordonnées cartographiques");
        out.println();
        out.println("Options optionnelles :");
        out.println("  --road <chemin>        Image du masque routier (défaut : maps/road.png si dimensions conformes)");
        out.println("  --osm-roads <chemin>   Fichier JSON OpenStreetMap (défaut : osm_roads.json adjacent)");
        out.println("  --out-dir <dossier>    Dossier de destination pour clipped.png, mask.png, overlay.png");
        out.println("  --output <chemin>      Chemin explicite pour l'image détourée clipped.png");
        out.println("  --mask-out <chemin>    Chemin explicite pour le masque monochrome mask.png");
        out.println("  --overlay-out <chemin> Chemin explicite pour l'image de contrôle overlay.png");
        out.println("  --ss <int>             Facteur de supersampling vectoriel (défaut : 4)");
        out.println("  --mode <nom>           Mode d'opération : territory, zone (défaut : territory)");
        out.println("  --crop-margin <int>    Marge de sécurité de la boîte englobante en pixels (défaut : 90)");
        out.println("  --eps <double>         Marge de sécurité d'expansion géodésique (défaut : 4.0)");
        out.println("  --help, -h             Affiche ce message d'aide.");
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

    private static String getRequiredOptionValue(String[] args, String option) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            throw new IllegalArgumentException("Option obligatoire manquante : " + option);
        }
        return val;
    }

    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    return args[i + 1];
                }
                throw new IllegalArgumentException("Valeur manquante pour l'option : " + option);
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
            throw new IllegalArgumentException("Valeur entière invalide pour " + option + " : " + val);
        }
    }

    private static double getDoubleOption(String[] args, String option, double defaultValue) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur décimale invalide pour " + option + " : " + val);
        }
    }
}
