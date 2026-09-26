package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.image.BufferedImage;

/**
 * Façade historique conservée pour les appels CLI/GUI. La détection de route
 * repose uniquement sur les couleurs ; les gradients Sobel ne sont pas des routes.
 */
public class RoadDetector {

    public BinaryMask detectRoads(BufferedImage mapImage, SnappingConfig config) {
        if (mapImage == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (config == null) {
            throw new IllegalArgumentException("La configuration ne peut pas être null.");
        }

        BinaryMask candidates = new RoadCandidateDetector().detect(mapImage);
        return config.closingRadius() == 0
                ? candidates
                : MorphologyOps.close(candidates, config.closingRadius());
    }
}
