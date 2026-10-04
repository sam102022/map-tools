package com.sam102022.photoshop.v2.contour;

/**
 * Configuration immuable des hyperparamètres de lissage vectoriel V2.
 * Les valeurs par défaut correspondent rigoureusement aux étalons validés du prototype Python V5.
 *
 * @param resampleStep    Pas de rééchantillonnage curviligne uniforme en pixels (défaut : 1.0 px).
 * @param cornerWindowL   Demi-largeur de fenêtre pour le calcul des vecteurs sécants de coins (défaut : 80 px).
 * @param cornerThreshold Angle minimal en degrés pour la détection d'un coin (défaut : 38.0°).
 * @param preSmoothSigma  Écart-type du pré-filtrage gaussien périodique des coins (défaut : 4.0 px).
 * @param lqrSigma        Écart-type de la régression quadratique locale LQR (défaut : 22.0 px).
 * @param lqrScale        Échelle du M-estimateur robuste de Cauchy/Tukey (défaut : 3.0 px).
 * @param lqrIterations   Nombre d'itérations de repondération robuste LQR (défaut : 6).
 * @param blendR0         Rayon intérieur en pixels de préservation totale de l'arête brute (défaut : 35.0 px).
 * @param blendR1         Rayon extérieur en pixels marquant le lissage plein effet (défaut : 95.0 px).
 * @param roundaboutZr   Rayon relatif d'influence de l'anneau giratoire (défaut : 2.0).
 */
public record ContourSmoothingConfig(
        double resampleStep,
        int cornerWindowL,
        double cornerThreshold,
        double preSmoothSigma,
        double lqrSigma,
        double lqrScale,
        int lqrIterations,
        double blendR0,
        double blendR1,
        double roundaboutZr
) {

    /**
     * Valide les invariants de la configuration de lissage.
     */
    public ContourSmoothingConfig {
        if (resampleStep <= 0.0) {
            throw new IllegalArgumentException("resampleStep doit être strictement positif.");
        }
        if (cornerWindowL <= 0) {
            throw new IllegalArgumentException("cornerWindowL doit être strictement positif.");
        }
        if (cornerThreshold < 0.0 || cornerThreshold > 180.0) {
            throw new IllegalArgumentException("cornerThreshold doit être compris entre 0 et 180 degrés.");
        }
        if (preSmoothSigma <= 0.0 || lqrSigma <= 0.0 || lqrScale <= 0.0) {
            throw new IllegalArgumentException("Les sigmas et échelles doivent être strictement positifs.");
        }
        if (lqrIterations <= 0) {
            throw new IllegalArgumentException("lqrIterations doit être strictement positif.");
        }
        if (blendR0 < 0.0 || blendR1 <= blendR0) {
            throw new IllegalArgumentException("blendR1 doit être strictement supérieur à blendR0 >= 0.");
        }
        if (roundaboutZr <= 1.0) {
            throw new IllegalArgumentException("roundaboutZr doit être strictement supérieur à 1.0.");
        }
    }

    /**
     * Fournit la configuration standard nominale pour le lissage et les ronds-points.
     *
     * @return Configuration par défaut immuable.
     */
    public static ContourSmoothingConfig defaultConfig() {
        return new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0
        );
    }
}
