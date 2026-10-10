package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.ContourSmoothingEngine;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.contour.RoundaboutDetector;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.edge.EdgeBandGuard;
import com.sam102022.photoshop.v2.edge.LocalLoopRemover;
import com.sam102022.photoshop.v2.edge.PolygonalSimplifier;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import com.sam102022.photoshop.v2.refine.AlphaRefiner;
import com.sam102022.photoshop.v2.render.ImageClipper;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.render.SupersampleRenderer;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.TopologicalVoteEngine;
import com.sam102022.photoshop.v2.zone.RoadExactCoverage;
import com.sam102022.photoshop.v2.zone.TraceAlignedOutline;
import com.sam102022.photoshop.v2.zone.ZoneInteriorBuilder;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Pipeline de détourage d'une zone sur le bord intérieur des routes.
 * <p>
 * Reprend les étapes du pipeline territoire ({@link V2Pipeline}) avec trois différences : le polygone
 * d'intention est un masque (zone extraite du plan des zones) ; les routes frontières ne sont pas incluses
 * (seules les rues intérieures le sont, {@link ZoneInteriorBuilder}) et les giratoires frontaliers sont
 * exclus ; le contour est accroché au bord intérieur des chaussées.
 */
public class V2ZonePipeline {

    private static final Logger LOGGER = Logger.getLogger(V2ZonePipeline.class.getName());
    private static final double EDGE_GUARD_RADIUS = 2.0;
    private static final double MAX_ROUNDABOUT_RADIUS = 90.0;
    private static final int ROUNDABOUT_RAYS = 240;

    private final BoundingBoxCropper cropper = new BoundingBoxCropper();
    private final CellLabeler cellLabeler = new CellLabeler();
    private final TopologicalVoteEngine voteEngine = new TopologicalVoteEngine();
    private final RoundaboutDetector roundaboutDetector = new RoundaboutDetector();
    private final ZoneInteriorBuilder interiorBuilder = new ZoneInteriorBuilder();
    private final ContourSmoothingEngine smoothingEngine = new ContourSmoothingEngine();
    private final LocalLoopRemover localLoopRemover = new LocalLoopRemover();
    private final PolygonalSimplifier polygonalSimplifier = new PolygonalSimplifier();
    private final AlphaRefiner alphaRefiner = new AlphaRefiner();
    private final EdgeBandGuard edgeBandGuard = new EdgeBandGuard();
    private final SupersampleRenderer supersampleRenderer = new SupersampleRenderer();
    private final ImageClipper imageClipper = new ImageClipper();
    private final RoadExactCoverage roadExactCoverage = new RoadExactCoverage();
    private final TraceAlignedOutline traceAligner = new TraceAlignedOutline();

    /**
     * Initialise le pipeline zone avec ses sous-composants par défaut.
     */
    public V2ZonePipeline() {
        // Sous-composants initialisés à la déclaration.
    }

    /**
     * Détoure une zone pouvant comporter plusieurs parties : chaque partie est détourée séparément, les
     * couvertures sont réunies puis la carte est découpée.
     *
     * @param mapImage Carte source (non nulle).
     * @param parts    Parties de la zone (masques pleine image, non vide).
     * @param roadMask Masque routier pleine image (non nul).
     * @param config   Configuration du pipeline (non nulle).
     * @return Résultat de rendu (image découpée, masque, aperçu, couverture).
     */
    public RenderResult execute(BufferedImage mapImage, List<BinaryMask> parts, BinaryMask roadMask, V2Config config) {
        return execute(mapImage, parts, roadMask, config, null);
    }

