package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.ContourSmoothingEngine;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.edge.EdgeBandGuard;
import com.sam102022.photoshop.v2.edge.LocalLoopRemover;
import com.sam102022.photoshop.v2.edge.PolygonalSimplifier;
import com.sam102022.photoshop.v2.edge.RoadEdgeSnapper;
import com.sam102022.photoshop.v2.expansion.BoulevardPocketFiller;
import com.sam102022.photoshop.v2.expansion.BoundaryRoundaboutIncluder;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.expansion.RoadBoundaryConsolidator;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.TerritoryGeometry;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import com.sam102022.photoshop.v2.refine.AlphaRefiner;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import com.sam102022.photoshop.v2.render.ImageClipper;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.render.SupersampleRenderer;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.TopologicalVoteEngine;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Orchestrateur fonctionnel de bout en bout du pipeline V2 reliant les 9 étapes algorithmiques (Sprints 1 à 9).
 */
public class V2Pipeline {

    private static final Logger LOGGER = Logger.getLogger(V2Pipeline.class.getName());
    private static final double EDGE_GUARD_RADIUS = 2.0;

    private final PolygonRasterizer polygonRasterizer;
    private final BoundingBoxCropper cropper;
    private final CellLabeler cellLabeler;
    private final TopologicalVoteEngine voteEngine;
    private final RoadBoundaryConsolidator consolidator;
    private final ContourSmoothingEngine smoothingEngine;
    private final AlphaRefiner alphaRefiner;
    private final SupersampleRenderer supersampleRenderer;
    private final ImageClipper imageClipper;
    private final RoadEdgeSnapper roadEdgeSnapper;
    private final PolygonalSimplifier polygonalSimplifier = new PolygonalSimplifier();
    private final EdgeBandGuard edgeBandGuard = new EdgeBandGuard();
    private final LocalLoopRemover localLoopRemover = new LocalLoopRemover();
    private final BoulevardPocketFiller boulevardPocketFiller = new BoulevardPocketFiller();
    private final BoundaryRoundaboutIncluder roundaboutIncluder = new BoundaryRoundaboutIncluder();
    private final RoadDetectorStyle motorwayDetector = new RoadDetectorStyle();

    /**
     * Initialise le pipeline complet avec ses sous-composants par défaut.
     */
    public V2Pipeline() {
        this(
                new PolygonRasterizer(),
                new BoundingBoxCropper(),
                new CellLabeler(),
                new TopologicalVoteEngine(),
                new RoadBoundaryConsolidator(),
                new ContourSmoothingEngine(),
                new AlphaRefiner(),
                new SupersampleRenderer(),
                new ImageClipper(),
                new RoadEdgeSnapper()
        );
    }

    /**
     * Initialise le pipeline avec injection explicite des sous-composants.
     *
     * @param polygonRasterizer   Rastériseur du polygone d'intention (non nul).
     * @param cropper             Recadreur de région d'intérêt (non nul).
     * @param cellLabeler         Segmenteur et étiqueteur de cellules urbaines (non nul).
     * @param voteEngine          Moteur de vote topologique (non nul).
     * @param consolidator        Consolidateur géodésique de barrières routières (non nul).
     * @param smoothingEngine     Moteur de lissage vectoriel sub-pixel (non nul).
     * @param alphaRefiner        Aaffineur spectral de lisière et giratoires (non nul).
     * @param supersampleRenderer Rastériseur sub-pixel par supersampling (non nul).
     * @param imageClipper        Découpeur et assembleur des images finales (non nul).
     */
    public V2Pipeline(
            PolygonRasterizer polygonRasterizer,
            BoundingBoxCropper cropper,
            CellLabeler cellLabeler,
            TopologicalVoteEngine voteEngine,
            RoadBoundaryConsolidator consolidator,
            ContourSmoothingEngine smoothingEngine,
            AlphaRefiner alphaRefiner,
            SupersampleRenderer supersampleRenderer,
            ImageClipper imageClipper
    ) {
        this(polygonRasterizer, cropper, cellLabeler, voteEngine, consolidator, smoothingEngine,
                alphaRefiner, supersampleRenderer, imageClipper, new RoadEdgeSnapper());
    }

