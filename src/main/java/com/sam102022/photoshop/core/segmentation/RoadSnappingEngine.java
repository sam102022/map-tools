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

    /** API de compatibilité : retourne un masque binaire seuillé à 128. */
    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        return snapCoverage(roughGreenMask, roadCandidates, config).toBinaryMask(128);
    }

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

        List<Point> contour = contourExtractor.extractLargestContour(roughGreenMask);
        if (contour.size() < 3) {
            return CoverageMask.fromBinaryMask(roughGreenMask);
        }

        // Simplifier les micro-marches d'escalier du contour raster
        List<Point> simplified = contourSimplifier.simplify(contour, 1.0);
        if (simplified.size() < 3) {
            simplified = contour;
        }

        // Même sans route, rasteriser le contour initial en couverture permet
        // d'obtenir des bords anti-aliasés sans déplacer la géométrie.
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
        return rasterizeAndPreserveCore(roughGreenMask, moved ? adjustedContour : simplified, config.antialiasing());
    }

    private CoverageMask rasterizeAndPreserveCore(BinaryMask original, List<Point> contour, boolean antialiasing) {
        CoverageMask coverage = polygonBuilder.rasterizeCoverage(
                original.getWidth(), original.getHeight(), contour, antialiasing);
        BinaryMask interiorCore = MorphologyOps.erode(original, 1);
        return coverage.max(CoverageMask.fromBinaryMask(interiorCore));
    }
}
