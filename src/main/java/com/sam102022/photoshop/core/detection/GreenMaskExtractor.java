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

    /**
     * Détecte et extrait le masque binaire à partir d'une image de calque.
     * Prend en charge les images nativement en niveaux de gris, les calques chromatiques verts,
     * ainsi que les masques noir et blanc codés en RVB.
     *
     * @param maskImage Image source du masque ou du calque.
     * @return Masque binaire nettoyé par ouverture morphologique.
     * @throws IllegalArgumentException si maskImage est null.
     */
    public BinaryMask extract(BufferedImage maskImage) {
        if (maskImage == null) {
            throw new IllegalArgumentException("L'image de masque ne peut pas être null.");
        }

        BinaryMask rawMask;
        if (isNativeGrayscale(maskImage)) {
            rawMask = extractFromNativeGrayscale(maskImage);
        } else {
            rawMask = extractGreenMask(maskImage);
            if (rawMask.countActivePixels() == 0 && isRgbBlackAndWhiteMask(maskImage)) {
                rawMask = extractFromRgbBlackAndWhite(maskImage);
            }
        }

        return MorphologyOps.open(rawMask, 1);
    }

    /**
     * Détermine si l'image est nativement encodée en niveaux de gris ou en binaire.
     *
     * @param image Image à analyser.
     * @return Vrai si le type d'image correspond à TYPE_BYTE_GRAY ou TYPE_BYTE_BINARY.
     */
    private boolean isNativeGrayscale(BufferedImage image) {
        int type = image.getType();
        return type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_BYTE_BINARY;
    }

    /**
     * Extrait le masque d'une image nativement en niveaux de gris par seuillage à 128.
     *
     * @param image Image source en niveaux de gris.
     * @return Masque binaire brut seuillé.
     */
    private BinaryMask extractFromNativeGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BinaryMask mask = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) & 0xFF) > 128) {
                    mask.set(x, y, true);
                }
            }
        }
        return mask;
    }

    /**
     * Balaye l'image pour détecter les pixels verts en combinant dominance RVB et plage HSV.
     *
     * @param image Image source couleur.
     * @return Masque binaire brut des composantes vertes.
     */
    private BinaryMask extractGreenMask(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BinaryMask mask = new BinaryMask(width, height);
        float[] hsv = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (isGreenPixel(image.getRGB(x, y), hsv)) {
                    mask.set(x, y, true);
                }
            }
        }
        return mask;
    }

    /**
     * Évalue si un pixel donné satisfait aux critères chromatiques de couleur verte.
     *
     * @param argb Valeur ARGB entière du pixel.
     * @param hsv  Tableau réutilisable pour la conversion HSB.
     * @return Vrai si le pixel est vert et non transparent.
     */
    private boolean isGreenPixel(int argb, float[] hsv) {
        int alpha = (argb >>> 24) & 0xFF;
        if (alpha < 30) {
            return false;
        }

        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        return isRgbDominantGreen(r, g, b) || isHsvGreen(r, g, b, hsv);
    }

    /**
     * Vérifie la dominance stricte du canal vert par rapport au rouge et au bleu.
     *
     * @param r Composante rouge [0..255].
     * @param g Composante verte [0..255].
     * @param b Composante bleue [0..255].
     * @return Vrai si le vert dépasse nettement le rouge et le bleu.
     */
    private boolean isRgbDominantGreen(int r, int g, int b) {
        return g > (r + 25) && g > (b + 25);
    }

    /**
     * Analyse l'espace HSB pour détecter les teintes vertes (70° à 170°) avec saturation et luminosité suffisantes.
     *
     * @param r   Composante rouge [0..255].
     * @param g   Composante verte [0..255].
     * @param b   Composante bleue [0..255].
     * @param hsv Tableau réutilisable recevant les valeurs HSB.
     * @return Vrai si les coordonnées HSB correspondent à une nuance de vert.
     */
    private boolean isHsvGreen(int r, int g, int b, float[] hsv) {
        Color.RGBtoHSB(r, g, b, hsv);
        float hueDeg = hsv[0] * 360f;
        float saturation = hsv[1];
        float brightness = hsv[2];

        return hueDeg >= 70f && hueDeg <= 170f && saturation > 0.20f && brightness > 0.20f;
    }

    /**
     * Détermine si l'image RVB correspond à un masque binaire noir et blanc par échantillonnage régulier.
     *
     * @param image Image source.
     * @return Vrai si l'échantillonnage détecte à la fois des pixels très sombres et très clairs.
     */
    private boolean isRgbBlackAndWhiteMask(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int whiteCount = 0;
        int blackCount = 0;

        for (int y = 0; y < height; y += 10) {
            for (int x = 0; x < width; x += 10) {
                int rgb = image.getRGB(x, y);
                if (isNearBlack(rgb)) {
                    blackCount++;
                } else if (isNearWhite(rgb)) {
                    whiteCount++;
                }
            }
        }
        return whiteCount > 0 && blackCount > 0;
    }

    /**
     * Vérifie si un pixel est proche du noir.
     *
     * @param rgb Valeur RVB entière du pixel.
     * @return Vrai si les trois canaux sont sous le seuil sombre.
     */
    private boolean isNearBlack(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r < 30 && g < 30 && b < 30;
    }

    /**
     * Vérifie si un pixel est proche du blanc.
     *
     * @param rgb Valeur RVB entière du pixel.
     * @return Vrai si les trois canaux sont au-dessus du seuil clair.
     */
    private boolean isNearWhite(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r > 200 && g > 200 && b > 200;
    }

    /**
     * Extrait les pixels actifs d'un calque noir et blanc codé en RVB en seuillant la luminance.
     *
     * @param image Image source RVB.
     * @return Masque binaire brut.
     */
    private BinaryMask extractFromRgbBlackAndWhite(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BinaryMask mask = new BinaryMask(width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >>> 16) & 0xFF;
                int g = (rgb >>> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 128 && g > 128 && b > 128) {
                    mask.set(x, y, true);
                }
            }
        }
        return mask;
    }
}
