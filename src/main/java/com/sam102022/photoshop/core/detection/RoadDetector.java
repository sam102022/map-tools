package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.image.BufferedImage;

/**
 * Façade principale pour la détection et l'unification des axes routiers sur une carte.
 * <p>
 * Ce service orchestre la détection chromatique via {@link RoadCandidateDetector} et applique
 * une fermeture morphologique (selon {@link SnappingConfig#closingRadius()}) afin de connecter
 * les segments de routes interrompus par des libellés textuels ou des passages piétons.
 * </p>
 */
public class RoadDetector {

    /**
     * Détecte les axes routiers présents sur l'image de carte et consolide les tracés continus.
     *
     * @param mapImage Image de la carte géographique à analyser.
     * @param config   Configuration de recalage spécifiant les paramètres de sensibilité et de fermeture.
     * @return Masque binaire des axes routiers consolidés.
     * @throws IllegalArgumentException si l'image de la carte ou la configuration est null.
     */
    public BinaryMask detectRoads(BufferedImage mapImage, SnappingConfig config) {
        if (mapImage == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (config == null) {
            throw new IllegalArgumentException("La configuration ne peut pas être null.");
        }

        BinaryMask candidates = new RoadCandidateDetector().detect(mapImage, config.roadSensitivity());
        return config.closingRadius() == 0
                ? candidates
                : MorphologyOps.close(candidates, config.closingRadius());
    }
}
