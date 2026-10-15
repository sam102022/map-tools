package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.expansion.ResidualHoleResolver;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Constructeur du masque matriciel binaire de la région autorisée (Sprint 8).
 * Fusionne le territoire intérieur comblé, les chaussées routières locales
 * et les îlots centraux des carrefours giratoires substitués, puis comble
 * sélectivement les cavités compactes de la chaussée.
 */
public class AllowedRegionBuilder {

    private final ResidualHoleResolver holeResolver;

    /**
     * Initialise le constructeur avec un résolveur de cavités standard.
     */
    public AllowedRegionBuilder() {
        this(new ResidualHoleResolver());
    }

    /**
     * Initialise le constructeur avec un résolveur de cavités injecté.
     *
     * @param holeResolver Résolveur des cavités intérieures résiduelles (non nul).
     */
    public AllowedRegionBuilder(ResidualHoleResolver holeResolver) {
        this.holeResolver = Objects.requireNonNull(holeResolver, "holeResolver ne doit pas être nul.");
    }

    /**
     * Construit le masque binaire de l'espace autorisé à partir du territoire consolidé,
     * du réseau routier recadré et des ronds-points substitués.
     *
     * @param consolidatedMask       Masque binaire du territoire consolidé (non nul).
     * @param croppedRoad            Masque binaire de la chaussée fermée recadrée (non nul).
     * @param labelMap               Matrice d'étiquetage des cellules candidates (non nulle).
     * @param substitutedRoundabouts Liste des ronds-points substitués par spline (non nulle).
     * @param roadHoleMaxArea        Surface maximale des trous de chaussée à combler (>= 0).
     * @return Masque binaire de la zone autorisée M_allowed.
     */
    public BinaryMask build(
            BinaryMask consolidatedMask,
            BinaryMask croppedRoad,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts,
            long roadHoleMaxArea
    ) {
        validateInputs(consolidatedMask, croppedRoad, labelMap, substitutedRoundabouts, roadHoleMaxArea);

        int width = consolidatedMask.getWidth();
        int height = consolidatedMask.getHeight();

        // 1. Comblement total des cavités intérieures du territoire consolidé
        BinaryMask mFill = holeResolver.fillCompactHoles(consolidatedMask, Long.MAX_VALUE);

        // 2. Fusion binaire : M_allowed = M_fill | croppedRoad
        BinaryMask mAllowed = combineMasks(mFill, croppedRoad, width, height);

        // 3. Inclusion des îlots de ronds-points substitués
        includeSubstitutedIslands(mAllowed, labelMap, substitutedRoundabouts, width, height);

        // 4. Comblement sélectif des trous compacts de chaussée (< roadHoleMaxArea)
        if (roadHoleMaxArea > 0) {
            return holeResolver.fillCompactHoles(mAllowed, roadHoleMaxArea - 1);
        }
        return mAllowed;
    }

    /**
     * Valide l'intégrité et la cohérence dimensionnelle des entrées.
     *
     * @param consolidatedMask       Masque du territoire consolidé.
     * @param croppedRoad            Masque de la route recadrée.
     * @param labelMap               Matrice d'étiquetage des cellules.
     * @param substitutedRoundabouts Liste des giratoires substitués.
     * @param roadHoleMaxArea        Seuil de comblement des trous de route.
     */
    private void validateInputs(
            BinaryMask consolidatedMask,
            BinaryMask croppedRoad,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts,
            long roadHoleMaxArea
    ) {
        Objects.requireNonNull(consolidatedMask, "consolidatedMask ne doit pas être nul.");
        Objects.requireNonNull(croppedRoad, "croppedRoad ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "labelMap ne doit pas être nulle.");
        Objects.requireNonNull(substitutedRoundabouts, "substitutedRoundabouts ne doit pas être nulle.");
        if (roadHoleMaxArea < 0) {
            throw new IllegalArgumentException("roadHoleMaxArea doit être positif ou nul.");
        }
        int width = consolidatedMask.getWidth();
        int height = consolidatedMask.getHeight();
        if (croppedRoad.getWidth() != width || croppedRoad.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions de croppedRoad incompatibles avec consolidatedMask.");
        }
        if (labelMap.width() != width || labelMap.height() != height) {
            throw new IllegalArgumentException("Dimensions de labelMap incompatibles avec consolidatedMask.");
        }
    }

    /**
     * Fusionne deux masques binaires par une opération logique OU bit à bit.
     *
     * @param a      Premier masque binaire.
     * @param b      Second masque binaire.
     * @param width  Largeur des masques.
     * @param height Hauteur des masques.
     * @return Nouveau masque résultant de l'union a | b.
     */
    private BinaryMask combineMasks(BinaryMask a, BinaryMask b, int width, int height) {
        BinaryMask combined = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (a.get(x, y) || b.get(x, y)) {
                    combined.set(x, y, true);
                }
            }
        }
        return combined;
    }

    /**
     * Inclut dans le masque autorisé les pixels des cellules îlots des ronds-points substitués.
     *
     * @param mAllowed               Masque autorisé à enrichir en place.
     * @param labelMap               Matrice des étiquettes des cellules.
     * @param substitutedRoundabouts Liste des ronds-points substitués.
     * @param width                  Largeur de la matrice.
     * @param height                 Hauteur de la matrice.
     */
    private void includeSubstitutedIslands(
            BinaryMask mAllowed,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts,
            int width,
            int height
    ) {
        if (substitutedRoundabouts.isEmpty()) {
            return;
        }

        Set<Integer> targetCellIds = new HashSet<>();
        for (Roundabout rb : substitutedRoundabouts) {
            if (rb.cellId() > 0) {
                targetCellIds.add(rb.cellId());
            }
        }

        if (targetCellIds.isEmpty()) {
            return;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cellId = labelMap.getLabel(x, y);
                if (cellId > 0 && targetCellIds.contains(cellId)) {
                    mAllowed.set(x, y, true);
                }
            }
        }
    }
}
