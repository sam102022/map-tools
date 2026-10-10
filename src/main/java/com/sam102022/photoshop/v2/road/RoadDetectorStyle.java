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
    private static final int MIN_MOTORWAY_DELTA_BLUE_RED = 30;
    private static final int MAX_MOTORWAY_DELTA_BLUE_GREEN = 45;
    private static final int MAX_MOTORWAY_RED = 190;

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
        boolean ordinaryRoad = deltaBlueRed >= MIN_DELTA_BLUE_RED
                && deltaBlueGreen >= MIN_DELTA_BLUE_GREEN
                && deltaBlueGreen <= MAX_DELTA_BLUE_GREEN
                && r < MAX_RED_LUMINANCE;
        return ordinaryRoad;
    }

    /**
     * Détecte les autoroutes (teinte plus sombre que la voirie ordinaire). Elles ne participent pas à la
     * segmentation en cellules, mais servent de routes longées lorsqu'elles bordent le polygone d'intention.
     *
     * @param image Carte source.
     * @return Masque des pixels d'autoroute.
     * @throws IllegalArgumentException si l'image est null.
     */
    public BinaryMask detectMotorways(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image cartographique ne peut pas être null.");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        boolean[] maskData = new boolean[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >>> 16) & 0xFF;
            int g = (pixels[i] >>> 8) & 0xFF;
            int b = pixels[i] & 0xFF;
            maskData[i] = isMotorwayColor(r, b - r, b - g);
        }
        return new BinaryMask(width, height, maskData);
    }

    /**
     * Teinte des autoroutes du style contrasté : bleu-gris plus sombre et plus saturé que la voirie ordinaire
     * (par exemple RVB 139/165/193 ou 121/147/185 sur l'A811, Territoire CA02), hors de la plage B - V de la
     * voirie ordinaire.
     *
     * @param r              Composante rouge.
     * @param deltaBlueRed   Écart B - R.
     * @param deltaBlueGreen Écart B - V.
     * @return true pour un pixel d'autoroute.
     */
    static boolean isMotorwayColor(int r, int deltaBlueRed, int deltaBlueGreen) {
        return deltaBlueRed >= MIN_MOTORWAY_DELTA_BLUE_RED
                && deltaBlueGreen > MAX_DELTA_BLUE_GREEN
                && deltaBlueGreen <= MAX_MOTORWAY_DELTA_BLUE_GREEN
                && r < MAX_MOTORWAY_RED;
    }
}
