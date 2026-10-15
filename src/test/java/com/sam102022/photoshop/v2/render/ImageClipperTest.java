package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires du découpeur et assembleur {@link ImageClipper}.
 */
@DisplayName("Tests unitaires du découpeur et assembleur ImageClipper")
class ImageClipperTest {

    @Test
    @DisplayName("Assemblage conforme de clipped (ARGB), mask (BYTE_GRAY) et overlay (RGB)")
    void testClipComposition() {
        int w = 10;
        int h = 10;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        // Peindre la carte en bleu
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                map.setRGB(x, y, 0x0000FF);
            }
        }

        CoverageMask coverage = new CoverageMask(w, h);
        // Définir un bloc 4x4 au centre avec couverture 255
        for (int y = 3; y <= 6; y++) {
            for (int x = 3; x <= 6; x++) {
                coverage.set(x, y, 255);
            }
        }
        // Pixel avec couverture partielle à (2, 2)
        coverage.set(2, 2, 128);

        ImageClipper clipper = new ImageClipper();
        RenderResult result = clipper.clip(map, coverage);

        assertNotNull(result);

        // 1. Clipped (ARGB)
        BufferedImage clipped = result.clipped();
        assertEquals(BufferedImage.TYPE_INT_ARGB, clipped.getType());
        // Pixel intérieur : alpha 255, bleu conservé
        int argbInside = clipped.getRGB(4, 4);
        assertEquals(255, (argbInside >>> 24) & 0xFF);
        assertEquals(0x0000FF, argbInside & 0x00FFFFFF);

        // Pixel extérieur : alpha 0 (transparent)
        int argbOutside = clipped.getRGB(0, 0);
        assertEquals(0, (argbOutside >>> 24) & 0xFF);

        // Pixel partiel à (2, 2) : alpha = (255 * 128 + 127) / 255 = 128
        int argbPartial = clipped.getRGB(2, 2);
        assertEquals(128, (argbPartial >>> 24) & 0xFF);

        // 2. Mask (BYTE_GRAY)
        BufferedImage mask = result.mask();
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, mask.getType());
        assertEquals(255, mask.getRaster().getSample(4, 4, 0));
        assertEquals(0, mask.getRaster().getSample(0, 0, 0));
        assertEquals(128, mask.getRaster().getSample(2, 2, 0));

        // 3. Overlay (RGB) : bordure rouge vif #FF0000 sur le pourtour du bloc
        BufferedImage overlay = result.overlay();
        assertEquals(BufferedImage.TYPE_INT_RGB, overlay.getType());
        // Le pixel (3, 3) est sur le bord du bloc 4x4 -> doit être rouge
        assertEquals(0xFF0000, overlay.getRGB(3, 3) & 0xFFFFFF);
        // Le pixel intérieur (4, 4) n'est pas sur le bord (tous voisins actifs) -> couleur d'origine bleue
        assertEquals(0x0000FF, overlay.getRGB(4, 4) & 0xFFFFFF);
    }

    @Test
    @DisplayName("Rejet des arguments invalides ou de dimensions incohérentes")
    void testInvalidInputs() {
        ImageClipper clipper = new ImageClipper();
        BufferedImage img10 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask cov10 = new CoverageMask(10, 10);
        CoverageMask cov20 = new CoverageMask(20, 20);

        assertThrows(NullPointerException.class, () -> clipper.clip(null, cov10));
        assertThrows(NullPointerException.class, () -> clipper.clip(img10, null));
        assertThrows(IllegalArgumentException.class, () -> clipper.clip(img10, cov20));
    }
}
