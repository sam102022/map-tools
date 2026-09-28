package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Détecteur des pixels candidats appartenant au réseau routier Google Maps.
 * <p>
 * Ce composant identifie à la fois les axes routiers principaux colorés (autoroutes orange, routes jaunes)
 * et les rues locales blanches ou grises délimitées par des bordures de chaussée.
 * Le seuil de sensibilité est paramétrable via {@code roadSensitivity}.
 * </p>
 */
public class RoadCandidateDetector {

    /** Écart chromatique maximal entre R, G et B pour être considéré comme neutre. */
    private static final int MAX_NEUTRAL_DIFF = 12;

    /** Seuil maximal de luminance d'un bord de chaussée contrasté (casing gris). */
    private static final int MAX_BORDER_LUMINANCE = 215;

    /** Seuil minimal de luminance pour une bordure grise de chaussée. */
    private static final int MIN_BORDER_LUMINANCE = 175;

    /** Distance maximale de recherche transversale d'un bord de chaussée (en pixels). */
    private static final int MAX_CORRIDOR_RADIUS = 8;

    /**
     * Analyse l'image cartographique pour extraire un masque binaire des pixels candidats routiers
     * avec la sensibilité standard (1.0).
     *
     * @param image Image source de la carte (format standard RVB ou ARGB).
     * @return Masque binaire où chaque pixel actif représente un candidat routier.
     * @throws IllegalArgumentException si l'image fournie est null.
     */
    public BinaryMask detect(BufferedImage image) {
        return detect(image, 1.0f);
    }

    /**
     * Analyse l'image cartographique pour extraire un masque binaire des pixels candidats routiers
     * en modulant la détection par la sensibilité spécifiée.
     *
     * @param image           Image source de la carte.
     * @param roadSensitivity Facteur de sensibilité (strictement positif, typiquement 0.5 à 2.0).
     * @return Masque binaire où chaque pixel actif représente un candidat routier.
     * @throws IllegalArgumentException si l'image est null ou si roadSensitivity <= 0.
     */
    public BinaryMask detect(BufferedImage image, float roadSensitivity) {
        validateInputs(image, roadSensitivity);

        int width = image.getWidth();
        int height = image.getHeight();

        int[] rgbData = new int[width * height];
        int[] lumData = new int[width * height];
        image.getRGB(0, 0, width, height, rgbData, 0, width);

        computeLuminanceMap(rgbData, lumData);

        BinaryMask candidates = new BinaryMask(width, height);
        int whiteThreshold = computeWhiteRoadThreshold(roadSensitivity);

        detectRoadPixels(rgbData, lumData, candidates, whiteThreshold, width, height);

        return candidates;
    }

    /**
     * Valide les paramètres d'entrée.
     *
     * @param image           Image de carte.
     * @param roadSensitivity Sensibilité.
     */
    private static void validateInputs(BufferedImage image, float roadSensitivity) {
        if (image == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        if (roadSensitivity <= 0.0f) {
            throw new IllegalArgumentException("La sensibilité aux routes doit être strictement positive : " + roadSensitivity);
        }
    }

    /**
     * Précalcule la carte de luminance UIT-R BT.601 de l'image.
     *
     * @param rgbData Données RVB brutes.
     * @param lumData Tableau de destination pour la luminance.
     */
    private static void computeLuminanceMap(int[] rgbData, int[] lumData) {
        for (int i = 0; i < rgbData.length; i++) {
            int rgb = rgbData[i];
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            lumData[i] = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
        }
    }

    /**
     * Calcule le seuil de luminance minimal requis pour une chaussée blanche selon roadSensitivity.
     *
     * @param roadSensitivity Sensibilité de détection.
     * @return Seuil de luminance entier [200..255].
     */
    private static int computeWhiteRoadThreshold(float roadSensitivity) {
        int threshold = (int) Math.round(240.0 - 15.0 * (roadSensitivity - 1.0f));
        return Math.max(215, Math.min(252, threshold));
    }

    /**
     * Parcourt l'ensemble des pixels et active les candidats routiers majeurs et locaux.
     *
     * @param rgbData        Données RVB.
     * @param lumData        Données de luminance.
     * @param candidates     Masque binaire récepteur.
     * @param whiteThreshold Seuil de luminance pour chaussée blanche.
     * @param width          Largeur de l'image.
     * @param height         Hauteur de l'image.
     */
    private void detectRoadPixels(int[] rgbData, int[] lumData, BinaryMask candidates,
                                  int whiteThreshold, int width, int height) {
        boolean[] whiteSurface = new boolean[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int rgb = rgbData[idx];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (roadScore(r, g, b) > 0.0) {
                    candidates.set(x, y, true);
                } else if (isWhiteRoadSurface(r, g, b, lumData[idx], whiteThreshold, lumData, x, y, width, height)) {
                    candidates.set(x, y, true);
                    whiteSurface[idx] = true;
                }
            }
        }

        includeGreyBorders(rgbData, lumData, candidates, whiteSurface, width, height);
    }

