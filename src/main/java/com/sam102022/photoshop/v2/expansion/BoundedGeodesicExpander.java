package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.Arrays;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Moteur de propagation géodésique Dijkstra 8-connexe borné par la demi-largeur locale de chaussée.
 * <p>
 * Amorcé depuis le contour des cellules sélectionnées ayant pénétré dans le réseau routier,
 * ce propagateur progresse dans la chaussée limitrophe et s'interrompt strictement dès que
 * la distance géodésique dépasse {@code 2 * maxDist(x, y) + epsilon}, garantissant l'étanchéité
 * absolue aux carrefours et sur les rues transversales extérieures.
 */
public class BoundedGeodesicExpander {

    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double SQRT2 = Math.sqrt(2.0);

    /**
     * Propage les graines routières dans la limite de la demi-largeur locale maximale.
     *
     * @param retainedMask Masque des parcelles sélectionnées T (non nul).
     * @param roadMask     Masque binaire de voirie Rc (non nul).
     * @param maxDist      Carte des demi-largeurs maximales locales (non nulle).
     * @param config       Configuration de l'expansion et marges géométriques (non nulle).
     * @return Masque binaire de l'extension routière absorbée {@code ext}.
     */
    public BinaryMask expand(BinaryMask retainedMask, BinaryMask roadMask,
                             DistanceMap maxDist, ExpansionConfig config) {
        validateInputs(retainedMask, roadMask, maxDist, config);
        int width = retainedMask.getWidth();
        int height = retainedMask.getHeight();

        BinaryMask seeds = extractSeeds(retainedMask, roadMask, config.dilationSeedSteps());
        double[] dist = runBoundedDijkstra(seeds, roadMask, maxDist, config.epsilon(), width, height);

        return buildExtensionMask(roadMask, maxDist, config.epsilon(), dist, width, height);
    }

