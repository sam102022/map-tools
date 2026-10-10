package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MorphologyOpsTest {

    @Test
    @DisplayName("Dilatation d'un pixel isolé")
    void testDilateSinglePixel() {
        BinaryMask mask = new BinaryMask(7, 7);
        mask.set(3, 3, true);

        BinaryMask dilated = MorphologyOps.dilate(mask, 1);
        // Le pixel central + ses 8 voisins (rayon 1 boîte) doivent être actifs = 9 pixels
        assertEquals(9, dilated.countActivePixels());
        assertTrue(dilated.get(3, 3));
        assertTrue(dilated.get(2, 2));
        assertTrue(dilated.get(4, 4));
        assertFalse(dilated.get(1, 1));
    }

    @Test
    @DisplayName("Érosion d'un carré de 3x3 pixels")
    void testErodeSquare() {
        BinaryMask mask = new BinaryMask(7, 7);
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) {
                mask.set(x, y, true);
            }
        }
        assertEquals(9, mask.countActivePixels());

        BinaryMask eroded = MorphologyOps.erode(mask, 1);
        // Seul le pixel central doit survivre car tous ses voisins étaient actifs
        assertEquals(1, eroded.countActivePixels());
        assertTrue(eroded.get(3, 3));
    }

    @Test
    @DisplayName("Fermeture morphologique pour combler une brèche de 2 pixels")
    void testCloseFillsGap() {
        BinaryMask mask = new BinaryMask(10, 10);
        // Ligne horizontale avec un trou au milieu (x=4 et x=5 à false)
        for (int x = 0; x <= 3; x++) {
            mask.set(x, 5, true);
        }
        for (int x = 6; x <= 9; x++) {
            mask.set(x, 5, true);
        }
        assertFalse(mask.get(4, 5));
        assertFalse(mask.get(5, 5));

        BinaryMask closed = MorphologyOps.close(mask, 2);
        assertTrue(closed.get(4, 5), "Le trou à x=4 doit être rebouché après fermeture");
        assertTrue(closed.get(5, 5), "Le trou à x=5 doit être rebouché après fermeture");
    }

    @Test
    @DisplayName("Ouverture morphologique pour supprimer le bruit isolé")
    void testOpenRemovesNoise() {
        BinaryMask mask = new BinaryMask(10, 10);
        // Un pixel isolé de bruit
        mask.set(1, 1, true);
        // Une zone solide 4x4
        for (int y = 4; y <= 7; y++) {
            for (int x = 4; x <= 7; x++) {
                mask.set(x, y, true);
            }
        }

        BinaryMask opened = MorphologyOps.open(mask, 1);
        assertFalse(opened.get(1, 1), "Le pixel isolé doit être éliminé");
        assertTrue(opened.get(5, 5), "La zone solide doit être conservée");
    }
}
