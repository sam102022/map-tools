package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires du record immuable {@link RenderResult}.
 */
@DisplayName("Tests unitaires du contrat immuable RenderResult")
class RenderResultTest {

    @Test
    @DisplayName("Création valide d'un RenderResult et accès aux accesseurs")
    void testValidCreation() {
        BufferedImage clipped = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage overlay = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask coverageMask = new CoverageMask(10, 10);

        RenderResult result = new RenderResult(clipped, mask, overlay, coverageMask);

        assertNotNull(result);
        assertEquals(clipped, result.clipped());
        assertEquals(mask, result.mask());
        assertEquals(overlay, result.overlay());
        assertEquals(coverageMask, result.coverageMask());
    }

    @Test
    @DisplayName("Rejet des paramètres null dans RenderResult")
    void testRejectsNullParameters() {
        BufferedImage clipped = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage overlay = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask coverageMask = new CoverageMask(10, 10);

        assertThrows(IllegalArgumentException.class, () -> new RenderResult(null, mask, overlay, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, null, overlay, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, mask, null, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, mask, overlay, null));
    }
}