    /**
     * Initialise le pipeline avec injection explicite des sous-composants, y compris l'accrocheur de bord de route.
     *
     * @param polygonRasterizer   Rastériseur du polygone d'intention (non nul).
     * @param cropper             Recadreur de région d'intérêt (non nul).
     * @param cellLabeler         Segmenteur et étiqueteur de cellules urbaines (non nul).
     * @param voteEngine          Moteur de vote topologique (non nul).
     * @param consolidator        Consolidateur géodésique de barrières routières (non nul).
     * @param smoothingEngine     Moteur de lissage vectoriel sub-pixel (non nul).
     * @param alphaRefiner        Affineur spectral de lisière et giratoires (non nul).
     * @param supersampleRenderer Rastériseur sub-pixel par supersampling (non nul).
     * @param imageClipper        Découpeur et assembleur des images finales (non nul).
     * @param roadEdgeSnapper     Accrocheur du contour sur le bord vectoriel des routes (non nul).
     */
    public V2Pipeline(
            PolygonRasterizer polygonRasterizer,
            BoundingBoxCropper cropper,
            CellLabeler cellLabeler,
            TopologicalVoteEngine voteEngine,
            RoadBoundaryConsolidator consolidator,
            ContourSmoothingEngine smoothingEngine,
            AlphaRefiner alphaRefiner,
            SupersampleRenderer supersampleRenderer,
            ImageClipper imageClipper,
            RoadEdgeSnapper roadEdgeSnapper
    ) {
        this.polygonRasterizer = Objects.requireNonNull(polygonRasterizer, "polygonRasterizer ne doit pas être nul.");
        this.cropper = Objects.requireNonNull(cropper, "cropper ne doit pas être nul.");
        this.cellLabeler = Objects.requireNonNull(cellLabeler, "cellLabeler ne doit pas être nul.");
        this.voteEngine = Objects.requireNonNull(voteEngine, "voteEngine ne doit pas être nul.");
        this.consolidator = Objects.requireNonNull(consolidator, "consolidator ne doit pas être nul.");
        this.smoothingEngine = Objects.requireNonNull(smoothingEngine, "smoothingEngine ne doit pas être nul.");
        this.alphaRefiner = Objects.requireNonNull(alphaRefiner, "alphaRefiner ne doit pas être nul.");
        this.supersampleRenderer = Objects.requireNonNull(supersampleRenderer, "supersampleRenderer ne doit pas être nul.");
        this.imageClipper = Objects.requireNonNull(imageClipper, "imageClipper ne doit pas être nul.");
        this.roadEdgeSnapper = Objects.requireNonNull(roadEdgeSnapper, "roadEdgeSnapper ne doit pas être nul.");
    }

