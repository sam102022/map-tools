package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Détecte les composantes vertes dans une image de calque avec filtrage RGB et HSV,
 * complété par un nettoyage morphologique du bruit résiduel.
 */
public class GreenMaskExtractor {

    public BinaryMask extract(BufferedImage maskImage) {
        if (maskImage == null) {
            throw new IllegalArgumentException("L'image de masque ne peut pas être null.");
        }

        int width = maskImage.getWidth();
        int height = maskImage.getHeight();
        BinaryMask rawMask = new BinaryMask(width, height);

        float[] hsv = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = maskImage.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Ignorer les pixels transparents si canal alpha présent
                if (alpha < 30) {
                    continue;
                }

                boolean isGreen = false;

                // Règle 1 : Dominance RGB stricte (G nettement supérieur à R et B)
                if (g > (r + 25) && g > (b + 25)) {
                    isGreen = true;
                } else {
                    // Règle 2 : Analyse HSV (teinte verte entre 70° et 170°)
                    Color.RGBtoHSB(r, g, b, hsv);
                    float hueDeg = hsv[0] * 360f;
                    float saturation = hsv[1];
                    float brightness = hsv[2];

                    if (hueDeg >= 70f && hueDeg <= 170f && saturation > 0.20f && brightness > 0.20f) {
                        isGreen = true;
                    }
                }

                if (isGreen) {
                    rawMask.set(x, y, true);
                }
            }
        }

        // Nettoyage morphologique : ouverture 1px pour supprimer le bruit isolé
        return MorphologyOps.open(rawMask, 1);
    }
}
