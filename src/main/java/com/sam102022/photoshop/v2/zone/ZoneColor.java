package com.sam102022.photoshop.v2.zone;

import java.util.Locale;

/**
 * Couleurs des zones tracées sur le plan {@code 02_plan_avec_zones.png} (contour fermé d'un trait de couleur).
 * <p>
 * Les valeurs RVB de référence sont les couleurs médianes mesurées sur les tracés du Territoire CA02 ; la
 * tolérance colorimétrique de {@link ZoneExtractor} absorbe le liseré d'anti-crénelage des traits.
 */
public enum ZoneColor {

    /** Zone 1 : trait rouge. */
    ROUGE(1, 251, 49, 49),
    /** Zone 2 : trait vert. */
    VERTE(2, 46, 176, 49),
    /** Zone 3 : trait mauve. */
    MAUVE(3, 167, 49, 159),
    /** Zone 4 : trait orange. */
    ORANGE(4, 251, 164, 49),
    /** Zone 5 : trait cyan. */
    CYAN(5, 50, 218, 209),
    /** Zone 6 : trait noir. */
    NOIR(6, 41, 43, 46);

    private final int number;
    private final int red;
    private final int green;
    private final int blue;

    /**
     * Associe un numéro de zone à sa couleur de tracé.
     *
     * @param number Numéro de la zone (1 à 6).
     * @param red    Composante rouge de référence.
     * @param green  Composante verte de référence.
     * @param blue   Composante bleue de référence.
     */
    ZoneColor(int number, int red, int green, int blue) {
        this.number = number;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    /**
     * Numéro de la zone.
     *
     * @return Numéro (1 à 6).
     */
    public int number() {
        return number;
    }

    /**
     * Couleur de référence au format RVB empaqueté (0xRRGGBB).
     *
     * @return Couleur RVB.
     */
    public int rgb() {
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * Distance euclidienne (dans l'espace RVB) entre une couleur et la couleur de référence de la zone.
     *
     * @param rgb Couleur RVB empaquetée (le canal alpha est ignoré).
     * @return Distance RVB.
     */
    public double distance(int rgb) {
        int dr = ((rgb >> 16) & 0xFF) - red;
        int dg = ((rgb >> 8) & 0xFF) - green;
        int db = (rgb & 0xFF) - blue;
        return Math.sqrt((double) dr * dr + (double) dg * dg + (double) db * db);
    }

    /**
     * Libellé utilisé dans les noms de fichiers de sortie (ex. {@code zone1_rouge}).
     *
     * @return Libellé de fichier.
     */
    public String fileLabel() {
        return "zone" + number + "_" + name().toLowerCase(Locale.ROOT);
    }

    /**
     * Retrouve une zone par son numéro ou son nom (insensible à la casse).
     *
     * @param value Numéro (« 3 ») ou nom (« mauve »).
     * @return Zone correspondante.
     * @throws IllegalArgumentException si la valeur ne désigne aucune zone.
     */
    public static ZoneColor parse(String value) {
        String v = value.trim().toUpperCase(Locale.ROOT);
        for (ZoneColor zone : values()) {
            if (zone.name().equals(v) || String.valueOf(zone.number).equals(v)) {
                return zone;
            }
        }
        throw new IllegalArgumentException("Zone inconnue : " + value + " (attendu : 1 à 6 ou rouge, verte, mauve, orange, cyan, noir).");
    }
}
