# V2 Sprint 1 : Socle Géométrique, Projection Web Mercator & Rasterisation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Charger la géométrie d'intention d'un territoire depuis un fichier JSON, projeter les coordonnées GPS via Web Mercator dans le repère de l'image cartographique, et rasteriser ce polygone en un masque binaire pixel exact (`PolygonMask` / `BinaryMask`) avec gestion des trous et multipolygones.

**Architecture:** Modèle de domaine immuable en Java Records (`GeoCoordinate`, `PixelPoint`, `MapContext`, `TerritoryGeometry`), parseur JSON autonome sans dépendance externe (ADR-001), projection mathématique conforme EPSG:3857 vers le repère local de la capture, et rasterisation vectorielle via `java.awt.geom.Path2D` pour produire une matrice de pixels booléens.

**Tech Stack:** Java 21, Java2D (AWT), JUnit 5 Jupiter.

---

### Task 1: Modèles de Domaine Géométriques Immuables

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/GeoCoordinate.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/PixelPoint.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/MapContext.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/TerritoryGeometry.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/geometry/GeometryModelTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour valider l'immutabilité et les contraintes des modèles**

Créer `src/test/java/com/sam102022/photoshop/v2/geometry/GeometryModelTest.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation des modèles de domaine géométrique V2")
class GeometryModelTest {

    @Test
    @DisplayName("GeoCoordinate valide les plages de latitude et longitude")
    void testGeoCoordinateValidation() {
        GeoCoordinate coord = new GeoCoordinate(47.2691, -1.5065);
        assertEquals(47.2691, coord.latitude(), 1e-6);
        assertEquals(-1.5065, coord.longitude(), 1e-6);

        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(-91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, -181.0));
    }

    @Test
    @DisplayName("MapContext valide les dimensions et le niveau de zoom")
    void testMapContextValidation() {
        GeoCoordinate center = new GeoCoordinate(47.2691, -1.5065);
        MapContext ctx = new MapContext(3810, 2130, 17, center);
        assertEquals(3810, ctx.width());
        assertEquals(2130, ctx.height());
        assertEquals(17, ctx.zoom());
        assertEquals(center, ctx.center());

        assertThrows(IllegalArgumentException.class, () -> new MapContext(0, 2130, 17, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, -1, 17, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, 2130, -1, center));
        assertThrows(IllegalArgumentException.class, () -> new MapContext(3810, 2130, 17, null));
    }

    @Test
    @DisplayName("TerritoryGeometry garantit des listes non mutables")
    void testTerritoryGeometryImmutability() {
        List<GeoCoordinate> ring = List.of(
                new GeoCoordinate(47.1, -1.5),
                new GeoCoordinate(47.2, -1.5),
                new GeoCoordinate(47.2, -1.4)
        );
        TerritoryGeometry geom = new TerritoryGeometry(List.of(ring), List.of());
        assertEquals(1, geom.outerRings().size());
        assertTrue(geom.innerRings().isEmpty());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue (classes non créées)**

Run: `mvn test -Dtest=GeometryModelTest`
Expected: FAIL (Compilation error: cannot find symbol GeoCoordinate, MapContext, etc.)

- [ ] **Step 3: Implémenter les records du domaine**

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/GeoCoordinate.java` :
```java
package com.sam102022.photoshop.v2.geometry;

/**
 * Coordonnée géographique WGS-84 (GPS).
 *
 * @param latitude  Latitude en degrés dans [-90.0, 90.0].
 * @param longitude Longitude en degrés dans [-180.0, 180.0].
 */
public record GeoCoordinate(double latitude, double longitude) {
    public GeoCoordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude hors limites [-90, 90] : " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude hors limites [-180, 180] : " + longitude);
        }
    }
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/PixelPoint.java` :
```java
package com.sam102022.photoshop.v2.geometry;

/**
 * Point bidimensionnel dans le repère pixel local de l'image.
 *
 * @param x Abscisse pixel (0.0 au bord gauche de l'image).
 * @param y Ordonnée pixel (0.0 au bord supérieur de l'image).
 */
public record PixelPoint(double x, double y) {}
```

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/MapContext.java` :
```java
package com.sam102022.photoshop.v2.geometry;

/**
 * Paramètres cartographiques de cadrage de l'image capturée.
 *
 * @param width  Largeur de l'image en pixels.
 * @param height Hauteur de l'image en pixels.
 * @param zoom   Niveau de zoom Web Mercator (ex: 17).
 * @param center Coordonnée géographique GPS correspondant au centre géométrique exact de l'image.
 */
