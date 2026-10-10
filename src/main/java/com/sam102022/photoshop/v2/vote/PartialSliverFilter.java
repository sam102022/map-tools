package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.expansion.DistanceMap;
import com.sam102022.photoshop.v2.expansion.EuclideanDistanceTransform;

import java.util.Objects;

/**
 * Filtre des lamelles fines issues des cellules partielles.
 * <p>
 * Lorsque le polygone d'intention déborde de quelques pixels au-delà d'une route frontière, les îlots
 * voisins sont faiblement couverts (typiquement 5 à 7 %) et classés PARTIAL : leur intersection avec le
 * polygone forme alors une lamelle de 10 à 15 px le long de la chaussée. Conserver ces lamelles décale le
 * contour à l'intérieur des îlots voisins, et leur basculement autour du seuil de rejet crée des crans.
 * </p>
 * <p>
 * Ce filtre retire chaque composante 4-connexe dont l'épaisseur maximale (deux fois la distance
 * euclidienne maximale au fond) est inférieure au seuil, tout en conservant les véritables zones ouvertes
 * (champs, parcelles sans voirie), beaucoup plus épaisses.
 * </p>
 */
public class PartialSliverFilter {

    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    private final EuclideanDistanceTransform distanceTransform;

    /**
     * Construit le filtre avec la transformée de distance euclidienne standard.
     */
    public PartialSliverFilter() {
        this(new EuclideanDistanceTransform());
    }

    /**
     * Construit le filtre avec une transformée de distance injectée.
     *
     * @param distanceTransform Transformée de distance euclidienne exacte.
     * @throws NullPointerException si distanceTransform est null.
     */
    public PartialSliverFilter(EuclideanDistanceTransform distanceTransform) {
        this.distanceTransform = Objects.requireNonNull(distanceTransform, "distanceTransform ne doit pas être nul.");
    }

    /**
     * Retourne le sous-masque des composantes suffisamment épaisses.
     *
     * @param candidates   Union des intersections cellule partielle ∩ polygone.
     * @param minThickness Épaisseur minimale en pixels ; 0 ou moins désactive le filtre.
     * @return Nouveau masque ne contenant que les composantes d'épaisseur &gt;= minThickness.
     * @throws NullPointerException si candidates est null.
     */
    public BinaryMask keepThickComponents(BinaryMask candidates, double minThickness) {
        Objects.requireNonNull(candidates, "candidates ne doit pas être nul.");
        int width = candidates.getWidth();
        int height = candidates.getHeight();
        BinaryMask kept = new BinaryMask(width, height);
        if (minThickness <= 0.0) {
            copyInto(candidates, kept, width, height);
            return kept;
        }

        DistanceMap distances = distanceTransform.compute(candidates);
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (candidates.get(x, y) && !visited[idx]) {
                    int size = exploreComponent(candidates, visited, queue, idx, width, height);
                    if (2.0 * maxDistance(distances, queue, size, width) >= minThickness) {
                        markComponent(kept, queue, size, width);
                    }
                }
            }
        }
        return kept;
    }

    /**
     * Parcourt en largeur (4-connexité) la composante contenant startIdx et la stocke dans queue.
     *
     * @param mask     Masque des candidats.
     * @param visited  Marqueurs de visite.
     * @param queue    Tampon recevant les indices de la composante.
     * @param startIdx Indice linéaire de départ.
     * @param width    Largeur du masque.
     * @param height   Hauteur du masque.
     * @return Nombre de pixels de la composante.
     */
    private int exploreComponent(BinaryMask mask, boolean[] visited, int[] queue,
                                 int startIdx, int width, int height) {
        int head = 0;
        int tail = 0;
        visited[startIdx] = true;
        queue[tail++] = startIdx;
        while (head < tail) {
            int curr = queue[head++];
            int cx = curr % width;
            int cy = curr / width;
            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIdx = ny * width + nx;
                    if (!visited[nIdx] && mask.get(nx, ny)) {
                        visited[nIdx] = true;
                        queue[tail++] = nIdx;
                    }
                }
            }
        }
        return tail;
    }

    /**
     * Calcule la distance maximale au fond sur une composante.
     *
     * @param distances Carte des distances euclidiennes.
     * @param queue     Indices de la composante.
     * @param size      Nombre de pixels de la composante.
     * @param width     Largeur du masque.
     * @return Distance maximale en pixels.
     */
    private double maxDistance(DistanceMap distances, int[] queue, int size, int width) {
        double max = 0.0;
        for (int i = 0; i < size; i++) {
            int idx = queue[i];
            max = Math.max(max, distances.get(idx % width, idx / width));
        }
        return max;
    }

    /**
     * Active dans le masque cible tous les pixels d'une composante.
     *
     * @param target Masque cible.
     * @param queue  Indices de la composante.
     * @param size   Nombre de pixels de la composante.
     * @param width  Largeur du masque.
     */
    private void markComponent(BinaryMask target, int[] queue, int size, int width) {
        for (int i = 0; i < size; i++) {
            int idx = queue[i];
            target.set(idx % width, idx / width, true);
        }
    }

    /**
     * Recopie intégralement un masque dans un autre de mêmes dimensions.
     *
     * @param source Masque source.
     * @param target Masque cible.
     * @param width  Largeur commune.
     * @param height Hauteur commune.
     */
    private void copyInto(BinaryMask source, BinaryMask target, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (source.get(x, y)) {
                    target.set(x, y, true);
                }
            }
        }
    }
}
