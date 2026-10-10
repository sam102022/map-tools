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
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.road.RoadDetectorOsm;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;
import com.sam102022.photoshop.v2.road.RoadMaskCleaner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Point d'entrée d'exécution en ligne de commande pour le pipeline V2 haute-fidélité (Sprint 9).
 */
public final class V2CliRunner {

    /**
     * Source routière par défaut : détection colorimétrique sur la carte.
     */
    private static final String ROAD_SOURCE_STYLE = "style";

    /** Fichier de géométrie recherché à côté de la carte lorsque {@code --json} est absent. */
    static final String DEFAULT_JSON_FILE = "01_plan_avec_territoires.json";

    /**
     * Source routière alternative : rasterisation des axes OpenStreetMap.
     */
    static final String ROAD_SOURCE_OSM = "osm";

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
        if (V2ZoneCliRunner.isZoneRequest(args)) {
            return V2ZoneCliRunner.execute(args);
        }
        Path mapPath = resolveMapPath(args);
        Path jsonPath = resolveJsonPath(args, mapPath);

        System.out.println("-> [V2] Chargement des données sources...");
        BufferedImage mapImg = ImageLoader.load(mapPath);

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        MapContext mapContext = loaded.mapContext();
        if (mapContext.width() != mapImg.getWidth() || mapContext.height() != mapImg.getHeight()) {
            mapContext = new MapContext(mapImg.getWidth(), mapImg.getHeight(), mapContext.zoom(), mapContext.center());
        }

        GoogleRoadsImage googleRoads = GoogleRoadsResolver.resolve(args, mapImg, mapPath);
        BinaryMask roadMask = googleRoads != null && !GoogleRoadsResolver.otherRoadSourceRequested(args)
                ? googleRoads.toMask()
                : resolveRoadMask(args, mapImg, mapPath, jsonPath, mapContext);

        V2Config config = parseConfig(args);
        V2Pipeline pipeline = new V2Pipeline();
        RenderResult result = pipeline.execute(mapImg, loaded.geometry(), mapContext, roadMask, config, googleRoads);