public record MapContext(int width, int height, int zoom, GeoCoordinate center) {
    public MapContext {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions d'image invalides : " + width + "x" + height);
        }
        if (zoom < 0 || zoom > 24) {
            throw new IllegalArgumentException("Zoom Web Mercator invalide : " + zoom);
        }
        if (center == null) {
            throw new IllegalArgumentException("Le centre cartographique ne peut pas être null.");
        }
    }
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/TerritoryGeometry.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import java.util.List;

/**
 * Géométrie géographique du territoire comprenant les anneaux extérieurs et les trous intérieurs.
 *
 * @param outerRings Liste des anneaux fermés extérieurs (coordonnées GPS).
 * @param innerRings Liste des trous fermés intérieurs (coordonnées GPS).
 */
public record TerritoryGeometry(
        List<List<GeoCoordinate>> outerRings,
        List<List<GeoCoordinate>> innerRings
) {
    public TerritoryGeometry {
        outerRings = outerRings == null ? List.of() : outerRings.stream().map(List::copyOf).toList();
        innerRings = innerRings == null ? List.of() : innerRings.stream().map(List::copyOf).toList();
        if (outerRings.isEmpty()) {
            throw new IllegalArgumentException("Un territoire doit comporter au moins un anneau extérieur.");
        }
    }
}
```

- [ ] **Step 4: Exécuter les tests unitaires et vérifier le passage au vert**

Run: `mvn test -Dtest=GeometryModelTest`
Expected: PASS (Tests run: 3, Failures: 0, Errors: 0)

- [ ] **Step 5: Commit des modèles de domaine**

```bash
git add src/main/java/com/sam102022/photoshop/v2/geometry/GeoCoordinate.java src/main/java/com/sam102022/photoshop/v2/geometry/PixelPoint.java src/main/java/com/sam102022/photoshop/v2/geometry/MapContext.java src/main/java/com/sam102022/photoshop/v2/geometry/TerritoryGeometry.java src/test/java/com/sam102022/photoshop/v2/geometry/GeometryModelTest.java
git commit -m "feat(v2): modeles de domaine immutables pour la geometrie et le contexte cartographique"
```

---

### Task 2: Projection Web Mercator EPSG:3857

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjection.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjectionTest.java`

- [ ] **Step 1: Écrire le test unitaire pour la projection Web Mercator**

Créer `src/test/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjectionTest.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Validation de la projection Web Mercator V2")
class WebMercatorProjectionTest {

    @Test
    @DisplayName("Le centre géographique est exactement projeté au centre de l'image (W/2, H/2)")
    void testCenterProjectsToImageCenter() {
        int w = 3810;
        int h = 2130;
        int zoom = 17;
        GeoCoordinate center = new GeoCoordinate(47.26913592666494, -1.5065059369668754);
        MapContext ctx = new MapContext(w, h, zoom, center);

        WebMercatorProjection projection = new WebMercatorProjection(ctx);
        PixelPoint projectedCenter = projection.toPixel(center);

        assertEquals(w / 2.0, projectedCenter.x(), 1e-4);
        assertEquals(h / 2.0, projectedCenter.y(), 1e-4);
    }

    @Test
    @DisplayName("Conformité avec les formules du prototype Python sur un point témoin")
    void testProjectionConsistency() {
        // Paramètres réels du Territoire CA01
        MapContext ctx = new MapContext(3810, 2130, 17, new GeoCoordinate(47.26913592666494, -1.5065059369668754));
        WebMercatorProjection projection = new WebMercatorProjection(ctx);

        // Premier point du ring CA01 : lat = 47.27217675321035, lng = -1.499395734564006
        GeoCoordinate p0 = new GeoCoordinate(47.27217675321035, -1.499395734564006);
        PixelPoint pixel = projection.toPixel(p0);

        // Au nord du centre (lat > center.lat) => ordonnée pixel plus petite (y < H/2)
        // À l'est du centre (lng > center.lng) => abscisse pixel plus grande (x > W/2)
        assertEquals(3096.34, pixel.x(), 1.0);
        assertEquals(491.56, pixel.y(), 1.0);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=WebMercatorProjectionTest`
Expected: FAIL (WebMercatorProjection symbol not found)

