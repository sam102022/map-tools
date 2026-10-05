package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.ContourSmoothingEngine;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
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

    private final PolygonRasterizer polygonRasterizer;
    private final BoundingBoxCropper cropper;
    private final CellLabeler cellLabeler;
    private final TopologicalVoteEngine voteEngine;
    private final RoadBoundaryConsolidator consolidator;
    private final ContourSmoothingEngine smoothingEngine;
    private final AlphaRefiner alphaRefiner;
    private final SupersampleRenderer supersampleRenderer;
    private final ImageClipper imageClipper;

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
                new ImageClipper()
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
        this.polygonRasterizer = Objects.requireNonNull(polygonRasterizer, "polygonRasterizer ne doit pas être nul.");
        this.cropper = Objects.requireNonNull(cropper, "cropper ne doit pas être nul.");
        this.cellLabeler = Objects.requireNonNull(cellLabeler, "cellLabeler ne doit pas être nul.");
        this.voteEngine = Objects.requireNonNull(voteEngine, "voteEngine ne doit pas être nul.");
        this.consolidator = Objects.requireNonNull(consolidator, "consolidator ne doit pas être nul.");
        this.smoothingEngine = Objects.requireNonNull(smoothingEngine, "smoothingEngine ne doit pas être nul.");
        this.alphaRefiner = Objects.requireNonNull(alphaRefiner, "alphaRefiner ne doit pas être nul.");
        this.supersampleRenderer = Objects.requireNonNull(supersampleRenderer, "supersampleRenderer ne doit pas être nul.");
        this.imageClipper = Objects.requireNonNull(imageClipper, "imageClipper ne doit pas être nul.");
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
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                config.toExpansionConfig()
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

        // 6. Affinage spectral (Sprint 8)
        AlphaRefinementMap refinementMap = alphaRefiner.refine(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                smoothContour.substitutedRoundabouts(),
                config.toAlphaRefinementConfig()
        );

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
