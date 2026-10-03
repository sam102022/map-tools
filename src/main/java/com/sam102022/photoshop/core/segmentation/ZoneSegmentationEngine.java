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
import java.util.ArrayList;
import java.util.List;

/**
 * Moteur de segmentation de zone interne par inondation géodésique et calage au bord intérieur de la chaussée.
 * <p>
 * Orchestre l'extraction déterministe d'une zone délimitée par un cadre d'annotation :
 * <ul>
 *     <li>Sélection de graine déterministe au cœur de l'emprise admissible (centroïde et transformée de distance) ;</li>
 *     <li>Inondation BFS confinée haute performance sans allocation d'objets ;</li>
 *     <li>Vectorisation continue anti-aliasée sub-pixel via {@link PolygonBuilder} ;</li>
 *     <li>Garantie mathématiquement absolue d'exclusion de la chaussée routière (mise à zéro stricte des pixels de route).</li>
 * </ul>
 * </p>
 */
public final class ZoneSegmentationEngine {

    /** Tolérance pour la comparaison d'égalités de distances au centroïde. */
    private static final double EPSILON = 1e-9;

    private final ContourExtractor contourExtractor;
    private final ContourSimplifier contourSimplifier;
    private final PolygonBuilder polygonBuilder;
    private final RoadSnapper roadSnapper;

    /**
     * Initialise une nouvelle instance du moteur avec ses composants géométriques dédiés.
     */
    public ZoneSegmentationEngine() {
        this.contourExtractor = new ContourExtractor();
        this.contourSimplifier = new ContourSimplifier();
        this.polygonBuilder = new PolygonBuilder();
        this.roadSnapper = new RoadSnapper();
    }

    /**
     * Découpe la zone interne ciblée avec exclusion stricte de la chaussée et anti-aliasing sub-pixel.
     *
     * @param interiorMask         Masque de la région intérieure fermée du cadre d'annotation.
     * @param roadCandidates       Masque des axes routiers candidats.
     * @param consolidatedBarriers Masque des barrières infranchissables étanchéifiées.
     * @param territoryMask        Masque du territoire global.
     * @param config               Configuration d'exécution (incluant l'option anti-aliasing).
     * @return Masque d'opacité continue {@link CoverageMask} délimitant la zone sans aucun pixel routier.
     * @throws IllegalArgumentException si l'un des paramètres est {@code null} ou si les dimensions diffèrent.
     * @throws IllegalStateException    si l'intérieur du cadre ne contient aucun pixel libre dans le territoire.
     */
    public CoverageMask segmentZone(BinaryMask interiorMask, BinaryMask roadCandidates,
                                    BinaryMask consolidatedBarriers, BinaryMask territoryMask,
                                    SnappingConfig config) {
        validateInputs(interiorMask, roadCandidates, consolidatedBarriers, territoryMask, config);

        int width = interiorMask.getWidth();
        int height = interiorMask.getHeight();

        BinaryMask admissibleDomain = computeAdmissibleDomain(interiorMask, territoryMask, consolidatedBarriers);
        if (admissibleDomain.countActivePixels() == 0) {
            throw new IllegalStateException("Impossible d'initialiser la zone : l'intérieur du cadre ne contient "
                    + "aucun pixel libre dans le territoire.");
        }

        Point seed = findDeterministicSeed(admissibleDomain, width, height);
        BinaryMask zoneBinary = floodFillZone(interiorMask, consolidatedBarriers, seed, width, height);

        CoverageMask coverage = rasterizeZoneCoverage(zoneBinary, roadCandidates, consolidatedBarriers, config, width, height);
        applyStrictExclusions(coverage, roadCandidates, territoryMask, width, height);

        return coverage;
    }