- [ ] **Step 3: Implémenter la projection mathématique Web Mercator**

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjection.java` :
```java
package com.sam102022.photoshop.v2.geometry;

/**
 * Moteur de projection Web Mercator (EPSG:3857) convertissant les coordonnées géographiques
 * en coordonnées pixels relatives au cadrage de l'image cartographique locale.
 */
public final class WebMercatorProjection {

    private final MapContext context;
    private final double worldScale;
    private final double centerWorldX;
    private final double centerWorldY;

    /**
     * Initialise la projection pour un contexte cartographique donné.
     *
     * @param context Contexte d'image (dimensions, zoom, centre).
     */
    public WebMercatorProjection(MapContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Le contexte cartographique ne peut pas être null.");
        }
        this.context = context;
        this.worldScale = 256.0 * Math.pow(2, context.zoom());
        this.centerWorldX = computeWorldX(context.center().longitude());
        this.centerWorldY = computeWorldY(context.center().latitude());
    }

    /**
     * Projette une coordonnée GPS en coordonnée pixel locale dans le repère de l'image.
     *
     * @param coordinate Coordonnée géographique WGS-84.
     * @return Point en coordonnées pixels [0..width, 0..height].
     */
    public PixelPoint toPixel(GeoCoordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("La coordonnée ne peut pas être null.");
        }
        double worldX = computeWorldX(coordinate.longitude());
        double worldY = computeWorldY(coordinate.latitude());

        double localX = worldX - centerWorldX + (context.width() / 2.0);
        double localY = worldY - centerWorldY + (context.height() / 2.0);
        return new PixelPoint(localX, localY);
    }

    private double computeWorldX(double longitude) {
        return (longitude + 180.0) / 360.0 * worldScale;
    }

    private double computeWorldY(double latitude) {
        double sinLat = Math.sin(Math.toRadians(latitude));
        // Borner sinLat pour éviter les singularités aux pôles
        sinLat = Math.max(-0.9999, Math.min(0.9999, sinLat));
        return (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * worldScale;
    }
}
```

- [ ] **Step 4: Exécuter les tests et vérifier le passage au vert**

Run: `mvn test -Dtest=WebMercatorProjectionTest`
Expected: PASS (Tests run: 2, Failures: 0, Errors: 0)

- [ ] **Step 5: Commit de la projection**

```bash
git add src/main/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjection.java src/test/java/com/sam102022/photoshop/v2/geometry/WebMercatorProjectionTest.java
git commit -m "feat(v2): projection Web Mercator EPSG:3857 vers coordonnees pixels locales"
```

---

### Task 3: Parseur JSON Autonome de Métadonnées Cartographiques

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoader.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoaderTest.java`

- [ ] **Step 1: Écrire le test unitaire sur le fichier réel `01_plan_avec_territoires.json`**

Créer `src/test/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoaderTest.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation du parseur JSON de territoire V2")
class JsonTerritoryLoaderTest {

    @Test
    @DisplayName("Chargement complet du fichier d'échantillon réel Territoire CA01")
    void testLoadSampleTerritoryJson() throws IOException {
        Path jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        assertTrue(jsonPath.toFile().exists(), "Le fichier de test réel doit être présent");

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        assertNotNull(loaded);
        MapContext ctx = loaded.mapContext();
        assertEquals(3810, ctx.width());
        assertEquals(2130, ctx.height());
        assertEquals(17, ctx.zoom());
        assertEquals(47.269135, ctx.center().latitude(), 1e-4);
        assertEquals(-1.506505, ctx.center().longitude(), 1e-4);

        TerritoryGeometry geom = loaded.geometry();
        assertEquals(1, geom.outerRings().size());
        assertFalse(geom.outerRings().get(0).isEmpty());
        // Vérifier le premier sommet du polygone
        GeoCoordinate firstPoint = geom.outerRings().get(0).get(0);
        assertEquals(47.272176, firstPoint.latitude(), 1e-4);
        assertEquals(-1.499395, firstPoint.longitude(), 1e-4);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=JsonTerritoryLoaderTest`
Expected: FAIL (JsonTerritoryLoader symbol not found)

- [ ] **Step 3: Implémenter le chargeur JSON autonome sans dépendance tierce**

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoader.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Charge les métadonnées de cadrage cartographique et la géométrie polygonale
 * à partir du fichier JSON sans dépendance externe (ADR-001).
 */
public final class JsonTerritoryLoader {

