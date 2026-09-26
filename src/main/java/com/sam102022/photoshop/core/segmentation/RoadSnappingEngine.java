package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.geometry.ContourExtractor;
import com.sam102022.photoshop.core.geometry.ContourSimplifier;
import com.sam102022.photoshop.core.geometry.PolygonBuilder;
import com.sam102022.photoshop.core.geometry.RoadSnapper;
import com.sam102022.photoshop.core.model.BinaryMask;
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

    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        if (roughGreenMask == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du moteur de recalage ne peuvent pas être null.");
        }
        if (roughGreenMask.getWidth() != roadCandidates.getWidth()
                || roughGreenMask.getHeight() != roadCandidates.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre le masque et les candidats routiers.");
        }
        if (roughGreenMask.countActivePixels() == 0) {
            return new BinaryMask(roughGreenMask.getWidth(), roughGreenMask.getHeight());
        }
        if (roadCandidates.countActivePixels() == 0) {
            return roughGreenMask.copy();
        }

        List<Point> contour = contourExtractor.extractLargestContour(roughGreenMask);
        if (contour.size() < 3) {
            return roughGreenMask.copy();
        }

        // Simplifier les micro-marches d'escalier du contour raster
        List<Point> simplified = contourSimplifier.simplify(contour, 1.0);
        if (simplified.size() < 3) {
            simplified = contour;
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
            return roughGreenMask.copy();
        }

        BinaryMask rasterized = polygonBuilder.rasterize(
                roughGreenMask.getWidth(), roughGreenMask.getHeight(), adjustedContour);

        // Préserver le noyau intérieur du masque initial contre toute perte accidentelle
        BinaryMask interiorCore = MorphologyOps.erode(roughGreenMask, 1);
        return rasterized.or(interiorCore);
    }
}
