package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.edge.RoadEdgeField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires de la capture Google « Routes seules » {@link GoogleRoadsImage}.
 */
@DisplayName("Tests unitaires de GoogleRoadsImage")
class GoogleRoadsImageTest {

    /**
     * Plan 120 x 80 : route horizontale blanche (lignes 20 à 39) avec un marquage sombre de 1 px (ligne 30) et un
     * bord anti-crénelé (ligne 40 à mi-gris) ; sentier blanc de 2 px (colonnes 60-61, lignes 50 à 79).
     *
     * @return Capture synthétique.
     */
    private static BufferedImage plan() {
        BufferedImage img = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 120; x++) {
                int v = 0;
                if (y >= 20 && y < 40) {
                    v = y == 30 ? 40 : 255;
                } else if (y == 40) {
                    v = 128;
                } else if (y >= 50 && (x == 60 || x == 61)) {
                    v = 255;
                }
                img.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    /**
     * Le marquage intérieur est effacé, le sentier trop fin disparaît, le bord anti-crénelé est conservé.
     */
    @Test
    @DisplayName("Nettoyage : marquage effacé, sentier retiré, bord conservé")
    void testCleaning() {
        GoogleRoadsImage roads = GoogleRoadsImage.from(plan());
        BinaryMask mask = roads.toMask();

        assertTrue(mask.get(50, 30), "le marquage sombre intérieur doit être effacé");
        assertTrue(mask.get(50, 25));
        assertFalse(mask.get(60, 65), "un trait de 2 px n'est pas une route");
        assertEquals(128 / 255.0, roads.value(50, 40), 0.01, "le bord anti-crénelé doit être conservé");
        assertEquals(0.0, roads.value(50, 10), 1e-6);
    }

    /**
     * Les pixels exclus sont retirés avant nettoyage ; le champ de bord lit la capture dans le repère local.
     */
    @Test
    @DisplayName("Exclusion et champ de bord")
    void testExclusionAndEdgeField() {
        BinaryMask excluded = new BinaryMask(120, 80);
        for (int y = 0; y < 80; y++) {
            for (int x = 0; x < 30; x++) {
                excluded.set(x, y, true);
            }
        }
        GoogleRoadsImage roads = GoogleRoadsImage.from(plan(), excluded);

        assertFalse(roads.toMask().get(10, 25));
        assertTrue(roads.toMask().get(50, 25));
        RoadEdgeField field = new RoadEdgeField(roads, new CropWindow(40, 10, 60, 50));
        assertEquals(1.0, field.sample(10, 15), 1e-6);
        assertEquals(0.5, field.sample(10, 30), 0.02, "bord gris de la chaussée (iso-ligne 0,5)");
        assertThrows(IllegalArgumentException.class, () -> GoogleRoadsImage.from(plan(), new BinaryMask(10, 10)));
    }
}