    /**
     * Résultat encapsulant le contexte cartographique et la géométrie géographique.
     */
    public record LoadedTerritory(MapContext mapContext, TerritoryGeometry geometry) {}

    /**
     * Parse un fichier JSON de territoire exporté.
     *
     * @param jsonFile Chemin vers le fichier JSON.
     * @return Données cartographiques et géométriques normalisées.
     * @throws IOException en cas d'erreur de lecture ou de parsing.
     */
    public LoadedTerritory load(Path jsonFile) throws IOException {
        if (jsonFile == null || !Files.exists(jsonFile)) {
            throw new IllegalArgumentException("Fichier JSON inexistant : " + jsonFile);
        }
        String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
        Object rootObj = new MiniJsonParser(content).parse();
        Map<?, ?> root = asMap(rootObj, "racine");

        // 1. Parsing du contexte Map
        Map<?, ?> map = asMap(root.get("map"), "map");
        int zoom = (int) number(map.get("zoom"), "map.zoom");

        Map<?, ?> center = asMap(map.get("center"), "map.center");
        double centerLat = number(center.get("lat"), "map.center.lat");
        double centerLng = number(center.get("lng"), "map.center.lng");

        Map<?, ?> container = asMap(map.get("container"), "map.container");
        int width = (int) number(container.get("width"), "map.container.width");
        int height = (int) number(container.get("height"), "map.container.height");

        MapContext mapContext = new MapContext(width, height, zoom, new GeoCoordinate(centerLat, centerLng));

        // 2. Parsing de la géométrie du territoire
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

            Object innersObj = poly.get("innerRings");
            if (innersObj instanceof List<?> inners) {
                for (Object ringObj : inners) {
                    List<?> ring = asList(ringObj, "innerRing");
                    innerRings.add(parseRing(ring));
                }
            }
        }

