package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Façade d'orchestration globale de la géométrie vectorielle sub-pixel du Sprint 6.
 * Enchaîne séquentiellement :
 * 1. Extraction sub-pixel par Marching Squares 2D et rééchantillonnage 1 px ;
 * 2. Détection multi-échelles des angles vifs (coins) ;
 * 3. Lissage quadratique local LQR robuste (rejet des carrefours) ;
 * 4. Modulation continue par fondu Hermite smoothstep ;
 * 5. Détection des carrefours giratoires et modélisation des anneaux ;
 * 6. Substitution géométrique des arcs de ronds-points par splines C1 d'Hermite ;
 * 7. Production du contrat immuable {@link SmoothVectorContour}.
 */
public class ContourSmoothingEngine {

    private static final Logger LOGGER = Logger.getLogger(ContourSmoothingEngine.class.getName());

    private final SubpixelContourExtractor contourExtractor;
    private final CornerDetector cornerDetector;
    private final RobustLqrSmoother lqrSmoother;
    private final CornerPreservationBlender preservationBlender;
    private final RoundaboutDetector roundaboutDetector;
    private final HermiteSplineConnector hermiteConnector;

    /**
     * Initialise le moteur avec l'ensemble des modules spécialisés.
     */
    public ContourSmoothingEngine() {
        this.contourExtractor = new SubpixelContourExtractor();
        this.cornerDetector = new CornerDetector();
        this.lqrSmoother = new RobustLqrSmoother();
        this.preservationBlender = new CornerPreservationBlender();
        this.roundaboutDetector = new RoundaboutDetector();
        this.hermiteConnector = new HermiteSplineConnector();
    }

    /**
     * Exécute le pipeline complet de lissage et de modélisation vectorielle.
     *
     * @param consolidatedMask Masque consolidé binaire issu du Sprint 5.
     * @param croppedRoad      Masque de la chaussée routière fermée.
     * @param labelMap         Matrice des étiquettes des cellules du non-route.
     * @param cells            Liste des cellules topologiques.
     * @param territoryMask    Masque binaire des cellules sélectionnées (Sprint 4).
     * @param config           Configuration des hyperparamètres de lissage.
     * @return Modèle de contour vectoriel lissé sub-pixel de haute fidélité.
     */
    public SmoothVectorContour process(ConsolidatedMask consolidatedMask,
                                       BinaryMask croppedRoad,
                                       CellLabelMap labelMap,
                                       List<Cell> cells,
                                       BinaryMask territoryMask,
                                       ContourSmoothingConfig config) {
        Objects.requireNonNull(consolidatedMask, "Le masque consolidé ne doit pas être nul.");
        Objects.requireNonNull(croppedRoad, "Le masque routier ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "La matrice des étiquettes ne doit pas être nulle.");
        Objects.requireNonNull(cells, "La liste des cellules ne doit pas être nulle.");
        Objects.requireNonNull(territoryMask, "Le masque du territoire ne doit pas être nul.");
        Objects.requireNonNull(config, "La configuration de lissage ne doit pas être nulle.");

        LOGGER.info(() -> "[Sprint 6] Démarrage de l'extraction sub-pixel et du lissage LQR.");

        // 1. Extraction sub-pixel Marching Squares 2D
        List<PixelPoint> rawContour = contourExtractor.extract(consolidatedMask.mask(), config.resampleStep());
        LOGGER.info(() -> String.format("[Sprint 6] Contour brut extrait : %d points (pas = %.1f px).",
                rawContour.size(), config.resampleStep()));

        // 2. Détection des angles vifs
        List<Integer> initialCorners = cornerDetector.detectCorners(
                rawContour,
                config.cornerWindowL(),
                config.cornerThreshold(),
                config.preSmoothSigma()
        );
        LOGGER.info(() -> String.format("[Sprint 6] Angles vifs initiaux détectés : %d coins.", initialCorners.size()));

        // 3. Lissage LQR robuste
        List<PixelPoint> lqrSmoothed = lqrSmoother.smoothContour(
                rawContour,
                initialCorners,
                config.lqrSigma(),
                config.lqrScale(),
                config.lqrIterations()
        );

        // 4. Fondu de préservation des coins
        List<PixelPoint> blended = preservationBlender.blend(
                rawContour,
                lqrSmoothed,
                initialCorners,
                config.blendR0(),
                config.blendR1()
        );

        // 5. Détection des ronds-points
        List<Roundabout> roundabouts = roundaboutDetector.detect(croppedRoad, labelMap, cells, 90.0, 240);
        LOGGER.info(() -> String.format("[Sprint 6] Ronds-points candidats détectés dans le réseau : %d.", roundabouts.size()));

        // 6. Raccordement tangentiel C1 et substitution d'arcs
        List<PixelPoint> finalPoints = hermiteConnector.integrateRoundabouts(
                blended,
                roundabouts,
                territoryMask,
                config.roundaboutZr()
        );

        // Ré-identification précise des coins sur le tracé final
        List<Integer> finalCorners = cornerDetector.detectCorners(
                finalPoints,
                config.cornerWindowL(),
                config.cornerThreshold(),
                config.preSmoothSigma()
        );

        LOGGER.info(() -> String.format("[Sprint 6] Pipeline achevé avec succès : %d points finaux, %d coins préservés.",
                finalPoints.size(), finalCorners.size()));

        return new SmoothVectorContour(
                finalPoints,
                finalCorners,
                consolidatedMask.width(),
                consolidatedMask.height(),
                consolidatedMask.cropWindow()
        );
    }
}
