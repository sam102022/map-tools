package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.ArrayList;
import java.util.List;

/**
 * Outils de composantes connexes sur masques binaires : extraction, plus grande composante, comblement des trous.
 */
public final class MaskComponents {

    private static final int[] DX8 = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY8 = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DX4 = {0, 0, -1, 1};
    private static final int[] DY4 = {-1, 1, 0, 0};

    /**
     * Composante connexe décrite par ses pixels (indices y·w + x) et sa boîte englobante.
     *
     * @param pixels   Indices linéaires des pixels.
     * @param size     Nombre de pixels.
     * @param minX     Abscisse minimale.
     * @param minY     Ordonnée minimale.
     * @param maxX     Abscisse maximale.
     * @param maxY     Ordonnée maximale.
     * @param onBorder Vrai si la composante touche le bord de l'image.
     */
    public record Component(int[] pixels, int size, int minX, int minY, int maxX, int maxY, boolean onBorder) {

        /**
         * Plus grande dimension de la boîte englobante.
         *
         * @return max(largeur, hauteur) en pixels.
         */
        public int extent() {
            return Math.max(maxX - minX + 1, maxY - minY + 1);
        }

        /**
         * Masque de la composante seule.
         *
         * @param width  Largeur de l'image.
         * @param height Hauteur de l'image.
         * @return Masque binaire.
         */
        public BinaryMask toMask(int width, int height) {
            BinaryMask out = new BinaryMask(width, height);
            for (int k = 0; k < size; k++) {
                out.set(pixels[k] % width, pixels[k] / width, true);
            }
            return out;
        }
    }

    private MaskComponents() {
        // Classe utilitaire.
    }

    /**
     * Extrait les composantes connexes d'un masque.
     *
     * @param mask          Masque.
     * @param eightConnected Vrai pour la 8-connexité, faux pour la 4-connexité.
     * @return Composantes, dans l'ordre de balayage.
     */
    public static List<Component> components(BinaryMask mask, boolean eightConnected) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int[] dx = eightConnected ? DX8 : DX4;
        int[] dy = eightConnected ? DY8 : DY4;
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        List<Component> result = new ArrayList<>();
        for (int start = 0; start < w * h; start++) {
            if (visited[start] || !mask.get(start % w, start / w)) {
                continue;
            }
            result.add(collect(mask, visited, queue, start, dx, dy));
        }
        return result;
    }

    /**
     * Parcourt en largeur la composante contenant {@code start}.
     *
     * @param mask    Masque.
     * @param visited Marqueurs de visite.
     * @param queue   Tampon partagé.
     * @param start   Pixel de départ.
     * @param dx      Décalages horizontaux du voisinage.
     * @param dy      Décalages verticaux du voisinage.
     * @return Composante.
     */
    private static Component collect(BinaryMask mask, boolean[] visited, int[] queue, int start, int[] dx, int[] dy) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        visited[start] = true;
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        while (head < tail) {
            int p = queue[head++];
            int x = p % w;
            int y = p / w;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            for (int k = 0; k < dx.length; k++) {
                int nx = x + dx[k];
                int ny = y + dy[k];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) {
                    int q = ny * w + nx;
                    if (!visited[q] && mask.get(nx, ny)) {
                        visited[q] = true;
                        queue[tail++] = q;
                    }
                }
            }
        }
        int[] pixels = new int[tail];
        System.arraycopy(queue, 0, pixels, 0, tail);
        boolean onBorder = minX == 0 || minY == 0 || maxX == w - 1 || maxY == h - 1;
        return new Component(pixels, tail, minX, minY, maxX, maxY, onBorder);
    }

    /**
     * Plus grande composante 4-connexe du masque (masque vide si aucune).
     *
     * @param mask Masque.
     * @return Masque de la plus grande composante.
     */
    public static BinaryMask largest(BinaryMask mask) {
        Component best = null;
        for (Component c : components(mask, false)) {
            if (best == null || c.size() > best.size()) {
                best = c;
            }
        }
        return best == null ? new BinaryMask(mask.getWidth(), mask.getHeight())
                : best.toMask(mask.getWidth(), mask.getHeight());
    }

    /**
     * Comble les trous du masque : composantes 4-connexes du complément qui ne touchent pas le bord.
     *
     * @param mask Masque.
     * @return Masque sans trous.
     */
    public static BinaryMask fillHoles(BinaryMask mask) {
        BinaryMask out = mask.copy();
        int w = mask.getWidth();
        for (Component hole : components(mask.not(), false)) {
            if (!hole.onBorder()) {
                for (int k = 0; k < hole.size(); k++) {
                    out.set(hole.pixels()[k] % w, hole.pixels()[k] / w, true);
                }
            }
        }
        return out;
    }
}
