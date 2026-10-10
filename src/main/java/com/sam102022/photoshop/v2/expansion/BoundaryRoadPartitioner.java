package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.Arrays;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Partitionneur géodésique médian de voirie frontalière pour le mode ZONE.
 * <p>
 * Réalise un diagramme de Voronoi géodésique à double front au sein de la chaussée mitoyenne
 * séparant deux zones contiguës, garantissant l'équidistance médiane et le non-recouvrement
 * strict {@code Z1 & Z2 = Ø}.
 */
public class BoundaryRoadPartitioner {

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double SQRT2 = Math.sqrt(2.0);

    /**
     * Attribue à la zone principale la portion de voirie qui lui est géodésiquement la plus proche.
     *
     * @param roadMask      Masque binaire de la chaussée à partitionner (non nul).
     * @param primaryZone   Masque binaire de la zone principale (non nul).
     * @param competingZone Masque binaire de la zone concurrente (non nul).
     * @return Masque binaire de voirie alloué exclusivement à la zone principale.
     */
    public BinaryMask partition(BinaryMask roadMask, BinaryMask primaryZone, BinaryMask competingZone) {
        validateInputs(roadMask, primaryZone, competingZone);
        int width = roadMask.getWidth();
        int height = roadMask.getHeight();

        double[] distPrimary = computeGeodesicDistances(roadMask, primaryZone, width, height);
        double[] distCompeting = computeGeodesicDistances(roadMask, competingZone, width, height);

        return buildAllocatedMask(roadMask, distPrimary, distCompeting, width, height);
    }

    /**
     * Valide la présence et la cohérence dimensionnelle des masques.
     *
     * @param roadMask      Masque de routes.
     * @param primaryZone   Zone principale.
     * @param competingZone Zone concurrente.
     */
    private void validateInputs(BinaryMask roadMask, BinaryMask primaryZone, BinaryMask competingZone) {
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(primaryZone, "primaryZone ne doit pas être nul.");
        Objects.requireNonNull(competingZone, "competingZone ne doit pas être nul.");

        int w = roadMask.getWidth();
        int h = roadMask.getHeight();
        if (primaryZone.getWidth() != w || primaryZone.getHeight() != h
                || competingZone.getWidth() != w || competingZone.getHeight() != h) {
            throw new IllegalArgumentException("Incohérence dimensionnelle entre les masques.");
        }
    }

    /**
     * Calcule la carte des distances géodésiques à travers la voirie depuis une zone donnée.
     *
     * @param roadMask Masque de voirie autorisée.
     * @param zone     Zone source.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     * @return Tableau 1D des distances géodésiques.
     */
    private double[] computeGeodesicDistances(BinaryMask roadMask, BinaryMask zone,
                                              int width, int height) {
        double[] dist = new double[width * height];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        PriorityQueue<RoadNode> pq = new PriorityQueue<>();

        initializeBoundarySeeds(roadMask, zone, dist, pq, width, height);

        while (!pq.isEmpty()) {
            RoadNode curr = pq.poll();
            int currIdx = curr.y * width + curr.x;
            if (curr.dist > dist[currIdx]) {
                continue;
            }
            exploreRoadNeighbors(curr, roadMask, dist, pq, width, height);
        }

        return dist;
    }

    /**
     * Initialise la file de priorité avec les pixels routiers mitoyens de la zone.
     *
     * @param roadMask Masque routier.
     * @param zone     Zone source.
     * @param dist     Tableau de distances.
     * @param pq       File de priorité.
     * @param width    Largeur.
     * @param height   Hauteur.
     */
    private void initializeBoundarySeeds(BinaryMask roadMask, BinaryMask zone,
                                         double[] dist, PriorityQueue<RoadNode> pq,
                                         int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) {
                    double bestStep = findBestStepToZone(x, y, zone, width, height);
                    if (bestStep < Double.POSITIVE_INFINITY) {
                        int idx = y * width + x;
                        dist[idx] = bestStep;
                        pq.add(new RoadNode(x, y, bestStep));
                    }
                }
            }
        }
    }

    /**
     * Détermine la distance minimale au pixel voisin appartenant à la zone.
     *
     * @param x      Abscisse du pixel routier.
     * @param y      Ordonnée du pixel routier.
     * @param zone   Zone source.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Distance euclidienne minimale (1.0, SQRT2 ou POSITIVE_INFINITY).
     */
    private double findBestStepToZone(int x, int y, BinaryMask zone, int width, int height) {
        double minStep = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height && zone.get(nx, ny)) {
                double step = (DX[i] == 0 || DY[i] == 0) ? 1.0 : SQRT2;
                if (step < minStep) {
                    minStep = step;
                }
            }
        }
        return minStep;
    }

    /**
     * Propage le front géodésique à travers les voisins routiers.
     *
     * @param curr     Nœud courant.
     * @param roadMask Masque de la route.
     * @param dist     Tableau des distances.
     * @param pq       File de priorité.
     * @param width    Largeur.
     * @param height   Hauteur.
     */
    private void exploreRoadNeighbors(RoadNode curr, BinaryMask roadMask,
                                      double[] dist, PriorityQueue<RoadNode> pq,
                                      int width, int height) {
        for (int i = 0; i < 8; i++) {
            int nx = curr.x + DX[i];
            int ny = curr.y + DY[i];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height || !roadMask.get(nx, ny)) {
                continue;
            }
            double step = (DX[i] == 0 || DY[i] == 0) ? 1.0 : SQRT2;
            double cand = curr.dist + step;
            int nIdx = ny * width + nx;
            if (cand < dist[nIdx]) {
                dist[nIdx] = cand;
                pq.add(new RoadNode(nx, ny, cand));
            }
        }
    }

    /**
     * Alloue les pixels routiers satisfaisant l'inégalité stricte {@code distPrimary < distCompeting}.
     *
     * @param roadMask      Masque de routes.
     * @param distPrimary   Distances à la zone principale.
     * @param distCompeting Distances à la zone concurrente.
     * @param width         Largeur.
     * @param height        Hauteur.
     * @return Masque binaire alloué à la zone principale.
     */
    private BinaryMask buildAllocatedMask(BinaryMask roadMask, double[] distPrimary,
                                         double[] distCompeting, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (roadMask.get(x, y)) {
                    double dp = distPrimary[idx];
                    double dc = distCompeting[idx];
                    if (dp < dc && dp < Double.POSITIVE_INFINITY) {
                        result.set(x, y, true);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Nœud de graphe pour la propagation géodésique routière.
     */
    private static final class RoadNode implements Comparable<RoadNode> {
        final int x;
        final int y;
        final double dist;

        RoadNode(int x, int y, double dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }

        @Override
        public int compareTo(RoadNode other) {
            return Double.compare(this.dist, other.dist);
        }
    }
}
