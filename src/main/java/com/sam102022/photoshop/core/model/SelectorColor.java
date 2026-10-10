package com.sam102022.photoshop.core.model;

import java.awt.Color;

/**
 * Énumération des couleurs de sélection pour le détourage de zones internes dans une carte.
 */
public enum SelectorColor {

    /**
     * Détection automatique de la couleur d'annotation présente sur le calque.
     */
    AUTO,

    /**
     * Couleur rouge vif (ex: Zone 1).
     */
    RED,

    /**
     * Couleur bleue vive (ex: Zone 2).
     */
    BLUE,

    /**
     * Couleur magenta / violette (ex: Zone 3).
     */
    MAGENTA,

    /**
     * Couleur cyan / turquoise (ex: Zone 4).
     */
    CYAN;

    /**
     * Analyse et convertit une chaîne de caractères en {@link SelectorColor} insensible à la casse.
     *
     * @param name Nom ou alias de la couleur (ex: "red", "rouge", "blue", "bleu", "auto").
     * @return L'instance correspondante de {@link SelectorColor}.
     * @throws IllegalArgumentException si la chaîne est {@code null} ou ne correspond à aucune couleur supportée.
     */
    public static SelectorColor fromString(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom de la couleur de sélection ne peut pas être null ou vide.");
        }

        String normalized = name.trim().toLowerCase();
        return switch (normalized) {
            case "auto" -> AUTO;
            case "red", "rouge" -> RED;
            case "blue", "bleu" -> BLUE;
            case "magenta" -> MAGENTA;
            case "cyan" -> CYAN;
            default -> throw new IllegalArgumentException("Couleur de sélection non reconnue ou non supportée : " + name);
        };
    }

    /**
     * Vérifie si un composant chromatique RGB correspond à cette couleur de sélection.
     *
     * @param r Composante rouge [0..255].
     * @param g Composante verte [0..255].
     * @param b Composante bleue [0..255].
     * @return {@code true} si le pixel répond aux critères chromatiques stricts, {@code false} sinon.
     */
    public boolean matches(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));

        // Luminosité minimale requise pour une couleur d'annotation vive
        if (max < 80) {
            return false;
        }

        int delta = max - min;
        // Saturation minimale (delta / max >= 0.40 => delta * 100 >= 40 * max)
        if (delta * 100 < 40 * max) {
            return false;
        }

        int hue = computeHue(r, g, b, max, delta);

        return switch (this) {
            case RED -> isRedHue(hue);
            case BLUE -> isBlueHue(hue);
            case MAGENTA -> isMagentaHue(hue);
            case CYAN -> isCyanHue(hue);
            case AUTO -> isRedHue(hue) || isBlueHue(hue) || isMagentaHue(hue) || isCyanHue(hue);
        };
    }

    /**
     * Surcharge vérifiant si un objet {@link Color} correspond à cette couleur de sélection.
     *
     * @param color Couleur AWT à tester.
     * @return {@code true} si l'objet est non-nul et répond aux critères chromatiques, {@code false} sinon.
     */
    public boolean matches(Color color) {
        if (color == null) {
            return false;
        }
        return matches(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Calcule la teinte (Hue) en degrés [0..359] de manière exacte en arithmétique entière.
     *
     * @param r     Composante rouge.
     * @param g     Composante verte.
     * @param b     Composante bleue.
     * @param max   Valeur maximale parmi r, g, b.
     * @param delta Différence (max - min).
     * @return Teinte en degrés dans l'intervalle [0..359].
     */
    private static int computeHue(int r, int g, int b, int max, int delta) {
        if (delta == 0) {
            return 0;
        }

        int hue;
        if (max == r) {
            hue = (60 * (g - b)) / delta;
            if (hue < 0) {
                hue += 360;
            }
        } else if (max == g) {
            hue = 120 + (60 * (b - r)) / delta;
        } else {
            hue = 240 + (60 * (r - g)) / delta;
        }

        return (hue % 360 + 360) % 360;
    }

    /**
     * Vérifie si la teinte se situe dans le secteur du rouge vif.
     *
     * @param hue Teinte en degrés [0..359].
     * @return {@code true} si la teinte est rouge.
     */
    private static boolean isRedHue(int hue) {
        return hue <= 18 || hue >= 342;
    }

    /**
     * Vérifie si la teinte se situe dans le secteur du bleu vif.
     *
     * @param hue Teinte en degrés [0..359].
     * @return {@code true} si la teinte est bleue.
     */
    private static boolean isBlueHue(int hue) {
        return hue >= 205 && hue <= 255;
    }

    /**
     * Vérifie si la teinte se situe dans le secteur du magenta / violet.
     *
     * @param hue Teinte en degrés [0..359].
     * @return {@code true} si la teinte est magenta.
     */
    private static boolean isMagentaHue(int hue) {
        return hue >= 275 && hue <= 335;
    }

    /**
     * Vérifie si la teinte se situe dans le secteur du cyan / turquoise.
     *
     * @param hue Teinte en degrés [0..359].
     * @return {@code true} si la teinte est cyan.
     */
    private static boolean isCyanHue(int hue) {
        return hue >= 165 && hue <= 198;
    }
}
