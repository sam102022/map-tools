package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests unitaires de la morphologie par disques {@link DiskMorphology} : le calcul borné au rectangle englobant
 * doit donner exactement la dilatation et l'érosion par le disque {@code dx² + dy² <= r²}.
 */
@DisplayName("Tests de la morphologie par disques euclidiens")
class DiskMorphologyTest {

    /**
     * Dilatation et érosion comparées à la force brute sur des ensembles aléatoires, y compris collés au bord de
     * l'image, vides ou pleins.
     */
    @Test
    @DisplayName("Dilatation et érosion identiques à la force brute")
    void testMatchesBruteForce() {
        Random random = new Random(3);
        DiskMorphology morphology = new DiskMorphology();
        for (int trial = 0; trial < 40; trial++) {
            int width = 5 + random.nextInt(45);
            int height = 5 + random.nextInt(45);
            BinaryMask set = new BinaryMask(width, height);
            int blobs = random.nextInt(4);
            double fill = trial % 10 == 9 ? 1.0 : 0.0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    set.set(x, y, random.nextDouble() < fill);
                }
            }
            for (int b = 0; b < blobs; b++) {
                int cx = random.nextInt(width);
                int cy = random.nextInt(height);
                int rx = 1 + random.nextInt(12);
                int ry = 1 + random.nextInt(12);
                for (int y = Math.max(0, cy - ry); y < Math.min(height, cy + ry); y++) {
                    for (int x = Math.max(0, cx - rx); x < Math.min(width, cx + rx); x++) {
                        set.set(x, y, random.nextDouble() < 0.9);
                    }
                }
            }
            double r = random.nextInt(8) + (trial % 3 == 0 ? 0.5 : 0.0);
            assertSame(bruteDilate(set, r), morphology.dilate(set, r), "dilatation, essai " + trial);
            assertSame(bruteErode(set, r), morphology.erode(set, r), "érosion, essai " + trial);
        }
    }

    /**
     * Dilatation par force brute : pixels à distance &lt;= r d'un pixel de l'ensemble.
     *
     * @param set Ensemble.
     * @param r   Rayon.
     * @return Ensemble dilaté.
     */
    private BinaryMask bruteDilate(BinaryMask set, double r) {
        int w = set.getWidth();
        int h = set.getHeight();
        BinaryMask out = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                for (int v = 0; v < h && !out.get(x, y); v++) {
                    for (int u = 0; u < w; u++) {
                        if (set.get(u, v) && (u - x) * (u - x) + (v - y) * (v - y) <= r * r) {
                            out.set(x, y, true);
                            break;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Érosion par force brute : pixels de l'ensemble dont aucun pixel du complément n'est à distance &lt;= r
     * (l'ensemble est conservé s'il couvre toute l'image).
     *
     * @param set Ensemble.
     * @param r   Rayon.
     * @return Ensemble érodé.
     */
    private BinaryMask bruteErode(BinaryMask set, double r) {
        int w = set.getWidth();
        int h = set.getHeight();
        BinaryMask out = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!set.get(x, y)) {
                    continue;
                }
                boolean keep = true;
                for (int v = 0; v < h && keep; v++) {
                    for (int u = 0; u < w; u++) {
                        if (!set.get(u, v) && (u - x) * (u - x) + (v - y) * (v - y) <= r * r) {
                            keep = false;
                            break;
                        }
                    }
                }
                out.set(x, y, keep);
            }
        }
        return out;
    }

    /**
     * Compare deux masques pixel à pixel.
     *
     * @param expected Masque attendu.
     * @param actual   Masque obtenu.
     * @param message  Contexte.
     */
    private void assertSame(BinaryMask expected, BinaryMask actual, String message) {
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.get(x, y), actual.get(x, y), message + " (" + x + ", " + y + ")");
            }
        }
    }
}
