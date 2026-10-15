package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Concordance du détourage du Territoire CA02 avec le masque de référence détouré à la main dans Photoshop
 * ({@code expected_territory_mask.png}, niveaux de gris : blanc = territoire, bord anti-crénelé).
 * <p>
 * Le masque de référence est exporté sur un canevas recadré (4464 x 3125 px) : il est recalé automatiquement sur la
 * capture (5715 x 3195 px, capture du 11/10/2026) par translation entière. Trois mesures sont contrôlées :
 * </p>
 * <ul>
 *     <li>l'IoU global des deux masques (seuil 0,5) ;</li>
 *     <li>le « bruit » du bord : somme des écarts d'opacité |sortie − référence| hors des zones écartées ;</li>
 *     <li>les défauts visibles : écarts de plus de 1 px d'épaisseur couvrant au moins {@value #MIN_DEFECT_AREA} px,
 *     hors des zones écartées.</li>
 * </ul>
 * <p>
 * Zones écartées (choix d'inclusion à revoir avec la référence, sans rapport avec la précision du bord) : le bout est
 * vers l'échangeur de l'autoroute, le sud-ouest (poche et bretelles au pied de l'échangeur) et la fourche du
 * sud-est. Les seuils figent la qualité mesurée le 12/10/2026 avec une petite marge : une régression du bord fait
 * échouer le test.
 * </p>
 * <p>
 * Mesures du 12/10/2026 : IoU 0,9941, bruit 2 232 px, 3 défauts connus (non corrigés) : angle de l'amorce coupée
 * au nord (34 px, 2735, 334), coupe en biais d'une bretelle au sud-est (112 px, 4063, 1410 ; la référence coupe
 * perpendiculairement un peu plus loin) et coin de route latérale à la sortie du giratoire ouest (34 px, 1431, 2554 ;
 * le filet blanc qui sépare les deux chaussées est bouché par le nettoyage des marquages de la capture).
 * </p>
 */
@DisplayName("Concordance avec le masque de référence CA02 (détourage Photoshop)")
class ExpectedTerritoryCA02Test {

    private static final Path DIR = Paths.get("maps/captures_maps/Territoire CA02");
    private static final String REFERENCE_FILE = "expected_territory_mask.png";
    /** Dimensions de la capture sur laquelle la référence a été détourée. */
    private static final int CAPTURE_WIDTH = 5715;
    private static final int CAPTURE_HEIGHT = 3195;
    /** IoU minimal (mesuré : 0,9941). */
    private static final double MIN_IOU = 0.993;
    /** Bruit maximal du bord hors zones écartées, en pixels pleins (mesuré : 2 232). */
    private static final double MAX_EDGE_NOISE = 2600.0;
    /** Nombre maximal de défauts visibles hors zones écartées (mesuré : 3, voir la description de la classe). */
    private static final int MAX_DEFECTS = 3;
    /** Surface minimale (px) d'un défaut visible. */
    private static final int MIN_DEFECT_AREA = 20;
    /** Demi-largeur (px) de l'affinage du recalage autour de l'estimation par les rectangles englobants. */
    private static final int REGISTRATION_RADIUS = 4;

    private static float[] output;
    private static float[] reference;
    private static boolean available;

    /**
     * Détoure CA02 une seule fois et recale la référence sur la capture.
     *
     * @throws IOException en cas d'erreur de lecture.
     */
    @BeforeAll
    static void detourAndRegister() throws IOException {
        Path map = DIR.resolve("05_style_contraste_sans_rien.png");
        Path json = DIR.resolve("01_plan_avec_territoires.json");
        Path roadsPath = DIR.resolve(GoogleRoadsImage.DEFAULT_FILE_NAME);
        Path referencePath = DIR.resolve(REFERENCE_FILE);
        available = Files.exists(map) && Files.exists(json) && Files.exists(roadsPath) && Files.exists(referencePath);
        if (!available) {
            return;
        }
        BufferedImage img = ImageIO.read(map.toFile());
        available = img.getWidth() == CAPTURE_WIDTH && img.getHeight() == CAPTURE_HEIGHT;
        if (!available) {
            return; // référence établie sur une autre capture
        }
        JsonTerritoryLoader.LoadedTerritory loaded = new JsonTerritoryLoader().load(json);
        MapContext ctx = loaded.mapContext();
        if (ctx.width() != img.getWidth() || ctx.height() != img.getHeight()) {
            ctx = new MapContext(img.getWidth(), img.getHeight(), ctx.zoom(), ctx.center());
        }
        GoogleRoadsImage roads = GoogleRoadsImage.from(ImageIO.read(roadsPath.toFile()),
                new RoadDetectorStyle().detectMotorways(img));
        RenderResult result = new V2Pipeline().execute(img, loaded.geometry(), ctx, roads.toMask(),
                V2Config.defaultConfig(), roads);
        output = opacity(result.coverageMask());
        reference = register(ImageIO.read(referencePath.toFile()), output);
    }

    /**
     * L'IoU global avec la référence ne doit pas baisser.
     */
    @Test
    @DisplayName("IoU global >= 0,993")
    void testGlobalIou() {
        assumeTrue(available, "Capture CA02 du 11/10 ou masque de référence absent.");
        long inter = 0;
        long union = 0;
        for (int i = 0; i < output.length; i++) {
            boolean m = output[i] >= 0.5f;
            boolean e = reference[i] >= 0.5f;
            inter += m && e ? 1 : 0;
            union += m || e ? 1 : 0;
        }
        double iou = (double) inter / union;
        System.out.printf(Locale.ROOT, "CA02 : IoU avec le masque de référence = %.4f%n", iou);
        assertTrue(iou >= MIN_IOU, "IoU avec la référence insuffisant : " + iou);
    }

    /**
     * Le bruit du bord (écarts d'opacité cumulés) hors des zones écartées ne doit pas augmenter.
     */
    @Test
    @DisplayName("Bruit du bord <= 2 600 px hors zones écartées")
    void testEdgeNoise() {
        assumeTrue(available, "Capture CA02 du 11/10 ou masque de référence absent.");
        double noise = 0.0;
        for (int i = 0; i < output.length; i++) {
            if (!excluded(i % CAPTURE_WIDTH, i / CAPTURE_WIDTH)) {
                noise += Math.abs(output[i] - reference[i]);
            }
        }
        System.out.printf(Locale.ROOT, "CA02 : bruit du bord hors zones écartées = %.0f px%n", noise);
        assertTrue(noise <= MAX_EDGE_NOISE, "Bruit du bord trop élevé : " + noise);
    }

    /**
     * Les défauts visibles (écarts de plus de 1 px d'épaisseur) hors des zones écartées ne doivent pas se multiplier.
     */
    @Test
    @DisplayName("Au plus 3 défauts visibles hors zones écartées")
    void testVisibleDefects() {
        assumeTrue(available, "Capture CA02 du 11/10 ou masque de référence absent.");
        boolean[] diff = new boolean[output.length];
        for (int i = 0; i < output.length; i++) {
            diff[i] = (output[i] >= 0.5f) != (reference[i] >= 0.5f) && !excluded(i % CAPTURE_WIDTH, i / CAPTURE_WIDTH);
        }
        List<String> defects = defects(open2x2(diff));
        System.out.println("CA02 : défauts visibles hors zones écartées = " + defects);
        assertTrue(defects.size() <= MAX_DEFECTS, "Trop de défauts visibles : " + defects);
    }

    /**
     * Opacités [0, 1] de la couverture produite.
     *
     * @param coverage Couverture pleine image.
     * @return Opacités, ligne par ligne.
     */
    private static float[] opacity(CoverageMask coverage) {
        float[] values = new float[CAPTURE_WIDTH * CAPTURE_HEIGHT];
        for (int y = 0; y < CAPTURE_HEIGHT; y++) {
            for (int x = 0; x < CAPTURE_WIDTH; x++) {
                values[y * CAPTURE_WIDTH + x] = coverage.get(x, y) / 255.0f;
            }
        }
        return values;
    }

    /**
     * Recale le masque de référence sur la capture : estimation par les coins des rectangles englobants, puis
     * translation entière qui maximise le recouvrement dans un voisinage de {@value #REGISTRATION_RADIUS} px.
     *
     * @param image  Masque de référence (niveaux de gris).
     * @param output Opacités produites.
     * @return Opacités de référence dans le repère de la capture.
     */
    private static float[] register(BufferedImage image, float[] output) {
        Raster raster = image.getRaster();
        int w = image.getWidth();
        int h = image.getHeight();
        float[] local = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                local[y * w + x] = raster.getSample(x, y, 0) / 255.0f;
            }
        }
        int[] refBox = boundingBox(local, w, h);
        int[] outBox = boundingBox(output, CAPTURE_WIDTH, CAPTURE_HEIGHT);
        int bestDx = outBox[0] - refBox[0];
        int bestDy = outBox[1] - refBox[1];
        long bestScore = -1;
        int guessDx = bestDx;
        int guessDy = bestDy;
        for (int dy = guessDy - REGISTRATION_RADIUS; dy <= guessDy + REGISTRATION_RADIUS; dy++) {
            for (int dx = guessDx - REGISTRATION_RADIUS; dx <= guessDx + REGISTRATION_RADIUS; dx++) {
                long score = overlap(local, w, refBox, output, dx, dy);
                if (score > bestScore) {
                    bestScore = score;
                    bestDx = dx;
                    bestDy = dy;
                }
            }
        }
        System.out.printf(Locale.ROOT, "CA02 : masque de référence recalé de (%d, %d) px%n", bestDx, bestDy);
        return shift(local, w, h, bestDx, bestDy);
    }

    /**
     * Rectangle englobant des pixels d'opacité au moins 0,5.
     *
     * @param values Opacités.
     * @param w      Largeur.
     * @param h      Hauteur.
     * @return {xmin, ymin, xmax, ymax}.
     */
    private static int[] boundingBox(float[] values, int w, int h) {
        int[] box = {w, h, -1, -1};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (values[y * w + x] >= 0.5f) {
                    box[0] = Math.min(box[0], x);
                    box[1] = Math.min(box[1], y);
                    box[2] = Math.max(box[2], x);
                    box[3] = Math.max(box[3], y);
                }
            }
        }
        return box;
    }

    /**
     * Nombre de pixels (un sur deux en x et en y) à la fois dans la référence décalée et dans la sortie.
     *
     * @param local  Opacités de référence (repère du canevas).
     * @param w      Largeur du canevas.
     * @param box    Rectangle englobant de la référence.
     * @param output Opacités produites.
     * @param dx     Décalage en x.
     * @param dy     Décalage en y.
     * @return Recouvrement.
     */
    private static long overlap(float[] local, int w, int[] box, float[] output, int dx, int dy) {
        long count = 0;
        for (int y = box[1]; y <= box[3]; y += 2) {
            int oy = y + dy;
            for (int x = box[0]; x <= box[2]; x += 2) {
                int ox = x + dx;
                boolean inside = ox >= 0 && oy >= 0 && ox < CAPTURE_WIDTH && oy < CAPTURE_HEIGHT;
                if (inside && local[y * w + x] >= 0.5f && output[oy * CAPTURE_WIDTH + ox] >= 0.5f) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Place les opacités de référence dans le repère de la capture.
     *
     * @param local Opacités du canevas.
     * @param w     Largeur du canevas.
     * @param h     Hauteur du canevas.
     * @param dx    Décalage en x.
     * @param dy    Décalage en y.
     * @return Opacités dans le repère de la capture (0 hors du canevas).
     */
    private static float[] shift(float[] local, int w, int h, int dx, int dy) {
        float[] shifted = new float[CAPTURE_WIDTH * CAPTURE_HEIGHT];
        for (int y = 0; y < h; y++) {
            int oy = y + dy;
            for (int x = 0; x < w; x++) {
                int ox = x + dx;
                if (ox >= 0 && oy >= 0 && ox < CAPTURE_WIDTH && oy < CAPTURE_HEIGHT) {
                    shifted[oy * CAPTURE_WIDTH + ox] = local[y * w + x];
                }
            }
        }
        return shifted;
    }

    /**
     * Indique si un pixel de la capture est dans une zone écartée (choix d'inclusion à revoir).
     *
     * @param x Abscisse dans la capture.
     * @param y Ordonnée dans la capture.
     * @return true pour le bout est, le sud-ouest et la fourche du sud-est.
     */
    private static boolean excluded(int x, int y) {
        boolean eastEnd = x > 3990 && y < 1250;
        boolean southWest = x < 2060 && y > 2690;
        boolean southEastFork = Math.hypot(x - 3639.0, y - 1720.0) < 90.0;
        return eastEnd || southWest || southEastFork;
    }

    /**
     * Ouverture par un carré de 2 x 2 px : retire les liserés de 1 px d'épaisseur (précision du tracé à la main).
     *
     * @param mask Masque.
     * @return Masque ouvert.
     */
    private static boolean[] open2x2(boolean[] mask) {
        int w = CAPTURE_WIDTH;
        int h = CAPTURE_HEIGHT;
        boolean[] eroded = new boolean[mask.length];
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 1; x++) {
                int i = y * w + x;
                eroded[i] = mask[i] && mask[i + 1] && mask[i + w] && mask[i + w + 1];
            }
        }
        boolean[] opened = new boolean[mask.length];
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 1; x++) {
                int i = y * w + x;
                if (eroded[i]) {
                    opened[i] = true;
                    opened[i + 1] = true;
                    opened[i + w] = true;
                    opened[i + w + 1] = true;
                }
            }
        }
        return opened;
    }

    /**
     * Composantes (8-connexité) d'au moins {@value #MIN_DEFECT_AREA} px.
     *
     * @param mask Masque des écarts.
     * @return Description « surface px (x, y) » de chaque défaut.
     */
    private static List<String> defects(boolean[] mask) {
        int w = CAPTURE_WIDTH;
        boolean[] visited = new boolean[mask.length];
        int[] queue = new int[mask.length];
        List<String> found = new ArrayList<>();
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || visited[start]) {
                continue;
            }
            int tail = 0;
            int head = 0;
            queue[tail++] = start;
            visited[start] = true;
            long sx = 0;
            long sy = 0;
            while (head < tail) {
                int p = queue[head++];
                int x = p % w;
                int y = p / w;
                sx += x;
                sy += y;
                tail = visitNeighbours(mask, visited, queue, tail, x, y);
            }
            if (tail >= MIN_DEFECT_AREA) {
                found.add(String.format(Locale.ROOT, "%d px (%d, %d)", tail, sx / tail, sy / tail));
            }
        }
        return found;
    }

    /**
     * Ajoute à la file les 8 voisins non visités d'un pixel qui appartiennent au masque.
     *
     * @param mask    Masque.
     * @param visited Pixels visités.
     * @param queue   File.
     * @param tail    Fin de file.
     * @param x       Abscisse.
     * @param y       Ordonnée.
     * @return Nouvelle fin de file.
     */
    private static int visitNeighbours(boolean[] mask, boolean[] visited, int[] queue, int tail, int x, int y) {
        int end = tail;
        for (int ny = Math.max(0, y - 1); ny <= Math.min(CAPTURE_HEIGHT - 1, y + 1); ny++) {
            for (int nx = Math.max(0, x - 1); nx <= Math.min(CAPTURE_WIDTH - 1, x + 1); nx++) {
                int q = ny * CAPTURE_WIDTH + nx;
                if (mask[q] && !visited[q]) {
                    visited[q] = true;
                    queue[end++] = q;
                }
            }
        }
        return end;
    }
}
