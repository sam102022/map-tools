package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires pour {@link ZoneSegmentationEngine}.
 * <p>
 * Valide l'inondation géodésique confinée, la sélection déterministe de la graine,
 * l'exclusion mathématiquement absolue des axes routiers et la préservation de l'anti-aliasing sub-pixel.
 * </p>
 */
@DisplayName("Tests unitaires pour ZoneSegmentationEngine")
class ZoneSegmentationEngineTest {

    private ZoneSegmentationEngine engine;
    private SnappingConfig defaultConfig;

    @BeforeEach
    void setUp() {
        engine = new ZoneSegmentationEngine();
        defaultConfig = SnappingConfig.defaults();
    }

    @Test
    @DisplayName("Exclusion absolue des axes routiers (chaussée à 0)")
    void testSegmentZoneStrictRoadExclusion() {
        int w = 40;
        int h = 40;

        BinaryMask territory = new BinaryMask(w, h);
        fillRegion(territory, 5, 5, 30, 30);

        // Axe routier vertical traversant au milieu à x = 20
        BinaryMask roadCandidates = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            roadCandidates.set(20, y, true);
        }

        // Barrières consolidées incluant la route et l'extérieur du territoire
        BinaryMask barriers = roadCandidates.or(territory.not());

        // Cadre intérieur ciblant la zone gauche (x de 6 à 18, y de 6 à 34)
        BinaryMask interiorMask = new BinaryMask(w, h);
        fillRegion(interiorMask, 6, 6, 13, 28);

        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig);

        assertNotNull(result, "Le masque de résultat ne doit pas être null.");

        // Vérification stricte : aucun pixel de l'axe routier ne doit être actif
        for (int y = 0; y < h; y++) {
            assertEquals(0, result.get(20, y), "La route au pixel (20, " + y + ") doit avoir une couverture de 0.");
        }

        // L'intérieur de la zone doit être actif
        assertTrue(result.get(10, 15) > 0, "L'intérieur de la zone doit être préservé avec couverture active.");
    }

    @Test
    @DisplayName("Sélection déterministe de la graine sur une zone symétrique")
    void testDeterministicSeedSelectionOnSymmetry() {
        int w = 50;
        int h = 50;

        BinaryMask territory = new BinaryMask(w, h).not();
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        // Zone intérieure carrée symétrique [15..35, 15..35]
        BinaryMask interiorMask = new BinaryMask(w, h);
        fillRegion(interiorMask, 15, 15, 21, 21);

        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig);

        assertNotNull(result);
        assertEquals(255, result.get(25, 25), "Le centre géométrique (25, 25) doit être couvert au maximum.");
    }

    @Test
    @DisplayName("Échec avec exception explicite si aucun pixel libre n'existe dans le territoire")
    void testFailWhenNoFreePixelsInTerritory() {
        int w = 30;
        int h = 30;

        BinaryMask territory = new BinaryMask(w, h); // Territoire entièrement vide
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        BinaryMask interiorMask = new BinaryMask(w, h);
        fillRegion(interiorMask, 5, 5, 10, 10);

        assertThrows(IllegalStateException.class,
                () -> engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig),
                "L'absence de pixel libre dans le territoire doit lever IllegalStateException.");
    }

    @Test
    @DisplayName("Préservation de l'anti-aliasing sub-pixel sur les bordures non routières")
    void testSubPixelAntiAliasingPreserved() {
        int w = 60;
        int h = 60;

        BinaryMask territory = new BinaryMask(w, h).not();
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        // Zone circulaire ou diagonale pour générer des bordures douces
        BinaryMask interiorMask = new BinaryMask(w, h);
        int cx = 30, cy = 30, r = 15;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) {
                    interiorMask.set(x, y, true);
                }
            }
        }

        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig);

        // Recherche de pixels avec couverture intermédiaire (0 < v < 255)
        boolean hasSubPixelFraction = false;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = result.get(x, y);
                if (val > 0 && val < 255) {
                    hasSubPixelFraction = true;
                    break;
                }
            }
            if (hasSubPixelFraction) break;
        }

        assertTrue(hasSubPixelFraction, "Le masque doit contenir des valeurs sub-pixel fractionnaires [1..254] pour l'anti-aliasing.");
    }

    @Test
    @DisplayName("Désactivation de l'anti-aliasing produisant un masque strictement binaire")
    void testAntialiasingDisabled() {
        int w = 40;
        int h = 40;

        BinaryMask territory = new BinaryMask(w, h).not();
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        BinaryMask interiorMask = new BinaryMask(w, h);
        fillRegion(interiorMask, 10, 10, 15, 15);

        SnappingConfig noAaConfig = defaultConfig.withAntialiasing(false);
        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, noAaConfig);

        assertNotNull(result);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = result.get(x, y);
                assertTrue(val == 0 || val == 255, "En mode sans anti-aliasing, la valeur doit être 0 ou 255 : " + val);
            }
        }
    }

    @Test
    @DisplayName("Départage lexicographique de la graine sur une zone de dimensions paires (4 centres équidistants)")
    void testDeterministicSeedSelectionEvenSymmetry() {
        int w = 40;
        int h = 40;

        BinaryMask territory = new BinaryMask(w, h).not();
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        // Zone 20x20 : x=[10..29], y=[10..29]. Les centres géométriques sont (19,19), (20,19), (19,20), (20,20).
        // L'arbitrage déterministe (plus petit y puis plus petit x) doit choisir (19, 19).
        BinaryMask interiorMask = new BinaryMask(w, h);
        fillRegion(interiorMask, 10, 10, 20, 20);

        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig);
        assertNotNull(result);
        assertEquals(255, result.get(19, 19));
    }

    @Test
    @DisplayName("Gestion d'un contour minuscule (< 3 points)")
    void testDegenerateSmallContour() {
        int w = 20;
        int h = 20;

        BinaryMask territory = new BinaryMask(w, h).not();
        BinaryMask roadCandidates = new BinaryMask(w, h);
        BinaryMask barriers = new BinaryMask(w, h);

        // Une zone de 1 seul pixel
        BinaryMask interiorMask = new BinaryMask(w, h);
        interiorMask.set(5, 5, true);

        CoverageMask result = engine.segmentZone(interiorMask, roadCandidates, barriers, territory, defaultConfig);
        assertNotNull(result);
        assertTrue(result.get(5, 5) > 0);
    }

    @Test
    @DisplayName("Validation des arguments null et des dimensions discordantes")
    void testValidationNullAndDimensions() {
        BinaryMask mask10 = new BinaryMask(10, 10);
        BinaryMask mask20 = new BinaryMask(20, 10);

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(null, mask10, mask10, mask10, defaultConfig));

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(mask10, null, mask10, mask10, defaultConfig));

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(mask10, mask10, null, mask10, defaultConfig));

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(mask10, mask10, mask10, null, defaultConfig));

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(mask10, mask10, mask10, mask10, null));

        assertThrows(IllegalArgumentException.class,
                () -> engine.segmentZone(mask10, mask20, mask10, mask10, defaultConfig));
    }

    /**
     * Remplit une région rectangulaire dans un masque binaire.
     *
     * @param mask Masque cible.
     * @param rx   Origine X.
     * @param ry   Origine Y.
     * @param rw   Largeur.
     * @param rh   Hauteur.
     */
    private void fillRegion(BinaryMask mask, int rx, int ry, int rw, int rh) {
        for (int y = ry; y < ry + rh && y < mask.getHeight(); y++) {
            for (int x = rx; x < rx + rw && x < mask.getWidth(); x++) {
                mask.set(x, y, true);
            }
        }
    }
}
