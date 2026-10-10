package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.pipeline.V2Config;
import com.sam102022.photoshop.v2.pipeline.V2ZonePipeline;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.zone.ZoneColor;
import com.sam102022.photoshop.v2.zone.ZoneExtractor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Mode « zones » de la ligne de commande V2 : détourage, sur le bord intérieur des routes, de chaque zone
 * tracée en couleur sur le plan des zones ({@code --zones 02_plan_avec_zones.png}).
 * <p>
 * Pour chaque zone présente, deux fichiers sont produits dans le dossier de sortie :
 * {@code zoneN_<couleur>_mask.png} (masque 8 bits) et {@code zoneN_<couleur>_rendu.png} (carte détourée ARGB).
 */
final class V2ZoneCliRunner {

    /** Option activant le mode zones. */
    static final String ZONES_OPTION = "--zones";
    /**
     * Tolérance par défaut (px) du polygone à grands segments en mode zones : les bords intérieurs des routes
     * sont interrompus par les débouchés de rues et entrées charretières, qui feraient onduler un polygone serré ;
     * les giratoires restent suivis exactement (découpe lue dans la capture « Routes seules »).
     */
    static final double DEFAULT_ZONE_POLYGON_TOLERANCE = 5.0;
    /** Plan des zones recherché à côté de la carte avec {@code --mode zone}. */
    static final String DEFAULT_ZONES_FILE = "02_plan_avec_zones.png";

    private V2ZoneCliRunner() {
    }