    /**
     * Détoure une zone pouvant comporter plusieurs parties, en lisant le bord des chaussées dans la capture
     * Google « Routes seules » lorsqu'elle est fournie.
     *
     * @param mapImage    Carte source (non nulle).
     * @param parts       Parties de la zone (masques pleine image, non vide).
     * @param roadMask    Masque routier pleine image (non nul).
     * @param config      Configuration du pipeline (non nulle).
     * @param googleRoads Capture « Routes seules » nettoyée, ou null.
     * @return Résultat de rendu (image découpée, masque, aperçu, couverture).
     */
    public RenderResult execute(BufferedImage mapImage, List<BinaryMask> parts, BinaryMask roadMask, V2Config config,
                                GoogleRoadsImage googleRoads) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(parts, "parts ne doit pas être nulle.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(config, "config ne doit pas être nulle.");
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("La zone doit comporter au moins une partie.");
        }
        long start = System.currentTimeMillis();
        CoverageMask coverage = null;
        for (BinaryMask part : parts) {
            CoverageMask partCoverage = renderPart(mapImage, part, roadMask, config, googleRoads);
            coverage = coverage == null ? partCoverage : coverage.max(partCoverage);
        }
        RenderResult result = imageClipper.clip(mapImage, coverage);
        long elapsed = System.currentTimeMillis() - start;
        LOGGER.info(() -> String.format("[Zones] Zone détourée en %d ms (%d partie(s)).", elapsed, parts.size()));
        return result;
    }

    /**
     * Détoure une partie de zone et rend sa couverture pleine image.
     *
     * @param mapImage Carte source.
     * @param part     Partie de zone (masque pleine image).
     * @param roadMask Masque routier pleine image.
     * @param config      Configuration.
     * @param googleRoads Capture « Routes seules », ou null.
     * @return Couverture alpha pleine image.
     */
    CoverageMask renderPart(BufferedImage mapImage, BinaryMask part, BinaryMask roadMask, V2Config config,
                            GoogleRoadsImage googleRoads) {
        int width = mapImage.getWidth();
        int height = mapImage.getHeight();
        CropWindow cropWindow = cropper.computeCropWindow(boundingCorners(part), width, height, config.cropMargin());
        BinaryMask croppedRoad = cropper.crop(roadMask, cropWindow);
        BinaryMask croppedZone = cropper.crop(part, cropWindow);
        CellLabelingResult labeling = cellLabeler.label(croppedRoad);
        CellSelection selection = voteEngine.execute(labeling.labelMap(), labeling.cells(), croppedZone,
                config.toCellSelectionPolicy());
        List<Roundabout> roundabouts = roundaboutDetector.detect(croppedRoad, labeling.labelMap(), labeling.cells(),
                MAX_ROUNDABOUT_RADIUS, ROUNDABOUT_RAYS);
        BinaryMask interior = interiorBuilder.build(selection.retainedMask(), croppedRoad, roundabouts,
                ZoneInteriorBuilder.DEFAULT_INTERIOR_ROAD_RADIUS, croppedZone);
        // Bord rectifié en grands segments parallèles au tracé de l'utilisateur (« lasso polygonal »).
        BinaryMask zoneMask = traceAligner.align(interior, croppedZone);
        ConsolidatedMask consolidated = new ConsolidatedMask(cropWindow.width(), cropWindow.height(), zoneMask,
                cropWindow);

        SmoothVectorContour contour = smoothingEngine.process(consolidated, croppedRoad, labeling.labelMap(),
                labeling.cells(), zoneMask, config.toContourSmoothingConfig(), false);
        // Pas d'accrochage au bord de chaussée : il ramènerait les décrochements que la rectification a supprimés
        // (le masque rectifié est déjà calé sur le bord de chaussée ; les giratoires sont repris plus bas).
        contour = localLoopRemover.removeLoops(contour, LocalLoopRemover.DEFAULT_MAX_LOOP_POINTS);
        contour = polygonalSimplifier.simplify(contour, config.polygonTolerance());

        AlphaRefinementMap refinement = alphaRefiner.refine(consolidated, croppedRoad, labeling.labelMap(),
                contour.substitutedRoundabouts(), config.toAlphaRefinementConfig());
        if (config.roadEdgeSnapEnabled()) {
            refinement = edgeBandGuard.protect(refinement, contour, EDGE_GUARD_RADIUS);
        }
        CoverageMask coverage = supersampleRenderer.render(contour, refinement, config.supersamplingFactor(), width,
                height);
        // Autour des giratoires, le bord est lu directement dans la capture « Routes seules » ; ailleurs, le
        // contour vectoriel en grands segments rectilignes est conservé.
        return googleRoads == null ? coverage
                : roadExactCoverage.refine(coverage, interior, croppedRoad, googleRoads, cropWindow, roundabouts);
    }

    /**
     * Coins de la boîte englobante d'un masque (pour le calcul de la fenêtre de recadrage).
     *
     * @param mask Masque non vide.
     * @return Quatre coins de la boîte englobante.
     */
    private static List<PixelPoint> boundingCorners(BinaryMask mask) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (mask.get(x, y)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            throw new IllegalArgumentException("La partie de zone est vide.");
        }
        return List.of(new PixelPoint(minX, minY), new PixelPoint(maxX, minY), new PixelPoint(maxX, maxY),
                new PixelPoint(minX, maxY));
    }
}
