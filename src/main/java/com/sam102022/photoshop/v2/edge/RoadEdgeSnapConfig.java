package com.sam102022.photoshop.v2.edge;

/**
 * Configuration immuable de l'accrochage du contour sur le bord vectoriel des routes.
 *
 * @param enabled              Active l'accrochage.
 * @param fieldScale           Écart (B - R) d'une chaussée pleine : le champ de « routéité » vaut
 *                             {@code clamp((B - R) / fieldScale, 0, 1)} et son iso-ligne 0,5 est le bord de route.
 * @param searchRadius         Distance maximale (px) de recherche du bord de part et d'autre du contour.
 * @param searchStep           Pas d'échantillonnage (px) le long de la normale.
 * @param minInsideRoadRatio   Proportion minimale d'échantillons de chaussée côté intérieur du bord trouvé.
 * @param insideProbeLength    Longueur (px) du sondage côté intérieur.
 * @param medianHalfWindow     Demi-fenêtre (points) de la médiane glissante de rejet des accrochages parasites.
 * @param outlierTolerance     Écart maximal (px) à la médiane locale pour qu'un accrochage soit retenu.
 * @param smoothingSigma       Écart-type (points) du lissage gaussien des décalages retenus.
 */
public record RoadEdgeSnapConfig(
        boolean enabled,
        double fieldScale,
        double searchRadius,
        double searchStep,
        double minInsideRoadRatio,
        double insideProbeLength,
        int medianHalfWindow,
        double outlierTolerance,
        double smoothingSigma
) {

    /**
     * Valide les invariants de la configuration.
     *
     * @param enabled            Active l'accrochage.
     * @param fieldScale         Écart (B - R) d'une chaussée pleine (&gt; 0).
     * @param searchRadius       Rayon de recherche (&gt; 0).
     * @param searchStep         Pas d'échantillonnage (&gt; 0 et &lt;= searchRadius).
     * @param minInsideRoadRatio Proportion minimale dans [0, 1].
     * @param insideProbeLength  Longueur du sondage intérieur (&gt;= 0).
     * @param medianHalfWindow   Demi-fenêtre de médiane (&gt;= 0).
     * @param outlierTolerance   Tolérance de rejet (&gt; 0).
     * @param smoothingSigma     Écart-type du lissage (&gt; 0).
     * @throws IllegalArgumentException si un paramètre est hors bornes.
     */
    public RoadEdgeSnapConfig {
        if (fieldScale <= 0.0 || searchRadius <= 0.0 || searchStep <= 0.0 || searchStep > searchRadius) {
            throw new IllegalArgumentException("fieldScale, searchRadius et searchStep doivent être strictement positifs (searchStep <= searchRadius).");
        }
        if (minInsideRoadRatio < 0.0 || minInsideRoadRatio > 1.0 || insideProbeLength < 0.0) {
            throw new IllegalArgumentException("minInsideRoadRatio doit être dans [0, 1] et insideProbeLength positif ou nul.");
        }
        if (medianHalfWindow < 0 || outlierTolerance <= 0.0 || smoothingSigma <= 0.0) {
            throw new IllegalArgumentException("medianHalfWindow >= 0, outlierTolerance > 0 et smoothingSigma > 0 sont requis.");
        }
    }

    /**
     * Configuration par défaut, étalonnée sur le style contrasté (CA01) : chaussée B - R ≈ 27, fond ≈ 0.
     *
     * @return Configuration nominale (accrochage actif).
     */
    public static RoadEdgeSnapConfig defaultConfig() {
        return new RoadEdgeSnapConfig(true, 27.0, 8.0, 0.25, 0.8, 3.0, 12, 1.0, 6.0);
    }

    /**
     * Configuration désactivée (le contour lissé est rendu tel quel).
     *
     * @return Configuration avec accrochage inactif.
     */
    public static RoadEdgeSnapConfig disabled() {
        RoadEdgeSnapConfig d = defaultConfig();
        return new RoadEdgeSnapConfig(false, d.fieldScale(), d.searchRadius(), d.searchStep(), d.minInsideRoadRatio(),
                d.insideProbeLength(), d.medianHalfWindow(), d.outlierTolerance(), d.smoothingSigma());
    }
}
