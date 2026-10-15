package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Suite de tests unitaires pour le composant {@link BoundingBoxCropper}.
 */
@DisplayName("Validation du découpeur et calculateur de boîte englobante BoundingBoxCropper")
class BoundingBoxCropperTest {

    /**
     * Vérifie le calcul de la fenêtre de recadrage avec la marge de sécurité de 90 pixels.
     */
    @Test
    @DisplayName("Calcul de la fenêtre de recadrage avec marge de 90px")
    void testComputeCropWindowWithMargin() {
        BoundingBoxCropper cropper = new BoundingBoxCropper();

        List<PixelPoint> points = List.of(
                new PixelPoint(200, 300),
                new PixelPoint(400, 300),
                new PixelPoint(400, 500),
                new PixelPoint(200, 500)
        );

        CropWindow window = cropper.computeCropWindow(points, 1000, 1000, 90);

        assertEquals(110, window.x0());
        assertEquals(210, window.y0());
        assertEquals(380, window.width());
        assertEquals(380, window.height());
        assertEquals(490, window.x1());
        assertEquals(590, window.y1());
    }

    /**
     * Vérifie le bornage strict de la boîte englobante aux limites de l'image source.
     */
    @Test
    @DisplayName("Bornage strict aux limites de l'image")
    void testComputeCropWindowClamped() {
        BoundingBoxCropper cropper = new BoundingBoxCropper();

        List<PixelPoint> points = List.of(
                new PixelPoint(10, 20),
                new PixelPoint(95, 80)
        );

        CropWindow window = cropper.computeCropWindow(points, 100, 100, 50);

        assertEquals(0, window.x0());
        assertEquals(0, window.y0());
        assertEquals(100, window.width());
        assertEquals(100, window.height());
    }

    /**
     * Vérifie la découpe spatiale exacte d'un sous-masque binaire BinaryMask.
     */
    @Test
    @DisplayName("Découpe d'un sous-masque BinaryMask")
    void testCropBinaryMask() {
        BoundingBoxCropper cropper = new BoundingBoxCropper();

        BinaryMask source = new BinaryMask(10, 10);
        source.set(4, 4, true);

        CropWindow window = new CropWindow(2, 2, 5, 5);
        BinaryMask cropped = cropper.crop(source, window);

        assertEquals(5, cropped.getWidth());
        assertEquals(5, cropped.getHeight());

        assertTrue(cropped.get(2, 2));
        assertFalse(cropped.get(0, 0));
        assertFalse(cropped.get(4, 4));
    }

    /**
     * Vérifie le rejet par exception des paramètres invalides ou incohérents.
     */
    @Test
    @DisplayName("Validation défensive des paramètres invalides")
    void testValidation() {
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        List<PixelPoint> pts = List.of(new PixelPoint(10, 10));

        assertThrows(IllegalArgumentException.class, () -> cropper.computeCropWindow(null, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> cropper.computeCropWindow(List.of(), 100, 100));
        assertThrows(IllegalArgumentException.class, () -> cropper.computeCropWindow(pts, 0, 100));
        assertThrows(IllegalArgumentException.class, () -> cropper.computeCropWindow(pts, 100, -1));
        assertThrows(IllegalArgumentException.class, () -> cropper.computeCropWindow(pts, 100, 100, -5));

        CropWindow window = new CropWindow(50, 50, 100, 100);
        BinaryMask smallMask = new BinaryMask(50, 50);
        assertThrows(IllegalArgumentException.class, () -> cropper.crop(smallMask, window));
        assertThrows(IllegalArgumentException.class, () -> cropper.crop(null, window));
        assertThrows(IllegalArgumentException.class, () -> cropper.crop(smallMask, null));
    }
}
