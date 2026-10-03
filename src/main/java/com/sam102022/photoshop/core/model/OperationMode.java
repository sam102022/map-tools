package com.sam102022.photoshop.core.model;

/**
 * Modes d'opération supportés par le moteur de découpage cartographique.
 */
public enum OperationMode {

    /**
     * Détecte automatiquement la présence d'un cadre de sélection de zone fermé
     * pour basculer en découpage de zone, ou se replie sur le détourage de territoire global.
     */
    AUTO,

    /**
     * Force le détourage du territoire global à partir du masque vert initial.
     */
    TERRITORY,

    /**
     * Force le découpage d'une zone interne ciblée délimitée par un cadre d'annotation polychrome.
     */
    ZONE;

    /**
     * Analyse et convertit une chaîne de caractères en {@link OperationMode} insensible à la casse.
     *
     * @param name Nom ou alias du mode (ex: "auto", "territory", "territoire", "zone").
     * @return Le mode d'opération correspondant.
     * @throws IllegalArgumentException si le nom est {@code null} ou ne correspond à aucun mode connu.
     */
    public static OperationMode fromString(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du mode d'opération ne peut pas être null ou vide.");
        }

        String normalized = name.trim().toLowerCase();
        return switch (normalized) {
            case "auto" -> AUTO;
            case "territory", "territoire" -> TERRITORY;
            case "zone" -> ZONE;
            default -> throw new IllegalArgumentException("Mode d'opération non reconnu : " + name);
        };
    }
}
