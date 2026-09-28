package com.sam102022.photoshop.core.model;

/**
 * Configuration immuable paramétrant le moteur de recalage géodésique sur les routes et le détourage.
 *
 * @param snapDistance      Rayon maximal de recherche et d'attraction vers les axes routiers en pixels (> 0).
 * @param roadSensitivity   Facteur multiplicateur de sensibilité chromatique des routes (> 0).
 * @param smoothRadius      Rayon d'adoucissement / lissage gaussien des bords en pixels (>= 0).
 * @param seedErosionRadius Rayon de sécurité d'érosion pour la préservation du noyau intérieur profond (>= 0).
 * @param closingRadius     Rayon de fermeture morphologique pour unifier les sous-parcelles et combler les césures (>= 0).
 * @param antialiasing      Activation du suréchantillonnage sub-pixel et de l'anti-aliasing vectoriel.
 * @param mode              Mode d'opération sélectionné (AUTO, TERRITORY ou ZONE).
 * @param zoneColor         Couleur de l'annotation identifiant la zone interne ciblée (AUTO, RED, BLUE, etc.).
 */
public record SnappingConfig(
        int snapDistance,
        float roadSensitivity,
        int smoothRadius,
        int seedErosionRadius,
        int closingRadius,
        boolean antialiasing,
        OperationMode mode,
        SelectorColor zoneColor
) {

    /**
     * Constructeur canonique compact validant les bornes strictes de chaque paramètre de configuration.
     *
     * @throws IllegalArgumentException si un paramètre est négatif, nul ou hors de son domaine de validité.
     */
    public SnappingConfig {
        if (snapDistance <= 0) {
            throw new IllegalArgumentException("snapDistance doit être > 0 : " + snapDistance);
        }
        if (roadSensitivity <= 0.0f) {
            throw new IllegalArgumentException("roadSensitivity doit être > 0 : " + roadSensitivity);
        }
        if (smoothRadius < 0) {
            throw new IllegalArgumentException("smoothRadius doit être >= 0 : " + smoothRadius);
        }
        if (seedErosionRadius < 0) {
            throw new IllegalArgumentException("seedErosionRadius doit être >= 0 : " + seedErosionRadius);
        }
        if (closingRadius < 0) {
            throw new IllegalArgumentException("closingRadius doit être >= 0 : " + closingRadius);
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode ne doit pas être null");
        }
        if (zoneColor == null) {
            throw new IllegalArgumentException("zoneColor ne doit pas être null");
        }
    }

    /**
     * Constructeur de compatibilité à six paramètres (mode AUTO et zoneColor AUTO par défaut).
     *
     * @param snapDistance      Rayon maximal de recherche vers les axes routiers en pixels (> 0).
     * @param roadSensitivity   Sensibilité chromatique des routes (> 0).
     * @param smoothRadius      Rayon d'adoucissement des bords en pixels (>= 0).
     * @param seedErosionRadius Rayon d'érosion du noyau intérieur (>= 0).
     * @param closingRadius     Rayon de fermeture morphologique (>= 0).
     * @param antialiasing      Activation de l'anti-aliasing sub-pixel.
     * @throws IllegalArgumentException si les bornes de configuration sont enfreintes.
     */
    public SnappingConfig(int snapDistance, float roadSensitivity, int smoothRadius,
                          int seedErosionRadius, int closingRadius, boolean antialiasing) {
        this(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius, closingRadius,
                antialiasing, OperationMode.AUTO, SelectorColor.AUTO);
    }

    /**
     * Constructeur de compatibilité historique à cinq paramètres (anti-aliasing activé par défaut, mode AUTO, couleur AUTO).
     *
     * @param snapDistance      Rayon maximal de recherche vers les axes routiers en pixels (> 0).
     * @param roadSensitivity   Sensibilité chromatique des routes (> 0).
     * @param smoothRadius      Rayon d'adoucissement des bords en pixels (>= 0).
     * @param seedErosionRadius Rayon d'érosion du noyau intérieur (>= 0).
     * @param closingRadius     Rayon de fermeture morphologique (>= 0).
     * @throws IllegalArgumentException si les bornes de configuration sont enfreintes.
     */
    public SnappingConfig(int snapDistance, float roadSensitivity, int smoothRadius,
                          int seedErosionRadius, int closingRadius) {
        this(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius, closingRadius,
                true, OperationMode.AUTO, SelectorColor.AUTO);
    }

    /**
     * Retourne une instance de configuration initialisée avec les valeurs recommandées par défaut.
     *
     * @return Configuration standard (snapDistance=40, roadSensitivity=1.0, smoothRadius=1, seedErosionRadius=8,
     * closingRadius=2, antialiasing=true, mode=AUTO, zoneColor=AUTO).
     */
    public static SnappingConfig defaults() {
        return new SnappingConfig(40, 1.0f, 1, 8, 2, true, OperationMode.AUTO, SelectorColor.AUTO);
    }

    /**
     * Retourne une nouvelle instance de configuration en mettant à jour le mode d'opération.
     *
     * @param mode Nouveau mode d'opération.
     * @return Nouvelle instance de {@link SnappingConfig}.
     */
    public SnappingConfig withMode(OperationMode mode) {
        return new SnappingConfig(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius,
                closingRadius, antialiasing, mode, zoneColor);
    }

    /**
     * Retourne une nouvelle instance de configuration en mettant à jour la couleur de sélection de zone.
     *
     * @param zoneColor Nouvelle couleur d'annotation ciblée.
     * @return Nouvelle instance de {@link SnappingConfig}.
     */
    public SnappingConfig withZoneColor(SelectorColor zoneColor) {
        return new SnappingConfig(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius,
                closingRadius, antialiasing, mode, zoneColor);
    }

    /**
     * Crée un nouveau constructeur fluide (Builder) pour assembler une configuration personnalisée.
     *
     * @return Nouvelle instance de {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Constructeur fluide (Builder) permettant d'instancier pas-à-pas un {@link SnappingConfig}.
     */
    public static class Builder {
        private int snapDistance = 40;
        private float roadSensitivity = 1.0f;
        private int smoothRadius = 1;
        private int seedErosionRadius = 8;
        private int closingRadius = 2;
        private boolean antialiasing = true;
        private OperationMode mode = OperationMode.AUTO;
        private SelectorColor zoneColor = SelectorColor.AUTO;

        /**
         * Définit la distance maximale de recalage vers les routes en pixels.
         *
         * @param snapDistance Rayon de recherche (> 0).
         * @return Cette instance de constructeur.
         */
        public Builder snapDistance(int snapDistance) {
            this.snapDistance = snapDistance;
            return this;
        }

        /**
         * Définit le coefficient de sensibilité pour la détection des routes.
         *
         * @param roadSensitivity Facteur de sensibilité (> 0).
         * @return Cette instance de constructeur.
         */
        public Builder roadSensitivity(float roadSensitivity) {
            this.roadSensitivity = roadSensitivity;
            return this;
        }

        /**
         * Définit le rayon de lissage / adoucissement des contours en pixels.
         *
         * @param smoothRadius Rayon d'adoucissement (>= 0).
         * @return Cette instance de constructeur.
         */
        public Builder smoothRadius(int smoothRadius) {
            this.smoothRadius = smoothRadius;
            return this;
        }

        /**
         * Définit le rayon d'érosion pour la préservation du noyau intérieur.
         *
         * @param seedErosionRadius Rayon d'érosion en pixels (>= 0).
         * @return Cette instance de constructeur.
         */
        public Builder seedErosionRadius(int seedErosionRadius) {
            this.seedErosionRadius = seedErosionRadius;
            return this;
        }

        /**
         * Définit le rayon de fermeture morphologique pour l'unification des composantes disjointes.
         *
         * @param closingRadius Rayon de fermeture en pixels (>= 0).
         * @return Cette instance de constructeur.
         */
        public Builder closingRadius(int closingRadius) {
            this.closingRadius = closingRadius;
            return this;
        }

        /**
         * Active ou désactive l'anti-aliasing sub-pixel avec suréchantillonnage.
         *
         * @param antialiasing Vrai pour activer l'anti-aliasing sub-pixel.
         * @return Cette instance de constructeur.
         */
        public Builder antialiasing(boolean antialiasing) {
            this.antialiasing = antialiasing;
            return this;
        }

        /**
         * Définit le mode d'opération (AUTO, TERRITORY ou ZONE).
         *
         * @param mode Mode d'opération.
         * @return Cette instance de constructeur.
         */
        public Builder mode(OperationMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Définit la couleur d'annotation ciblée pour le découpage de zone interne.
         *
         * @param zoneColor Couleur de sélection.
         * @return Cette instance de constructeur.
         */
        public Builder zoneColor(SelectorColor zoneColor) {
            this.zoneColor = zoneColor;
            return this;
        }

        /**
         * Construit et valide l'instance immuable de {@link SnappingConfig}.
         *
         * @return Nouvelle configuration validée.
         * @throws IllegalArgumentException si des valeurs définies violent les règles de validation.
         */
        public SnappingConfig build() {
            return new SnappingConfig(snapDistance, roadSensitivity, smoothRadius,
                    seedErosionRadius, closingRadius, antialiasing, mode, zoneColor);
        }
    }
}
