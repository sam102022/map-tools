package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.contour.ContourSmoothingConfig;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.refine.AlphaRefinementConfig;
import com.sam102022.photoshop.v2.vote.CellSelectionPolicy;

import java.util.Objects;

/**
 * Record immuable encapsulant la totalité des hyperparamètres du pipeline V2 (Sprints 1 à 9).
 *
 * @param hi                    Seuil minimal de couverture pour classifier une cellule en INSIDE (ex: 0.60).
 * @param lo                    Seuil de rejet en deçà duquel une cellule est OUTSIDE (ex: 0.05).
 * @param cropMargin            Marge de sécurité en pixels autour de la boîte englobante (ex: 90 px).
 * @param eps                   Marge géométrique d'expansion en pixels (ex: 4.0 px).
 * @param rho                   Rayon de l'élément structurant d'ouverture morphologique (ex: 5 px).
 * @param residualHoleMaxArea   Surface maximale des îlots intérieurs comblés (ex: 15 000 px).
 * @param operationMode         Mode d'opération cartographique (TERRITORY ou ZONE).
 * @param sig                   Écart-type du lissage LQR standard (ex: 22.0 px).
 * @param cornerAngle           Angle de détection des coins vifs en degrés (ex: 38.0°).
 * @param cornerWindowL         Demi-fenêtre d'échantillonnage des coins (ex: 80 px).
 * @param r0                    Rayon intérieur de préservation des coins (ex: 35.0 px).
 * @param r1                    Rayon extérieur marquant le lissage plein effet (ex: 95.0 px).
 * @param roundaboutRadiusZr    Rayon relatif d'influence des carrefours giratoires (ex: 2.0).
 * @param straightFactor        Facteur multiplicateur LQR pour tronçons droits (ex: 2.7).
 * @param straightScale         Tolérance Cauchy de l'estimateur robuste (ex: 3.0 px).
 * @param straightThreshold     Écart maximal pour qualifier un tronçon droit (ex: 3.0 px).
 * @param straightTransitionK   Demi-largeur de transition C1 des tronçons droits (ex: 10 points).
 * @param gaussianBlurSigma     Écart-type du flou gaussien séparable d'affinage (ex: 1.4 px).
 * @param contrastStiffness     Facteur de raideur de la coupure d'affinage (ex: 2.0).
 * @param roundaboutBufferInner Rayon normé interne d'exemption des giratoires (ex: 2.3).
 * @param roundaboutBufferOuter Rayon normé externe d'exemption des giratoires (ex: 3.0).
 * @param roadHoleMaxArea       Surface maximale des micro-trous routiers comblés (ex: 2 000 px).
 * @param supersamplingFactor   Facteur de sur-échantillonnage vectoriel sub-pixel SS (ex: 4).
 */
public record V2Config(
        double hi,
        double lo,
        int cropMargin,
        double eps,
        int rho,
        long residualHoleMaxArea,
        OperationMode operationMode,
        double sig,
        double cornerAngle,
        int cornerWindowL,
        double r0,
        double r1,
        double roundaboutRadiusZr,
        double straightFactor,
        double straightScale,
        double straightThreshold,
        int straightTransitionK,
        double gaussianBlurSigma,
        double contrastStiffness,
        double roundaboutBufferInner,
        double roundaboutBufferOuter,
        long roadHoleMaxArea,
        int supersamplingFactor
) {

    /**
     * Valide défensivement l'ensemble des invariants de la configuration V2.
     */
    public V2Config {
        if (lo < 0.0 || lo >= hi || hi > 1.0) {
            throw new IllegalArgumentException("Les seuils doivent vérifier 0.0 <= lo < hi <= 1.0.");
        }
        if (cropMargin < 0) {
            throw new IllegalArgumentException("cropMargin doit être positif ou nul.");
        }
        if (eps < 0.0 || rho < 0 || residualHoleMaxArea < 0) {
            throw new IllegalArgumentException("eps, rho et residualHoleMaxArea doivent être positifs ou nuls.");
        }
        Objects.requireNonNull(operationMode, "operationMode ne doit pas être nul.");
        if (sig <= 0.0 || cornerAngle < 0.0 || cornerWindowL <= 0 || r0 < 0.0 || r1 <= r0) {
            throw new IllegalArgumentException("Paramètres de lissage de contour invalides.");
        }
        if (roundaboutRadiusZr <= 1.0 || straightFactor < 1.0 || straightScale <= 0.0 || straightThreshold <= 0.0 || straightTransitionK < 1) {
            throw new IllegalArgumentException("Paramètres de giratoire ou de tronçon droit invalides.");
        }
        if (gaussianBlurSigma <= 0.0 || contrastStiffness < 1.0 || roundaboutBufferInner <= 0.0 || roundaboutBufferOuter <= roundaboutBufferInner || roadHoleMaxArea < 0) {
            throw new IllegalArgumentException("Paramètres d'affinage spectral invalides.");
        }
        if (supersamplingFactor < 1) {
            throw new IllegalArgumentException("supersamplingFactor doit être supérieur ou égal à 1.");
        }
    }

    /**
     * Fournit la configuration par défaut étalonnée sur le cas de référence Python V7.
     *
     * @return Configuration V2 nominale par défaut.
     */
    public static V2Config defaultConfig() {
        return new V2Config(
                0.60, 0.05, 90,
                4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0,
                2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L,
                4
        );
    }

    /**
     * Convertit vers la politique de sélection de cellules (Sprint 4).
     *
     * @return Nouvelle instance de CellSelectionPolicy.
     */
    public CellSelectionPolicy toCellSelectionPolicy() {
        return new CellSelectionPolicy(hi, lo);
    }

    /**
     * Convertit vers la configuration d'expansion géodésique (Sprint 5).
     *
     * @return Nouvelle instance de ExpansionConfig.
     */
    public ExpansionConfig toExpansionConfig() {
        return new ExpansionConfig(eps, 41, rho, residualHoleMaxArea, 2, operationMode);
    }

    /**
     * Convertit vers la configuration de lissage de contour (Sprints 6 &amp; 7).
     *
     * @return Nouvelle instance de ContourSmoothingConfig.
     */
    public ContourSmoothingConfig toContourSmoothingConfig() {
        return new ContourSmoothingConfig(
                1.0, cornerWindowL, cornerAngle, 4.0, sig, 3.0, 6,
                r0, r1, roundaboutRadiusZr, straightFactor, straightScale,
                straightThreshold, straightTransitionK
        );
    }

    /**
     * Convertit vers la configuration d'affinage spectral (Sprint 8).
     *
     * @return Nouvelle instance de AlphaRefinementConfig.
     */
    public AlphaRefinementConfig toAlphaRefinementConfig() {
        return new AlphaRefinementConfig(
                gaussianBlurSigma, contrastStiffness,
                roundaboutBufferInner, roundaboutBufferOuter,
                roadHoleMaxArea
        );
    }
}
