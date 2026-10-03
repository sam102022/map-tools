package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Détecteur colorimétrique des surfaces de chaussée optimisé pour le style contrasté Google Maps.
 * Applique la règle différentielle bleu-gris issue de l'algorithme de référence script.py :
 * {@code (B - R >= 12) && (B - G >= 2) && (B - G <= 22) && (R < 228)}.
 */
public final class RoadDetectorStyle implements RoadDetector {

    private static final int MIN_DELTA_BLUE_RED = 12;
    private static final int MIN_DELTA_BLUE_GREEN = 2;
    private static final int MAX_DELTA_BLUE_GREEN = 22;
    private static final int MAX_RED_LUMINANCE = 228;

    /**
     * Initialise une nouvelle instance du détecteur colorimétrique de routes.
     */
    public RoadDetectorStyle() {
    }

    /**
     * Extrait le masque brut des routes à partir de l'image de segmentation cartographique.
     *
     * @param image Image source de carte contrastée.
     * @return Masque binaire brut des chaussées détectées.
     * @throws IllegalArgumentException Si l'image est null.
     */
    @Override
    public BinaryMask detect(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image cartographique ne peut pas être null.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        boolean[] maskData = new boolean[width * height];

        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >>> 16) & 0xFF;
            int g = (rgb >>> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (isRoadColor(r, g, b)) {
                maskData[i] = true;
            }
        }

        return new BinaryMask(width, height, maskData);
    }

    /**
     * Évalue si les composantes RVB correspondent à la signature colorimétrique d'une chaussée.
     *
     * @param r Composante rouge [0..255].
     * @param g Composante verte [0..255].
     * @param b Composante bleue [0..255].
     * @return Vrai si le triplet RVB valide la règle de chaussée.
     */
    public static boolean isRoadColor(int r, int g, int b) {
        int deltaBlueRed = b - r;
        int deltaBlueGreen = b - g;
        return deltaBlueRed >= MIN_DELTA_BLUE_RED
                && deltaBlueGreen >= MIN_DELTA_BLUE_GREEN
                && deltaBlueGreen <= MAX_DELTA_BLUE_GREEN
                && r < MAX_RED_LUMINANCE;
    }
}
