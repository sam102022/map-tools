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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
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
        return snapCoverage(roughGreenMask, roadCandidates, null, config);
    }

    /**
     * Recale le contour en utilisant les routes candidates comme guide et les bords visibles de la carte comme précision finale.
     *
     * @param roughGreenMask Masque initial de territoire.
     * @param roadCandidates Masque des routes OSM ou détectées sur la carte.
     * @param mapImage Image cartographique source, utilisée pour mesurer les bords de chaussée.
     * @param config Configuration de recalage et de rendu.
     * @return Masque de couverture continue final.
     */
    public CoverageMask snapCoverage(BinaryMask roughGreenMask, BinaryMask roadCandidates,
                                     BufferedImage mapImage, SnappingConfig config) {
        if (roughGreenMask == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du moteur de recalage ne peuvent pas être null.");
        }
        if (roughGreenMask.getWidth() != roadCandidates.getWidth()
                || roughGreenMask.getHeight() != roadCandidates.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre le masque et les candidats routiers.");
        }
        if (mapImage != null && (mapImage.getWidth() != roughGreenMask.getWidth()
                || mapImage.getHeight() != roughGreenMask.getHeight())) {
            throw new IllegalArgumentException("La carte et le masque de territoire doivent avoir les mêmes dimensions.");
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

        // Un contour raster simplifié peut relier deux sommets très éloignés par une droite.
        // Le densifier permet de suivre les courbes routières au lieu de couper les virages.
        List<Point> samplingContour = densify(simplified, 4.0);
        List<Point> adjustedContour = roadSnapper.snap(samplingContour, roadCandidates,
                primaryMask.getWidth(), primaryMask.getHeight(), config, SnapTargetEdge.OUTER, mapImage);
        adjustedContour = smoothNormalOffsets(samplingContour, adjustedContour, 2);

        boolean moved = false;
        for (int i = 0; i < samplingContour.size(); i++) {
            if (!samplingContour.get(i).equals(adjustedContour.get(i))) {
                moved = true;
                break;
            }
        }
        if (!moved) {
            return rasterizeAndPreserveCore(primaryMask, simplified, config.antialiasing());
        }

        return rasterizeAndPreserveCore(primaryMask, adjustedContour, config.antialiasing());
    }

    /**
     * Élimine les accroches isolées en filtrant médianement les déplacements le long des normales.
     *
     * @param originalContour Contour dense avant recalage.
     * @param snappedContour Contour après accrochage aux routes et aux contrastes de la carte.
     * @param windowRadius Rayon de la fenêtre médiane, en nombre d'échantillons.
     * @return Contour recalé sans pointes dues à un pixel ou une intersection isolée.
     */
    private List<Point> smoothNormalOffsets(List<Point> originalContour, List<Point> snappedContour,
                                            int windowRadius) {
        if (originalContour.size() != snappedContour.size() || originalContour.size() < 3) {
            return snappedContour;
        }
        double area = signedArea(originalContour);
        if (area == 0) return snappedContour;

        double[] offsets = measureNormalOffsets(originalContour, snappedContour, area);
        List<Point> smoothed = new ArrayList<>(originalContour.size());
        for (int i = 0; i < originalContour.size(); i++) {
            Point current = originalContour.get(i);
            double[] neighborhood = new double[windowRadius * 2 + 1];
            for (int delta = -windowRadius; delta <= windowRadius; delta++) {
                int index = Math.floorMod(i + delta, offsets.length);
                neighborhood[delta + windowRadius] = offsets[index];
            }
            Arrays.sort(neighborhood);
            double offset = neighborhood[windowRadius];
            smoothed.add(moveAlongNormal(originalContour, i, area, offset));
        }
        return List.copyOf(smoothed);
    }

    /**
     * Projette le déplacement observé sur la normale de chaque sommet original.
     *
     * @param originalContour Contour non recalé.
     * @param snappedContour Contour recalé de mêmes dimensions.
     * @param area Aire signée du contour.
     * @return Déplacements scalaires en pixels.
     */
    private double[] measureNormalOffsets(List<Point> originalContour, List<Point> snappedContour,
                                          double area) {
        double[] offsets = new double[originalContour.size()];
        for (int i = 0; i < offsets.length; i++) {
            double[] normal = normalAt(originalContour, i, area);
            Point original = originalContour.get(i);
            Point snapped = snappedContour.get(i);
            offsets[i] = (snapped.x - original.x) * normal[0] + (snapped.y - original.y) * normal[1];
        }
        return offsets;
    }

    /**
     * Repositionne un sommet le long de sa normale orientée vers l'extérieur.
     *
     * @param contour Contour servant au calcul des normales.
     * @param index Indice du sommet.
     * @param area Aire signée.
     * @param offset Déplacement signé.
     * @return Sommet repositionné.
     */
    private Point moveAlongNormal(List<Point> contour, int index, double area, double offset) {
        double[] normal = normalAt(contour, index, area);
        Point current = contour.get(index);
        return new Point((int) Math.round(current.x + normal[0] * offset),
                (int) Math.round(current.y + normal[1] * offset));
    }

    /**
     * Calcule la normale extérieure locale d'un contour fermé.
     *
     * @param contour Sommets ordonnés.
     * @param index Indice du sommet.
     * @param area Aire signée.
     * @return Normale unitaire sous la forme [nx, ny].
     */
    private double[] normalAt(List<Point> contour, int index, double area) {
        int size = contour.size();
        Point previous = contour.get(Math.floorMod(index - 1, size));
        Point next = contour.get((index + 1) % size);
        double tx = next.x - previous.x;
        double ty = next.y - previous.y;
        double length = Math.hypot(tx, ty);
        if (length == 0) return new double[]{0, 0};
        double nx = area > 0 ? ty / length : -ty / length;
        double ny = area > 0 ? -tx / length : tx / length;
        return new double[]{nx, ny};
    }

    /**
     * Calcule l'aire signée d'un contour fermé.
     *
     * @param points Sommets du polygone.
     * @return Aire signée.
     */
    private double signedArea(List<Point> points) {
        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            Point current = points.get(i);
            Point next = points.get((i + 1) % points.size());
            area += (double) current.x * next.y - (double) next.x * current.y;
        }
        return area / 2.0;
    }

    /**
     * Ajoute des échantillons régulièrement espacés sur chaque segment d'un contour fermé.
     *
     * @param contour Sommets ordonnés du contour.
     * @param maxSpacing Espacement maximal entre deux échantillons en pixels.
     * @return Contour densifié sans répétition du premier point à la fin.
     */
    private List<Point> densify(List<Point> contour, double maxSpacing) {
        List<Point> dense = new ArrayList<>();
        for (int i = 0; i < contour.size(); i++) {
            Point start = contour.get(i);
            Point end = contour.get((i + 1) % contour.size());
            double length = start.distance(end);
            int segments = Math.max(1, (int) Math.ceil(length / maxSpacing));
            for (int step = 0; step < segments; step++) {
                double fraction = (double) step / segments;
                Point sample = new Point((int) Math.round(start.x + (end.x - start.x) * fraction),
                        (int) Math.round(start.y + (end.y - start.y) * fraction));
                if (dense.isEmpty() || !dense.getLast().equals(sample)) dense.add(sample);
            }
        }
        return List.copyOf(dense);
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
