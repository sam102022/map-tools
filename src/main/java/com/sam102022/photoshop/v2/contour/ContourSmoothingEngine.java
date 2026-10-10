package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.HermiteSplineConnector.RoundaboutSubstitutionResult;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.expansion.ResidualHoleResolver;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Façade d'orchestration globale de la géométrie vectorielle sub-pixel (Sprints 6 &amp; 7).
 * Enchaîne séquentiellement :
 * 1. Extraction sub-pixel par Marching Squares 2D et rééchantillonnage 1 px ;
 * 2. Détection multi-échelles des angles vifs (coins) ;
 * 3. Lissage adaptatif multi-échelle des tronçons droits (Sprint 7, double LQR &amp; transition C1) ;
 * 4. Modulation continue par fondu Hermite smoothstep ;
 * 5. Détection des carrefours giratoires et modélisation des anneaux ;
 * 6. Substitution géométrique des arcs de ronds-points par splines C1 d'Hermite ;
 * 7. Production du contrat immuable {@link SmoothVectorContour}.
 */
public class ContourSmoothingEngine {

    private static final Logger LOGGER = Logger.getLogger(ContourSmoothingEngine.class.getName());

    private final SubpixelContourExtractor contourExtractor;
    private final CornerDetector cornerDetector;
    private final StraightSegmentSmoother straightSmoother;
    private final CornerPreservationBlender preservationBlender;
    private final RoundaboutDetector roundaboutDetector;
    private final HermiteSplineConnector hermiteConnector;
    private final ResidualHoleResolver filledMaskResolver = new ResidualHoleResolver();

    /**
     * Initialise le moteur avec l'ensemble des modules spécialisés par défaut.
     */
    public ContourSmoothingEngine() {
        this(
                new SubpixelContourExtractor(),
                new CornerDetector(),
                new StraightSegmentSmoother(),
                new CornerPreservationBlender(),
                new RoundaboutDetector(),
                new HermiteSplineConnector()
        );
    }

    /**
     * Initialise le moteur avec injection explicite de ses dépendances spécialisées (ADR-008).
     *
     * @param contourExtractor    Extracteur sub-pixel Marching Squares.
     * @param cornerDetector      Détecteur d'angles vifs.
     * @param straightSmoother    Lisseur adaptatif multi-échelle des tronçons droits (Sprint 7).
     * @param preservationBlender Modulateur de fondu de coins.
     * @param roundaboutDetector  Détecteur de carrefours giratoires.
     * @param hermiteConnector    Connecteur tangentiel C1.
     */
    public ContourSmoothingEngine(SubpixelContourExtractor contourExtractor,
                                  CornerDetector cornerDetector,
                                  StraightSegmentSmoother straightSmoother,
                                  CornerPreservationBlender preservationBlender,
                                  RoundaboutDetector roundaboutDetector,
                                  HermiteSplineConnector hermiteConnector) {
        this.contourExtractor = Objects.requireNonNull(contourExtractor, "L'extracteur de contour ne doit pas être nul.");
        this.cornerDetector = Objects.requireNonNull(cornerDetector, "Le détecteur de coins ne doit pas être nul.");
        this.straightSmoother = Objects.requireNonNull(straightSmoother, "Le lisseur de tronçons ne doit pas être nul.");
        this.preservationBlender = Objects.requireNonNull(preservationBlender, "Le modulateur de coins ne doit pas être nul.");
        this.roundaboutDetector = Objects.requireNonNull(roundaboutDetector, "Le détecteur de ronds-points ne doit pas être nul.");
        this.hermiteConnector = Objects.requireNonNull(hermiteConnector, "Le connecteur d'Hermite ne doit pas être nul.");
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
        return process(consolidatedMask, croppedRoad, labelMap, cells, territoryMask, config, true);
    }

