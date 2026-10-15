package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Orchestrateur de haut niveau du Sprint 5 pour la reconstruction et l'expansion géodésique routière.
 * <p>
 * Combine séquentiellement :
 * <ol>
 *   <li>La transformée de distance euclidienne exacte O(N) Meijster ({@link EuclideanDistanceTransform}) ;</li>
 *   <li>Le filtre maximum local séparable glissant O(N) Lemire ({@link LocalMaxFilter}) ;</li>
 *   <li>L'expansion géodésique bornée Dijkstra 8-connexe ({@link BoundedGeodesicExpander}) ;</li>
 *   <li>La régularisation morphologique par ouverture circulaire sanctuarisant T ({@link MorphologicalConsolidator}) ;</li>
 *   <li>Le comblement sélectif des îlots résiduels et ronds-points compacts ({@link ResidualHoleResolver}).</li>
 * </ol>
 */
public class RoadBoundaryConsolidator {

    private static final Logger LOGGER = Logger.getLogger(RoadBoundaryConsolidator.class.getName());

    private final EuclideanDistanceTransform distanceTransform;
    private final LocalMaxFilter localMaxFilter;
    private final BoundedGeodesicExpander geodesicExpander;
    private final MorphologicalConsolidator morphologicalConsolidator;
    private final ResidualHoleResolver holeResolver;

    /**
     * Initialise l'orchestrateur avec ses composants standards par défaut.
     */
    public RoadBoundaryConsolidator() {
        this(
                new EuclideanDistanceTransform(),
                new LocalMaxFilter(),
                new BoundedGeodesicExpander(),
                new MorphologicalConsolidator(),
                new ResidualHoleResolver()
        );
    }

    /**
     * Constructeur complet permettant l'injection de dépendances pour les tests.
     *
     * @param distanceTransform         Calculateur d'EDT exacte (non nul).
     * @param localMaxFilter            Filtre maximum local séparable (non nul).
     * @param geodesicExpander          Moteur de propagation Dijkstra bornée (non nul).
     * @param morphologicalConsolidator Consolidateur morphologique avec sanctuarisation (non nul).
     * @param holeResolver              Résolveur de trous résiduels compacts (non nul).
     */
    public RoadBoundaryConsolidator(
            EuclideanDistanceTransform distanceTransform,
            LocalMaxFilter localMaxFilter,
            BoundedGeodesicExpander geodesicExpander,
            MorphologicalConsolidator morphologicalConsolidator,
            ResidualHoleResolver holeResolver
    ) {
        this.distanceTransform = Objects.requireNonNull(distanceTransform, "distanceTransform ne doit pas être nul.");
        this.localMaxFilter = Objects.requireNonNull(localMaxFilter, "localMaxFilter ne doit pas être nul.");
        this.geodesicExpander = Objects.requireNonNull(geodesicExpander, "geodesicExpander ne doit pas être nul.");
        this.morphologicalConsolidator = Objects.requireNonNull(morphologicalConsolidator, "morphologicalConsolidator ne doit pas être nul.");
        this.holeResolver = Objects.requireNonNull(holeResolver, "holeResolver ne doit pas être nul.");
    }

    /**
     * Exécute le pipeline complet de consolidation géodésique et produit le masque consolidé officiel.
     *
     * @param retainedMask Masque intérieur des parcelles sélectionnées T (non nul).
     * @param roadMask     Masque binaire de voirie Rc recadré (non nul).
     * @param cropWindow   Fenêtre de recadrage d'origine du Sprint 3 (non nulle).
     * @param config       Configuration immuable d'expansion (non nulle).
     * @return Contrat officiel {@link ConsolidatedMask} contenant le masque binaire Mc.
     */
    public ConsolidatedMask consolidate(
            BinaryMask retainedMask,
            BinaryMask roadMask,
            CropWindow cropWindow,
            ExpansionConfig config
    ) {
        validateInputs(retainedMask, roadMask, cropWindow, config);
        int width = retainedMask.getWidth();
        int height = retainedMask.getHeight();

        LOGGER.log(Level.INFO, "Début de la consolidation routière Sprint 5 (mode : {0})...", config.operationMode());

        DistanceMap hw = distanceTransform.compute(roadMask);
        DistanceMap hwm = localMaxFilter.filter(hw, roadMask, config.maxFilterSize());
        BinaryMask ext = geodesicExpander.expand(retainedMask, roadMask, hwm, config);
        BinaryMask smoothed = morphologicalConsolidator.consolidate(retainedMask, ext, config.openingDiskRadius());
        BinaryMask filled = holeResolver.fillCompactHoles(smoothed, config.maxHoleArea());

        long finalArea = filled.countActivePixels();
        LOGGER.log(Level.INFO, "Consolidation Sprint 5 achevée avec succès. Surface finale Mc : {0} px.", finalArea);

        return new ConsolidatedMask(width, height, filled, cropWindow);
    }

    /**
     * Valide la présence et la cohérence des arguments.
     *
     * @param retainedMask Masque intérieur T.
     * @param roadMask     Masque de routes Rc.
     * @param cropWindow   Fenêtre de recadrage.
     * @param config       Configuration.
     */
    private void validateInputs(
            BinaryMask retainedMask,
            BinaryMask roadMask,
            CropWindow cropWindow,
            ExpansionConfig config
    ) {
        Objects.requireNonNull(retainedMask, "retainedMask ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nul.");
        Objects.requireNonNull(config, "config ne doit pas être nul.");

        if (roadMask.getWidth() != retainedMask.getWidth() || roadMask.getHeight() != retainedMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence de dimensions entre retainedMask et roadMask.");
        }
        if (cropWindow.width() != retainedMask.getWidth() || cropWindow.height() != retainedMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence de dimensions entre retainedMask et cropWindow.");
        }
    }
}
