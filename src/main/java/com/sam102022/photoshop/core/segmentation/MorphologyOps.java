package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Opérations de morphologie mathématique 2D sur BinaryMask (dilatation, érosion, ouverture, fermeture, composantes connexes).
 */
public final class MorphologyOps {

    private MorphologyOps() {
    }

    /**
     * Remplit uniquement les petits trous internes d'un masque binaire (ex: l'intÃ©rieur d'un rond-point).
     * Les trous sont dÃ©finis comme des composantes de fond non connectÃ©es aux bords et dont l'aire est <= maxArea.
     * @param mask Le masque source
     * @param maxArea L'aire maximale en pixels pour considÃ©rer qu'un trou doit Ãªtre rempli.
     */
    public static BinaryMask fillSmallHoles(BinaryMask mask, int maxArea) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas Ãªtre null.");
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        boolean[] visited = new boolean[w * h];
        BinaryMask result = mask.copy();

        Queue<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            if (!mask.get(x, 0)) { queue.offer(x); visited[x] = true; }
            if (!mask.get(x, h - 1)) { int idx = (h - 1) * w + x; queue.offer(idx); visited[idx] = true; }
        }
        for (int y = 0; y < h; y++) {
            if (!mask.get(0, y)) { queue.offer(y * w); visited[y * w] = true; }
            if (!mask.get(w - 1, y)) { int idx = y * w + (w - 1); queue.offer(idx); visited[idx] = true; }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            int cx = cur % w;
            int cy = cur / w;
            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int nIdx = ny * w + nx;
                    if (!mask.get(nx, ny) && !visited[nIdx]) {
                        visited[nIdx] = true;
                        queue.offer(nIdx);
                    }
                }
            }
        }

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (!mask.get(x, y) && !visited[idx]) {
                    java.util.List<Integer> holePixels = new java.util.ArrayList<>();
                    Queue<Integer> holeQueue = new ArrayDeque<>();

                    holeQueue.offer(idx);
                    visited[idx] = true;
                    holePixels.add(idx);

                    while(!holeQueue.isEmpty()) {
                        int cur = holeQueue.poll();
                        int cx = cur % w;
                        int cy = cur / w;
                        for (int i = 0; i < 4; i++) {
                            int nx = cx + dx[i];
                            int ny = cy + dy[i];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int nIdx = ny * w + nx;
                                if (!mask.get(nx, ny) && !visited[nIdx]) {
                                    visited[nIdx] = true;
                                    holeQueue.offer(nIdx);
                                    holePixels.add(nIdx);
                                }
                            }
                        }
                    }
                    if (holePixels.size() <= maxArea) {
                        for (int p : holePixels) {
                            result.set(p % w, p / w, true);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Dilate le territoire de faÃ§on gÃ©odÃ©sique jusqu'au bord extÃ©rieur des routes.
     * Le territoire s'Ã©tend librement dans les zones vierges jusqu'Ã  maxDistance pour rattraper la route.
     * S'il rencontre une route, il l'englobe intÃ©gralement mais s'arrÃªte net sur son bord extÃ©rieur.
     */
    public static BinaryMask snapToOuterEdge(BinaryMask territory, BinaryMask roads, int maxDistance) {
        if (territory == null || roads == null) throw new IllegalArgumentException();
        int w = territory.getWidth();
        int h = territory.getHeight();
        BinaryMask result = territory.copy();

        int[] dist = new int[w * h];
        java.util.Arrays.fill(dist, Integer.MAX_VALUE);
        boolean[] touched = new boolean[w * h];

        Queue<Integer> queue = new ArrayDeque<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (territory.get(x, y)) {
                    boolean isBoundary = false;
                    if (x > 0 && !territory.get(x-1, y)) isBoundary = true;
                    else if (x < w-1 && !territory.get(x+1, y)) isBoundary = true;
                    else if (y > 0 && !territory.get(x, y-1)) isBoundary = true;
                    else if (y < h-1 && !territory.get(x, y+1)) isBoundary = true;

                    if (isBoundary) {
                        int idx = y * w + x;
                        queue.offer(idx);
                        dist[idx] = 0;
                        touched[idx] = roads.get(x, y);
                    }
                }
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int cx = idx % w;
            int cy = idx / w;
            int cDist = dist[idx];
            boolean cTouched = touched[idx];

            if (cDist >= maxDistance) continue;

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int nIdx = ny * w + nx;
                    if (!result.get(nx, ny)) {
                        boolean isRoad = roads.get(nx, ny);

                        if (isRoad) {
                            result.set(nx, ny, true);
                            dist[nIdx] = cDist + 1;
                            touched[nIdx] = true;
                            queue.offer(nIdx);
                        } else if (!cTouched) {
                            result.set(nx, ny, true);
                            dist[nIdx] = cDist + 1;
                            touched[nIdx] = false;
                            queue.offer(nIdx);
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * Applique une opÃ©ration de dilatation morphologique 2D sur un masque binaire.
     * Chaque pixel actif étend son empreinte dans une fenêtre carrée de demi-largeur {@code radius}.
     *
     * @param mask   Masque binaire d'entrée.
     * @param radius Rayon de dilatation en pixels. Si {@code <= 0}, une copie conforme est retournée.
     * @return Nouveau masque binaire dilaté.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BinaryMask dilate(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(x, y)) {
                    dilatePixel(result, x, y, radius, w, h);
                }
            }
        }
        return result;
    }

    /**
     * Active les pixels voisins d'un pixel source dans une fenêtre carrée de rayon donné.
     *
     * @param result Masque binaire de destination.
     * @param x      Coordonnée X du pixel source.
     * @param y      Coordonnée Y du pixel source.
     * @param radius Rayon de dilatation en pixels.
     * @param w      Largeur du masque.
     * @param h      Hauteur du masque.
     */
    private static void dilatePixel(BinaryMask result, int x, int y, int radius, int w, int h) {
        int minX = Math.max(0, x - radius);
        int maxX = Math.min(w - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(h - 1, y + radius);

        for (int ny = minY; ny <= maxY; ny++) {
            for (int nx = minX; nx <= maxX; nx++) {
                result.set(nx, ny, true);
            }
        }
    }

    /**
     * Applique une opération d'érosion morphologique 2D sur un masque binaire.
     * Un pixel actif n'est conservé que si l'ensemble de son voisinage carré de demi-largeur {@code radius} est actif.
     *
     * @param mask   Masque binaire d'entrée.
     * @param radius Rayon d'érosion en pixels. Si {@code <= 0}, une copie conforme est retournée.
     * @return Nouveau masque binaire érodé.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BinaryMask erode(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(x, y) && areAllNeighborsActive(mask, x, y, radius, w, h)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Vérifie si tous les pixels d'un voisinage carré de rayon donné autour de (x, y) sont actifs.
     *
     * @param mask   Masque binaire source.
     * @param x      Coordonnée X du centre.
     * @param y      Coordonnée Y du centre.
     * @param radius Rayon de la fenêtre carrée.
     * @param w      Largeur du masque.
     * @param h      Hauteur du masque.
     * @return Vrai si toute la fenêtre de rayon est contenue dans l'image et composée de pixels actifs.
     */
    private static boolean areAllNeighborsActive(BinaryMask mask, int x, int y, int radius, int w, int h) {
        if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) {
            return false;
        }

        for (int ny = y - radius; ny <= y + radius; ny++) {
            for (int nx = x - radius; nx <= x + radius; nx++) {
                if (!mask.get(nx, ny)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static final int[] DX4 = {1, -1, 0, 0};
    private static final int[] DY4 = {0, 0, 1, -1};

    /**
     * Applique une opération de fermeture morphologique 2D (dilatation suivie d'une érosion).
     * Permet de combler les trous étroits et de connecter les composantes disjointes proches.
     *
     * @param mask   Masque binaire d'entrée.
     * @param radius Rayon de fermeture en pixels. Si {@code <= 0}, une copie est retournée.
     * @return Nouveau masque binaire fermé.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BinaryMask close(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        return erode(dilate(mask, radius), radius);
    }

    /**
     * Lisse légèrement les contours d'un masque par vote local conservateur.
     * Les pixels de route sont conservés dès qu'ils ont deux voisins actifs, ce qui protège
     * les bandes étroites et les segments d'un pixel; un pixel extérieur n'est ajouté que si
     * six des huit voisins sont actifs. Une seule passe arrondit les petites irrégularités.
     */
    public static BinaryMask smoothEdges(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        int width = mask.getWidth();
        int height = mask.getHeight();
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int neighbors = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if ((dx != 0 || dy != 0) && mask.get(x + dx, y + dy)) {
                            neighbors++;
                        }
                    }
                }
                boolean active = mask.get(x, y)
                        ? neighbors >= 2
                        : neighbors >= 6;
                result.set(x, y, active);
            }
        }
        return result;
    }

    /**
     * Applique une opération d'ouverture morphologique 2D (érosion suivie d'une dilatation).
     * Permet d'éliminer le bruit isolé et les fins filaments sans altérer la taille globale de la zone.
     *
     * @param mask   Masque binaire d'entrée.
     * @param radius Rayon d'ouverture en pixels. Si {@code <= 0}, une copie est retournée.
     * @return Nouveau masque binaire ouvert.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BinaryMask open(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        return dilate(erode(mask, radius), radius);
    }

    /**
     * Extrait et conserve uniquement la composante 4-connexe de plus grande superficie dans le masque.
     * Toutes les composantes secondaires disjointes ainsi que les artefacts isolés sont éliminés.
     *
     * @param mask Masque binaire d'origine.
     * @return Nouveau masque binaire ne contenant que la plus grande composante connexe.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BinaryMask keepLargestComponent(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }

        int seedIdx = findLargestComponentSeed(mask);
        int w = mask.getWidth();
        int h = mask.getHeight();
        if (seedIdx == -1) {
            return new BinaryMask(w, h);
        }

        BinaryMask largest = new BinaryMask(w, h);
        exploreComponentBfs(mask, new boolean[w * h], seedIdx, largest);
        return largest;
    }

    /**
     * Recherche l'indice linéaire du pixel de départ de la plus grande composante connexe du masque.
     *
     * @param mask Masque binaire d'entrée.
     * @return Indice linéaire du point de départ de la plus grande composante, ou -1 si le masque est vide.
     */
    private static int findLargestComponentSeed(BinaryMask mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        boolean[] visited = new boolean[w * h];
        int maxComponentSize = 0;
        int maxStartIdx = -1;

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int idx = rowOffset + x;
                if (mask.get(x, y) && !visited[idx]) {
                    int size = exploreComponentBfs(mask, visited, idx, null);
                    if (size > maxComponentSize) {
                        maxComponentSize = size;
                        maxStartIdx = idx;
                    }
                }
            }
        }

        return maxStartIdx;
    }

    /**
     * Explore une composante 4-connexe par parcours en largeur (BFS).
     *
     * @param mask        Masque source.
     * @param visited     Tableau des pixels déjà visités.
     * @param startIdx    Index linéaire du pixel de départ.
     * @param destination Masque cible où activer les pixels explorés (ou null pour simple comptage).
     * @return Nombre de pixels appartenant à cette composante.
     */
    private static int exploreComponentBfs(BinaryMask mask, boolean[] visited, int startIdx, BinaryMask destination) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(startIdx);
        visited[startIdx] = true;
        if (destination != null) {
            destination.set(startIdx % w, startIdx / w, true);
        }

        int size = 0;
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            size++;
            expandNeighbors(mask, visited, cur, queue, destination, w, h);
        }
        return size;
    }

    /**
     * Propage l'exploration BFS aux 4 voisins cardinaux immédiats s'ils sont actifs et non visités.
     *
     * @param mask        Masque source.
     * @param visited     Tableau des pixels visités.
     * @param currentIdx  Index du pixel courant.
     * @param queue       File BFS.
     * @param destination Masque cible optionnel à alimenter.
     * @param w           Largeur du masque.
     * @param h           Hauteur du masque.
     */
    private static void expandNeighbors(BinaryMask mask, boolean[] visited, int currentIdx,
                                        Queue<Integer> queue, BinaryMask destination, int w, int h) {
        int cx = currentIdx % w;
        int cy = currentIdx / w;

        for (int i = 0; i < 4; i++) {
            int nx = cx + DX4[i];
            int ny = cy + DY4[i];
            int nIdx = ny * w + nx;
            if (isWithinBounds(nx, ny, w, h) && mask.get(nx, ny) && !visited[nIdx]) {
                visited[nIdx] = true;
                queue.offer(nIdx);
                if (destination != null) {
                    destination.set(nx, ny, true);
                }
            }
        }
    }

    /**
     * Vérifie si les coordonnées spécifiées se situent à l'intérieur de la grille de l'image.
     *
     * @param x Coordonnée X.
     * @param y Coordonnée Y.
     * @param w Largeur.
     * @param h Hauteur.
     * @return Vrai si (x, y) est dans [0, w[ et [0, h[.
     */
    private static boolean isWithinBounds(int x, int y, int w, int h) {
        return x >= 0 && x < w && y >= 0 && y < h;
    }
}
