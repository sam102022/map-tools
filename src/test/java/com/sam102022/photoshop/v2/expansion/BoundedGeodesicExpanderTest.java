package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant l'arrêt strict de la propagation géodésique Dijkstra bornée.
 */
@DisplayName("Tests de l'expansion géodésique Dijkstra bornée")
class BoundedGeodesicExpanderTest {

    @Test
    @DisplayName("L'expansion couvre la chaussée limitrophe mais s'arrête net sans fuiter dans une rue transversale")
    void testExpansionStopsAtPerpendicularStreet() {
        int width = 50;
        int height = 50;
        BinaryMask roadMask = new BinaryMask(width, height);
        // Route frontière horizontale (y = 20 à 25)
        for (int y = 20; y <= 25; y++) {
            for (int x = 0; x < width; x++) {
                roadMask.set(x, y, true);
            }
        }
        // Rue transversale montante vers le haut (x = 24 à 26, y = 0 à 19)
        for (int y = 0; y < 20; y++) {
            for (int x = 24; x <= 26; x++) {
                roadMask.set(x, y, true);
            }
        }

        // Cellule intérieure T située sous la route (y = 26 à 40)
        BinaryMask retainedMask = new BinaryMask(width, height);
        for (int y = 26; y < 40; y++) {
            for (int x = 0; x < width; x++) {
                retainedMask.set(x, y, true);
            }
        }

        DistanceMap maxDist = new DistanceMap(width, height, new float[width * height]);
        // Demi-largeur de 3 px partout sur la route
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) {
                    maxDist.set(x, y, 3.0f);
                }
            }
        }

        BoundedGeodesicExpander expander = new BoundedGeodesicExpander();
        ExpansionConfig config = ExpansionConfig.defaultTerritory(); // eps = 4.0 -> borne = 2 * 3 + 4 = 10 px
        BinaryMask ext = expander.expand(retainedMask, roadMask, maxDist, config);

        // La chaussée horizontale mitoyenne (y = 20 à 25) doit être entièrement absorbée
        assertTrue(ext.get(10, 22), "La chaussée mitoyenne doit être couverte");
        assertTrue(ext.get(10, 20), "Le bord externe de la chaussée doit être atteint");

        // La rue transversale lointaine (y = 5) ne doit PAS être absorbée (arrêt géodésique à 10 px)
        assertFalse(ext.get(25, 5), "La rue transversale extérieure ne doit pas être envahie");
    }

    @Test
    @DisplayName("Masque de cellules retenues vide produit une extension vide")
    void testEmptyRetainedMaskProducesEmptyExtension() {
        int width = 20;
        int height = 20;
        BinaryMask roadMask = new BinaryMask(width, height);
        roadMask.set(10, 10, true);
        BinaryMask retainedMask = new BinaryMask(width, height);
        DistanceMap maxDist = new DistanceMap(width, height, new float[width * height]);

        BoundedGeodesicExpander expander = new BoundedGeodesicExpander();
        BinaryMask ext = expander.expand(retainedMask, roadMask, maxDist, ExpansionConfig.defaultTerritory());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertFalse(ext.get(x, y));
            }
        }
    }
}
