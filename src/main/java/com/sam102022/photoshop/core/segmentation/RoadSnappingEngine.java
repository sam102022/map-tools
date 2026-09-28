package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.geometry.ContourExtractor;
import com.sam102022.photoshop.core.geometry.ContourSimplifier;
import com.sam102022.photoshop.core.geometry.PolygonBuilder;
import com.sam102022.photoshop.core.geometry.RoadSnapper;
import com.sam102022.photoshop.core.geometry.SnapTargetEdge;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.awt.Point;
import java.util.List;

/**
 * Moteur de recalage géodésique sur les axes routiers et de vectorisation du masque cartographique.
 * <p>
 * Ce pipeline orchestre :
 * <ol>
 *   <li>L'unification morphologique des sous-parcelles disjointes (selon {@link SnappingConfig#closingRadius()}).</li>
 *   <li>L'extraction du contour extérieur fermé via l'algorithme de Moore ({@link ContourExtractor}).</li>
 *   <li>La simplification polygonale Ramer-Douglas-Peucker ({@link ContourSimplifier}) pour supprimer les micro-escaliers raster.</li>
 *   <li>L'aimantation géodésique des sommets vers le bord extérieur des routes candidates ({@link RoadSnapper}).</li>
 *   <li>La reconstruction vectorielle anti-aliasée sub-pixel avec suréchantillonnage ({@link PolygonBuilder}).</li>
 *   <li>La préservation du noyau intérieur profond du masque d'origine.</li>
 * </ol>
 * </p>
 */
public class RoadSnappingEngine {
    private final ContourExtractor contourExtractor = new ContourExtractor();
    private final ContourSimplifier contourSimplifier = new ContourSimplifier();
    private final RoadSnapper roadSnapper = new RoadSnapper();
    private final PolygonBuilder polygonBuilder = new PolygonBuilder();

