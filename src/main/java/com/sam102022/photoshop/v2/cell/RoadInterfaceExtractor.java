package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracteur topologique des interfaces routières (RoadInterface) reliant les cellules urbaines adjacentes.
 * Utilise la détection directe des cellules contiguës et une propagation multi-sources de type Voronoi (BFS)
 * pour identifier les tronçons de chaussée séparant chaque paire de cellules.
 */
public class RoadInterfaceExtractor {

    /**
     * Distance maximale de propagation au sein du réseau routier (en pixels).
     */
    public static final int MAX_ROAD_PROPAGATION_DISTANCE = 35;

    /**
     * Construit le CellGraph complet à partir du résultat de labellisation et du masque routier.
     *
     * @param labelingResult Résultat de la segmentation en cellules (labelMap et cellules).
     * @param roadMask       Masque binaire des routes.
     * @return Graphe topologique immuable CellGraph.
     * @throws IllegalArgumentException si l'un des paramètres est null.
     */
    public CellGraph buildGraph(CellLabelingResult labelingResult, BinaryMask roadMask) {
        if (labelingResult == null) {
            throw new IllegalArgumentException("Le résultat de labellisation ne peut pas être null.");
        }
        if (roadMask == null) {
            throw new IllegalArgumentException("Le masque routier ne peut pas être null.");
        }

        List<RoadInterface> interfaces = extractInterfaces(labelingResult.labelMap(), roadMask);
        return new CellGraph(labelingResult.cells(), interfaces);
    }

    /**
     * Analyse la matrice d'étiquettes et le masque routier pour extraire toutes les interfaces de route.
     *
     * @param labelMap Matrice d'étiquettes des cellules.
     * @param roadMask Masque binaire de chaussée.
     * @return Liste ordonnée immuable des interfaces routières détectées.
     * @throws IllegalArgumentException si labelMap ou roadMask est null ou de dimensions incohérentes.
     */
    public List<RoadInterface> extractInterfaces(CellLabelMap labelMap, BinaryMask roadMask) {
        validateInputs(labelMap, roadMask);

        int width = labelMap.width();
        int height = labelMap.height();

        Map<Long, List<Integer>> pairToPixels = new HashMap<>();

        // 1. Détection directe sur les routes minces (1px) touchant plusieurs cellules
        detectDirectAdjacencies(labelMap, roadMask, width, height, pairToPixels);

        // 2. Détection par propagation Voronoi sur les routes plus larges (> 1px)
        detectWideRoadAdjacencies(labelMap, roadMask, width, height, pairToPixels);

        return buildRoadInterfaces(width, height, pairToPixels);
    }

    /**
     * Valide les dimensions et la non-nullité des entrées.
     */
    private void validateInputs(CellLabelMap labelMap, BinaryMask roadMask) {
        if (labelMap == null) {
            throw new IllegalArgumentException("La matrice de labels ne peut pas être null.");
        }
        if (roadMask == null) {
            throw new IllegalArgumentException("Le masque routier ne peut pas être null.");
        }
        if (labelMap.width() != roadMask.getWidth() || labelMap.height() != roadMask.getHeight()) {
            throw new IllegalArgumentException("Dimensions discordantes entre labelMap (" + labelMap.width()
                    + "x" + labelMap.height() + ") et roadMask (" + roadMask.getWidth()
                    + "x" + roadMask.getHeight() + ").");
        }
    }

