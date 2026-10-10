package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires validant l'exactitude du filtre maximum local séparable O(N) de Lemire.
 */
@DisplayName("Tests du filtre maximum local séparable O(N)")
class LocalMaxFilterTest {

    @Test
    @DisplayName("Propagation du maximum local dans une fenêtre glissante")
    void testSlidingWindowMaximum() {
        int width = 10;
        int height = 10;
        DistanceMap input = new DistanceMap(width, height, new float[width * height]);
        BinaryMask road = new BinaryMask(width, height);
        // Placer un pic isolé à (5, 5) avec valeur 8.0f
        input.set(5, 5, 8.0f);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                road.set(x, y, true);
            }
        }

        LocalMaxFilter filter = new LocalMaxFilter();
        // Fenêtre de taille 5 (k = 2 pixels de rayon)
        DistanceMap result = filter.filter(input, road, 5);

        // Les pixels dans le voisinage de rayon 2 doivent valoir 8.0f
        assertEquals(8.0f, result.get(5, 5), 0.001f);
        assertEquals(8.0f, result.get(3, 5), 0.001f);
        assertEquals(8.0f, result.get(7, 5), 0.001f);
        // Au-delà de rayon 2, la valeur redevient 0.0f
        assertEquals(0.0f, result.get(2, 5), 0.001f);
    }

    @Test
    @DisplayName("Les pixels hors route sont strictement masqués à 0.0f")
    void testRoadMasking() {
        int width = 10;
        int height = 10;
        DistanceMap input = new DistanceMap(width, height, new float[width * height]);
        BinaryMask road = new BinaryMask(width, height);
        input.set(5, 5, 8.0f);
        // La route couvre (5, 5) mais pas (4, 5)
        road.set(5, 5, true);

        LocalMaxFilter filter = new LocalMaxFilter();
        DistanceMap result = filter.filter(input, road, 5);

        assertEquals(8.0f, result.get(5, 5), 0.001f);
        assertEquals(0.0f, result.get(4, 5), 0.001f);
    }

    @Test
    @DisplayName("Validation de la taille de fenêtre (doit être impaire et > 0)")
    void testInvalidWindowSize() {
        DistanceMap input = new DistanceMap(5, 5, new float[25]);
        BinaryMask road = new BinaryMask(5, 5);
        LocalMaxFilter filter = new LocalMaxFilter();

        assertThrows(IllegalArgumentException.class, () -> filter.filter(input, road, 0));
        assertThrows(IllegalArgumentException.class, () -> filter.filter(input, road, 4));
    }
}