    /**
     * Exécute la chaîne de lissage, avec ou sans substitution des arcs de giratoires. En mode zone (bord
     * intérieur des routes), les giratoires frontaliers sont exclus de la zone : aucun arc n'est substitué.
     *
     * @param consolidatedMask      Masque consolidé.
     * @param croppedRoad           Masque routier recadré.
     * @param labelMap              Étiquettes des cellules.
     * @param cells                 Cellules.
     * @param territoryMask         Cellules retenues (T).
     * @param config                Configuration de lissage.
     * @param substituteRoundabouts Vrai pour substituer les arcs extérieurs des giratoires frontaliers.
     * @return Contour vectoriel lissé.
     */
    public SmoothVectorContour process(ConsolidatedMask consolidatedMask,
                                       BinaryMask croppedRoad,
                                       CellLabelMap labelMap,
                                       List<Cell> cells,
                                       BinaryMask territoryMask,
                                       ContourSmoothingConfig config,
                                       boolean substituteRoundabouts) {
        Objects.requireNonNull(consolidatedMask, "Le masque consolidé ne doit pas être nul.");
        Objects.requireNonNull(croppedRoad, "Le masque routier ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "La matrice des étiquettes ne doit pas être nulle.");
        Objects.requireNonNull(cells, "La liste des cellules ne doit pas être nulle.");
        Objects.requireNonNull(territoryMask, "Le masque du territoire ne doit pas être nul.");
        Objects.requireNonNull(config, "La configuration de lissage ne doit pas être nulle.");

        LOGGER.info(() -> "[Sprint 7] Démarrage de l'extraction sub-pixel et du lissage multi-échelle.");

        // 1. Extraction sub-pixel Marching Squares 2D
        List<PixelPoint> rawContour = contourExtractor.extract(consolidatedMask.mask(), config.resampleStep());
        LOGGER.info(() -> String.format("[Sprint 7] Contour brut extrait : %d points (pas = %.1f px).",
                rawContour.size(), config.resampleStep()));

        // 2. Détection des angles vifs
        List<Integer> initialCorners = cornerDetector.detectCorners(
                rawContour,
                config.cornerWindowL(),
                config.cornerThreshold(),
                config.preSmoothSigma()
        );
        LOGGER.info(() -> String.format("[Sprint 7] Angles vifs initiaux détectés : %d coins.", initialCorners.size()));

        // 3. Lissage adaptatif multi-échelle des tronçons droits (Sprint 7)
        List<PixelPoint> lqrSmoothed = straightSmoother.smoothContour(
                rawContour,
                initialCorners,
                config
        );
        LOGGER.info(() -> "[Sprint 7] Lissage adaptatif multi-échelle appliqué sur les segments.");

        // 4. Fondu de préservation des coins
        List<PixelPoint> blended = preservationBlender.blend(
                rawContour,
                lqrSmoothed,
                initialCorners,
                config.blendR0(),
                config.blendR1()
        );

        // 5. Détection des ronds-points
        List<Roundabout> roundabouts = substituteRoundabouts
                ? roundaboutDetector.detect(croppedRoad, labelMap, cells, 90.0, 240) : List.of();
        LOGGER.info(() -> String.format("[Sprint 7] Ronds-points candidats détectés dans le réseau : %d.", roundabouts.size()));

        // 6. Raccordement tangentiel C1 et substitution d'arcs
        // Le test d'appartenance du disque se fait sur le masque consolidé rempli (Mfill de l'étalon Python),
        // la sonde extérieure de l'arc sur les cellules retenues (T).
        BinaryMask consolidatedFilled = filledMaskResolver.fillCompactHoles(consolidatedMask.mask(), Long.MAX_VALUE);
        RoundaboutSubstitutionResult substitutionResult = hermiteConnector.integrateRoundaboutsWithTracking(
                blended,
                roundabouts,
                territoryMask,
                consolidatedFilled,
                config.roundaboutZr()
        );
        List<PixelPoint> finalPoints = substitutionResult.contour();

        // Ré-identification précise des coins sur le tracé final
        List<Integer> finalCorners = cornerDetector.detectCorners(
                finalPoints,
                config.cornerWindowL(),
                config.cornerThreshold(),
                config.preSmoothSigma()
        );

        LOGGER.info(() -> String.format("[Sprint 7] Pipeline achevé avec succès : %d points finaux, %d coins préservés, %d ronds-points substitués.",
                finalPoints.size(), finalCorners.size(), substitutionResult.substitutedRoundabouts().size()));

        return new SmoothVectorContour(
                finalPoints,
                finalCorners,
                consolidatedMask.width(),
                consolidatedMask.height(),
                consolidatedMask.cropWindow(),
                substitutionResult.substitutedRoundabouts()
        );
    }
}
