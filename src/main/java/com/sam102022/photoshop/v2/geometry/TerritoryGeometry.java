package com.sam102022.photoshop.v2.geometry;

import java.util.List;

/**
 * Géométrie géographique du territoire comprenant les anneaux extérieurs et les trous intérieurs.
 *
 * @param outerRings Liste des anneaux fermés extérieurs (coordonnées GPS).
 * @param innerRings Liste des trous fermés intérieurs (coordonnées GPS).
 */
public record TerritoryGeometry(
        List<List<GeoCoordinate>> outerRings,
        List<List<GeoCoordinate>> innerRings
) {

    /**
     * Valide et garantit l'immuabilité des anneaux extérieurs et intérieurs.
     *
     * @param outerRings Anneaux extérieurs (ne peut pas être vide).
     * @param innerRings Anneaux intérieurs (trous éventuels).
     * @throws IllegalArgumentException si les anneaux extérieurs sont absents ou vides.
     */
    public TerritoryGeometry {
        outerRings = outerRings == null ? List.of() : outerRings.stream().map(List::copyOf).toList();
        innerRings = innerRings == null ? List.of() : innerRings.stream().map(List::copyOf).toList();
        if (outerRings.isEmpty()) {
            throw new IllegalArgumentException("Un territoire doit comporter au moins un anneau extérieur.");
        }
    }
}
