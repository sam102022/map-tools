package com.sam102022.photoshop.v2.geometry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Charge les métadonnées de cadrage cartographique et la géométrie polygonale
 * à partir du fichier JSON sans dépendance externe (ADR-001).
 */
public final class JsonTerritoryLoader {

    /**
     * Résultat encapsulant le contexte cartographique et la géométrie géographique.
     *
     * @param mapContext Contexte de cadrage cartographique.
     * @param geometry Géométrie polygonale du territoire.
     */
    public record LoadedTerritory(MapContext mapContext, TerritoryGeometry geometry) {}

    /**
     * Parse un fichier JSON de territoire exporté.
     *
     * @param jsonFile Chemin vers le fichier JSON.
     * @return Données cartographiques et géométriques normalisées.
     * @throws IOException en cas d'erreur de lecture ou de parsing.
     * @throws IllegalArgumentException si le chemin est null ou inexistant.
     */
    public LoadedTerritory load(Path jsonFile) throws IOException {
        if (jsonFile == null || !Files.exists(jsonFile)) {
            throw new IllegalArgumentException("Fichier JSON inexistant : " + jsonFile);
        }
        String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
        Object rootObj = new MiniJsonParser(content).parse();
        Map<?, ?> root = asMap(rootObj, "racine");

        MapContext mapContext = parseMapContext(root);
        TerritoryGeometry geometry = parseTerritoryGeometry(root);

        return new LoadedTerritory(mapContext, geometry);
    }

    /**
     * Extrait le contexte cartographique à partir de la racine JSON.
     *
     * @param root Objet racine JSON.
     * @return Contexte cartographique initialisé.
     * @throws IOException si un champ attendu est manquant ou invalide.
     */
    private MapContext parseMapContext(Map<?, ?> root) throws IOException {
        Map<?, ?> map = asMap(root.get("map"), "map");
        int zoom = (int) number(map.get("zoom"), "map.zoom");

        Map<?, ?> center = asMap(map.get("center"), "map.center");
        double centerLat = number(center.get("lat"), "map.center.lat");
        double centerLng = number(center.get("lng"), "map.center.lng");

        Map<?, ?> container = asMap(map.get("container"), "map.container");
        int width = container.containsKey("pixelWidth")
                ? (int) Math.round(number(container.get("pixelWidth"), "map.container.pixelWidth"))
                : (int) Math.round(number(container.get("width"), "map.container.width"));
        int height = container.containsKey("pixelHeight")
                ? (int) Math.round(number(container.get("pixelHeight"), "map.container.pixelHeight"))
                : (int) Math.round(number(container.get("height"), "map.container.height"));

        return new MapContext(width, height, zoom, new GeoCoordinate(centerLat, centerLng));
    }

    /**
     * Extrait la géométrie du territoire à partir de la racine JSON.
     *
     * @param root Objet racine JSON.
     * @return Géométrie du territoire contenant les anneaux extérieurs et intérieurs.
     * @throws IOException si la structure géométrique est invalide.
     */
    private TerritoryGeometry parseTerritoryGeometry(Map<?, ?> root) throws IOException {
        Map<?, ?> territory = asMap(root.get("territory"), "territory");
        List<?> polygons = asList(territory.get("polygons"), "territory.polygons");

        List<List<GeoCoordinate>> outerRings = new ArrayList<>();
        List<List<GeoCoordinate>> innerRings = new ArrayList<>();

        for (Object polyObj : polygons) {
            Map<?, ?> poly = asMap(polyObj, "polygon");
            List<?> outers = asList(poly.get("outerRings"), "polygon.outerRings");
            for (Object ringObj : outers) {
                List<?> ring = asList(ringObj, "outerRing");
                outerRings.add(parseRing(ring));
            }
            parseInnerRings(poly, innerRings);
        }

        return new TerritoryGeometry(outerRings, innerRings);
    }