    /**
     * Valide la présence et la cohérence dimensionnelle des données d'entrée.
     *
     * @param retainedMask Masque intérieur T.
     * @param roadMask     Masque routier Rc.
     * @param maxDist      Carte de distances locales.
     * @param config       Configuration d'expansion.
     */
    private void validateInputs(BinaryMask retainedMask, BinaryMask roadMask,
                                DistanceMap maxDist, ExpansionConfig config) {
        Objects.requireNonNull(retainedMask, "retainedMask ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(maxDist, "maxDist ne doit pas être nul.");
        Objects.requireNonNull(config, "config ne doit pas être nul.");

        int w = retainedMask.getWidth();
        int h = retainedMask.getHeight();
        if (roadMask.getWidth() != w || roadMask.getHeight() != h
                || maxDist.width() != w || maxDist.height() != h) {
            throw new IllegalArgumentException("Incohérence dimensionnelle entre les masques et maxDist.");
        }
    }

    /**
     * Extrait les pixels germes situés sur la chaussée à proximité immédiate de T.
     *
     * @param retainedMask Masque intérieur T.
     * @param roadMask     Masque de routes Rc.
     * @param steps        Nombre d'étapes de dilatation 4-connexe.
     * @return Masque binaire des graines d'initialisation.
     */
    private BinaryMask extractSeeds(BinaryMask retainedMask, BinaryMask roadMask, int steps) {
        int width = retainedMask.getWidth();
        int height = retainedMask.getHeight();
        BinaryMask current = retainedMask;
        for (int i = 0; i < steps; i++) {
            current = dilateCross(current, width, height);
        }

        BinaryMask seeds = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y) && current.get(x, y)) {
                    seeds.set(x, y, true);
                }
            }
        }
        return seeds;
    }

    /**
     * Effectue une étape de dilatation élémentaire 4-connexe (croix).
     *
     * @param mask   Masque binaire d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Nouveau masque dilaté d'un pixel.
     */
    private BinaryMask dilateCross(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, true);
                    if (x > 0) result.set(x - 1, y, true);
                    if (x + 1 < width) result.set(x + 1, y, true);
                    if (y > 0) result.set(x, y - 1, true);
                    if (y + 1 < height) result.set(x, y + 1, true);
                }
            }
        }
        return result;
    }

    /**
     * Exécute l'algorithme de Dijkstra borné sur la grille 8-connexe.
     *
     * @param seeds    Graines d'amorce à distance 0.
     * @param roadMask Masque routier limitant la propagation.
     * @param maxDist  Carte des distances locales.
     * @param epsilon  Tolérance géométrique en pixels.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     * @return Tableau 1D des distances géodésiques minimales calculées.
     */
    private double[] runBoundedDijkstra(BinaryMask seeds, BinaryMask roadMask,
                                        DistanceMap maxDist, double epsilon,
                                        int width, int height) {
        double[] dist = new double[width * height];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        PriorityQueue<DijkstraNode> pq = new PriorityQueue<>();

        initializeQueue(seeds, dist, pq, width, height);

        while (!pq.isEmpty()) {
            DijkstraNode curr = pq.poll();
            int currIdx = curr.y * width + curr.x;
            if (curr.dist > dist[currIdx]) {
                continue;
            }
            exploreNeighbors(curr, roadMask, maxDist, epsilon, dist, pq, width, height);
        }

        return dist;
    }

    /**
     * Initialise la file de priorité avec l'ensemble des pixels germes.
     *
     * @param seeds  Masque des graines.
     * @param dist   Tableau des distances.
     * @param pq     File de priorité Dijkstra.
     * @param width  Largeur.
     * @param height Hauteur.
     */
    private void initializeQueue(BinaryMask seeds, double[] dist,
                                 PriorityQueue<DijkstraNode> pq, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (seeds.get(x, y)) {
                    int idx = y * width + x;
                    dist[idx] = 0.0;
                    pq.add(new DijkstraNode(x, y, 0.0));
                }
            }
        }
    }

    /**
     * Propage le front courant vers les 8 voisins connexes autorisés.
     *
     * @param curr     Nœud courant extrait de la file.
     * @param roadMask Masque routier.
     * @param maxDist  Carte des demi-largeurs maximales.
     * @param epsilon  Marge géométrique.
     * @param dist     Tableau des distances.
     * @param pq       File de priorité.
     * @param width    Largeur.
     * @param height   Hauteur.
     */
    private void exploreNeighbors(DijkstraNode curr, BinaryMask roadMask,
                                  DistanceMap maxDist, double epsilon,
                                  double[] dist, PriorityQueue<DijkstraNode> pq,
                                  int width, int height) {
        for (int i = 0; i < 8; i++) {
            int nx = curr.x + DX[i];
            int ny = curr.y + DY[i];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height || !roadMask.get(nx, ny)) {
                continue;
            }
            double step = (DX[i] == 0 || DY[i] == 0) ? 1.0 : SQRT2;
            double candDist = curr.dist + step;
            double bound = 2.0 * maxDist.get(nx, ny) + epsilon;
            int nIdx = ny * width + nx;
            if (candDist <= bound && candDist < dist[nIdx]) {
                dist[nIdx] = candDist;
                pq.add(new DijkstraNode(nx, ny, candDist));
            }
        }
    }

    /**
     * Construit le masque binaire final des pixels routiers atteints dans la borne géométrique.
     *
     * @param roadMask Masque routier.
     * @param maxDist  Carte des distances.
     * @param epsilon  Tolérance en pixels.
     * @param dist     Tableau des distances calculées.
     * @param width    Largeur.
     * @param height   Hauteur.
     * @return Masque binaire d'extension routière.
     */
    private BinaryMask buildExtensionMask(BinaryMask roadMask, DistanceMap maxDist,
                                          double epsilon, double[] dist,
                                          int width, int height) {
        BinaryMask ext = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (roadMask.get(x, y)) {
                    double bound = 2.0 * maxDist.get(x, y) + epsilon;
                    if (dist[idx] <= bound) {
                        ext.set(x, y, true);
                    }
                }
            }
        }
        return ext;
    }

    /**
     * Nœud de propagation Dijkstra stockant position et distance accumulée.
     */
    private static final class DijkstraNode implements Comparable<DijkstraNode> {
        final int x;
        final int y;
        final double dist;

        DijkstraNode(int x, int y, double dist) {
            this.x = x;
            this.y = y;
            this.dist = dist;
        }

        @Override
        public int compareTo(DijkstraNode other) {
            return Double.compare(this.dist, other.dist);
        }
    }
}
