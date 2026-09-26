package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.awt.image.BufferedImage;

/** Détecte uniquement les surfaces de routes reconnaissables par leur couleur. */
public class RoadCandidateDetector {

    public BinaryMask detect(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        BinaryMask candidates = new BinaryMask(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (roadScore((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff) > 0) {
                    candidates.set(x, y, true);
                }
            }
        }
        return candidates;
    }

    /** Score couleur entre 0 et 1 ; les rues blanches sont volontairement exclues. */
    public double roadScore(int r, int g, int b) {
        if (r >= 210 && g >= 130 && g <= 220 && b >= 50 && b <= 165 && r - g >= 15) {
            return 1.0;
        }
        if (r >= 220 && g >= 190 && b >= 100 && b <= 190
                && Math.abs(r - g) <= 35 && r - b >= 40) {
            return 0.75;
        }
        return 0.0;
    }
}
