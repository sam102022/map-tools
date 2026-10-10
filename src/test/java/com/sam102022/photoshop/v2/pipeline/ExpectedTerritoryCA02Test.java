package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;
import com.sam102022.photoshop.v2.road.RoadMaskCleaner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de concordance avec le détourage de référence fourni pour le Territoire CA02
 * ({@code expected_territory.jpg} : territoire détouré sur fond blanc).
 */
@DisplayName("Concordance avec le détourage de référence CA02")
class ExpectedTerritoryCA02Test {

    private static final Path DIR = Paths.get("maps/captures_maps/Territoire CA02");

    /**
     * Extrait le masque attendu : tout pixel non relié au bord de l'image par du blanc pur (min RVB &gt;= 252).
     *
     * @param expected Image de référence (territoire sur fond blanc).
     * @return Masque attendu.
     */
    private static boolean[] expectedMask(BufferedImage expected) {
        int w = expected.getWidth();
        int h = expected.getHeight();
        boolean[] background = new boolean[w * h];
        int[] queue = new int[w * h];
        int tail = 0;
        for (int x = 0; x < w; x++) {
            tail = seed(expected, background, queue, tail, x, 0);
            tail = seed(expected, background, queue, tail, x, h - 1);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(expected, background, queue, tail, 0, y);
            tail = seed(expected, background, queue, tail, w - 1, y);
        }
        int head = 0;
        while (head < tail) {
            int idx = queue[head++];
            int x = idx % w;
            int y = idx / w;
            if (x > 0) tail = seed(expected, background, queue, tail, x - 1, y);
            if (x < w - 1) tail = seed(expected, background, queue, tail, x + 1, y);
            if (y > 0) tail = seed(expected, background, queue, tail, x, y - 1);
            if (y < h - 1) tail = seed(expected, background, queue, tail, x, y + 1);
        }
        boolean[] mask = new boolean[w * h];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = !background[i];
        }
        return mask;
    }

    /**
     * Ajoute un pixel blanc non visité à la file du remplissage du fond.
     *
     * @param img        Image de référence.
     * @param background Marqueurs du fond.
     * @param queue      File.
     * @param tail       Fin de file.
     * @param x          Abscisse.
     * @param y          Ordonnée.
     * @return Nouvelle fin de file.
     */
    private static int seed(BufferedImage img, boolean[] background, int[] queue, int tail, int x, int y) {
        int idx = y * img.getWidth() + x;
        if (background[idx]) {
            return tail;
        }
        int rgb = img.getRGB(x, y);
        int min = Math.min((rgb >>> 16) & 0xFF, Math.min((rgb >>> 8) & 0xFF, rgb & 0xFF));
        if (min >= 252) {
            background[idx] = true;
            queue[tail++] = idx;
        }
        return tail;
    }

    /**
     * Le masque produit doit recouvrir le masque de référence avec un IoU d'au moins 0,985 (0,989 mesuré le 08/10/2026 après les règles giratoires frontaliers, autoroutes et routes
     * longées).
     *
     * @throws IOException en cas d'erreur de lecture des fichiers.
     */
    @Test
    @DisplayName("IoU >= 0,985 avec expected_territory.jpg")
    void testIouWithExpectedTerritory() throws IOException {
        assertIou(false);
    }

    /**
     * Même contrôle avec la capture Google « Routes seules » (masque routier et bord des chaussées), si elle est
     * présente dans le dossier du territoire.
     *
     * @throws IOException en cas d'erreur de lecture.
     */
    @Test
    @DisplayName("IoU >= 0,985 avec expected_territory.jpg (capture « Routes seules »)")
    void testIouWithExpectedTerritoryGoogleRoads() throws IOException {
        assertIou(true);
    }

    /**
     * Détoure CA02 et vérifie l'IoU avec la référence.
     *
     * @param googleRoads Vrai pour lire routes et bords de chaussée dans {@code 06_routes_seules.png}.
     * @throws IOException en cas d'erreur de lecture.
     */
    private void assertIou(boolean googleRoads) throws IOException {
        Path map = DIR.resolve("05_style_contraste_sans_rien.png");
        Path json = DIR.resolve("01_plan_avec_territoires.json");
        Path expectedPath = DIR.resolve("expected_territory.jpg");
        Path roadsPath = DIR.resolve(GoogleRoadsImage.DEFAULT_FILE_NAME);
        if (!Files.exists(map) || !Files.exists(json) || !Files.exists(expectedPath)
                || (googleRoads && !Files.exists(roadsPath))) {
            return;
        }
        BufferedImage img = ImageIO.read(map.toFile());
        JsonTerritoryLoader.LoadedTerritory loaded = new JsonTerritoryLoader().load(json);
        MapContext ctx = loaded.mapContext();
        if (ctx.width() != img.getWidth() || ctx.height() != img.getHeight()) {
            ctx = new MapContext(img.getWidth(), img.getHeight(), ctx.zoom(), ctx.center());
        }
        GoogleRoadsImage roads = googleRoads
                ? GoogleRoadsImage.from(ImageIO.read(roadsPath.toFile()), new RoadDetectorStyle().detectMotorways(img))
                : null;
        BinaryMask road = roads != null ? roads.toMask()
                : new RoadMaskCleaner().clean(new RoadDetectorStyle().detect(img)).closed();
        RenderResult result = new V2Pipeline().execute(img, loaded.geometry(), ctx, road, V2Config.defaultConfig(), roads);

        boolean[] expected = expectedMask(ImageIO.read(expectedPath.toFile()));
        long inter = 0;
        long union = 0;
        int w = img.getWidth();
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < w; x++) {
                boolean m = result.coverageMask().get(x, y) >= 128;
                boolean e = expected[y * w + x];
                if (m && e) {
                    inter++;
                }
                if (m || e) {
                    union++;
                }
            }
        }
        double iou = (double) inter / union;
        System.out.printf("CA02%s : IoU avec le détourage de référence = %.4f%n", googleRoads ? " (Routes seules)" : "", iou);
        assertTrue(iou >= 0.985, "IoU avec expected_territory.jpg insuffisant : " + iou);
    }
}