    /**
     * Exécute le pipeline complet avec configuration par défaut.
     *
     * @param mapImage   Image source cartographique (non nulle).
     * @param geometry   Géométrie vectorielle du territoire (non nulle).
     * @param mapContext Contexte de projection géographique (non nul).
     * @param roadMask   Masque binaire des axes routiers (non nul).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask
    ) {
        return execute(mapImage, geometry, mapContext, roadMask, V2Config.defaultConfig());
    }

    /**
     * Exécute le pipeline complet avec masque routier fourni sous forme d'image.
     *
     * @param mapImage   Image source cartographique (non nulle).
     * @param geometry   Géométrie vectorielle du territoire (non nulle).
     * @param mapContext Contexte de projection géographique (non nul).
     * @param roadImage  Image source du masque routier (non nulle).
     * @param config     Configuration unifiée du pipeline V2 (non nulle).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BufferedImage roadImage,
            V2Config config
    ) {
        Objects.requireNonNull(roadImage, "roadImage ne doit pas être nulle.");
        BinaryMask roadMask = BinaryMask.fromImage(roadImage, 128);
        return execute(mapImage, geometry, mapContext, roadMask, config);
    }

    /**
     * Exécute le pipeline complet selon la configuration fournie.
     *
     * @param mapImage   Image source cartographique (non nulle).
     * @param geometry   Géométrie vectorielle du territoire (non nulle).
     * @param mapContext Contexte de projection géographique (non nul).
     * @param roadMask   Masque binaire des axes routiers (non nul).
     * @param config     Configuration unifiée du pipeline V2 (non nulle).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask,
            V2Config config
    ) {
        return execute(mapImage, geometry, mapContext, roadMask, config, null);
    }

    /**
     * Exécute le pipeline complet, en lisant le bord des chaussées dans la capture Google « Routes seules »
     * lorsqu'elle est fournie. Cette capture contenant déjà les voies rapides, la détection colorimétrique
     * complémentaire des autoroutes n'est alors pas utilisée.
     *
     * @param mapImage    Image source cartographique (non nulle).
     * @param geometry    Géométrie vectorielle du territoire (non nulle).
     * @param mapContext  Contexte de projection géographique (non nul).
     * @param roadMask    Masque binaire des axes routiers (non nul).
     * @param config      Configuration unifiée du pipeline V2 (non nulle).
     * @param googleRoads Capture « Routes seules » nettoyée, ou null (bord lu dans les couleurs de la carte).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask,
            V2Config config,
            GoogleRoadsImage googleRoads
    ) {
        validateInputs(mapImage, geometry, mapContext, roadMask, config);
        long startTime = System.currentTimeMillis();
        int width = mapImage.getWidth();
        int height = mapImage.getHeight();

        LOGGER.info(() -> String.format("[Pipeline V2] Démarrage du traitement (%dx%d px)...", width, height));

        // 1. Projection et rastérisation du polygone d'intention P (Sprint 1)
        List<PixelPoint> polygonPoints = projectPolygon(geometry, mapContext);
        PolygonMask polygonMask = polygonRasterizer.rasterize(width, height, List.of(polygonPoints), List.of());

        // 2. Recadrage et étiquetage des cellules (Sprint 3)
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, config.cropMargin());
        BinaryMask croppedRoad = cropper.crop(roadMask, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);
        CellLabelingResult labelingResult = cellLabeler.label(croppedRoad);

        // 3. Vote topologique (Sprint 4)
        CellSelection selection = voteEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPoly,
                config.toCellSelectionPolicy()
        );

        // 4. Consolidation géodésique et barrières (Sprint 5)
        ConsolidatedMask consolidatedRaw = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                config.toExpansionConfig()
        );

        // 4 bis. Comblement des poches (bretelles, îlots) entre le territoire et les boulevards
        //        (les autoroutes longées par le polygone sont traitées comme des routes frontières)
        BinaryMask croppedRoadWithMotorways = union(croppedRoad, motorwayDetector.detectMotorways(mapImage.getSubimage(
                cropWindow.x0(), cropWindow.y0(), cropWindow.width(), cropWindow.height())));
        ConsolidatedMask pocketFilled = boulevardPocketFiller.fill(
                consolidatedRaw,
                croppedRoadWithMotorways,
                selection.retainedMask(),
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPoly,
                toLocal(polygonPoints, cropWindow),
                config.boulevardPocketRadius()
        );

        // 4 ter. Giratoires englobés ou traversés par le polygone : inclus en entier (extérieur de l'anneau)
        ConsolidatedMask consolidated = roundaboutIncluder.include(
                pocketFilled,
                croppedRoad,
                croppedPoly,
                labelingResult.labelMap(),
                labelingResult.cells(),
                BoundaryRoundaboutIncluder.DEFAULT_MIN_POLYGON_COVERAGE
        );

        // 5. Lissage vectoriel multi-échelle et substitution giratoires (Sprints 6 & 7)
        SmoothVectorContour smoothContour = smoothingEngine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                config.toContourSmoothingConfig()
        );

        // 5 bis. Accrochage du contour sur le bord vectoriel (anti-crénelé) des routes de la carte
        smoothContour = roadEdgeSnapper.snap(smoothContour, mapImage, config.toRoadEdgeSnapConfig(), false, googleRoads);
        smoothContour = localLoopRemover.removeLoops(smoothContour, LocalLoopRemover.DEFAULT_MAX_LOOP_POINTS);

        // 5 ter. Réduction en polygone de grands segments rectilignes (détourage « lasso polygonal »)
        smoothContour = polygonalSimplifier.simplify(smoothContour, config.polygonTolerance());

        // 6. Affinage spectral (Sprint 8)
        AlphaRefinementMap refinementMap = alphaRefiner.refine(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                smoothContour.substitutedRoundabouts(),
                config.toAlphaRefinementConfig()
        );

        // 6 bis. Bord vectoriel : le facteur d'affinage (issu d'un masque binaire) n'est pas appliqué à moins de
        //        2 px du contour, pour ne pas réintroduire l'escalier des pixels sur les grands segments.
        if (config.roadEdgeSnapEnabled()) {
            refinementMap = edgeBandGuard.protect(refinementMap, smoothContour, EDGE_GUARD_RADIUS);
        }

        // 7. Rendu Sub-Pixel Supersampling SS=4 (Sprint 9)
        CoverageMask coverageMask = supersampleRenderer.render(
                smoothContour,
                refinementMap,
                config.supersamplingFactor(),
                width,
                height
        );

        // 8. Assemblage et découpe finale (Sprint 9)
        RenderResult renderResult = imageClipper.clip(mapImage, coverageMask);

        long totalElapsed = System.currentTimeMillis() - startTime;
        LOGGER.info(() -> String.format("[Pipeline V2] Pipeline achevé avec succès en %d ms.", totalElapsed));

        return renderResult;
    }

    /**
     * Exprime des sommets globaux dans le repère local de la fenêtre de recadrage.
     *
     * @param points     Sommets globaux.
     * @param cropWindow Fenêtre de recadrage.
     * @return Sommets locaux.
     */
    private static List<PixelPoint> toLocal(List<PixelPoint> points, CropWindow cropWindow) {
        return points.stream()
                .map(p -> new PixelPoint(p.x() - cropWindow.x0(), p.y() - cropWindow.y0()))
                .toList();
    }

