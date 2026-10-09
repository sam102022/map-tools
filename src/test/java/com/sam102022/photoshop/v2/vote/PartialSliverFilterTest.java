package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires du filtre des lamelles partielles fines {@link PartialSliverFilter}.
 */
@DisplayName("Tests unitaires du filtre des lamelles partielles PartialSliverFilter")
class PartialSliverFilterTest {

    /**
     * Remplit un rectangle [x0, x1) x [y0, y1) dans le masque.
     *
     * @param mask Masque cible.
     * @param x0   Abscisse de départ incluse.
     * @param y0   Ordonnée de départ incluse.
     * @param x1   Abscisse de fin exclue.
     * @param y1   Ordonnée de fin exclue.
     */
    private static void fillRect(BinaryMask mask, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                mask.set(x, y, true);
            }
        }
    }

    /**
     * Une lamelle de 12 px (débord de polygone le long d'une route) est retirée, un bloc de 60 px est conservé.
     */
    @Test
    @DisplayName("Retire une lamelle de 12 px et conserve un bloc épais de 60 px")
    void testRemovesThinSliverAndKeepsThickBlock() {
        BinaryMask candidates = new BinaryMask(200, 200);
        fillRect(candidates, 10, 10, 22, 190);    // lamelle 12 px x 180 px
        fillRect(candidates, 100, 100, 160, 160); // bloc 60 x 60 px

        BinaryMask kept = new PartialSliverFilter().keepThickComponents(candidates, 20.0);

        assertEquals(3600L, kept.countActivePixels(), "Seul le bloc épais doit être conservé.");
        assertEquals(false, kept.get(15, 100));
        assertEquals(true, kept.get(130, 130));
    }

    /**
     * Un seuil nul désactive le filtre (comportement de l'étalon Python v7).
     */
    @Test
    @DisplayName("Un seuil nul conserve toutes les composantes")
    void testZeroThresholdKeepsEverything() {
        BinaryMask candidates = new BinaryMask(50, 50);
        fillRect(candidates, 5, 5, 8, 45);

        BinaryMask kept = new PartialSliverFilter().keepThickComponents(candidates, 0.0);

        assertEquals(candidates.countActivePixels(), kept.countActivePixels());
    }

    /**
     * Le masque des candidats ne peut pas être null.
     */
    @Test
    @DisplayName("Rejet d'un masque de candidats null")
    void testRejectsNullCandidates() {
        assertThrows(NullPointerException.class, () -> new PartialSliverFilter().keepThickComponents(null, 20.0));
    }
}
