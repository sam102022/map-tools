package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant le comblement sélectif des îlots résiduels et ronds-points compacts.
 */
@DisplayName("Tests du comblement sélectif des îlots résiduels")
class ResidualHoleResolverTest {

    @Test
    @DisplayName("Comble un petit trou compact <= 15000 px mais préserve une enclave géante > 15000 px")
    void testSelectiveHoleFilling() {
        int width = 200;
        int height = 200;
        BinaryMask mask = new BinaryMask(width, height);
        // Remplir l'ensemble sauf les bords
        for (int y = 5; y < 195; y++) {
            for (int x = 5; x < 195; x++) {
                mask.set(x, y, true);
            }
        }
        // Créer un petit trou fermé de 10x10 = 100 pixels (centre à 50, 50)
        for (int y = 45; y < 55; y++) {
            for (int x = 45; x < 55; x++) {
                mask.set(x, y, false);
            }
        }
        // Créer un grand trou fermé de 130x130 = 16 900 pixels (> 15 000 px)
        for (int y = 60; y < 190; y++) {
            for (int x = 60; x < 190; x++) {
                mask.set(x, y, false);
            }
        }

        ResidualHoleResolver resolver = new ResidualHoleResolver();
        BinaryMask filled = resolver.fillCompactHoles(mask, 15000L);

        // Le petit trou (100 px) doit être comblé
        assertTrue(filled.get(50, 50), "Le petit trou compact doit être comblé");
        // Le grand trou (16 900 px) doit être conservé intact
        assertFalse(filled.get(100, 100), "Le grand trou > 15 000 px ne doit pas être bouché");
    }

    @Test
    @DisplayName("Masque sans aucun trou reste inchangé")
    void testMaskWithoutHoles() {
        int width = 20;
        int height = 20;
        BinaryMask mask = new BinaryMask(width, height);
        mask.set(5, 5, true);

        ResidualHoleResolver resolver = new ResidualHoleResolver();
        BinaryMask result = resolver.fillCompactHoles(mask, 15000L);

        assertTrue(result.get(5, 5));
        assertFalse(result.get(0, 0));
    }
}