        saveOutputs(result, args, mapPath);
        System.out.println("Succès ! Pipeline V2 achevé avec succès.");
        return 0;
    }

    /**
     * Résout et valide le chemin de l'image cartographique source.
     */
    static Path resolveMapPath(String[] args) {
        String mapPathStr = getRequiredOptionValue(args, "--map");
        Path mapPath = Paths.get(mapPathStr);
        if (!Files.exists(mapPath)) {
            throw new IllegalArgumentException("Le fichier image cartographique n'existe pas : " + mapPath);
        }
        return mapPath;
    }

    /**
     * Résout et valide le chemin du fichier JSON de géométrie : {@code --json}, sinon
     * {@value #DEFAULT_JSON_FILE} dans le dossier de la carte.
     *
     * @param args    Arguments CLI.
     * @param mapPath Chemin de la carte.
     * @return Chemin du fichier JSON.
     * @throws IllegalArgumentException si le fichier est introuvable.
     */
    static Path resolveJsonPath(String[] args, Path mapPath) {
        String jsonPathStr = getOptionValue(args, "--json", null);
        Path jsonPath = jsonPathStr != null ? Paths.get(jsonPathStr)
                : mapPath.toAbsolutePath().resolveSibling(DEFAULT_JSON_FILE);
        if (!Files.exists(jsonPath)) {
            throw new IllegalArgumentException(jsonPathStr != null
                    ? "Le fichier JSON de géométrie n'existe pas : " + jsonPath
                    : "Fichier JSON de géométrie introuvable à côté de la carte (" + jsonPath
                    + ") : précisez-le avec --json <chemin>.");
        }
        return jsonPath;
    }

    /**
     * Résout et prépare le masque routier selon la source demandée.
     * <p>
     * Ordre de priorité :
     * <ol>
     *   <li>masque bitmap explicite fourni via {@code --road} ;</li>
     *   <li>rasterisation OpenStreetMap si {@code --road-source osm} ou {@code --osm-roads} est fourni ;</li>
     *   <li>par défaut, détection colorimétrique sur la carte elle-même ({@link RoadDetectorStyle}) suivie du
     *       nettoyage morphologique ({@link RoadMaskCleaner}), conformément à l'étalon Python
     *       {@code snap_cells_prototype_v7.py}.</li>
     * </ol>
     *
     * @param args       Arguments CLI.
     * @param mapImg     Image cartographique source.
     * @param mapPath    Chemin de l'image source cartographique.
     * @param jsonPath   Chemin du fichier JSON de cadrage du territoire.
     * @param mapContext Contexte géométrique et projection cartographique.
     * @return Masque binaire des axes routiers aux dimensions de la carte.
     * @throws IOException              en cas d'erreur d'entrée/sortie.
     * @throws IllegalArgumentException si la source demandée est invalide ou introuvable, ou si les dimensions divergent.
     */
    static BinaryMask resolveRoadMask(
            String[] args,
            BufferedImage mapImg,
            Path mapPath,
            Path jsonPath,
            MapContext mapContext
    ) throws IOException {
        BinaryMask explicitMask = resolveExplicitRoadMask(args, mapImg.getWidth(), mapImg.getHeight());
        if (explicitMask != null) {
            return explicitMask;
        }

        String source = resolveRoadSource(args);
        if (ROAD_SOURCE_OSM.equals(source)) {
            BinaryMask osmMask = resolveOsmRoadMask(args, mapPath, jsonPath, mapContext);
            if (osmMask == null) {
                throw new IllegalArgumentException(
                        "Source routière OSM demandée mais aucun fichier osm_roads.json trouvé "
                                + "(spécifiez --osm-roads <chemin> ou placez osm_roads.json à côté de la carte).");
            }
            return osmMask;
        }

        return detectRoadMaskFromStyle(mapImg);
    }

    /**
     * Détermine la source du masque routier ({@code style} par défaut, {@code osm} si demandé).
     *
     * @param args Arguments CLI.
     * @return {@link #ROAD_SOURCE_STYLE} ou {@link #ROAD_SOURCE_OSM}.
     * @throws IllegalArgumentException si la valeur de {@code --road-source} est inconnue.
     */
    static String resolveRoadSource(String[] args) {
        String explicitSource = getOptionValue(args, "--road-source", null);
        if (explicitSource == null) {
            return getOptionValue(args, "--osm-roads", null) != null ? ROAD_SOURCE_OSM : ROAD_SOURCE_STYLE;
        }
        String normalized = explicitSource.trim().toLowerCase(Locale.ROOT);
        if (!ROAD_SOURCE_STYLE.equals(normalized) && !ROAD_SOURCE_OSM.equals(normalized)) {
            throw new IllegalArgumentException("Valeur inconnue pour --road-source : " + explicitSource
                    + " (valeurs admises : style, osm).");
        }
        return normalized;
    }

    /**
     * Détecte les routes par colorimétrie sur la carte contrastée puis applique le nettoyage morphologique
     * (fermeture en croix puis ouverture 2x2), à l'identique de l'étalon Python.
     *
     * @param mapImg Image cartographique source.
     * @return Masque routier nettoyé aux dimensions de la carte.
     */
    static BinaryMask detectRoadMaskFromStyle(BufferedImage mapImg) {
        System.out.println("-> [V2] Détection colorimétrique des routes sur la carte (RoadDetectorStyle + RoadMaskCleaner)...");
        BinaryMask raw = new RoadDetectorStyle().detect(mapImg);
        BinaryMask cleaned = new RoadMaskCleaner().clean(raw).closed();
        System.out.printf("   %d pixels routiers détectés par colorimétrie.%n", cleaned.countActivePixels());
        return cleaned;
    }

    /**
     * Tente de charger le masque routier bitmap explicitement passé en argument via --road.
     */
    static BinaryMask resolveExplicitRoadMask(String[] args, int expectedWidth, int expectedHeight) throws IOException {
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
     * Rasterise les données OpenStreetMap depuis --osm-roads ou un fichier osm_roads.json adjacent.
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

        System.out.println("-> [V2] Rasterisation des axes routiers OSM : " + osmPath.toAbsolutePath());
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
     * Parse et assemble la configuration V2 à partir des options fournies.
     */
    static V2Config parseConfig(String[] args) {
        V2Config def = V2Config.defaultConfig();
        int ss = getIntOption(args, "--ss", def.supersamplingFactor());
        int cropMargin = getIntOption(args, "--crop-margin", def.cropMargin());
        double eps = getDoubleOption(args, "--eps", def.eps());
        int rho = getIntOption(args, "--rho", def.rho());
        double sig = getDoubleOption(args, "--sig", def.sig());
        double cornerAngle = getDoubleOption(args, "--corner-angle", def.cornerAngle());
        double minPartialThickness = getDoubleOption(args, "--min-partial-thickness", def.minPartialThickness());
        double polygonTolerance = getDoubleOption(args, "--polygon-tolerance", V2ZoneCliRunner.isZoneRequest(args)
                ? V2ZoneCliRunner.DEFAULT_ZONE_POLYGON_TOLERANCE : def.polygonTolerance());
        int pocketRadius = getIntOption(args, "--pocket-radius", def.boulevardPocketRadius());
        String modeStr = getOptionValue(args, "--mode", "territory");
        OperationMode mode = OperationMode.fromString(modeStr);

        return new V2Config(
                def.hi(), def.lo(), cropMargin, eps, rho, def.residualHoleMaxArea(),
                mode, sig, cornerAngle, def.cornerWindowL(), def.r0(), def.r1(),
                def.roundaboutRadiusZr(), def.straightFactor(), def.straightScale(),
                def.straightThreshold(), def.straightTransitionK(), def.gaussianBlurSigma(),
                def.contrastStiffness(), def.roundaboutBufferInner(), def.roundaboutBufferOuter(),
                def.roadHoleMaxArea(), ss, minPartialThickness, !hasOption(args, "--no-road-snap"), polygonTolerance,
                pocketRadius
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
        out.println("Option obligatoire :");
        out.println("  --map <chemin>         Image cartographique source (ex: 05_style_contraste_sans_rien.png)");
        out.println();
        out.println("Options facultatives principales :");
        out.println("  --json <chemin>        Fichier JSON contenant les polygones et coordonnées cartographiques");
        out.println("                         (défaut : 01_plan_avec_territoires.json à côté de la carte)");
        out.println();
        out.println("Options optionnelles :");
        out.println("  --google-roads <png>   Capture Google « Routes seules » (défaut : 06_routes_seules.png à côté de la carte,");
        out.println("                         utilisée si elle existe pour les routes et le bord des chaussées)");
        out.println("  --no-google-roads      Ignore la capture « Routes seules » (détection par la couleur de la carte)");
        out.println("  --road <chemin>        Image du masque routier (prioritaire sur --road-source)");
        out.println("  --road-source <nom>    Source du masque routier : style, osm (défaut : capture Google si présente, sinon style)");
        out.println("  --osm-roads <chemin>   Fichier JSON OpenStreetMap (implique --road-source osm ; défaut : osm_roads.json adjacent)");
        out.println("  --out-dir <dossier>    Dossier de destination pour clipped.png, mask.png, overlay.png");
        out.println("  --output <chemin>      Chemin explicite pour l'image détourée clipped.png");
        out.println("  --mask-out <chemin>    Chemin explicite pour le masque monochrome mask.png");
        out.println("  --overlay-out <chemin> Chemin explicite pour l'image de contrôle overlay.png");
        out.println("  --ss <int>             Facteur de supersampling vectoriel (défaut : 4)");
        out.println("  --mode <nom>           Mode d'opération : territory, zone (défaut : territory)");
        out.println("  --crop-margin <int>    Marge de sécurité de la boîte englobante en pixels (défaut : 90)");
        out.println("  --eps <double>         Marge de sécurité d'expansion géodésique (défaut : 4.0)");
        out.println("  --min-partial-thickness <px>  Épaisseur minimale des lamelles de cellules partielles conservées");
        out.println("                         (défaut : 25 ; 0 = comportement de l'étalon Python v7)");
        out.println("  --no-road-snap         Désactive l'accrochage du contour sur le bord vectoriel des routes");
        out.println("  --polygon-tolerance <px>  Écart max. du polygone à grands segments (défaut : 1.0, zones : 5.0 ; 0 = désactivé)");
        out.println("  --pocket-radius <px>   Comble bretelles et îlots le long des boulevards (défaut : 25 ; 0 = désactivé)");
        out.println();
        out.println("Mode zones (détourage sur le bord intérieur des routes, --json facultatif) :");
        out.println("  --zones <chemin>       Plan des zones (02_plan_avec_zones.png) : une zone par couleur de trait");
        out.println("                         (1 rouge, 2 verte, 3 mauve, 4 orange, 5 cyan, 6 noir) ; produit pour chaque");
        out.println("                         zone présente zoneN_<couleur>_mask.png et zoneN_<couleur>_rendu.png");
        out.println("                         (--mode zone sans --zones : 02_plan_avec_zones.png à côté de la carte)");
        out.println("  --zone <liste>         Zones à traiter, ex. 1,3 ou rouge,mauve (défaut : toutes)");
        out.println("  --help, -h             Affiche ce message d'aide.");
    }

    static boolean hasOption(String[] args, String... options) {
        for (String arg : args) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    static String getRequiredOptionValue(String[] args, String option) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            throw new IllegalArgumentException("Option obligatoire manquante : " + option);
        }
        return val;
    }

    static String getOptionValue(String[] args, String option, String defaultValue) {
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
