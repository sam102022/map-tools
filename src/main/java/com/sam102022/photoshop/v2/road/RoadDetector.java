package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Contrat commun pour les composants d'extraction de surface routière.
 */
public interface RoadDetector {

    /**
     * Analyse l'image cartographique pour en extraire les pixels de surface routière.
     *
     * @param image Image cartographique source.
     * @return Masque binaire brut où chaque pixel à true représente une surface routière.
     * @throws IllegalArgumentException si l'image est null.
     */
    BinaryMask detect(BufferedImage image);
}