    /**
     * Exécute le détourage des zones.
     *
     * @param args Arguments CLI.
     * @return 0 si au moins une zone a été détourée, 1 sinon.
     * @throws IOException en cas d'erreur de lecture ou d'écriture.
     */
    static int execute(String[] args) throws IOException {
        Path mapPath = V2CliRunner.resolveMapPath(args);
        Path zonesPath = resolveZonesPath(args, mapPath);
        if (!Files.exists(zonesPath)) {
            throw new IllegalArgumentException("Le plan des zones n'existe pas : " + zonesPath);
        }
        System.out.println("-> [V2 zones] Chargement des données sources...");
        BufferedImage mapImg = ImageLoader.load(mapPath);
        BufferedImage zonesImg = ImageLoader.load(zonesPath);
        if (zonesImg.getWidth() != mapImg.getWidth() || zonesImg.getHeight() != mapImg.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions incompatibles entre la carte (%dx%d) et le plan des zones (%dx%d).",
                    mapImg.getWidth(), mapImg.getHeight(), zonesImg.getWidth(), zonesImg.getHeight()));
        }
        GoogleRoadsImage googleRoads = GoogleRoadsResolver.resolve(args, mapImg, mapPath);
        BinaryMask roadMask = googleRoads != null && !GoogleRoadsResolver.otherRoadSourceRequested(args)
                ? googleRoads.toMask()
                : resolveRoadMask(args, mapImg, mapPath);
        V2Config config = V2CliRunner.parseConfig(args);
        Path outDir = resolveOutDir(args, mapPath);

        ZoneExtractor extractor = new ZoneExtractor();
        V2ZonePipeline pipeline = new V2ZonePipeline();
        int done = 0;
        for (ZoneColor zone : selectedZones(args)) {
            List<BinaryMask> parts = extractor.extract(zonesImg, zone);
            if (parts.isEmpty()) {
                System.out.println("   [Zone " + zone.number() + "] " + zone.name().toLowerCase(Locale.ROOT) + " : absente du plan.");
                continue;
            }
            RenderResult result = pipeline.execute(mapImg, parts, roadMask, config, googleRoads);
            Path maskPath = outDir.resolve(zone.fileLabel() + "_mask.png");
            Path renderPath = outDir.resolve(zone.fileLabel() + "_rendu.png");
            ImageExporter.savePng(result.mask(), maskPath);
            ImageExporter.savePng(result.clipped(), renderPath);
            System.out.println("   [Zone " + zone.number() + "] " + maskPath.toAbsolutePath());
            System.out.println("   [Zone " + zone.number() + "] " + renderPath.toAbsolutePath());
            done++;
        }
        if (done == 0) {
            System.err.println("Aucune zone trouvée sur le plan des zones : " + zonesPath);
            return 1;
        }
        System.out.println("Succès ! " + done + " zone(s) détourée(s).");
        return 0;
    }

    /**
     * Indique si la commande demande le détourage des zones : option {@code --zones}, ou {@code --mode zone}
     * (le plan des zones est alors pris à côté de la carte).
     *
     * @param args Arguments CLI.
     * @return true pour le mode zones.
     */
    static boolean isZoneRequest(String[] args) {
        return V2CliRunner.hasOption(args, ZONES_OPTION)
                || "zone".equalsIgnoreCase(V2CliRunner.getOptionValue(args, "--mode", "").trim());
    }

    /**
     * Chemin du plan des zones : {@code --zones}, sinon {@value #DEFAULT_ZONES_FILE} dans le dossier de la carte.
     *
     * @param args    Arguments CLI.
     * @param mapPath Chemin de la carte.
     * @return Chemin du plan des zones.
     */
    static Path resolveZonesPath(String[] args, Path mapPath) {
        String explicit = V2CliRunner.getOptionValue(args, ZONES_OPTION, null);
        return explicit != null ? Paths.get(explicit) : mapPath.toAbsolutePath().resolveSibling(DEFAULT_ZONES_FILE);
    }

    /**
     * Masque routier : {@code --road} explicite, sinon source OSM (nécessite {@code --json}), sinon détection
     * colorimétrique sur la carte.
     *
     * @param args    Arguments CLI.
     * @param mapImg  Carte source.
     * @param mapPath Chemin de la carte.
     * @return Masque routier pleine image.
     * @throws IOException en cas d'erreur de lecture.
     */
    private static BinaryMask resolveRoadMask(String[] args, BufferedImage mapImg, Path mapPath) throws IOException {
        String json = V2CliRunner.getOptionValue(args, "--json", null);
        if (json != null) {
            Path jsonPath = Paths.get(json);
            if (!Files.exists(jsonPath)) {
                throw new IllegalArgumentException("Le fichier JSON de géométrie n'existe pas : " + jsonPath);
            }
            MapContext ctx = new JsonTerritoryLoader().load(jsonPath).mapContext();
            if (ctx.width() != mapImg.getWidth() || ctx.height() != mapImg.getHeight()) {
                ctx = new MapContext(mapImg.getWidth(), mapImg.getHeight(), ctx.zoom(), ctx.center());
            }
            return V2CliRunner.resolveRoadMask(args, mapImg, mapPath, jsonPath, ctx);
        }
        BinaryMask explicit = V2CliRunner.resolveExplicitRoadMask(args, mapImg.getWidth(), mapImg.getHeight());
        if (explicit != null) {
            return explicit;
        }
        if (V2CliRunner.ROAD_SOURCE_OSM.equals(V2CliRunner.resolveRoadSource(args))) {
            throw new IllegalArgumentException("La source routière OSM nécessite --json (cadrage géographique de la carte).");
        }
        return V2CliRunner.detectRoadMaskFromStyle(mapImg);
    }

    /**
     * Zones demandées par {@code --zone} (liste séparée par des virgules), ou toutes.
     *
     * @param args Arguments CLI.
     * @return Zones à traiter.
     */
    static List<ZoneColor> selectedZones(String[] args) {
        String value = V2CliRunner.getOptionValue(args, "--zone", null);
        if (value == null) {
            return Arrays.asList(ZoneColor.values());
        }
        List<ZoneColor> zones = new ArrayList<>();
        for (String token : value.split(",")) {
            if (!token.isBlank()) {
                zones.add(ZoneColor.parse(token));
            }
        }
        return zones;
    }

    /**
     * Dossier de sortie ({@code --out-dir}, sinon dossier de la carte), créé si nécessaire.
     *
     * @param args    Arguments CLI.
     * @param mapPath Chemin de la carte.
     * @return Dossier de sortie.
     * @throws IOException si le dossier ne peut être créé.
     */
    private static Path resolveOutDir(String[] args, Path mapPath) throws IOException {
        String outDirStr = V2CliRunner.getOptionValue(args, "--out-dir", null);
        Path outDir = outDirStr != null ? Paths.get(outDirStr) : mapPath.toAbsolutePath().getParent();
        if (outDir == null) {
            outDir = Paths.get(".");
        }
        Files.createDirectories(outDir);
        return outDir;
    }
}