    /**
     * Valide la présence et la concordance dimensionnelle des masques en entrée.
     *
     * @param interiorMask         Masque intérieur du sélecteur.
     * @param roadCandidates       Masque des axes routiers candidats.
     * @param consolidatedBarriers Masque consolidé des obstacles.
     * @param territoryMask        Masque de territoire.
     * @param config               Configuration de recalage.
     * @throws IllegalArgumentException si un argument est null ou en cas de dimensions différentes.
     */
    private static void validateInputs(BinaryMask interiorMask, BinaryMask roadCandidates,
                                       BinaryMask consolidatedBarriers, BinaryMask territoryMask,
                                       SnappingConfig config) {
        if (interiorMask == null || roadCandidates == null || consolidatedBarriers == null
                || territoryMask == null || config == null) {
            throw new IllegalArgumentException("Aucun argument ne peut être null.");
        }

        int w = interiorMask.getWidth();
        int h = interiorMask.getHeight();

        if (roadCandidates.getWidth() != w || roadCandidates.getHeight() != h
                || consolidatedBarriers.getWidth() != w || consolidatedBarriers.getHeight() != h
                || territoryMask.getWidth() != w || territoryMask.getHeight() != h) {
            throw new IllegalArgumentException("Dimensions discordantes entre les masques fournis.");
        }
    }

    /**
     * Calcule le domaine admissible pour planter la graine d'inondation : S = intérieur ∩ territoire \ barrières.
     *
     * @param interior  Masque intérieur du cadre d'annotation.
     * @param territory Masque du territoire global.
     * @param barriers  Masque consolidé des barrières infranchissables.
     * @return Masque binaire du domaine admissible.
     */
    private static BinaryMask computeAdmissibleDomain(BinaryMask interior, BinaryMask territory, BinaryMask barriers) {
        BinaryMask s = interior.and(territory);
        return s.and(barriers.not());
    }

    /**
     * Sélectionne de manière déterministe le point de graine optimal au centre de l'espace libre.
     *
     * @param domain Domaine admissible non-vide.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @return Coordonnées du point de graine sélectionné.
     */
    private static Point findDeterministicSeed(BinaryMask domain, int width, int height) {
        int[] distMap = computeDistanceMap(domain, width, height);
        int maxDist = findMaxDistance(distMap);

        List<Point> candidates = new ArrayList<>();
        double[] centroid = collectCandidatesAndCentroid(domain, distMap, maxDist, width, height, candidates);

        return selectOptimalCandidate(candidates, centroid[0], centroid[1]);
    }

    /**
     * Recherche la distance maximale présente dans la carte de distance.
     *
     * @param distMap Tableau 1D des distances.
     * @return Valeur de distance maximale observée.
     */
    private static int findMaxDistance(int[] distMap) {
        int max = 0;
        for (int d : distMap) {
            if (d > max) {
                max = d;
            }
        }
        return max;
    }