    /**
     * Identifie les pixels routiers touchant directement 2 cellules distinctes ou plus dans leur 4-voisinage.
     */
    private void detectDirectAdjacencies(CellLabelMap labelMap, BinaryMask roadMask, int width, int height,
                                         Map<Long, List<Integer>> pairToPixels) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) {
                    int idx = y * width + x;
                    List<Integer> neighbors = findDistinctAdjacentCells(labelMap, x, y);
                    if (neighbors.size() >= 2) {
                        recordPairCombinations(neighbors, idx, pairToPixels);
                    }
                }
            }
        }
    }

    /**
     * Recherche tous les identifiants uniques de cellules dans le 4-voisinage immédiat du pixel (x, y).
     */
    private List<Integer> findDistinctAdjacentCells(CellLabelMap labelMap, int x, int y) {
        Set<Integer> set = new HashSet<>(4);
        addIfCell(labelMap.getLabel(x - 1, y), set);
        addIfCell(labelMap.getLabel(x + 1, y), set);
        addIfCell(labelMap.getLabel(x, y - 1), set);
        addIfCell(labelMap.getLabel(x, y + 1), set);
        return new ArrayList<>(set);
    }

    /**
     * Ajoute l'identifiant s'il s'agit d'une cellule valide (> 0).
     */
    private void addIfCell(int label, Set<Integer> set) {
        if (label > 0) {
            set.add(label);
        }
    }

    /**
     * Enregistre l'ensemble des combinaisons par paires (c1, c2) pour un pixel de croisement.
     */
    private void recordPairCombinations(List<Integer> neighbors, int pixelIdx, Map<Long, List<Integer>> pairToPixels) {
        int size = neighbors.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                int c1 = neighbors.get(i);
                int c2 = neighbors.get(j);
                long key = encodeCellPair(c1, c2);
                pairToPixels.computeIfAbsent(key, k -> new ArrayList<>()).add(pixelIdx);
            }
        }
    }

    /**
     * Détecte les adjacences au travers des routes larges via propagation Voronoi BFS.
     */
    private void detectWideRoadAdjacencies(CellLabelMap labelMap, BinaryMask roadMask, int width, int height,
                                          Map<Long, List<Integer>> pairToPixels) {
        int[] owner = new int[width * height];
        int[] dist = new int[width * height];
        Arrays.fill(dist, -1);

        int[] queue = new int[width * height];
        int tail = initMultiSourceBfs(labelMap, roadMask, width, height, owner, dist, queue);

        propagateBfs(roadMask, width, height, owner, dist, queue, tail);

        findBoundaryCollisions(roadMask, width, height, owner, pairToPixels);
    }

    /**
     * Initialise la file BFS avec tous les pixels de chaussée contigus à une cellule urbaine.
     */
    private int initMultiSourceBfs(CellLabelMap labelMap, BinaryMask roadMask, int width, int height,
                                   int[] owner, int[] dist, int[] queue) {
        int tail = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) {
                    int neighborCell = findPrimaryAdjacentCell(labelMap, x, y);
                    if (neighborCell > 0) {
                        int idx = y * width + x;
                        owner[idx] = neighborCell;
                        dist[idx] = 1;
                        queue[tail++] = idx;
                    }
                }
            }
        }
        return tail;
    }

    /**
     * Recherche la première cellule adjacente pour l'initialisation du front BFS.
     */
    private int findPrimaryAdjacentCell(CellLabelMap labelMap, int x, int y) {
        int label = labelMap.getLabel(x - 1, y);
        if (label > 0) return label;
        label = labelMap.getLabel(x + 1, y);
        if (label > 0) return label;
        label = labelMap.getLabel(x, y - 1);
        if (label > 0) return label;
        return labelMap.getLabel(x, y + 1);
    }

    /**
     * Propage les étiquettes de cellules à l'intérieur du masque routier via BFS.
     */
    private void propagateBfs(BinaryMask roadMask, int width, int height,
                              int[] owner, int[] dist, int[] queue, int tail) {
        int head = 0;
        int[] dx = new int[]{-1, 1, 0, 0};
        int[] dy = new int[]{0, 0, -1, 1};

        while (head < tail) {
            int curr = queue[head++];
            int currDist = dist[curr];

            if (currDist >= MAX_ROAD_PROPAGATION_DISTANCE) {
                continue;
            }

            int cx = curr % width;
            int cy = curr / width;
            int cOwner = owner[curr];

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height && roadMask.get(nx, ny)) {
                    int nIdx = ny * width + nx;
                    if (dist[nIdx] == -1) {
                        dist[nIdx] = currDist + 1;
                        owner[nIdx] = cOwner;
                        queue[tail++] = nIdx;
                    }
                }
            }
        }
    }

    /**
     * Détecte les zones de collision où les influences de deux cellules distinctes se rejoignent.
     */
    private void findBoundaryCollisions(BinaryMask roadMask, int width, int height,
                                        int[] owner, Map<Long, List<Integer>> pairToPixels) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (!roadMask.get(x, y) || owner[idx] <= 0) {
                    continue;
                }
                checkPixelAdjacencies(x, y, width, height, roadMask, owner, pairToPixels);
            }
        }
    }

    /**
     * Vérifie les voisins Est et Sud du pixel (x, y) pour identifier les transitions d'appartenance.
     */
    private void checkPixelAdjacencies(int x, int y, int width, int height, BinaryMask roadMask,
                                       int[] owner, Map<Long, List<Integer>> pairToPixels) {
        int idx = y * width + x;
        int c1 = owner[idx];

        if (x < width - 1 && roadMask.get(x + 1, y)) {
            recordTransition(c1, owner[idx + 1], idx, idx + 1, pairToPixels);
        }
        if (y < height - 1 && roadMask.get(x, y + 1)) {
            recordTransition(c1, owner[idx + width], idx, idx + width, pairToPixels);
        }
    }

    /**
     * Enregistre les pixels de contact entre deux cellules si leurs identifiants sont distincts.
     */
    private void recordTransition(int c1, int c2, int idx1, int idx2, Map<Long, List<Integer>> pairToPixels) {
        if (c1 > 0 && c2 > 0 && c1 != c2) {
            long key = encodeCellPair(c1, c2);
            List<Integer> list = pairToPixels.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(idx1);
            list.add(idx2);
        }
    }

    /**
     * Encode une paire non-orientée de cellules (A, B) avec A < B sous forme d'un entier long unique.
     */
    private long encodeCellPair(int c1, int c2) {
        int min = Math.min(c1, c2);
        int max = Math.max(c1, c2);
        return (((long) min) << 32) | (max & 0xFFFFFFFFL);
    }

    /**
     * Instancie les records RoadInterface à partir des pixels collectés pour chaque paire.
     */
    private List<RoadInterface> buildRoadInterfaces(int width, int height, Map<Long, List<Integer>> pairToPixels) {
        List<RoadInterface> interfaces = new ArrayList<>(pairToPixels.size());
        int interfaceId = 1;

        List<Long> sortedKeys = new ArrayList<>(pairToPixels.keySet());
        Collections.sort(sortedKeys);

        for (long key : sortedKeys) {
            int cellA = (int) (key >>> 32);
            int cellB = (int) (key & 0xFFFFFFFFL);

            List<Integer> pixels = pairToPixels.get(key);
            BinaryMask mask = new BinaryMask(width, height);
            Set<Integer> uniquePixels = new HashSet<>(pixels);

            for (int p : uniquePixels) {
                mask.set(p % width, p / width, true);
            }

            long area = uniquePixels.size();
            interfaces.add(new RoadInterface(interfaceId++, cellA, cellB, mask, area));
        }

        return Collections.unmodifiableList(interfaces);
    }
}