    /**
     * Exécute le recalage géodésique et retourne un masque binaire dur seuillé à 128 (compatibilité historique).
     *
     * @param roughGreenMask Masque initial grossier (calque vert ou tracé utilisateur).
     * @param roadCandidates Masque binaire des axes routiers détectés sur la carte.
     * @param config         Configuration des paramètres de recalage et de sensibilité.
     * @return Masque binaire final seuillé à 128.
     * @deprecated Préférer {@link #snapCoverage(BinaryMask, BinaryMask, SnappingConfig)} pour conserver l'anti-aliasing sub-pixel.
     */
    @Deprecated(forRemoval = false)
    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        return snapBinary(roughGreenMask, roadCandidates, config);
    }

    /**
     * Exécute le recalage géodésique et retourne explicitement un masque binaire sans nuances sub-pixel.
     *
     * @param roughGreenMask Masque initial grossier.
     * @param roadCandidates Masque binaire des axes routiers détectés.
     * @param config         Configuration des paramètres de recalage.
     * @return Masque binaire où les pixels de couverture >= 128 sont activés.
     * @throws IllegalArgumentException si un des arguments est null ou si les dimensions sont incompatibles.
     */
    public BinaryMask snapBinary(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        return snapCoverage(roughGreenMask, roadCandidates, config).toBinaryMask(128);
    }

    /**
     * Exécute le pipeline complet de recalage géodésique et produit un masque de couverture continue [0..255].
     * <p>
     * Conserve les fractions de pixels anti-aliasées sur la frontière recalée ou sur le contour initial
     * si aucune route n'est candidate ou si le contour n'a pas bougé.
     * </p>
     *
     * @param roughGreenMask Masque initial grossier issu de l'extraction de couleur ou de niveau de gris.
     * @param roadCandidates Masque binaire des axes routiers candidats issus de {@link com.sam102022.photoshop.core.detection.RoadDetector}.
     * @param config         Configuration définissant la tolérance de recalage, l'anti-aliasing et les rayons de morphologie.
     * @return Masque de couverture continue [0..255] prêt pour le détourage ARGB fluide.
     * @throws IllegalArgumentException si un des arguments est null ou si les dimensions sont incompatibles.
     */
    public CoverageMask snapCoverage(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
        if (roughGreenMask == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du moteur de recalage ne peuvent pas être null.");
        }
        if (roughGreenMask.getWidth() != roadCandidates.getWidth()
                || roughGreenMask.getHeight() != roadCandidates.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre le masque et les candidats routiers.");
        }
        if (roughGreenMask.countActivePixels() == 0) {
            return new CoverageMask(roughGreenMask.getWidth(), roughGreenMask.getHeight());
        }

        // Unifier d'abord les césures fines : plusieurs morceaux voisins du
        // territoire peuvent ainsi former sa composante principale.
        BinaryMask unifiedMask = config.closingRadius() > 0
                ? MorphologyOps.close(roughGreenMask, config.closingRadius())
                : roughGreenMask;

        // La forme est vectorisée depuis sa composante principale uniquement.
        // Le noyau et les replis doivent être limités à cette même composante,
        // sinon des îlots/artefacts du masque brut réapparaissent dans le résultat.
        BinaryMask primaryUnified = MorphologyOps.keepLargestComponent(unifiedMask);
        BinaryMask primaryMask = intersect(roughGreenMask, primaryUnified);

        List<Point> contour = contourExtractor.extractLargestContour(primaryUnified);
        if (contour.size() < 3) {
            return CoverageMask.fromBinaryMask(primaryMask);
        }

        // Simplifier les micro-marches d'escalier du contour raster
        List<Point> simplified = contourSimplifier.simplify(contour, 1.0);
        if (simplified.size() < 3) {
            simplified = contour;
        }

        long distinctPoints = simplified.stream().distinct().count();
        if (distinctPoints < 3) {
            return CoverageMask.fromBinaryMask(primaryMask);
        }

        // Si aucune route candidate n'est présente :
        // Le contour extrait est rasterisé pour conserver un rendu anti-aliasé sub-pixel
        if (roadCandidates.countActivePixels() == 0) {
            return rasterizeAndPreserveCore(primaryMask, simplified, config.antialiasing());
        }

        List<Point> adjustedContour = roadSnapper.snap(simplified, roadCandidates,
                primaryMask.getWidth(), primaryMask.getHeight(), config, SnapTargetEdge.OUTER);

        boolean moved = false;
        for (int i = 0; i < simplified.size(); i++) {
            if (!simplified.get(i).equals(adjustedContour.get(i))) {
                moved = true;
                break;
            }
        }
        if (!moved) {
            return rasterizeAndPreserveCore(primaryMask, simplified, config.antialiasing());
        }

        return rasterizeAndPreserveCore(primaryMask, adjustedContour, config.antialiasing());
    }

    private BinaryMask intersect(BinaryMask first, BinaryMask second) {
        BinaryMask result = new BinaryMask(first.getWidth(), first.getHeight());
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.get(x, y) && second.get(x, y)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Rasterise le contour polygonal en couverture continue et sécurise le noyau intérieur contre toute perte de matière.
     *
     * @param original     Masque binaire d'origine.
     * @param contour      Liste ordonnée des sommets du polygone (recalé ou simplifié).
     * @param antialiasing Vrai pour activer le suréchantillonnage et l'anti-aliasing sub-pixel.
     * @return Masque de couverture combiné.
     */
    private CoverageMask rasterizeAndPreserveCore(BinaryMask original, List<Point> contour, boolean antialiasing) {
        CoverageMask coverage = polygonBuilder.rasterizePixelCenterContour(
                original.getWidth(), original.getHeight(), contour, antialiasing);
        BinaryMask interiorCore = MorphologyOps.erode(original, 1);
        return coverage.max(CoverageMask.fromBinaryMask(interiorCore));
    }
}
