package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires validant la résolution géométrique des cellules partielles
 * et la synthèse du masque matriciel d'amorçage T.
 */
@DisplayName("Tests unitaires du résolveur de parcelles partielles PartialCellResolver")
class PartialCellResolverTest {

    /**
     * Valide la génération conforme du masque T : cellules INSIDE retenues à 100%,
     * et cellules PARTIAL retenues uniquement sur leur intersection avec le polygone d'intention.
     */
    @Test
    @DisplayName("Génération conforme du masque d'amorçage T : INSIDE complet + PARTIAL restreint")
    void testResolveRetainedMask() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // y < 5 : Cellule 1 (INSIDE), y >= 5 : Cellule 2 (PARTIAL)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                labels[y * 10 + x] = (y < 5) ? 1 : 2;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 2);

        // Polygone actif uniquement sur x < 5
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 5; x++) {
                polygonMask.set(x, y, true);
            }
        }

        Set<Integer> insideIds = Set.of(1);
        Set<Integer> partialIds = Set.of(2);

        PartialCellResolver resolver = new PartialCellResolver();
        PartialCellResolver.ResolutionResult result = resolver.resolve(labelMap, polygonMask, insideIds, partialIds);

        BinaryMask retainedMask = result.retainedMask();

        // La cellule 1 doit être conservée à 100% (50 px), même pour x >= 5
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 10; x++) {
                assertTrue(retainedMask.get(x, y), "Pixel cellule 1 doit être conservé à 100%");
            }
        }

        // La cellule 2 ne doit être conservée que sur l'intersection avec le polygone (x < 5)
        for (int y = 5; y < 10; y++) {
            for (int x = 0; x < 5; x++) {
                assertTrue(retainedMask.get(x, y), "Intersection cellule 2 doit être conservée");
            }
            for (int x = 5; x < 10; x++) {
                assertFalse(retainedMask.get(x, y), "Hors-intersection cellule 2 doit être rejeté");
            }
        }

        assertEquals(75, retainedMask.countActivePixels());
        assertTrue(result.partialMasks().containsKey(2));
        assertEquals(25, result.partialMasks().get(2).countActivePixels());
    }

    /**
     * Valide le comportement avec des pixels de route (label 0) et des cellules non retenues (OUTSIDE).
     */
    @Test
    @DisplayName("Ignorance des pixels de route (label <= 0) et des cellules OUTSIDE")
    void testResolveWithRoadAndOutsideCells() {
        int width = 4;
        int height = 4;
        // 0 = route, 1 = inside, 2 = partial, 3 = outside
        int[] labels = {
                0, 0, 1, 1,
                0, 0, 1, 1,
                2, 2, 3, 3,
                2, 2, 3, 3
        };
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 3);

        // Masque de polygone actif sur toute la matrice
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                polygonMask.set(x, y, true);
            }
        }

        Set<Integer> insideIds = Set.of(1);
        Set<Integer> partialIds = Set.of(2);

        PartialCellResolver resolver = new PartialCellResolver();
        PartialCellResolver.ResolutionResult result = resolver.resolve(labelMap, polygonMask, insideIds, partialIds);

        BinaryMask retainedMask = result.retainedMask();

        // Pixels de route (x < 2, y < 2) doivent être inactifs
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) {
                assertFalse(retainedMask.get(x, y), "Les pixels de route doivent rester inactifs");
            }
        }

        // Pixels de cellule 1 (x >= 2, y < 2) doivent être actifs
        for (int y = 0; y < 2; y++) {
            for (int x = 2; x < 4; x++) {
                assertTrue(retainedMask.get(x, y), "Les pixels INSIDE doivent être actifs");
            }
        }

        // Pixels de cellule 2 (x < 2, y >= 2) doivent être actifs (intersection avec polygone = true)
        for (int y = 2; y < 4; y++) {
            for (int x = 0; x < 2; x++) {
                assertTrue(retainedMask.get(x, y), "Les pixels PARTIAL dans le polygone doivent être actifs");
            }
        }

        // Pixels de cellule 3 (x >= 2, y >= 2) doivent être inactifs car OUTSIDE
        for (int y = 2; y < 4; y++) {
            for (int x = 2; x < 4; x++) {
                assertFalse(retainedMask.get(x, y), "Les pixels OUTSIDE doivent rester inactifs");
            }
        }

        assertEquals(8, retainedMask.countActivePixels());
    }

    /**
     * Valide le rejet défensif en cas d'incohérence dimensionnelle ou d'arguments nulls.
     */
    @Test
    @DisplayName("Rejet défensif des arguments nulls et dimensions incompatibles")
    void testDefensiveValidation() {
        PartialCellResolver resolver = new PartialCellResolver();
        CellLabelMap labelMap = new CellLabelMap(10, 10, new int[100], 0);
        BinaryMask polygonMask = new BinaryMask(10, 10);
        BinaryMask wrongSizeMask = new BinaryMask(12, 10);
        Set<Integer> emptySet = Set.of();

        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(null, polygonMask, emptySet, emptySet));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(labelMap, null, emptySet, emptySet));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(labelMap, polygonMask, null, emptySet));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(labelMap, polygonMask, emptySet, null));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(labelMap, wrongSizeMask, emptySet, emptySet));
    }

    /**
     * Valide l'immutabilité et la validation défensive du record ResolutionResult.
     */
    @Test
    @DisplayName("Validation défensive et immutabilité du record ResolutionResult")
    void testResolutionResultDefensive() {
        BinaryMask mask = new BinaryMask(5, 5);
        Map<Integer, BinaryMask> map = Map.of(1, new BinaryMask(5, 5));

        assertThrows(IllegalArgumentException.class, () -> new PartialCellResolver.ResolutionResult(null, map));
        assertThrows(IllegalArgumentException.class, () -> new PartialCellResolver.ResolutionResult(mask, null));

        PartialCellResolver.ResolutionResult result = new PartialCellResolver.ResolutionResult(mask, map);
        assertNotNull(result.retainedMask());
        assertNotNull(result.partialMasks());
        assertThrows(UnsupportedOperationException.class, () -> result.partialMasks().put(2, new BinaryMask(5, 5)));
    }

    /**
     * Une lamelle partielle plus fine que le seuil est retirée de T et du masque partiel, alors que la
     * surcharge historique (sans seuil) la conserve.
     */
    @Test
    @DisplayName("Retire de T les lamelles partielles plus fines que minPartialThickness")
    void testResolveRemovesThinPartialSliver() {
        int width = 100;
        int height = 100;
        int[] labels = new int[width * height];
        java.util.Arrays.fill(labels, 1);
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 1);

        // Le polygone ne recouvre qu'une bande intérieure de 10 px de la cellule partielle
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 40; x < 50; x++) {
                polygonMask.set(x, y, true);
            }
        }

        PartialCellResolver resolver = new PartialCellResolver();
        PartialCellResolver.ResolutionResult legacy = resolver.resolve(labelMap, polygonMask, Set.of(), Set.of(1));
        PartialCellResolver.ResolutionResult filtered = resolver.resolve(labelMap, polygonMask, Set.of(), Set.of(1), 20.0);

        assertEquals(1000L, legacy.retainedMask().countActivePixels(), "Sans seuil, la lamelle est conservée.");
        assertEquals(0L, filtered.retainedMask().countActivePixels(), "La lamelle de 10 px doit être retirée de T.");
        assertEquals(0L, filtered.partialMasks().get(1).countActivePixels(), "Le masque partiel doit être vidé.");
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(labelMap, polygonMask, Set.of(), Set.of(1), -1.0));
    }
}