    /**
     * Union de deux masques de mêmes dimensions.
     *
     * @param a Premier masque.
     * @param b Second masque.
     * @return a ∪ b.
     */
    private static BinaryMask union(BinaryMask a, BinaryMask b) {
        BinaryMask out = a.copy();
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                if (b.get(x, y)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Projette les sommets du premier anneau extérieur géographique en coordonnées pixels.
     */
    private List<PixelPoint> projectPolygon(TerritoryGeometry geometry, MapContext mapContext) {
        if (geometry.outerRings().isEmpty()) {
            throw new IllegalArgumentException("La géométrie du territoire ne contient aucun anneau extérieur.");
        }
        WebMercatorProjection projection = new WebMercatorProjection(mapContext);
        return geometry.outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();
    }

    /**
     * Valide la présence et la cohérence dimensionnelle de tous les arguments obligatoires.
     */
    private void validateInputs(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask,
            V2Config config
    ) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(geometry, "geometry ne doit pas être nulle.");
        Objects.requireNonNull(mapContext, "mapContext ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(config, "config ne doit pas être nulle.");

        if (roadMask.getWidth() != mapImage.getWidth() || roadMask.getHeight() != mapImage.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions incompatibles entre mapImage (%dx%d) et roadMask (%dx%d).",
                    mapImage.getWidth(), mapImage.getHeight(),
                    roadMask.getWidth(), roadMask.getHeight()
            ));
        }
    }
}
