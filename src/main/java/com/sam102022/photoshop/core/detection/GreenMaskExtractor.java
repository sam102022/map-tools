package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Détecte les composantes de masque dans une image de calque.
 * Prend en charge les calques verts (dominance RGB et plage HSV) ainsi que les masques
 * binaires en niveaux de gris (TYPE_BYTE_GRAY) ou noir & blanc.
 */
public class GreenMaskExtractor {

    public BinaryMask extract(BufferedImage maskImage) {
        if (maskImage == null) {
            throw new IllegalArgumentException("L'image de masque ne peut pas être null.");
        }

        int width = maskImage.getWidth();
        int height = maskImage.getHeight();
        BinaryMask rawMask = new BinaryMask(width, height);

        // 1. Cas particulier : Image nativement en niveaux de gris ou binaire (ex: masque noir et blanc)
        if (maskImage.getType() == BufferedImage.TYPE_BYTE_GRAY || maskImage.getType() == BufferedImage.TYPE_BYTE_BINARY) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int gray = maskImage.getRGB(x, y) & 0xFF;
                    if (gray > 128) {
                        rawMask.set(x, y, true);
                    }
                }
            }
            return MorphologyOps.open(rawMask, 1);
        }

        // 2. Détection chromatique verte (dominance RGB et teinte HSV)
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

        // 3. Repli : si aucun pixel vert n'est trouvé, vérifier s'il s'agit d'un masque N&B codé en RGB (blanc sur noir)
        if (rawMask.countActivePixels() == 0) {
            int whiteCount = 0;
            int blackCount = 0;
            for (int y = 0; y < height; y += 10) {
                for (int x = 0; x < width; x += 10) {
                    int rgb = maskImage.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (r < 30 && g < 30 && b < 30) blackCount++;
                    if (r > 200 && g > 200 && b > 200) whiteCount++;
                }
            }
            if (whiteCount > 0 && blackCount > 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int rgb = maskImage.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8) & 0xFF;
                        int b = rgb & 0xFF;
                        if (r > 128 && g > 128 && b > 128) {
                            rawMask.set(x, y, true);
                        }
                    }
                }
            }
        }

        // Nettoyage morphologique : ouverture 1px pour supprimer le bruit isolé
        return MorphologyOps.open(rawMask, 1);
    }
}
