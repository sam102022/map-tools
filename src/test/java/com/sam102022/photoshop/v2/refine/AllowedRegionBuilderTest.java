package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du constructeur de région autorisée {@link AllowedRegionBuilder}.
 * Valide l'union binaire, l'inclusion des îlots giratoires substitués et le comblement
 * sélectif des trous compacts de la chaussée.
 */
@DisplayName("Tests du composant AllowedRegionBuilder (Sprint 8)")
class AllowedRegionBuilderTest {

    @Test
    @DisplayName("Validation des arguments : rejets des valeurs nulles, négatives et incohérentes")
    void testArgumentValidation() {
        AllowedRegionBuilder builder = new AllowedRegionBuilder();
        BinaryMask mask = new BinaryMask(50, 50);
        CellLabelMap labelMap = new CellLabelMap(50, 50, new int[50 * 50], 0);

        assertThrows(NullPointerException.class, () -> builder.build(null, mask, labelMap, List.of(), 2000L));
        assertThrows(NullPointerException.class, () -> builder.build(mask, null, labelMap, List.of(), 2000L));
        assertThrows(NullPointerException.class, () -> builder.build(mask, mask, null, List.of(), 2000L));
        assertThrows(NullPointerException.class, () -> builder.build(mask, mask, labelMap, null, 2000L));
        assertThrows(IllegalArgumentException.class, () -> builder.build(mask, mask, labelMap, List.of(), -1L));

        BinaryMask mismatchMask = new BinaryMask(40, 50);
        assertThrows(IllegalArgumentException.class, () -> builder.build(mask, mismatchMask, labelMap, List.of(), 2000L));
    }

    @Test
    @DisplayName("Union binaire et comblement intégral des cavités du territoire consolidé")
    void testConsolidatedHolesFilledAndRoadUnion() {
        AllowedRegionBuilder builder = new AllowedRegionBuilder();
        int w = 60;
        int h = 60;

        // Territoire sous forme de cadre fermé avec cavité centrale
        BinaryMask consolidated = new BinaryMask(w, h);
        for (int y = 10; y <= 50; y++) {
            for (int x = 10; x <= 50; x++) {
                boolean border = (x == 10 || x == 50 || y == 10 || y == 50);
                if (border) {
                    consolidated.set(x, y, true);
                }
            }
        }
        // Le centre (11..49, 11..49) est un trou de 39x39 = 1521 pixels
        assertFalse(consolidated.get(30, 30));

        // Route traversant à l'extérieur du territoire (x: 2..5, y: 10..50)
        BinaryMask road = new BinaryMask(w, h);
        for (int y = 10; y <= 50; y++) {
            for (int x = 2; x <= 5; x++) {
                road.set(x, y, true);
            }
        }

        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);
        BinaryMask allowed = builder.build(consolidated, road, labelMap, List.of(), 2000L);

        // La cavité intérieure du territoire doit être totalement comblée
        assertTrue(allowed.get(30, 30), "La cavité intérieure du territoire doit être comblée");
        // Les routes extérieures doivent être incluses
        assertTrue(allowed.get(3, 30), "La route extérieure doit être incluse");
        // L'extérieur lointain reste vide
        assertFalse(allowed.get(0, 0), "L'extérieur reste faux");
    }

    @Test
    @DisplayName("Inclusion des îlots de ronds-points substitués d'après leur label de cellule")
    void testSubstitutedRoundaboutIslandInclusion() {
        AllowedRegionBuilder builder = new AllowedRegionBuilder();
        int w = 50;
        int h = 50;
        BinaryMask consolidated = new BinaryMask(w, h);
        BinaryMask road = new BinaryMask(w, h);

        int[] labels = new int[w * h];
        // Cellule 7 : îlot rond-point (x: 20..25, y: 20..25)
        for (int y = 20; y <= 25; y++) {
            for (int x = 20; x <= 25; x++) {
                labels[y * w + x] = 7;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(w, h, labels, 10);

        EllipseModel ell = new EllipseModel(22.5, 22.5, 5.0, 5.0, 0.0);
        Roundabout rbSubstituted = new Roundabout(7, new PixelPoint(22.5, 22.5), ell, 36.0, 0.9);
        Roundabout rbUnsubstituted = new Roundabout(3, new PixelPoint(5.0, 5.0), ell, 20.0, 0.9);

        BinaryMask allowed = builder.build(consolidated, road, labelMap, List.of(rbSubstituted), 2000L);

        // L'îlot de la cellule 7 doit être marqué dans allowed
        assertTrue(allowed.get(22, 22), "L'îlot du rond-point substitué doit être inclus");
        assertFalse(allowed.get(5, 5), "Le rond-point non substitué ne doit pas être inclus");
    }

    @Test
    @DisplayName("Comblement sélectif des trous de chaussée strictement inférieurs à roadHoleMaxArea")
    void testSelectiveRoadHoleFilling() {
        AllowedRegionBuilder builder = new AllowedRegionBuilder();
        int w = 80;
        int h = 80;

        // Une grande couronne routière de 70x70 avec bordure de 2 px
        BinaryMask road = new BinaryMask(w, h);
        for (int y = 5; y <= 75; y++) {
            for (int x = 5; x <= 75; x++) {
                boolean outerBorder = (x == 5 || x == 6 || x == 74 || x == 75 || y == 5 || y == 6 || y == 74 || y == 75);
                if (outerBorder) {
                    road.set(x, y, true);
                }
            }
        }

        // 1. Petit trou de 4x4 = 16 pixels enclavé dans un bloc de route
        for (int y = 10; y <= 20; y++) {
            for (int x = 10; x <= 20; x++) {
                road.set(x, y, true);
            }
        }
        for (int y = 14; y <= 17; y++) {
            for (int x = 14; x <= 17; x++) {
                road.set(x, y, false); // Petit trou de 16 px
            }
        }

        // Le centre (25..55, 25..55) est un grand trou de 31x31 = 961 pixels
        // Avec seuil = 100 px :
        // - Le petit trou (16 px < 100) doit être comblé
        // - Le grand trou (961 px >= 100) NE doit PAS être comblé

        BinaryMask emptyConsolidated = new BinaryMask(w, h);
        CellLabelMap labelMap = new CellLabelMap(w, h, new int[w * h], 0);

        BinaryMask allowed = builder.build(emptyConsolidated, road, labelMap, List.of(), 100L);

        // Petit trou comblé
        assertTrue(allowed.get(15, 15), "Le petit trou (16 px < 100 px) doit être comblé");
        // Grand trou conservé
        assertFalse(allowed.get(40, 40), "Le grand trou (961 px >= 100 px) ne doit pas être comblé");
    }
}
