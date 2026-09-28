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
        if (isNoiseOrMapBackground(r, g, b)) {
            return false;
        }

        return switch (this) {
            case RED -> matchesRed(r, g, b);
            case BLUE -> matchesBlue(r, g, b);
            case MAGENTA -> matchesMagenta(r, g, b);
            case CYAN -> matchesCyan(r, g, b);
            case AUTO -> matchesAny(r, g, b);
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
     * Détermine si le pixel correspond à du bruit, un fond neutre ou un élément cartographique protégé.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel doit être systématiquement écarté, {@code false} sinon.
     */
    private static boolean isNoiseOrMapBackground(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));

        // Luminosité HSB < 0.20 (51 / 255 = 0.20)
        if (max < 51) {
            return true;
        }

        // Saturation HSB = (max - min) / max < 0.35 => (max - min) * 100 < 35 * max
        if ((max - min) * 100 < 35 * max) {
            return true;
        }

        if (isGreenTerritory(r, g, b)) {
            return true;
        }

        return isOrangeRoad(r, g, b);
    }

    /**
     * Vérifie si les composantes correspondent à la teinte verte du territoire.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est identifié comme vert territoire.
     */
    private static boolean isGreenTerritory(int r, int g, int b) {
        return g > r + 20 && g > b + 20;
    }

    /**
     * Vérifie si les composantes correspondent à une chaussée routière jaune ou orange Google Maps.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est identifié comme route orange/jaune.
     */
    private static boolean isOrangeRoad(int r, int g, int b) {
        return r > 200 && g >= 130 && g <= 210 && b < 120;
    }

    /**
     * Vérifie le prédicat chromatique propre au rouge vif.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est un rouge vif d'annotation.
     */
    private static boolean matchesRed(int r, int g, int b) {
        return r >= 150 && r > g + 40 && r > b + 40;
    }

    /**
     * Vérifie le prédicat chromatique propre au bleu vif.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est un bleu vif d'annotation.
     */
    private static boolean matchesBlue(int r, int g, int b) {
        return b >= 150 && b > r + 40 && b > g + 20;
    }

    /**
     * Vérifie le prédicat chromatique propre au magenta.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est un magenta d'annotation.
     */
    private static boolean matchesMagenta(int r, int g, int b) {
        return r >= 140 && b >= 140 && r > g + 40 && b > g + 40;
    }

    /**
     * Vérifie le prédicat chromatique propre au cyan.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est un cyan d'annotation.
     */
    private static boolean matchesCyan(int r, int g, int b) {
        return g >= 140 && b >= 140 && g > r + 40 && b > r + 40;
    }

    /**
     * Vérifie si le pixel correspond à l'une quelconque des couleurs d'annotation supportées.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si au moins une signature couleur est satisfaite.
     */
    private static boolean matchesAny(int r, int g, int b) {
        return matchesRed(r, g, b) || matchesBlue(r, g, b) || matchesMagenta(r, g, b) || matchesCyan(r, g, b);
    }
}
