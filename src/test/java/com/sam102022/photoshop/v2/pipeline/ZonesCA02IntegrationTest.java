package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;
import com.sam102022.photoshop.v2.road.RoadMaskCleaner;
import com.sam102022.photoshop.v2.zone.ZoneColor;
import com.sam102022.photoshop.v2.zone.ZoneExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test d'intégration du détourage des zones sur le bord intérieur des routes (Territoire CA02,
 * {@code 02_plan_avec_zones.png} : zones rouge, verte, mauve, orange et cyan).
 */
@DisplayName("Détourage des zones CA02 sur le bord intérieur des routes")
class ZonesCA02IntegrationTest {

    private static final Path DIR = Paths.get("maps/captures_maps/Territoire CA02");

    /**
     * Les zones présentes sont détourées, ne se recouvrent pas, et les routes qui les bordent sont exclues :
     * les pixels de chaussée situés à moins de 6 px du tracé de la zone sont très majoritairement hors du masque
     * (mesuré : 4 à 15 % inclus, correspondant aux débouchés des rues intérieures et aux tronçons où le tracé
     * coupe des îlots).
     *
     * @throws IOException en cas d'erreur de lecture.
     */
    @Test
    @DisplayName("Zones disjointes, routes frontières exclues")
    void testZonesOnInnerRoadEdges() throws IOException {
        Path mapPath = DIR.resolve("05_style_contraste_sans_rien.png");
        Path zonesPath = DIR.resolve("02_plan_avec_zones.png");
        assumeTrue(Files.exists(mapPath) && Files.exists(zonesPath), "Données CA02 absentes.");
        BufferedImage map = ImageIO.read(mapPath.toFile());
        BufferedImage zonesImg = ImageIO.read(zonesPath.toFile());
        BinaryMask road = new RoadMaskCleaner().clean(new RoadDetectorStyle().detect(map)).closed();

        ZoneExtractor extractor = new ZoneExtractor();
        V2ZonePipeline pipeline = new V2ZonePipeline();
        Map<ZoneColor, BinaryMask> masks = new EnumMap<>(ZoneColor.class);
        for (ZoneColor zone : ZoneColor.values()) {
            List<BinaryMask> parts = extractor.extract(zonesImg, zone);
            if (parts.isEmpty()) {
                continue;
            }
            RenderResult result = pipeline.execute(map, parts, road, V2Config.defaultConfig());
            masks.put(zone, result.coverageMask().toBinaryMask(128));
            double boundaryRoadIncluded = boundaryRoadInclusion(result.coverageMask().toBinaryMask(128), parts, road);
            System.out.printf("%s : %d px, chaussée frontière incluse %.1f %%%n", zone.fileLabel(),
                    masks.get(zone).countActivePixels(), 100 * boundaryRoadIncluded);
            assertTrue(boundaryRoadIncluded < 0.25, zone + " : route frontière incluse à " + boundaryRoadIncluded);
        }
        assertEquals(List.of(ZoneColor.ROUGE, ZoneColor.VERTE, ZoneColor.MAUVE, ZoneColor.ORANGE, ZoneColor.CYAN),
                List.copyOf(masks.keySet()));
        for (ZoneColor a : masks.keySet()) {
            for (ZoneColor b : masks.keySet()) {
                if (a.compareTo(b) < 0) {
                    long overlap = masks.get(a).and(masks.get(b)).countActivePixels();
                    assertTrue(overlap < 200, a + " et " + b + " se recouvrent sur " + overlap + " px");
                }
            }
        }
    }

    /**
     * Proportion des pixels de chaussée proches du tracé de la zone (moins de 6 px du bord de la zone tracée)
     * qui sont inclus dans le masque détouré.
     *
     * @param mask  Masque détouré.
     * @param parts Parties de la zone tracée.
     * @param road  Masque routier.
     * @return Proportion incluse (0 à 1).
     */
    private static double boundaryRoadInclusion(BinaryMask mask, List<BinaryMask> parts, BinaryMask road) {
        long near = 0;
        long included = 0;
        int r = 6;
        for (BinaryMask part : parts) {
            for (int y = r; y < part.getHeight() - r; y += 2) {
                for (int x = r; x < part.getWidth() - r; x += 2) {
                    boolean p = part.get(x, y);
                    boolean nearLine = p != part.get(x + r, y) || p != part.get(x - r, y)
                            || p != part.get(x, y + r) || p != part.get(x, y - r);
                    if (road.get(x, y) && nearLine) {
                        near++;
                        if (mask.get(x, y)) {
                            included++;
                        }
                    }
                }
            }
        }
        return near == 0 ? 0.0 : (double) included / near;
    }
}
