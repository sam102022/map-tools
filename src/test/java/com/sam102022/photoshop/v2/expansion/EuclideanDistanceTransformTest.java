package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires validant l'exactitude de la transformée de distance euclidienne Meijster O(N).
 */
@DisplayName("Tests de la transformée de distance euclidienne exacte O(N)")
class EuclideanDistanceTransformTest {

    @Test
    @DisplayName("Calcul de demi-largeur sur une bande routière rectiligne")
    void testStraightRoadHalfWidth() {
        int width = 20;
        int height = 10;
        BinaryMask roadMask = new BinaryMask(width, height);
        // Route horizontale entre y = 3 et y = 6 (largeur 4 pixels : y=3,4,5,6)
        for (int y = 3; y <= 6; y++) {
            for (int x = 0; x < width; x++) {
                roadMask.set(x, y, true);
            }
        }

        EuclideanDistanceTransform edt = new EuclideanDistanceTransform();
        DistanceMap distanceMap = edt.compute(roadMask);

        // Au bord extérieur (y=3 ou y=6), distance au bord est de 1.0 px
        assertEquals(1.0f, distanceMap.get(5, 3), 0.01f);
        assertEquals(1.0f, distanceMap.get(5, 6), 0.01f);
        // Au centre (y=4 ou y=5), distance au bord est de 2.0 px
        assertEquals(2.0f, distanceMap.get(5, 4), 0.01f);
        assertEquals(2.0f, distanceMap.get(5, 5), 0.01f);
        // Hors route (y=0), distance = 0
        assertEquals(0.0f, distanceMap.get(5, 0), 0.01f);
    }

    @Test
    @DisplayName("Pixel isolé et distance diagonale")
    void testIsolatedPixelAndDiagonal() {
        int width = 5;
        int height = 5;
        BinaryMask roadMask = new BinaryMask(width, height);
        // Pixel isolé à (2, 2)
        roadMask.set(2, 2, true);

        EuclideanDistanceTransform edt = new EuclideanDistanceTransform();
        DistanceMap distanceMap = edt.compute(roadMask);

        // Le pixel isolé est entouré de non-routes, son voisin le plus proche est à distance 1.0 px
        assertEquals(1.0f, distanceMap.get(2, 2), 0.01f);
        assertEquals(0.0f, distanceMap.get(1, 2), 0.01f);
    }

    @Test
    @DisplayName("Masque vide sans route retourne des distances nulles partout")
    void testEmptyMask() {
        int width = 10;
        int height = 10;
        BinaryMask roadMask = new BinaryMask(width, height);

        EuclideanDistanceTransform edt = new EuclideanDistanceTransform();
        DistanceMap distanceMap = edt.compute(roadMask);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(0.0f, distanceMap.get(x, y), 0.001f);
            }
        }
    }
}
