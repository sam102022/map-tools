package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.geometry.ContourExtractor;
import com.sam102022.photoshop.core.geometry.ContourSimplifier;
import com.sam102022.photoshop.core.geometry.PolygonBuilder;
import com.sam102022.photoshop.core.geometry.RoadSnapper;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.awt.Point;
import java.util.List;

/**
 * Moteur de recalage géométrique : extrait le contour extérieur du masque,
 * le simplifie, l'aimante sur les candidats routiers proches et reconstruit
 * le masque polygonal final tout en préservant le noyau intérieur.
 */
public class RoadSnappingEngine {
    private final ContourExtractor contourExtractor = new ContourExtractor();
    private final ContourSimplifier contourSimplifier = new ContourSimplifier();
    private final RoadSnapper roadSnapper = new RoadSnapper();
    private final PolygonBuilder polygonBuilder = new PolygonBuilder();

    /**
     * API de compatibilité binaire. Les couvertures partielles sont supprimées au seuil 128.
     * Pour le rendu anti-aliasé, appeler {@link #snapCoverage(BinaryMask, BinaryMask, SnappingConfig)}.
     */
    @Deprecated(forRemoval = false)
    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        return snapBinary(roughGreenMask, roadCandidates, config);
    }

    /** Retourne explicitement le résultat binaire seuillé à 128. */
    public BinaryMask snapBinary(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        return snapCoverage(roughGreenMask, roadCandidates, config).toBinaryMask(128);
    }

    /** Retourne la couverture continue à utiliser pour rendre l'image anti-aliasée. */
    public CoverageMask snapCoverage(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        if (roughGreenMask == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du moteur de recalage ne peuvent pas être null.");
        }
        if (roughGreenMask.getWidth() != roadCandidates.getWidth()
                || roughGreenMask.getHeight() != roadCandidates.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre le masque et les candidats routiers.");
        }
        if (roughGreenMask.countActivePixels() == 0) {
            return new CoverageMask(roughGreenMask.getWidth(), roughGreenMask.getHeight());
        }

        // Unifier les composantes disjointes séparées par des étiquettes ou césures fines
        BinaryMask unifiedMask = config.closingRadius() > 0
                ? MorphologyOps.close(roughGreenMask, config.closingRadius())
                : roughGreenMask;

        List<Point> contour = contourExtractor.extractLargestContour(unifiedMask);
        if (contour.size() < 3) {
            return CoverageMask.fromBinaryMask(roughGreenMask);
        }

        // Simplifier les micro-marches d'escalier du contour raster
        List<Point> simplified = contourSimplifier.simplify(contour, 1.0);
        if (simplified.size() < 3) {
            simplified = contour;
        }

        long distinctPoints = simplified.stream().distinct().count();
        if (distinctPoints < 3) {
            return CoverageMask.fromBinaryMask(roughGreenMask);
        }

        // Si aucune route candidate n'est présente :
        // Le contour extrait est rasterisé pour conserver un rendu anti-aliasé sub-pixel
        if (roadCandidates.countActivePixels() == 0) {
            return rasterizeAndPreserveCore(roughGreenMask, simplified, config.antialiasing());
        }

        List<Point> adjustedContour = roadSnapper.snap(simplified, roadCandidates,
                roughGreenMask.getWidth(), roughGreenMask.getHeight(), config);

        boolean moved = false;
        for (int i = 0; i < simplified.size(); i++) {
            if (!simplified.get(i).equals(adjustedContour.get(i))) {
                moved = true;
                break;
            }
        }
        if (!moved) {
            return rasterizeAndPreserveCore(roughGreenMask, simplified, config.antialiasing());
        }

        return rasterizeAndPreserveCore(roughGreenMask, adjustedContour, config.antialiasing());
    }

    private CoverageMask rasterizeAndPreserveCore(BinaryMask original, List<Point> contour, boolean antialiasing) {
        CoverageMask coverage = polygonBuilder.rasterizePixelCenterContour(
                original.getWidth(), original.getHeight(), contour, antialiasing);
        BinaryMask interiorCore = MorphologyOps.erode(original, 1);
        return coverage.max(CoverageMask.fromBinaryMask(interiorCore));
    }
}
