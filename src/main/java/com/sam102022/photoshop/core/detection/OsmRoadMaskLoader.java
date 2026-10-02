package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Charge les axes routiers d'un export RoadSnapper/Overpass et les rasterise dans le repère de la capture Google Maps.
 * Les routes OSM sont décalées de 15 pixels vers la droite et le bas, conformément au recalage visuel validé pour CA01.
 */
public final class OsmRoadMaskLoader {
    private static final double EARTH_CIRCUMFERENCE_METERS = 40_075_016.686;
    private static final double OFFSET_X_PIXELS = 15.0;
    private static final double OFFSET_Y_PIXELS = 15.0;
    private static final List<String> INCLUDED_HIGHWAYS = List.of(
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
            "residential", "living_street", "road", "service", "busway"
    );

    /**
     * Rasterise les routes OSM vers un masque binaire aligné sur l'image de carte.
     *
     * @param jsonPath fichier osm_roads.json.
     * @param width largeur de la capture.
     * @param height hauteur de la capture.
     * @return masque routier rasterisé.
     * @throws IOException si le fichier est illisible ou ne correspond pas au cadrage fourni.
     */
    public BinaryMask load(Path jsonPath, int width, int height) throws IOException {
        if (jsonPath == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Le chemin OSM et les dimensions de la carte doivent être valides.");
        }
        Object parsed = new JsonParser(Files.readString(jsonPath, StandardCharsets.UTF_8)).parse();
        Map<?, ?> root = asMap(parsed, "racine JSON");
        Map<?, ?> map = asMap(root.get("map"), "map");
        Map<?, ?> center = asMap(map.get("center"), "map.center");
        Map<?, ?> container = asMap(map.get("container"), "map.container");
        double centerLat = number(center.get("lat"), "map.center.lat");
        double centerLon = number(center.get("lng"), "map.center.lng");
        int zoom = (int) number(map.get("zoom"), "map.zoom");
        int expectedWidth = (int) number(container.get("pixelWidth"), "map.container.pixelWidth");
        int expectedHeight = (int) number(container.get("pixelHeight"), "map.container.pixelHeight");
        if (width != expectedWidth || height != expectedHeight) {
            throw new IOException("La capture carte (" + width + "x" + height + ") ne correspond pas au cadrage OSM ("
                    + expectedWidth + "x" + expectedHeight + "). Utilisez la capture associée à osm_roads.json.");
        }
        if (number(map.containsKey("heading") ? map.get("heading") : 0, "map.heading") != 0
                || number(map.containsKey("tilt") ? map.get("tilt") : 0, "map.tilt") != 0) {
            throw new IOException("Le rasteriseur OSM requiert une capture sans rotation ni inclinaison (heading=0, tilt=0).");
        }

        double worldSize = 256.0 * Math.pow(2.0, zoom);
        double centerX = worldX(centerLon, worldSize);
        double centerY = worldY(centerLat, worldSize);
        double metersPerPixel = Math.cos(Math.toRadians(centerLat)) * EARTH_CIRCUMFERENCE_METERS / worldSize;

        BufferedImage raster = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = raster.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            Object roadsValue = root.get("roads");
            if (!(roadsValue instanceof List<?> roads)) {
                throw new IOException("Le fichier OSM ne contient pas de tableau roads.");
            }
            for (Object roadValue : roads) {
                Map<?, ?> road = asMap(roadValue, "roads[]");
                Map<?, ?> tags = asMap(road.get("tags"), "roads[].tags");
                String highway = String.valueOf(tags.get("highway"));
                if (!INCLUDED_HIGHWAYS.contains(highway) || isMinorService(tags)) {
                    continue;
                }
                Object geometryValue = road.get("geometry");
                if (!(geometryValue instanceof List<?> geometry) || geometry.size() < 2) {
                    continue;
                }
                Path2D path = new Path2D.Double();
                boolean first = true;
                for (Object pointValue : geometry) {
                    Map<?, ?> point = asMap(pointValue, "roads[].geometry[]");
                    double lat = number(point.get("lat"), "geometry.lat");
                    double lon = number(point.get("lon"), "geometry.lon");
                    double x = worldX(lon, worldSize) - centerX + width / 2.0 + OFFSET_X_PIXELS;
                    double y = worldY(lat, worldSize) - centerY + height / 2.0 + OFFSET_Y_PIXELS;
                    if (first) {
                        path.moveTo(x, y);
                        first = false;
                    } else {
                        path.lineTo(x, y);
                    }
                }
                float widthPixels = (float) Math.max(1.0, roadWidthMeters(highway, tags) / metersPerPixel);
                graphics.setStroke(new BasicStroke(widthPixels, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(path);
            }
        } catch (IllegalArgumentException ex) {
            throw new IOException("Structure invalide dans le fichier OSM : " + ex.getMessage(), ex);
        } finally {
            graphics.dispose();
        }
        return BinaryMask.fromImage(raster, 1);
    }

    /** Retourne le fichier OSM voisin de la capture, s'il existe. */
    public static Path findAdjacent(Path mapImagePath) {
        if (mapImagePath == null) return null;
        Path parent = mapImagePath.toAbsolutePath().getParent();
        if (parent == null) return null;
        Path candidate = parent.resolve("osm_roads.json");
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private static boolean isMinorService(Map<?, ?> tags) {
        if (!"service".equals(tags.get("highway"))) return false;
        Object service = tags.get("service");
        return "driveway".equals(service) || "parking_aisle".equals(service)
                || "emergency_access".equals(service) || "private".equals(tags.get("access"));
    }

    private static double roadWidthMeters(String highway, Map<?, ?> tags) {
        double taggedWidth = parsePositive(tags.get("width"));
        if (taggedWidth > 0) return taggedWidth;
        double lanes = parsePositive(tags.get("lanes"));
        if (lanes <= 0) lanes = defaultLanes(highway);
        double metersPerLane = switch (highway) {
            case "motorway", "trunk" -> 3.6;
            case "primary", "secondary" -> 3.3;
            case "tertiary", "unclassified", "residential", "living_street", "road", "busway" -> 3.0;
            default -> 3.0;
        };
        return Math.max(3.0, lanes * metersPerLane + 1.0);
    }

    private static double defaultLanes(String highway) {
        return switch (highway) {
            case "motorway", "trunk", "primary", "secondary" -> 2.0;
            default -> 1.0;
        };
    }

    private static double parsePositive(Object value) {
        if (value == null) return -1;
        String text = String.valueOf(value).trim().replace(',', '.');
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^[0-9]+(?:\\.[0-9]+)?").matcher(text);
        if (!matcher.find()) return -1;
        try {
            double parsed = Double.parseDouble(matcher.group());
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static double worldX(double longitude, double worldSize) {
        return (longitude + 180.0) / 360.0 * worldSize;
    }

    private static double worldY(double latitude, double worldSize) {
        double clamped = Math.max(-85.05112878, Math.min(85.05112878, latitude));
        double sin = Math.sin(Math.toRadians(clamped));
        return (0.5 - Math.log((1.0 + sin) / (1.0 - sin)) / (4.0 * Math.PI)) * worldSize;
    }

    private static Map<?, ?> asMap(Object value, String field) throws IOException {
        if (value instanceof Map<?, ?> map) return map;
        throw new IOException("Champ JSON manquant ou invalide : " + field);
    }

    private static double number(Object value, String field) throws IOException {
        if (value instanceof Number number) return number.doubleValue();
        throw new IOException("Valeur numérique manquante ou invalide : " + field);
    }

    /** Petit parseur JSON autonome pour garder le projet sans dépendance d'exécution externe. */
    private static final class JsonParser {
        private final String input;
        private int index;

        private JsonParser(String input) { this.input = input; }

        private Object parse() throws IOException {
            Object value = readValue();
            skipWhitespace();
            if (index != input.length()) fail("contenu après la valeur racine");
            return value;
        }

        private Object readValue() throws IOException {
            skipWhitespace();
            if (index >= input.length()) return fail("fin inattendue du JSON");
            return switch (input.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() throws IOException {
            index++;
            java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return result;
            do {
                skipWhitespace();
                if (index >= input.length() || input.charAt(index) != '"') return fail("clé d'objet attendue");
                String key = readString();
                skipWhitespace();
                if (!consume(':')) return fail("':' attendu après une clé");
                result.put(key, readValue());
                skipWhitespace();
                if (consume('}')) return result;
                if (!consume(',')) return fail("',' attendu dans un objet");
            } while (true);
        }

        private List<Object> readArray() throws IOException {
            index++;
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return result;
            do {
                result.add(readValue());
                skipWhitespace();
                if (consume(']')) return result;
                if (!consume(',')) return fail("',' attendu dans un tableau");
            } while (true);
        }

        private String readString() throws IOException {
            index++;
            StringBuilder result = new StringBuilder();
            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == '"') return result.toString();
                if (c == '\\') {
                    if (index >= input.length()) return fail("échappement incomplet");
                    char escaped = input.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (index + 4 > input.length()) return fail("séquence Unicode incomplète");
                            try { result.append((char) Integer.parseInt(input.substring(index, index + 4), 16)); }
                            catch (NumberFormatException ex) { return fail("séquence Unicode invalide"); }
                            index += 4;
                        }
                        default -> { return fail("échappement invalide"); }
                    }
                } else result.append(c);
            }
            return fail("chaîne non terminée");
        }

        private Object readNumber() throws IOException {
            int start = index;
            while (index < input.length() && "-+0123456789.eE".indexOf(input.charAt(index)) >= 0) index++;
            if (start == index) return fail("valeur JSON invalide");
            try { return Double.parseDouble(input.substring(start, index)); }
            catch (NumberFormatException ex) { return fail("nombre invalide"); }
        }

        private Object readLiteral(String literal, Object value) throws IOException {
            if (!input.startsWith(literal, index)) return fail("valeur JSON invalide");
            index += literal.length();
            return value;
        }

        private boolean consume(char expected) {
            if (index < input.length() && input.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private <T> T fail(String message) throws IOException {
            throw new IOException("JSON invalide à la position " + index + " : " + message);
        }
    }
}
