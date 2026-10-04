package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     *
     * @param labelMap Matrice d'étiquettes de cellules.
     * @param roadMask Masque binaire des routes fermées.
     * @throws IllegalArgumentException si l'un des masques est null ou si leurs dimensions ne concordent pas.
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
     *
     * @param labelMap     Matrice d'étiquettes de cellules.
     * @param roadMask     Masque binaire des routes.
     * @param width        Largeur de la matrice en pixels.
     * @param height       Hauteur de la matrice en pixels.
     * @param pairToPixels Table collectrice associant chaque paire de cellules aux pixels de route partagés.
     */
    private void detectDirectAdjacencies(CellLabelMap labelMap, BinaryMask roadMask, int width, int height,
                                         Map<Long, List<Integer>> pairToPixels) {
        int[] buffer = new int[4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) {
                    int count = collectDistinctAdjacentCells(labelMap, x, y, buffer);
                    if (count >= 2) {
                        recordPairCombinations(buffer, count, y * width + x, pairToPixels);
                    }
                }
            }
        }
    }

    /**
     * Recherche sans allocation d'objet les identifiants uniques de cellules dans le 4-voisinage du pixel (x, y).
     *
     * @param labelMap Matrice d'étiquettes de cellules.
     * @param x        Abscisse du pixel central.
     * @param y        Ordonnée du pixel central.
     * @param buffer   Tableau tampon de taille au moins 4 pour stocker les labels distincts.
     * @return Nombre d'identifiants de cellules distincts et strictement positifs collectés.
     */
    private int collectDistinctAdjacentCells(CellLabelMap labelMap, int x, int y, int[] buffer) {
        int count = 0;
        count = addDistinct(buffer, count, labelMap.getLabel(x - 1, y));
        count = addDistinct(buffer, count, labelMap.getLabel(x + 1, y));
        count = addDistinct(buffer, count, labelMap.getLabel(x, y - 1));
        return addDistinct(buffer, count, labelMap.getLabel(x, y + 1));
    }

    /**
     * Ajoute un identifiant dans le tableau tampon s'il est strictement positif et non déjà présent.
     *
     * @param buffer Tableau récepteur des labels.
     * @param count  Nombre actuel d'éléments déjà enregistrés.
     * @param label  Identifiant à tester et insérer.
     * @return Nouveau nombre d'éléments enregistrés dans buffer.
     */
    private int addDistinct(int[] buffer, int count, int label) {
        if (label <= 0) {
            return count;
        }
        for (int i = 0; i < count; i++) {
            if (buffer[i] == label) {
                return count;
            }
        }
        buffer[count] = label;
        return count + 1;
    }

    /**
     * Enregistre l'ensemble des combinaisons par paires (c1, c2) pour un pixel de croisement.
     *
     * @param neighbors    Tableau contenant les identifiants de cellules adjacentes.
     * @param count        Nombre de cellules valides dans neighbors.
     * @param pixelIdx     Index linéaire du pixel routier partagé.
     * @param pairToPixels Table collectrice des paires de cellules.
     */
    private void recordPairCombinations(int[] neighbors, int count, int pixelIdx,
                                        Map<Long, List<Integer>> pairToPixels) {
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                long key = encodeCellPair(neighbors[i], neighbors[j]);
                pairToPixels.computeIfAbsent(key, k -> new ArrayList<>()).add(pixelIdx);
            }
        }
    }

    /**
     * Détecte les adjacences au travers des routes larges via propagation Voronoi BFS.
     *
     * @param labelMap     Matrice d'étiquettes de cellules.
     * @param roadMask     Masque binaire de chaussée.
     * @param width        Largeur de la matrice en pixels.
     * @param height       Hauteur de la matrice en pixels.
     * @param pairToPixels Table collectrice des paires de cellules.
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
     *
     * @param labelMap Matrice d'étiquettes de cellules.
     * @param roadMask Masque binaire des routes.
     * @param width    Largeur de la matrice.
     * @param height   Hauteur de la matrice.
     * @param owner    Tableau d'appartenance de cellule pour chaque pixel de chaussée.
     * @param dist     Tableau des distances de propagation géodésique.
     * @param queue    File BFS circulaire plate.
     * @return Nombre d'éléments initiaux insérés dans la file.
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
     *
     * @param labelMap Matrice d'étiquettes de cellules.
     * @param x        Abscisse du pixel source.
     * @param y        Ordonnée du pixel source.
     * @return Identifiant de la première cellule trouvée dans le 4-voisinage (0 si aucune).
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
     *
     * @param roadMask Masque binaire des routes.
     * @param width    Largeur de la matrice.
     * @param height   Hauteur de la matrice.
     * @param owner    Tableau d'appartenance des pixels aux cellules sources.
     * @param dist     Tableau des distances de propagation.
     * @param queue    File BFS.
     * @param tail     Nombre total d'éléments dans la file à traiter.
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

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIdx = ny * width + nx;
                    if (dist[nIdx] == -1 && roadMask.get(nx, ny)) {
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
     *
     * @param roadMask     Masque binaire de chaussée.
     * @param width        Largeur en pixels.
     * @param height       Hauteur en pixels.
     * @param owner        Tableau des cellules de rattachement.
     * @param pairToPixels Table collectrice des paires de cellules.
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
     *
     * @param x            Abscisse du pixel courant.
     * @param y            Ordonnée du pixel courant.
     * @param width        Largeur en pixels.
     * @param height       Hauteur en pixels.
     * @param roadMask     Masque binaire de chaussée.
     * @param owner        Tableau des cellules de rattachement.
     * @param pairToPixels Table collectrice des paires de cellules.
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
     *
     * @param c1           Identifiant de la première cellule.
     * @param c2           Identifiant de la seconde cellule.
     * @param idx1         Index du premier pixel de chaussée.
     * @param idx2         Index du second pixel de chaussée.
     * @param pairToPixels Table collectrice des paires de cellules.
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
     * Encode une paire non-orientée de cellules (A, B) avec A &lt; B sous forme d'un entier long unique.
     *
     * @param c1 Première cellule.
     * @param c2 Seconde cellule.
     * @return Entier 64 bits combinant les deux identifiants ordonnés.
     */
    private long encodeCellPair(int c1, int c2) {
        int min = Math.min(c1, c2);
        int max = Math.max(c1, c2);
        return (((long) min) << 32) | (max & 0xFFFFFFFFL);
    }

    /**
     * Instancie les records RoadInterface à partir des pixels collectés pour chaque paire.
     *
     * @param width        Largeur de la matrice en pixels.
     * @param height       Hauteur de la matrice en pixels.
     * @param pairToPixels Table des pixels associés à chaque paire de cellules.
     * @return Liste immuable ordonnée des interfaces routières construites.
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
            long area = 0;

            for (int p : pixels) {
                int px = p % width;
                int py = p / width;
                if (!mask.get(px, py)) {
                    mask.set(px, py, true);
                    area++;
                }
            }

            interfaces.add(new RoadInterface(interfaceId++, cellA, cellB, mask, area));
        }

        return Collections.unmodifiableList(interfaces);
    }
}