    /**
     * Extrait les anneaux intérieurs d'un polygone s'ils sont présents.
     *
     * @param poly Objet polygone JSON.
     * @param innerRings Liste réceptrice des anneaux intérieurs.
     * @throws IOException si un anneau intérieur est invalide.
     */
    private void parseInnerRings(Map<?, ?> poly, List<List<GeoCoordinate>> innerRings) throws IOException {
        Object innersObj = poly.get("innerRings");
        if (innersObj instanceof List<?> inners) {
            for (Object ringObj : inners) {
                List<?> ring = asList(ringObj, "innerRing");
                innerRings.add(parseRing(ring));
            }
        }
    }

    /**
     * Parse un anneau de coordonnées géographiques.
     *
     * @param points Liste de points JSON.
     * @return Liste ordonnée de coordonnées géodésiques.
     * @throws IOException si un point ne contient pas les coordonnées attendues.
     */
    private List<GeoCoordinate> parseRing(List<?> points) throws IOException {
        List<GeoCoordinate> ring = new ArrayList<>();
        for (Object ptObj : points) {
            Map<?, ?> pt = asMap(ptObj, "point");
            double lat = number(pt.get("lat"), "point.lat");
            double lng = number(pt.get("lng"), "point.lng");
            ring.add(new GeoCoordinate(lat, lng));
        }
        return List.copyOf(ring);
    }

    /**
     * Valide et convertit un objet en Map.
     *
     * @param obj Objet source.
     * @param field Nom du champ pour le message d'erreur.
     * @return Map typée.
     * @throws IOException si l'objet n'est pas une Map.
     */
    private Map<?, ?> asMap(Object obj, String field) throws IOException {
        if (obj instanceof Map<?, ?> map) {
            return map;
        }
        throw new IOException("Objet JSON attendu pour le champ : " + field);
    }

    /**
     * Valide et convertit un objet en List.
     *
     * @param obj Objet source.
     * @param field Nom du champ pour le message d'erreur.
     * @return List typée.
     * @throws IOException si l'objet n'est pas une List.
     */
    private List<?> asList(Object obj, String field) throws IOException {
        if (obj instanceof List<?> list) {
            return list;
        }
        throw new IOException("Tableau JSON attendu pour le champ : " + field);
    }

    /**
     * Valide et extrait une valeur numérique double.
     *
     * @param obj Objet source.
     * @param field Nom du champ pour le message d'erreur.
     * @return Valeur double.
     * @throws IOException si l'objet n'est pas un nombre.
     */
    private double number(Object obj, String field) throws IOException {
        if (obj instanceof Number num) {
            return num.doubleValue();
        }
        throw new IOException("Valeur numérique attendue pour le champ : " + field);
    }

    /**
     * Mini-parseur JSON récursif descendant standard conforme RFC 8259, sans dépendance externe.
     */
    private static final class MiniJsonParser {
        private final String src;
        private int pos = 0;

        /**
         * Initialise le parseur avec la chaîne source JSON.
         *
         * @param src Chaîne JSON brute.
         */
        private MiniJsonParser(String src) {
            this.src = src;
        }

        /**
         * Parse la chaîne source complète en graphe d'objets Java.
         *
         * @return Objet racine (Map, List, String, Number, Boolean ou null).
         * @throws IOException en cas de syntaxe JSON invalide.
         */
        private Object parse() throws IOException {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos != src.length()) {
                throw new IOException("Caractères résiduels en fin de JSON");
            }
            return value;
        }

