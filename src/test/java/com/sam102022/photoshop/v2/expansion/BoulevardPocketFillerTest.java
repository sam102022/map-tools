package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du comblement des poches le long des boulevards {@link BoulevardPocketFiller}.
 */
@DisplayName("Tests unitaires du combleur de poches BoulevardPocketFiller")
class BoulevardPocketFillerTest {

    /**
     * Remplit un rectangle [x0, x1) x [y0, y1).
     *
     * @param m  Masque.
     * @param x0 Abscisse de début.
     * @param y0 Ordonnée de début.
     * @param x1 Abscisse de fin (exclue).
     * @param y1 Ordonnée de fin (exclue).
     */
    private static void rect(BinaryMask m, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                m.set(x, y, true);
            }
        }
    }

    /**
     * Scène : territoire en haut (y &lt; 100), contre-allée fine (y 100..106), îlot (y 106..130), boulevard large
     * (y 130..160). L'îlot entre contre-allée et boulevard et le boulevard longé (jusqu'à son bord opposé) doivent
     * être ajoutés ; l'îlot au-delà du boulevard non.
     */
    @Test
    @DisplayName("Ajoute l'îlot entre contre-allée et boulevard et le boulevard longé, pas au-delà")
    void testFillsPocketUpToBoulevard() {
        int w = 200;
        int h = 220;
        BinaryMask road = new BinaryMask(w, h);
        rect(road, 0, 100, w, 106);   // contre-allée (demi-largeur 3)
        rect(road, 0, 130, w, 160);   // boulevard (demi-largeur 15)
        rect(road, 60, 106, 66, 130); // bretelle reliant les deux
        CellLabelingResult labels = new CellLabeler().label(road);
        BinaryMask retained = new BinaryMask(w, h);
        rect(retained, 0, 0, w, 100);
        BinaryMask consolidatedMask = retained.copy();
        rect(consolidatedMask, 0, 100, w, 106);
        ConsolidatedMask consolidated = new ConsolidatedMask(w, h, consolidatedMask, new CropWindow(0, 0, w, h));

        ConsolidatedMask filled = new BoulevardPocketFiller().fill(consolidated, road, retained,
                labels.labelMap(), labels.cells(), 25);

        assertTrue(filled.mask().get(100, 118), "L'îlot entre contre-allée et boulevard doit être ajouté.");
        assertTrue(filled.mask().get(63, 118), "La bretelle doit être ajoutée.");
        assertTrue(filled.mask().get(100, 145), "Le boulevard longé est inclus.");
        assertTrue(filled.mask().get(100, 159), "Le boulevard est inclus jusqu'à son bord opposé.");
        assertFalse(filled.mask().get(100, 190), "L'îlot au-delà du boulevard n'est pas ajouté.");
        assertFalse(consolidated.mask().get(100, 118), "Le masque d'origine ne doit pas être modifié.");
    }

    /**
     * Sans chaussée large, ou avec un rayon nul, le masque est inchangé.
     */
    @Test
    @DisplayName("Sans boulevard ou rayon nul, le masque consolidé est retourné tel quel")
    void testNoWideRoadOrZeroRadius() {
        int w = 100;
        int h = 100;
        BinaryMask road = new BinaryMask(w, h);
        rect(road, 0, 50, w, 54);
        CellLabelingResult labels = new CellLabeler().label(road);
        BinaryMask retained = new BinaryMask(w, h);
        rect(retained, 0, 0, w, 50);
        ConsolidatedMask consolidated = new ConsolidatedMask(w, h, retained.copy(), new CropWindow(0, 0, w, h));
        BoulevardPocketFiller filler = new BoulevardPocketFiller();

        assertSame(consolidated, filler.fill(consolidated, road, retained, labels.labelMap(), labels.cells(), 25));
        assertSame(consolidated, filler.fill(consolidated, road, retained, labels.labelMap(), labels.cells(), 0));
        assertEquals(5000L, consolidated.mask().countActivePixels());
    }
}