    /**
     * Détermine si un pixel correspond à une chaussée blanche neutre encadrée par un corridor routier.
     *
     * @param r              Composante rouge.
     * @param g              Composante verte.
     * @param b              Composante bleue.
     * @param lum            Luminance du pixel.
     * @param whiteThreshold Seuil minimal de luminance.
     * @param lumData        Carte de luminance complète.
     * @param x              Coordonnée X.
     * @param y              Coordonnée Y.
     * @param width          Largeur.
     * @param height         Hauteur.
     * @return {@code true} si le pixel est une chaussée blanche valide.
     */
    private static boolean isWhiteRoadSurface(int r, int g, int b, int lum, int whiteThreshold,
                                              int[] lumData, int x, int y, int width, int height) {
        if (lum < whiteThreshold) {
            return false;
        }

        if (!isNeutralColor(r, g, b)) {
            return false;
        }

        return hasCorridorBorder(lumData, x, y, width, height);
    }

    /**
     * Vérifie si les composantes RVB présentent une stricte neutralité chromatique (gris / blanc).
     *
     * @param r Rouge.
     * @param g Vert.
     * @param b Bleu.
     * @return {@code true} si le pixel est neutre.
     */
    private static boolean isNeutralColor(int r, int g, int b) {
        int diffRG = Math.abs(r - g);
        int diffRB = Math.abs(r - b);
        int diffGB = Math.abs(g - b);
        return diffRG <= MAX_NEUTRAL_DIFF && diffRB <= MAX_NEUTRAL_DIFF && diffGB <= MAX_NEUTRAL_DIFF;
    }

    /**
     * Vérifie la présence d'une bordure de chaussée sombre dans un voisinage transversal de 1 à 8 pixels.
     *
     * @param lumData Carte de luminance.
     * @param x       Origine X.
     * @param y       Origine Y.
     * @param width   Largeur.
     * @param height  Hauteur.
     * @return {@code true} si une transition vers un bord sombre existe horizontalement ou verticalement.
     */
    private static boolean hasCorridorBorder(int[] lumData, int x, int y, int width, int height) {
        boolean borderH = hasBorderInDirection(lumData, x, y, 1, 0, width, height)
                || hasBorderInDirection(lumData, x, y, -1, 0, width, height);
        boolean borderV = hasBorderInDirection(lumData, x, y, 0, 1, width, height)
                || hasBorderInDirection(lumData, x, y, 0, -1, width, height);
        return borderH || borderV;
    }

    /**
     * Balaye une direction donnée jusqu'à {@value #MAX_CORRIDOR_RADIUS} pixels pour trouver un bord sombre.
     *
     * @param lumData Carte de luminance.
     * @param startX  X de départ.
     * @param startY  Y de départ.
     * @param dx      Pas en X (-1, 0, 1).
     * @param dy      Pas en Y (-1, 0, 1).
     * @param width   Largeur.
     * @param height  Hauteur.
     * @return {@code true} si un pixel avec luminance <= {@value #MAX_BORDER_LUMINANCE} est rencontré.
     */
    private static boolean hasBorderInDirection(int[] lumData, int startX, int startY,
                                                int dx, int dy, int width, int height) {
        for (int step = 1; step <= MAX_CORRIDOR_RADIUS; step++) {
            int cx = startX + dx * step;
            int cy = startY + dy * step;
            if (cx < 0 || cx >= width || cy < 0 || cy >= height) {
                return true;
            }
            if (lumData[cy * width + cx] <= MAX_BORDER_LUMINANCE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Intègre les bordures de chaussée grises neutres adjacentes à des surfaces blanches actives.
     *
     * @param rgbData      Données RVB.
     * @param lumData      Données de luminance.
     * @param candidates   Masque de destination.
     * @param whiteSurface Surface blanche identifiée.
     * @param width        Largeur.
     * @param height       Hauteur.
     */
    private static void includeGreyBorders(int[] rgbData, int[] lumData, BinaryMask candidates,
                                           boolean[] whiteSurface, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int lum = lumData[idx];
                if (lum >= MIN_BORDER_LUMINANCE && lum <= MAX_BORDER_LUMINANCE) {
                    int rgb = rgbData[idx];
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    if (isNeutralColor(r, g, b) && isAdjacentToWhiteSurface(whiteSurface, x, y, width, height)) {
                        candidates.set(x, y, true);
                    }
                }
            }
        }
    }

    /**
     * Vérifie si le pixel est adjacent (4-voisinage) à une surface blanche active.
     */
    private static boolean isAdjacentToWhiteSurface(boolean[] whiteSurface, int x, int y, int width, int height) {
        if (x > 0 && whiteSurface[y * width + (x - 1)]) return true;
        if (x < width - 1 && whiteSurface[y * width + (x + 1)]) return true;
        if (y > 0 && whiteSurface[(y - 1) * width + x]) return true;
        return (y < height - 1 && whiteSurface[(y + 1) * width + x]);
    }

    /**
     * Calcule un score de confiance chromatique pour un triplet RVB donné pour les grands axes colorés.
     *
     * @param r Composante rouge du pixel [0..255].
     * @param g Composante verte du pixel [0..255].
     * @param b Composante bleue du pixel [0..255].
     * @return Score de confiance dans l'intervalle [0.0, 1.0].
     */
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