        /**
         * Parse une valeur JSON quelconque à la position courante.
         *
         * @return Valeur parsée.
         * @throws IOException si aucun jeton valide n'est rencontré.
         */
        private Object parseValue() throws IOException {
            skipWhitespace();
            if (pos >= src.length()) {
                throw new IOException("Fin inattendue du JSON");
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
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            return parseLiteral();
        }

        /**
         * Parse les littéraux true, false et null.
         *
         * @return Valeur littérale correspondante.
         * @throws IOException si le littéral est inconnu.
         */
        private Object parseLiteral() throws IOException {
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
            throw new IOException("Jeton JSON invalide à la position " + pos + " : " + src.charAt(pos));
        }

        /**
         * Parse un objet JSON délimité par des accolades.
         *
         * @return Map associant les clés aux valeurs.
         * @throws IOException si la syntaxe de l'objet est incorrecte.
         */
        private Map<String, Object> parseObject() throws IOException {
            pos++; // Passe l'accolade ouvrante '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (!consume(':')) {
                    throw new IOException("':' attendu après la clé");
                }
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (consume('}')) {
                    break;
                }
                if (!consume(',')) {
                    throw new IOException("',' attendu entre les paires");
                }
            }
            return map;
        }

        /**
         * Parse un tableau JSON délimité par des crochets.
         *
         * @return Liste des éléments du tableau.
         * @throws IOException si la syntaxe du tableau est incorrecte.
         */
        private List<Object> parseArray() throws IOException {
            pos++; // Passe le crochet ouvrant '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (consume(']')) {
                    break;
                }
                if (!consume(',')) {
                    throw new IOException("',' attendu entre les éléments du tableau");
                }
            }
            return list;
        }

        /**
         * Parse une chaîne de caractères délimitée par des guillemets.
         *
         * @return Chaîne décodée.
         * @throws IOException si la chaîne est mal formée ou non fermée.
         */
        private String parseString() throws IOException {
            if (src.charAt(pos) != '"') {
                throw new IOException("'\"' attendu");
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    handleEscape(sb);
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Chaîne non fermée");
        }

        /**
         * Décode un caractère ou une séquence d'échappement dans une chaîne.
         *
         * @param sb Tampon de chaîne récepteur.
         * @throws IOException si l'échappement est incomplet ou invalide.
         */
        private void handleEscape(StringBuilder sb) throws IOException {
            if (pos >= src.length()) {
                throw new IOException("Échappement incomplet");
            }
            char esc = src.charAt(pos++);
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> decodeUnicode(sb);
                default -> sb.append(esc);
            }
        }

        /**
         * Décode un point de code Unicode hexadécimal à 4 chiffres.
         *
         * @param sb Tampon de chaîne récepteur.
         * @throws IOException si le code hexadécimal est tronqué.
         */
        private void decodeUnicode(StringBuilder sb) throws IOException {
            if (pos + 4 > src.length()) {
                throw new IOException("Unicode incomplet");
            }
            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
            pos += 4;
        }

        /**
         * Parse un nombre JSON (entier ou flottant).
         *
         * @return Nombre sous forme de Double, Integer ou Long.
         * @throws IOException si le format numérique est invalide.
         */
        private Number parseNumber() throws IOException {
            int start = pos;
            if (src.charAt(pos) == '-') {
                pos++;
            }
            consumeDigits();
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                consumeDigits();
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                    pos++;
                }
                consumeDigits();
            }
            String numStr = src.substring(start, pos);
            return convertNumber(numStr);
        }

        /**
         * Consomme une séquence consécutive de chiffres.
         */
        private void consumeDigits() {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }

        /**
         * Convertit une chaîne numérique en Number approprié.
         *
         * @param numStr Chaîne représentant le nombre.
         * @return Instance de Number.
         * @throws IOException en cas d'erreur de conversion.
         */
        private Number convertNumber(String numStr) throws IOException {
            try {
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    return Double.parseDouble(numStr);
                }
                long val = Long.parseLong(numStr);
                return (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) ? (int) val : val;
            } catch (NumberFormatException e) {
                throw new IOException("Nombre JSON invalide : " + numStr, e);
            }
        }

        /**
         * Consomme le caractère attendu s'il correspond au caractère courant.
         *
         * @param c Caractère attendu.
         * @return true si consommé, false sinon.
         */
        private boolean consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * Avance l'index au-delà des espaces blancs.
         */
        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