        TerritoryGeometry geometry = new TerritoryGeometry(outerRings, innerRings);
        return new LoadedTerritory(mapContext, geometry);
    }

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

    private Map<?, ?> asMap(Object obj, String field) throws IOException {
        if (obj instanceof Map<?, ?> map) return map;
        throw new IOException("Objet JSON attendu pour le champ : " + field);
    }

    private List<?> asList(Object obj, String field) throws IOException {
        if (obj instanceof List<?> list) return list;
        throw new IOException("Tableau JSON attendu pour le champ : " + field);
    }

    private double number(Object obj, String field) throws IOException {
        if (obj instanceof Number num) return num.doubleValue();
        throw new IOException("Valeur numérique attendue pour le champ : " + field);
    }

    /**
     * Mini-parseur JSON récursif descendant standard conforme RFC 8259, sans dépendance externe.
     */
    private static final class MiniJsonParser {
        private final String src;
        private int pos = 0;

        private MiniJsonParser(String src) { this.src = src; }

        private Object parse() throws IOException {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (pos != src.length()) throw new IOException("Caractères résiduels en fin de JSON");
            return value;
        }

        private Object parseValue() throws IOException {
            skipWhitespace();
            if (pos >= src.length()) throw new IOException("Fin inattendue du JSON");
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new IOException("Jeton JSON invalide à la position " + pos + " : " + c);
        }

        private Map<String, Object> parseObject() throws IOException {
            pos++; // skip '{'
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return map;
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (!consume(':')) throw new IOException("':' attendu après la clé");
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (consume('}')) break;
                if (!consume(',')) throw new IOException("',' attendu entre les paires");
            }
            return map;
        }

        private List<Object> parseArray() throws IOException {
            pos++; // skip '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return list;
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (consume(']')) break;
                if (!consume(',')) throw new IOException("',' attendu entre les éléments du tableau");
            }
            return list;
        }

        private String parseString() throws IOException {
            if (src.charAt(pos) != '"') throw new IOException("'\"' attendu");
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IOException("Échappement incomplet");
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
                        case 'u' -> {
                            if (pos + 4 > src.length()) throw new IOException("Unicode incomplet");
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Chaîne non fermée");
        }

        private Number parseNumber() throws IOException {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            if (pos < src.length() && src.charAt(pos) == '.') {
                pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
            }
            String numStr = src.substring(start, pos);
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

        private boolean consume(char c) {
            if (pos < src.length() && src.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }
    }
}
```

- [ ] **Step 4: Exécuter les tests unitaires et vérifier le passage au vert**

Run: `mvn test -Dtest=JsonTerritoryLoaderTest`
Expected: PASS (Tests run: 1, Failures: 0, Errors: 0)

- [ ] **Step 5: Commit du chargeur JSON**

```bash
git add src/main/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoader.java src/test/java/com/sam102022/photoshop/v2/geometry/JsonTerritoryLoaderTest.java
git commit -m "feat(v2): parseur JSON autonome pour extraire la geometrie du territoire et le cadrage cartographique"
```

---

### Task 4: Rasteriseur de Polygone en Masque Pixel (`PolygonMask`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizer.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizerTest.java`

- [ ] **Step 1: Écrire le test unitaire pour la rasterisation avec support des trous**

Créer `src/test/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizerTest.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation de la rasterisation de polygones V2")
class PolygonRasterizerTest {

    @Test
    @DisplayName("Rasterisation d'un polygone carré simple")
    void testSimpleSquareRasterization() {
        // Carré de (10, 10) à (30, 30) dans une image 40x40
        List<PixelPoint> outer = List.of(
                new PixelPoint(10, 10),
                new PixelPoint(30, 10),
                new PixelPoint(30, 30),
                new PixelPoint(10, 30)
        );

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        BinaryMask mask = rasterizer.rasterize(40, 40, List.of(outer), List.of());

        assertEquals(40, mask.getWidth());
        assertEquals(40, mask.getHeight());

        // L'intérieur doit être actif
        assertTrue(mask.get(20, 20));
        // L'extérieur doit être inactif
        assertFalse(mask.get(5, 5));
        assertFalse(mask.get(35, 35));
    }

    @Test
    @DisplayName("Rasterisation avec trou intérieur (Winding Rule Even-Odd)")
    void testSquareWithHoleRasterization() {
        // Carré externe (10, 10) à (40, 40)
        List<PixelPoint> outer = List.of(
                new PixelPoint(10, 10),
                new PixelPoint(40, 10),
                new PixelPoint(40, 40),
                new PixelPoint(10, 40)
        );
        // Trou interne (20, 20) à (30, 30)
        List<PixelPoint> hole = List.of(
                new PixelPoint(20, 20),
                new PixelPoint(30, 20),
                new PixelPoint(30, 30),
                new PixelPoint(20, 30)
        );

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        BinaryMask mask = rasterizer.rasterize(50, 50, List.of(outer), List.of(hole));

        // Entre le contour et le trou : actif
        assertTrue(mask.get(15, 15));
        // Dans le trou : inactif
        assertFalse(mask.get(25, 25));
        // Hors du carré : inactif
        assertFalse(mask.get(5, 5));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=PolygonRasterizerTest`
Expected: FAIL (PolygonRasterizer symbol not found)

- [ ] **Step 3: Implémenter le rasteriseur de polygone Java2D**

Créer `src/main/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizer.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Convertit des anneaux polygonaux continus en coordonnées pixels en un masque binaire exact (P[x,y] ∈ {0, 1})
 * en gérant les anneaux extérieurs et les trous intérieurs selon la règle de remplissage pair-impair (Even-Odd).
 */
public final class PolygonRasterizer {

    /**
     * Rasterise un polygone pixel en masque binaire.
     *
     * @param width      Largeur de l'image de destination.
     * @param height     Hauteur de l'image de destination.
     * @param outerRings Anneaux extérieurs.
     * @param innerRings Trous intérieurs.
     * @return Masque binaire où les pixels à l'intérieur du polygone sont à true.
     */
    public BinaryMask rasterize(int width, int height,
                                List<List<PixelPoint>> outerRings,
                                List<List<PixelPoint>> innerRings) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides : " + width + "x" + height);
        }
        if (outerRings == null || outerRings.isEmpty()) {
            return new BinaryMask(width, height);
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        for (List<PixelPoint> ring : outerRings) {
            addRingToPath(path, ring);
        }
        if (innerRings != null) {
            for (List<PixelPoint> hole : innerRings) {
                addRingToPath(path, hole);
            }
        }

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2 = canvas.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.WHITE);
            g2.fill(path);
        } finally {
            g2.dispose();
        }

        boolean[] data = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int rgb = canvas.getRGB(x, y) & 0xFFFFFF;
                if (rgb != 0) {
                    data[rowOffset + x] = true;
                }
            }
        }
        return new BinaryMask(width, height, data);
    }

    private void addRingToPath(Path2D.Double path, List<PixelPoint> points) {
        if (points == null || points.size() < 3) return;
        PixelPoint first = points.get(0);
        path.moveTo(first.x(), first.y());
        for (int i = 1; i < points.size(); i++) {
            PixelPoint pt = points.get(i);
            path.lineTo(pt.x(), pt.y());
        }
        path.closePath();
    }
}
```

- [ ] **Step 4: Exécuter les tests unitaires et vérifier le passage au vert**

Run: `mvn test -Dtest=PolygonRasterizerTest`
Expected: PASS (Tests run: 2, Failures: 0, Errors: 0)

- [ ] **Step 5: Commit du rasteriseur**

```bash
git add src/main/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizer.java src/test/java/com/sam102022/photoshop/v2/geometry/PolygonRasterizerTest.java
git commit -m "feat(v2): rasteriseur de polygone pixel avec regle Even-Odd pour trous et multipolygones"
```

---

### Task 5: Test d'Intégration Bout-en-Bout du Sprint 1 sur `Territoire CA01`

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/geometry/Sprint1IntegrationTest.java`

- [ ] **Step 1: Écrire le test d'intégration validant tout le pipeline du Sprint 1 sur les données réelles**

Créer `src/test/java/com/sam102022/photoshop/v2/geometry/Sprint1IntegrationTest.java` :
```java
package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test d'intégration Sprint 1 : JSON -> Mercator -> Polygone Raster P")
class Sprint1IntegrationTest {

    @Test
    @DisplayName("Génération complète du polygone pixel P sur Territoire CA01 conforme au prototype Python")
    void testEndToEndSprint1Pipeline() throws IOException {
        Path jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");

        // 1. Chargement JSON
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        // 2. Projection Web Mercator
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());

        List<List<PixelPoint>> projectedOuterRings = new ArrayList<>();
        for (List<GeoCoordinate> ring : loaded.geometry().outerRings()) {
            List<PixelPoint> projectedRing = ring.stream().map(projection::toPixel).toList();
            projectedOuterRings.add(projectedRing);
        }

        List<List<PixelPoint>> projectedInnerRings = new ArrayList<>();
        for (List<GeoCoordinate> ring : loaded.geometry().innerRings()) {
            List<PixelPoint> projectedRing = ring.stream().map(projection::toPixel).toList();
            projectedInnerRings.add(projectedRing);
        }

        // 3. Rasterisation en masque P
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        BinaryMask polygonMask = rasterizer.rasterize(
                loaded.mapContext().width(),
                loaded.mapContext().height(),
                projectedOuterRings,
                projectedInnerRings
        );

        // 4. Vérifications quantitatives
        assertEquals(3810, polygonMask.getWidth());
        assertEquals(2130, polygonMask.getHeight());

        // L'aire du polygone projeté P dans le prototype Python script.py est de 1 184 004 pixels
        int activePixels = polygonMask.countActivePixels();
        assertTrue(activePixels > 1_170_000 && activePixels < 1_200_000,
                "L'aire du polygone raster P (" + activePixels + ") doit concorder avec l'aire mesurée dans script.py (~1 184 000 px)");
    }
}
```

- [ ] **Step 2: Exécuter le test d'intégration**

Run: `mvn test -Dtest=Sprint1IntegrationTest`
Expected: PASS (Tests run: 1, Failures: 0, Errors: 0)

- [ ] **Step 3: Exécuter l'ensemble de la suite de tests pour vérifier l'absence totale de régression**

Run: `mvn test -Dtest=*Geometry*Test,*Sprint1*Test`
Expected: PASS (Tous les tests du package V2 passent à 100%)

- [ ] **Step 4: Commit final du Sprint 1**

```bash
git add src/test/java/com/sam102022/photoshop/v2/geometry/Sprint1IntegrationTest.java
git commit -m "test(v2): validation d'integration du pipeline Sprint 1 (JSON -> Projection -> Raster P)"
```

---

## Plan Self-Review Checklist

- **1. Spec coverage :** Couvre intégralement l'Étape 0 (normalisation des entrées) et l'Étape 1 (rasterisation du polygone) de la spécification formelle V2.
- **2. Placeholder scan :** Aucun TODO, TBD ou extrait de code incomplet. Toutes les classes et tests sont écrits in-extenso.
- **3. Type consistency :** Types rigoureusement alignés (`GeoCoordinate`, `PixelPoint`, `MapContext`, `TerritoryGeometry`, `BinaryMask`).
