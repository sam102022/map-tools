package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Détecte un territoire coloré en vert ou lit un masque binaire en niveaux de gris.
 * Pour un calque vert translucide, nettoie les petits écarts, sélectionne la composante
 * principale et remplit les détails cartographiques internes.
 */
public class GreenMaskExtractor {

    /**
     * Détecte et extrait le masque binaire à partir d'une image de calque.
     * Prend en charge les images nativement en niveaux de gris, les calques chromatiques verts,
     * ainsi que les masques noir et blanc codés en RVB.
     *
     * @param maskImage Image source du masque ou du calque.
     * @return Masque binaire du territoire ou du masque fourni.
     * @throws IllegalArgumentException si maskImage est null.
     */
    public BinaryMask extract(BufferedImage maskImage) {
        if (maskImage == null) {
            throw new IllegalArgumentException("L'image de masque ne peut pas être null.");
        }

        BinaryMask rawMask;
        if (isNativeGrayscale(maskImage)) {
            rawMask = extractFromNativeGrayscale(maskImage);
            return MorphologyOps.open(rawMask, 1);
        }

        rawMask = extractGreenMask(maskImage);
        if (rawMask.countActivePixels() > 0) {
            // Sur un remplissage vert translucide, noms, routes et pictogrammes
            // restent visibles et trouent le seuillage couleur. On referme les
            // petites coupures, conserve le grand territoire, puis remplit ses
            // trous intérieurs.
            BinaryMask connected = MorphologyOps.close(rawMask, 1);
            BinaryMask territory = MorphologyOps.keepLargestComponent(connected);
            return fillInteriorHoles(territory);
        }

        if (isRgbBlackAndWhiteMask(maskImage)) {
            rawMask = extractFromRgbBlackAndWhite(maskImage);
        }
        return MorphologyOps.open(rawMask, 1);
    }

    /**
     * Remplit les composantes de fond qui ne sont pas reliées au bord de l'image.
     * Le masque d'entrée doit déjà représenter uniquement le territoire visé.
     *
     * @param territory Masque binaire de la composante territoriale principale.
     * @return Masque dont l'intérieur est plein, sans trous dus aux détails cartographiques.
     */
    private BinaryMask fillInteriorHoles(BinaryMask territory) {
        int width = territory.getWidth();
        int height = territory.getHeight();
        int size = Math.multiplyExact(width, height);
        boolean[] exterior = new boolean[size];
        int[] queue = new int[size];
        int tail = 0;

        // Amorcer l'inondation de l'extérieur sur les quatre bords de l'image.
        for (int x = 0; x < width; x++) {
            tail = enqueueExterior(territory, exterior, queue, tail, x, 0);
            if (height > 1) tail = enqueueExterior(territory, exterior, queue, tail, x, height - 1);
        }
        for (int y = 1; y < height - 1; y++) {
            tail = enqueueExterior(territory, exterior, queue, tail, 0, y);
            if (width > 1) tail = enqueueExterior(territory, exterior, queue, tail, width - 1, y);
        }

        int head = 0;
        while (head < tail) {
            int index = queue[head++];
            int x = index % width;
            int y = index / width;
            if (x > 0) tail = enqueueExterior(territory, exterior, queue, tail, x - 1, y);
            if (x + 1 < width) tail = enqueueExterior(territory, exterior, queue, tail, x + 1, y);
            if (y > 0) tail = enqueueExterior(territory, exterior, queue, tail, x, y - 1);
            if (y + 1 < height) tail = enqueueExterior(territory, exterior, queue, tail, x, y + 1);
        }

        BinaryMask filled = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (territory.get(x, y) || !exterior[index]) {
                    filled.set(x, y, true);
                }
            }
        }
        return filled;
    }

    /** Ajoute un pixel de fond à la file s'il n'a pas encore été visité. */
    private int enqueueExterior(BinaryMask territory, boolean[] exterior, int[] queue,
                                int tail, int x, int y) {
        int index = y * territory.getWidth() + x;
        if (!territory.get(x, y) && !exterior[index]) {
            exterior[index] = true;
            queue[tail++] = index;
        }
        return tail;
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
