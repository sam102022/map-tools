package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Détecte les axes routiers colorés et délègue les rues locales à une détection
 * géométrique de rubans continus.
 */
public class RoadCandidateDetector {

    public BinaryMask detect(BufferedImage image) {
        return detect(image, 1.0f);
    }

    public BinaryMask detect(BufferedImage image, float roadSensitivity) {
        if (image == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (roadSensitivity <= 0.0f) {
            throw new IllegalArgumentException(
                    "La sensibilité aux routes doit être strictement positive : " + roadSensitivity);
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        BinaryMask candidates = new BinaryMask(width, height);

        // Les grands axes colorés sont détectés directement par leur teinte.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = pixels[y * width + x];
                if (roadScore((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff) > 0.0) {
                    candidates.set(x, y, true);
                }
            }
        }

        // Les rues locales sont détectées comme des rubans bornés et continus.
        BinaryMask ribbons = new RoadRibbonDetector().detect(image, roadSensitivity);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (ribbons.get(x, y)) candidates.set(x, y, true);
            }
        }
        return candidates;
    }

    /**
     * Évalue la couleur d'un axe principal Google Maps.
     *
     * @return score de confiance dans [0, 1].
     */
    public double roadScore(int r, int g, int b) {
        if (r >= 210 && g >= 130 && g <= 220 && b >= 50 && b <= 165 && r - g >= 15) {
            return 1.0;
        }
        if (r >= 220 && g >= 190 && b >= 100 && b <= 190
                && Math.abs(r - g) <= 35 && r - b >= 40) {
            return 0.75;
        }
        // Le style « contraste » du projet rend les grands axes bleu-gris au lieu de jaune.
        // L'écart chromatique plafonné écarte l'eau cyan, généralement plus saturée.
        if (r >= 65 && r <= 175 && g >= 90 && g <= 180 && b >= 110 && b <= 195
                && g - r >= 8 && b - r >= 16 && b - r <= 60 && b - g <= 45) {
            return 0.85;
        }
        return 0.0;
    }
}
