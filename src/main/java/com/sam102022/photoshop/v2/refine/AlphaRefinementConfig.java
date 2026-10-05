package com.sam102022.photoshop.v2.refine;

/**
 * Configuration immuable des hyperparamètres d'affinage spectral de la couverture alpha
 * et de protection des carrefours giratoires (Sprint 8).
 *
 * @param gaussianBlurSigma     Écart-type du filtre gaussien séparable appliqué sur le masque autorisé (en pixels).
 * @param contrastStiffness     Facteur de raideur du contraste pour la fonction de transfert à seuillage doux (FK).
 * @param roundaboutBufferInner Rayon normé interne d'exemption totale autour des ronds-points (FZ0).
 * @param roundaboutBufferOuter Rayon normé externe de transition smoothstep autour des ronds-points (FZ1).
 * @param roadHoleMaxArea       Surface maximale (en pixels) des cavités intérieures du réseau routier à combler.
 */
public record AlphaRefinementConfig(
        double gaussianBlurSigma,
        double contrastStiffness,
        double roundaboutBufferInner,
        double roundaboutBufferOuter,
        long roadHoleMaxArea
) {

    /**
     * Valide les invariants de la configuration d'affinage spectral.
     *
     * @param gaussianBlurSigma     Écart-type du filtre gaussien (doit être &gt; 0).
     * @param contrastStiffness     Facteur de raideur du contraste (doit être &ge; 1.0).
     * @param roundaboutBufferInner Rayon normé interne de protection (doit être &gt; 0).
     * @param roundaboutBufferOuter Rayon normé externe de protection (doit être &gt; roundaboutBufferInner).
     * @param roadHoleMaxArea       Surface maximale des cavités à combler (doit être &ge; 0).
     * @throws IllegalArgumentException si l'un des paramètres est hors bornes.
     */
    public AlphaRefinementConfig {
        if (gaussianBlurSigma <= 0.0) {
            throw new IllegalArgumentException("gaussianBlurSigma doit être strictement positif.");
        }
        if (contrastStiffness < 1.0) {
            throw new IllegalArgumentException("contrastStiffness doit être supérieur ou égal à 1.0.");
        }
        if (roundaboutBufferInner <= 0.0) {
            throw new IllegalArgumentException("roundaboutBufferInner doit être strictement positif.");
        }
        if (roundaboutBufferOuter <= roundaboutBufferInner) {
            throw new IllegalArgumentException("roundaboutBufferOuter doit être strictement supérieur à roundaboutBufferInner.");
        }
        if (roadHoleMaxArea < 0) {
            throw new IllegalArgumentException("roadHoleMaxArea doit être positif ou nul.");
        }
    }

    /**
     * Fournit la configuration d'affinage spectral par défaut étalonnée sur le cas de référence CA01.
     *
     * @return une instance de {@link AlphaRefinementConfig} initialisée avec les hyperparamètres nominaux.
     */
    public static AlphaRefinementConfig defaultConfig() {
        return new AlphaRefinementConfig(1.4, 2.0, 2.3, 3.0, 2000L);
    }
}
