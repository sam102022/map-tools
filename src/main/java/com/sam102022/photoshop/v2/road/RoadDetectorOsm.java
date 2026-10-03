package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Détecteur et rasteriseur vectoriel de chaussées OpenStreetMap.
 * Parse un export osm_roads.json, projette les géométries via WebMercatorProjection,
 * et dessine la surface de voirie selon la typologie routière et le nombre de voies.
 */
public final class RoadDetectorOsm {

    private static final double EARTH_CIRCUMFERENCE_METERS = 40_075_016.686;
    private static final List<String> INCLUDED_HIGHWAYS = List.of(
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
            "residential", "living_street", "road", "service", "busway"
    );

    /**
     * Constructeur par défaut.
     */
    public RoadDetectorOsm() {
    }

    /**
     * Rasterise le fichier OSM fourni dans le repère de la carte défini par MapContext.
     *
     * @param osmJson Fichier JSON contenant les tronçons routiers.
     * @param context Contexte géométrique et dimensionnel de la carte.
     * @return Masque binaire des routes rasterisées.
     * @throws IOException En cas d'erreur de lecture du fichier.
     * @throws IllegalArgumentException si un paramètre est null.
     */
    public BinaryMask detect(Path osmJson, MapContext context) throws IOException {
        if (osmJson == null) {
            throw new IllegalArgumentException("Le chemin du fichier OSM ne peut pas être null.");
        }
        if (context == null) {
            throw new IllegalArgumentException("Le contexte cartographique ne peut pas être null.");
        }
        if (!Files.exists(osmJson)) {
            throw new IOException("Fichier OSM introuvable : " + osmJson);
        }

        String content = Files.readString(osmJson, StandardCharsets.UTF_8);
        OsmData data = parseOsmData(content);

        WebMercatorProjection projection = new WebMercatorProjection(context);
        double worldSize = 256.0 * Math.pow(2.0, context.zoom());
        double metersPerPixel = Math.cos(Math.toRadians(context.center().latitude()))
                * EARTH_CIRCUMFERENCE_METERS / worldSize;

        BufferedImage canvas = new BufferedImage(context.width(), context.height(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            for (OsmWay way : data.ways()) {
                drawWay(g2d, way, projection, metersPerPixel);
            }
        } finally {
            g2d.dispose();
        }

        return BinaryMask.fromImage(canvas, 1);
    }

    /**
     * Dessine un tronçon routier sur le contexte graphique s'il est éligible.
     *
     * @param g2d Contexte graphique de rendu.
     * @param way Données du tronçon routier.
     * @param projection Moteur de projection géographique vers pixels.
     * @param metersPerPixel Échelle de résolution spatiale en mètres par pixel.
     */
    private void drawWay(Graphics2D g2d, OsmWay way, WebMercatorProjection projection, double metersPerPixel) {
        if (!INCLUDED_HIGHWAYS.contains(way.highway()) || isMinorService(way.highway(), way.service())) {
            return;
        }
        if (way.coordinates().size() < 2) {
            return;
        }

        Path2D path = buildWayPath(way, projection);
        float strokeWidth = (float) Math.max(1.0, computeRoadWidthMeters(way) / metersPerPixel);
        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(path);
    }

    /**
     * Construit le tracé géométrique en pixels pour un tronçon routier.
     *
     * @param way Données du tronçon routier.
     * @param projection Moteur de projection géographique vers pixels.
     * @return Chemin 2D polygonal correspondant au tracé de la route.
     */
    private Path2D buildWayPath(OsmWay way, WebMercatorProjection projection) {
        Path2D path = new Path2D.Double();
        boolean first = true;
        for (GeoCoordinate coord : way.coordinates()) {
            PixelPoint pt = projection.toPixel(coord);
            if (first) {
                path.moveTo(pt.x(), pt.y());
                first = false;
            } else {
                path.lineTo(pt.x(), pt.y());
            }
        }
        return path;
    }

    /**
     * Détermine si une voie de service est mineure (ex: allée privée, accès d'urgence).
     *
     * @param highway Type de voirie.
     * @param service Type de service associé.
     * @return true si la voie est une desserte mineure à ignorer.
     */
    private static boolean isMinorService(String highway, String service) {
        if (!"service".equals(highway)) {
            return false;
        }
        return "driveway".equals(service) || "parking_aisle".equals(service)
                || "emergency_access".equals(service);
    }

    /**
     * Calcule la largeur de la chaussée en mètres selon la typologie et le nombre de voies.
     *
     * @param way Données du tronçon routier.
     * @return Largeur estimée de la route en mètres.
     */
    private static double computeRoadWidthMeters(OsmWay way) {
        if (way.widthMeters() > 0) {
            return way.widthMeters();
        }
        double lanes = way.lanes() > 0 ? way.lanes() : defaultLanes(way.highway());
        double metersPerLane = switch (way.highway()) {
            case "motorway", "trunk" -> 3.6;
            case "primary", "secondary" -> 3.3;
            default -> 3.0;
        };
        return Math.max(3.0, lanes * metersPerLane + 1.0);
    }

    /**
     * Fournit le nombre de voies par défaut pour une catégorie de voirie donnée.
     *
     * @param highway Type de voirie.
     * @return Nombre de voies estimé par défaut.
     */
    private static double defaultLanes(String highway) {
        return switch (highway) {
            case "motorway", "trunk", "primary", "secondary" -> 2.0;
            default -> 1.0;
        };
    }

    /**
     * Représentation interne d'un tronçon routier OpenStreetMap.
     *
     * @param highway Type de route selon la classification OSM.
     * @param service Sous-type de service pour les voies de desserte.
     * @param lanes Nombre de voies de circulation.
     * @param widthMeters Largeur explicite de la voie en mètres si renseignée.
     * @param coordinates Liste ordonnée des coordonnées géographiques GPS formant le tracé.
     */
    private record OsmWay(String highway, String service, double lanes, double widthMeters, List<GeoCoordinate> coordinates) {}

    /**
     * Conteneur des données de voirie extraites du fichier OSM.
     *
     * @param ways Liste des tronçons routiers.
     */
    private record OsmData(List<OsmWay> ways) {}

    /**
     * Analyseur JSON autonome et léger pour extraire les routes OSM.
     *
     * @param json Contenu brut du document JSON.
     * @return Données OSM structurées.
     * @throws IOException en cas d'erreur de parsing ou structure invalide.
     */
    private static OsmData parseOsmData(String json) throws IOException {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object rootObj = parser.parse();
        if (!(rootObj instanceof Map<?, ?> root)) {
            throw new IOException("Structure JSON racine invalide");
        }

        List<OsmWay> ways = new ArrayList<>();
        Object roadsObj = root.get("roads");
        if (roadsObj instanceof List<?> roadsList) {
            for (Object rObj : roadsList) {
                if (rObj instanceof Map<?, ?> roadMap) {
                    ways.add(parseWay(roadMap));
                }
            }
        }
        return new OsmData(ways);
    }

    /**
     * Analyse un objet JSON représentant un tronçon routier.
     *
     * @param roadMap Dictionnaire représentant le tronçon de route.
     * @return Instance d'OsmWay instanciée.
     */
    private static OsmWay parseWay(Map<?, ?> roadMap) {
        Map<?, ?> tags = roadMap.get("tags") instanceof Map<?, ?> t ? t : Map.of();
        Object highwayObj = tags.get("highway");
        String highway = highwayObj != null ? highwayObj.toString() : "";
        Object serviceObj = tags.get("service");
        String service = serviceObj != null ? serviceObj.toString() : "";
        double lanes = parseDouble(tags.get("lanes"));
        double width = parseDouble(tags.get("width"));
        List<GeoCoordinate> coords = parseGeometry(roadMap.get("geometry"));
        return new OsmWay(highway, service, lanes, width, coords);
    }

    /**
     * Analyse la géométrie de points GPS associée à une route.
     *
     * @param geomObj Liste de points JSON.
     * @return Liste des coordonnées GPS valides.
     */
    private static List<GeoCoordinate> parseGeometry(Object geomObj) {
        List<GeoCoordinate> coords = new ArrayList<>();
        if (geomObj instanceof List<?> geomList) {
            for (Object ptObj : geomList) {
                if (ptObj instanceof Map<?, ?> ptMap) {
                    double lat = parseDouble(ptMap.get("lat"));
                    Object lonVal = ptMap.containsKey("lon") ? ptMap.get("lon") : ptMap.get("lng");
                    double lon = parseDouble(lonVal);
                    coords.add(new GeoCoordinate(lat, lon));
                }
            }
        }
        return coords;
    }

    /**
     * Convertit une valeur objet en nombre décimal flottant.
     *
     * @param val Valeur à convertir.
     * @return Valeur sous forme de double ou -1 en cas d'absence ou invalidité.
     */
    private static double parseDouble(Object val) {
        if (val == null) {
            return -1;
        }
        try {
            return Double.parseDouble(String.valueOf(val).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parseur JSON récursif basique pour respecter ADR-001 sans dépendance tierce.
     */
    private static final class SimpleJsonParser {
        private final String src;
        private int pos = 0;

        /**
         * Initialise le parseur avec la chaîne JSON source.
         *
         * @param src Texte JSON à analyser.
         */
        SimpleJsonParser(String src) {
            this.src = src;
        }

        /**
         * Lance l'analyse syntaxique du document JSON.
         *
         * @return Objet Java correspondant (Map, List, String, Number, Boolean, ou null).
         * @throws IOException en cas d'erreur de syntaxe.
         */
        Object parse() throws IOException {
            skipWhitespace();
            Object val = parseValue();
            skipWhitespace();
            return val;
        }

        /**
         * Analyse une valeur élémentaire ou structurée à la position courante.
         *
         * @return Valeur analysée.
         * @throws IOException si un caractère invalide est rencontré.
         */
        private Object parseValue() throws IOException {
            skipWhitespace();
            if (pos >= src.length()) {
                return null;
            }
            char c = src.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (Character.isDigit(c) || c == '-') {
                return parseNumber();
            }
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IOException("Caractère inattendu à la position " + pos + " : '" + c + "'");
        }

        /**
         * Analyse un objet JSON délimité par des accolades.
         *
         * @return Map représentant les associations clé/valeur.
         * @throws IOException si la syntaxe de l'objet est incorrecte.
         */
        private Map<String, Object> parseObject() throws IOException {
            Map<String, Object> map = new HashMap<>();
            pos++; // '{'
            while (pos < src.length()) {
                skipWhitespace();
                if (src.charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                String key = parseString();
                skipWhitespace();
                if (src.charAt(pos) != ':') {
                    throw new IOException("':' attendu après la clé");
                }
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (src.charAt(pos) == '}') {
                    pos++;
                    return map;
                }
                throw new IOException("',' ou '}' attendu dans l'objet");
            }
            throw new IOException("Objet non fermé");
        }

        /**
         * Analyse un tableau JSON délimité par des crochets.
         *
         * @return Liste d'éléments analysés.
         * @throws IOException si le tableau n'est pas refermé correctement.
         */
        private List<Object> parseArray() throws IOException {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            while (pos < src.length()) {
                skipWhitespace();
                if (src.charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                list.add(parseValue());
                skipWhitespace();
                if (src.charAt(pos) == ',') {
                    pos++;
                    continue;
                }
                if (src.charAt(pos) == ']') {
                    pos++;
                    return list;
                }
                throw new IOException("',' ou ']' attendu dans le tableau");
            }
            throw new IOException("Tableau non fermé");
        }

        /**
         * Analyse une chaîne de caractères délimitée par des guillemets.
         *
         * @return Chaîne extraite.
         * @throws IOException si la chaîne n'est pas refermée.
         */
        private String parseString() throws IOException {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        break;
                    }
                    char esc = src.charAt(pos++);
                    if (esc == 'n') {
                        sb.append('\n');
                    } else if (esc == 't') {
                        sb.append('\t');
                    } else {
                        sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Chaîne de caractères non terminée");
        }

        /**
         * Analyse un nombre numérique entier ou décimal.
         *
         * @return Valeur numérique (Long ou Double).
         */
        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E' || src.charAt(pos) == '+')) {
                pos++;
            }
            String numStr = src.substring(start, pos);
            if (numStr.contains(".")) {
                return Double.parseDouble(numStr);
            }
            return Long.parseLong(numStr);
        }

        /**
         * Avance le curseur pour ignorer les espaces blancs.
         */
        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
