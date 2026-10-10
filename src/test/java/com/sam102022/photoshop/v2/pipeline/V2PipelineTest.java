package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.TerritoryGeometry;
import com.sam102022.photoshop.v2.render.RenderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de l'orchestrateur de bout en bout {@link V2Pipeline}.
 */
@DisplayName("Tests unitaires de l'orchestrateur de bout en bout V2Pipeline")
class V2PipelineTest {

    @Test
    @DisplayName("Exécution fonctionnelle minimale sur canevas synthétique")
    void testMinimalPipelineExecution() {
        int w = 200;
        int h = 200;

        BufferedImage mapImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BinaryMask roadMask = new BinaryMask(w, h);

        // Dessiner un carroyage routier simple
        for (int i = 0; i < w; i++) {
            roadMask.set(i, 50, true);
            roadMask.set(i, 150, true);
            roadMask.set(50, i, true);
            roadMask.set(150, i, true);
        }

        // Polygone englobant centré
        MapContext mapContext = new MapContext(w, h, 10, new GeoCoordinate(0.0, 0.0));
        List<GeoCoordinate> ring = List.of(
                new GeoCoordinate(-0.01, -0.01),
                new GeoCoordinate(0.01, -0.01),
                new GeoCoordinate(0.01, 0.01),
                new GeoCoordinate(-0.01, 0.01)
        );
        TerritoryGeometry geometry = new TerritoryGeometry(List.of(ring), List.of());

        V2Pipeline pipeline = new V2Pipeline();
        V2Config config = V2Config.defaultConfig();

        RenderResult result = pipeline.execute(mapImage, geometry, mapContext, roadMask, config);

        assertNotNull(result);
        assertEquals(w, result.clipped().getWidth());
        assertEquals(h, result.clipped().getHeight());
        assertEquals(w, result.coverageMask().getWidth());
        assertEquals(h, result.coverageMask().getHeight());
    }

    @Test
    @DisplayName("Rejet des paramètres obligatoires nuls")
    void testRejectsNullParameters() {
        V2Pipeline pipeline = new V2Pipeline();
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        MapContext ctx = new MapContext(10, 10, 10, new GeoCoordinate(0, 0));
        TerritoryGeometry geom = new TerritoryGeometry(List.of(List.of(new GeoCoordinate(0, 0))), List.of());
        BinaryMask road = new BinaryMask(10, 10);
        V2Config cfg = V2Config.defaultConfig();

        assertThrows(NullPointerException.class, () -> pipeline.execute(null, geom, ctx, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, null, ctx, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, null, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, ctx, (BinaryMask) null, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, ctx, road, null));
    }

    @Test
    @DisplayName("Rejet des masques routiers aux dimensions incompatibles avec la carte")
    void testRejectsMismatchedDimensions() {
        V2Pipeline pipeline = new V2Pipeline();
        BufferedImage map = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        MapContext ctx = new MapContext(100, 100, 10, new GeoCoordinate(0, 0));
        TerritoryGeometry geom = new TerritoryGeometry(List.of(List.of(new GeoCoordinate(0, 0))), List.of());
        BinaryMask roadMismatch = new BinaryMask(50, 50);
        V2Config cfg = V2Config.defaultConfig();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> pipeline.execute(map, geom, ctx, roadMismatch, cfg));
        assertTrue(ex.getMessage().contains("Dimensions incompatibles"));
    }
}