    /**
     * Collecte les candidats maximisant la distance et calcule le centroïde géométrique du domaine.
     *
     * @param domain     Masque binaire du domaine admissible.
     * @param distMap    Carte des distances associées.
     * @param maxDist    Distance maximale cible.
     * @param width      Largeur de l'image.
     * @param height     Hauteur de l'image.
     * @param candidates Liste réceptrice des points candidats à distance maximale.
     * @return Tableau à 2 éléments [centroidX, centroidY].
     */
    private static double[] collectCandidatesAndCentroid(BinaryMask domain, int[] distMap, int maxDist,
                                                         int width, int height, List<Point> candidates) {
        double sumX = 0;
        double sumY = 0;
        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (domain.get(x, y)) {
                    sumX += x;
                    sumY += y;
                    count++;
                    if (distMap[y * width + x] == maxDist) {
                        candidates.add(new Point(x, y));
                    }
                }
            }
        }

        double cx = (count > 0) ? (sumX / count) : 0;
        double cy = (count > 0) ? (sumY / count) : 0;
        return new double[]{cx, cy};
    }

    /**
     * Calcule la carte des distances intérieures aux frontières du domaine admissible.
     *
     * @param domain Masque binaire du domaine.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @return Tableau 1D contenant la distance de chaque pixel à la frontière.
     */
    private static int[] computeDistanceMap(BinaryMask domain, int width, int height) {
        int[] dist = new int[width * height];
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (domain.get(x, y) && isDomainBorder(domain, x, y, width, height)) {
                    int idx = y * width + x;
                    dist[idx] = 1;
                    queue[tail++] = idx;
                }
            }
        }

        while (head < tail) {
            int current = queue[head++];
            int cx = current % width;
            int cy = current / width;
            int currentDist = dist[current];

            tail = checkAndEnqueueDistanceNeighbor(domain, dist, queue, cx + 1, cy, currentDist + 1, width, height, tail);
            tail = checkAndEnqueueDistanceNeighbor(domain, dist, queue, cx - 1, cy, currentDist + 1, width, height, tail);
            tail = checkAndEnqueueDistanceNeighbor(domain, dist, queue, cx, cy + 1, currentDist + 1, width, height, tail);
            tail = checkAndEnqueueDistanceNeighbor(domain, dist, queue, cx, cy - 1, currentDist + 1, width, height, tail);
        }

        return dist;
    }

    /**
     * Détermine si un pixel du domaine touche une frontière extérieure.
     *
     * @param domain Masque du domaine.
     * @param x      Coordonnée X.
     * @param y      Coordonnée Y.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return {@code true} si le pixel est adjacent à un pixel hors-domaine.
     */
    private static boolean isDomainBorder(BinaryMask domain, int x, int y, int width, int height) {
        if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
            return true;
        }
        return !domain.get(x + 1, y) || !domain.get(x - 1, y)
                || !domain.get(x, y + 1) || !domain.get(x, y - 1);
    }

    /**
     * Propage la distance vers un pixel voisin du domaine s'il n'a pas encore été exploré.
     *
     * @param domain   Masque binaire du domaine.
     * @param dist     Tableau des distances.
     * @param queue    File BFS.
     * @param nx       Coordonnée X du voisin.
     * @param ny       Coordonnée Y du voisin.
     * @param nextDist Distance calculée pour ce voisin.
     * @param width    Largeur de l'image.
     * @param height   Hauteur de l'image.
     * @param tail     Position d'écriture dans la file.
     * @return Nouvelle position d'écriture après ajout potentiel.
     */
    private static int checkAndEnqueueDistanceNeighbor(BinaryMask domain, int[] dist, int[] queue,
                                                      int nx, int ny, int nextDist,
                                                      int width, int height, int tail) {
        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
            int nIdx = ny * width + nx;
            if (domain.get(nx, ny) && dist[nIdx] == 0) {
                dist[nIdx] = nextDist;
                queue[tail] = nIdx;
                return tail + 1;
            }
        }
        return tail;
    }

    /**
     * Départage les candidats optimaux par proximité au centroïde puis ordre lexicographique.
     *
     * @param candidates Liste des points candidats maximisant la distance.
     * @param cx         Coordonnée X du centroïde.
     * @param cy         Coordonnée Y du centroïde.
     * @return Point de graine optimal sélectionné.
     */
    private static Point selectOptimalCandidate(List<Point> candidates, double cx, double cy) {
        Point best = candidates.get(0);
        double minD2 = computeDistanceSq(best.x, best.y, cx, cy);

        for (int i = 1; i < candidates.size(); i++) {
            Point p = candidates.get(i);
            double d2 = computeDistanceSq(p.x, p.y, cx, cy);
            boolean isCloser = (d2 < minD2 - EPSILON);
            boolean isEquivalent = Math.abs(d2 - minD2) <= EPSILON;

            if (isCloser || (isEquivalent && isLexicographicallySmaller(p, best))) {
                minD2 = d2;
                best = p;
            }
        }

        return best;
    }

    /**
     * Calcule le carré de la distance euclidienne entre un point et le centroïde.
     *
     * @param px Coordonnée X du point.
     * @param py Coordonnée Y du point.
     * @param cx Coordonnée X du centroïde.
     * @param cy Coordonnée Y du centroïde.
     * @return Carré de la distance euclidienne.
     */
    private static double computeDistanceSq(int px, int py, double cx, double cy) {
        double dx = px - cx;
        double dy = py - cy;
        return dx * dx + dy * dy;
    }

    /**
     * Teste si le point p est lexicographiquement antérieur à current (y croissant, puis x croissant).
     *
     * @param p       Point à comparer.
     * @param current Point de référence actuel.
     * @return {@code true} si p est strictement antérieur à current.
     */
    private static boolean isLexicographicallySmaller(Point p, Point current) {
        if (p.y != current.y) {
            return p.y < current.y;
        }
        return p.x < current.x;
    }

    /**
     * Inonde la zone connexe par BFS en 4-connectivité strictement confinée à l'intérieur du cadre.
     *
     * @param interior Masque intérieur du cadre d'annotation.
     * @param barriers Masque des barrières infranchissables.
     * @param seed     Point de départ de l'inondation.
     * @param width    Largeur de l'image.
     * @param height   Hauteur de l'image.
     * @return Masque binaire de la zone inondée.
     */
    private static BinaryMask floodFillZone(BinaryMask interior, BinaryMask barriers, Point seed, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        int head = 0;
        int tail = 0;

        int seedIdx = seed.y * width + seed.x;
        visited[seedIdx] = true;
        result.set(seed.x, seed.y, true);
        queue[tail++] = seedIdx;

        while (head < tail) {
            int current = queue[head++];
            int cx = current % width;
            int cy = current / width;

            tail = tryEnqueueZoneNeighbor(interior, barriers, result, visited, queue, cx + 1, cy, width, height, tail);
            tail = tryEnqueueZoneNeighbor(interior, barriers, result, visited, queue, cx - 1, cy, width, height, tail);
            tail = tryEnqueueZoneNeighbor(interior, barriers, result, visited, queue, cx, cy + 1, width, height, tail);
            tail = tryEnqueueZoneNeighbor(interior, barriers, result, visited, queue, cx, cy - 1, width, height, tail);
        }

        return result;
    }

    /**
     * Tente d'ajouter un voisin dans la file d'inondation de zone.
     *
     * @param interior Masque de l'intérieur du cadre.
     * @param barriers Masque des barrières infranchissables.
     * @param result   Masque récepteur des pixels inondés.
     * @param visited  Tableau des pixels déjà visités.
     * @param queue    File plate BFS.
     * @param nx       Coordonnée X du voisin.
     * @param ny       Coordonnée Y du voisin.
     * @param width    Largeur de l'image.
     * @param height   Hauteur de l'image.
     * @param tail     Position d'écriture actuelle dans la file.
     * @return Nouvelle position d'écriture après ajout potentiel.
     */
    private static int tryEnqueueZoneNeighbor(BinaryMask interior, BinaryMask barriers, BinaryMask result,
                                             boolean[] visited, int[] queue, int nx, int ny,
                                             int width, int height, int tail) {
        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
            int nIdx = ny * width + nx;
            if (!visited[nIdx] && interior.get(nx, ny) && !barriers.get(nx, ny)) {
                visited[nIdx] = true;
                result.set(nx, ny, true);
                queue[tail] = nIdx;
                return tail + 1;
            }
        }
        return tail;
    }

    /**
     * Rasterise le contour polygonisé avec calage INNER au pied de la chaussée, anti-aliasing et fusion du noyau érodé sécurisé.
     *
     * @param zoneBinary     Masque binaire brut de la zone inondée.
     * @param roadCandidates Masque des axes routiers candidats.
     * @param barriers       Masque des barrières infranchissables.
     * @param config         Configuration de recalage.
     * @param width          Largeur de l'image.
     * @param height         Hauteur de l'image.
     * @return Masque de couverture continue résultant.
     */
    private CoverageMask rasterizeZoneCoverage(BinaryMask zoneBinary, BinaryMask roadCandidates,
                                               BinaryMask barriers, SnappingConfig config,
                                               int width, int height) {
        List<Point> contour = contourExtractor.extractLargestContour(zoneBinary);
        if (contour.size() < 3) {
            return CoverageMask.fromBinaryMask(zoneBinary);
        }

        List<Point> simplified = contourSimplifier.simplify(contour, 0.8);
        if (simplified.size() < 3) {
            simplified = contour;
        }

        List<Point> snapped = roadSnapper.snapContour(
                simplified, roadCandidates, width, height, config.snapDistance(), SnapTargetEdge.INNER);
        if (snapped.size() < 3) {
            snapped = simplified;
        }

        CoverageMask coverage = polygonBuilder.rasterizePixelCenterContour(
                width, height, snapped, config.antialiasing());

        BinaryMask safeCore = MorphologyOps.erode(zoneBinary, 1).and(barriers.not());
        return coverage.max(CoverageMask.fromBinaryMask(safeCore));
    }

    /**
     * Force la mise à zéro absolue des pixels appartenant aux axes routiers ou extérieurs au territoire.
     *
     * @param coverage  Masque de couverture à épurer.
     * @param roads     Masque des axes routiers candidats.
     * @param territory Masque du territoire global.
     * @param width     Largeur de l'image.
     * @param height    Hauteur de l'image.
     */
    private static void applyStrictExclusions(CoverageMask coverage, BinaryMask roads, BinaryMask territory,
                                              int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roads.get(x, y) || !territory.get(x, y)) {
                    coverage.set(x, y, 0);
                }
            }
        }
    }
}
