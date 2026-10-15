package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Extracteur de contour sub-pixel continu 2D par Marching Squares et rééchantillonnage curviligne.
 * Comble les cavités intérieures résiduelles, extrait la boucle extérieure principale à isovaleur 0.5
 * et rééchantillonne à pas spatial régulier (1.0 px par défaut).
 */
public class SubpixelContourExtractor {

    /**
     * Initialise une nouvelle instance de l'extracteur de contour sub-pixel Marching Squares 2D.
     */
    public SubpixelContourExtractor() {
    }

    /**
     * Extrait le contour sub-pixel fermé lissé à pas régulier depuis un masque binaire.
     *
     * @param mask Masque binaire d'entrée.
     * @param step Pas de rééchantillonnage uniforme en pixels (typiquement 1.0 px).
     * @return Liste ordonnée des points sub-pixels formant la boucle fermée extérieure.
     */
    public List<PixelPoint> extract(BinaryMask mask, double step) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être nul.");
        }
        if (step <= 0.0) {
            throw new IllegalArgumentException("Le pas de rééchantillonnage doit être strictement positif.");
        }

        BinaryMask filled = fillHoles(mask);
        List<PixelPoint> rawLoop = extractMarchingSquaresLoop(filled);
        if (rawLoop.isEmpty()) {
            return List.of();
        }

        return resampleCurvilinear(rawLoop, step);
    }

    /**
     * Comble les cavités intérieures par marquage BFS de l'extérieur infini.
     *
     * @param mask Masque binaire avec trous intérieurs éventuels.
     * @return Masque binaire comblé étanche.
     */
    private BinaryMask fillHoles(BinaryMask mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        boolean[] reached = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        // Ensemencement depuis les 4 bordures
        for (int x = 0; x < w; x++) {
            enqueueIfBackground(mask, reached, queue, x, 0, w);
            enqueueIfBackground(mask, reached, queue, x, h - 1, w);
        }
        for (int y = 0; y < h; y++) {
            enqueueIfBackground(mask, reached, queue, 0, y, w);
            enqueueIfBackground(mask, reached, queue, w - 1, y, w);
        }

        // Inondation 4-connexe
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int x = idx % w;
            int y = idx / w;

            if (x > 0) enqueueIfBackground(mask, reached, queue, x - 1, y, w);
            if (x < w - 1) enqueueIfBackground(mask, reached, queue, x + 1, y, w);
            if (y > 0) enqueueIfBackground(mask, reached, queue, x, y - 1, w);
            if (y < h - 1) enqueueIfBackground(mask, reached, queue, x, y + 1, w);
        }

        // Tout pixel non atteint depuis l'extérieur appartient au territoire consolidé
        BinaryMask result = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!reached[y * w + x]) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Ajoute un pixel d'arrière-plan dans la file de propagation BFS s'il n'est pas encore exploré.
     *
     * @param mask    Masque binaire.
     * @param reached Tableau de visite des pixels.
     * @param queue   File BFS.
     * @param x       Abscisse du pixel.
     * @param y       Ordonnée du pixel.
     * @param w       Largeur de l'image.
     */
    private void enqueueIfBackground(BinaryMask mask, boolean[] reached, ArrayDeque<Integer> queue, int x, int y, int w) {
        int idx = y * w + x;
        if (!reached[idx] && !mask.get(x, y)) {
            reached[idx] = true;
            queue.add(idx);
        }
    }

    /**
     * Extrait les segments de Marching Squares sur la grille avec marge de 1 pixel et retourne la boucle maximale.
     *
     * @param mask Masque binaire comblé.
     * @return Liste ordonnée des sommets sub-pixels de la plus grande boucle extérieure.
     */
    private List<PixelPoint> extractMarchingSquaresLoop(BinaryMask mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int pw = w + 2;
        int ph = h + 2;

        Map<Long, Long> edgeTransitions = new HashMap<>();

        for (int y = 0; y < ph - 1; y++) {
            for (int x = 0; x < pw - 1; x++) {
                int code = computeCellCode(mask, x, y, w, h);
                addSegmentsForCode(code, x, y, pw, edgeTransitions);
            }
        }

        return findLongestLoop(edgeTransitions, pw);
    }

    /**
     * Calcule l'indice de cas Marching Squares (0 à 15) pour la cellule 2x2 à la position (x, y).
     *
     * @param mask Masque binaire d'origine.
     * @param x    Abscisse dans la grille avec marge.
     * @param y    Ordonnée dans la grille avec marge.
     * @param w    Largeur du masque sans marge.
     * @param h    Hauteur du masque sans marge.
     * @return Code binaire sur 4 bits (tl | tr &lt;&lt; 1 | br &lt;&lt; 2 | bl &lt;&lt; 3).
     */
    private int computeCellCode(BinaryMask mask, int x, int y, int w, int h) {
        int v0 = getPaddedValue(mask, x, y, w, h);
        int v1 = getPaddedValue(mask, x + 1, y, w, h);
        int v2 = getPaddedValue(mask, x + 1, y + 1, w, h);
        int v3 = getPaddedValue(mask, x, y + 1, w, h);
        return v0 | (v1 << 1) | (v2 << 2) | (v3 << 3);
    }

    /**
     * Récupère la valeur binaire (0 ou 1) d'un point dans la grille avec marge de 1 pixel.
     *
     * @param mask Masque binaire.
     * @param px   Abscisse avec marge.
     * @param py   Ordonnée avec marge.
     * @param w    Largeur du masque.
     * @param h    Hauteur du masque.
     * @return 1 si le pixel appartient au masque, 0 sinon.
     */
    private int getPaddedValue(BinaryMask mask, int px, int py, int w, int h) {
        int x = px - 1;
        int y = py - 1;
        if (x >= 0 && x < w && y >= 0 && y < h) {
            return mask.get(x, y) ? 1 : 0;
        }
        return 0;
    }

    /**
     * Encode de façon unique une arête de cellule par un identifiant 64-bit compact.
     *
     * @param x           Abscisse de la cellule.
     * @param y           Ordonnée de la cellule.
     * @param orientation 0 pour arête horizontale (x+0.5, y), 1 pour verticale (x, y+0.5).
     * @param pw          Largeur de grille avec marge.
     * @return Clé 64-bit unique identifiant le milieu d'arête.
     */
    private long encodeEdge(int x, int y, int orientation, int pw) {
        return (((long) (y * (pw + 1) + x)) << 1) | orientation;
    }

    /**
     * Enregistre les segments orientés reliant les milieux d'arêtes selon le cas Marching Squares.
     *
     * @param code        Code de cas (0 à 15).
     * @param x           Abscisse de cellule.
     * @param y           Ordonnée de cellule.
     * @param pw          Largeur de grille.
     * @param transitions Table de routage sommet de départ -&gt; sommet d'arrivée.
     */
    private void addSegmentsForCode(int code, int x, int y, int pw, Map<Long, Long> transitions) {
        long top = encodeEdge(x, y, 0, pw);
        long right = encodeEdge(x + 1, y, 1, pw);
        long bottom = encodeEdge(x, y + 1, 0, pw);
        long left = encodeEdge(x, y, 1, pw);

        switch (code) {
            case 1 -> transitions.put(top, left);
            case 2 -> transitions.put(right, top);
            case 3 -> transitions.put(right, left);
            case 4 -> transitions.put(bottom, right);
            case 5 -> { transitions.put(top, left); transitions.put(bottom, right); }
            case 6 -> transitions.put(bottom, top);
            case 7 -> transitions.put(bottom, left);
            case 8 -> transitions.put(left, bottom);
            case 9 -> transitions.put(top, bottom);
            case 10 -> { transitions.put(right, top); transitions.put(left, bottom); }
            case 11 -> transitions.put(right, bottom);
            case 12 -> transitions.put(left, right);
            case 13 -> transitions.put(top, right);
            case 14 -> transitions.put(left, top);
            default -> { /* 0 et 15 : aucun segment */ }
        }
    }

    /**
     * Parcourt les segments chaînés et sélectionne la boucle fermée de périmètre maximal.
     *
     * @param transitions Table de chaînage des arêtes.
     * @param pw          Largeur de grille avec marge.
     * @return Liste ordonnée des points de la boucle principale.
     */
    private List<PixelPoint> findLongestLoop(Map<Long, Long> transitions, int pw) {
        Set<Long> visited = new HashSet<>();
        List<PixelPoint> bestLoop = new ArrayList<>();

        for (Long startEdge : transitions.keySet()) {
            if (visited.contains(startEdge)) {
                continue;
            }

            List<PixelPoint> currentLoop = traceLoop(startEdge, transitions, visited, pw);
            if (currentLoop.size() > bestLoop.size()) {
                bestLoop = currentLoop;
            }
        }
        return bestLoop;
    }

    /**
     * Suit de manière déterministe les transitions orientées pour former un polygone fermé continu.
     *
     * @param startEdge   Arête de départ.
     * @param transitions Table des transitions d'arêtes.
     * @param visited     Ensemble des arêtes visitées.
     * @param pw          Largeur de grille.
     * @return Boucle fermée ordonnée des sommets géométriques.
     */
    private List<PixelPoint> traceLoop(Long startEdge, Map<Long, Long> transitions, Set<Long> visited, int pw) {
        List<PixelPoint> loop = new ArrayList<>();
        Long current = startEdge;

        while (current != null && !visited.contains(current)) {
            visited.add(current);
            loop.add(decodeEdgePoint(current, pw));
            current = transitions.get(current);
            if (current != null && current.equals(startEdge)) {
                break;
            }
        }
        return loop;
    }

    /**
     * Décode une clé d'arête en point sub-pixel dans le repère local (avec retrait de la marge de 1 px).
     *
     * @param edgeKey Clé 64-bit de l'arête.
     * @param pw      Largeur de grille.
     * @return Point géométrique sub-pixel (x, y).
     */
    private PixelPoint decodeEdgePoint(long edgeKey, int pw) {
        int orientation = (int) (edgeKey & 1L);
        long pos = edgeKey >> 1;
        int x = (int) (pos % (pw + 1));
        int y = (int) (pos / (pw + 1));

        // Décodage avec retrait de la marge de 1.0 px
        if (orientation == 0) {
            return new PixelPoint((x + 0.5) - 1.0, y - 1.0);
        } else {
            return new PixelPoint(x - 1.0, (y + 0.5) - 1.0);
        }
    }

    /**
     * Rééchantillonne la boucle fermée à pas curviligne équidistant.
     *
     * @param rawLoop Boucle discrète issue du Marching Squares.
     * @param step    Pas de rééchantillonnage uniforme en pixels.
     * @return Liste ordonnée immuable des points rééchantillonnés.
     */
    private List<PixelPoint> resampleCurvilinear(List<PixelPoint> rawLoop, double step) {
        int n = rawLoop.size();
        if (n < 3) {
            return rawLoop;
        }

        // Calcul des distances cumulées le long de la boucle
        double[] dists = new double[n + 1];
        dists[0] = 0.0;
        for (int i = 0; i < n; i++) {
            PixelPoint p1 = rawLoop.get(i);
            PixelPoint p2 = rawLoop.get((i + 1) % n);
            dists[i + 1] = dists[i] + Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
        }

        double totalLen = dists[n];
        int m = Math.max(3, (int) Math.floor(totalLen / step));
        List<PixelPoint> resampled = new ArrayList<>(m);

        int segIdx = 0;
        for (int j = 0; j < m; j++) {
            double targetDist = j * (totalLen / m);
            while (segIdx < n && dists[segIdx + 1] < targetDist) {
                segIdx++;
            }
            if (segIdx >= n) {
                segIdx = n - 1;
            }

            PixelPoint p0 = rawLoop.get(segIdx);
            PixelPoint p1 = rawLoop.get((segIdx + 1) % n);
            double segLen = dists[segIdx + 1] - dists[segIdx];
            double alpha = segLen > 1e-9 ? (targetDist - dists[segIdx]) / segLen : 0.0;

            resampled.add(new PixelPoint(
                    p0.x() + alpha * (p1.x() - p0.x()),
                    p0.y() + alpha * (p1.y() - p0.y())
            ));
        }

        return Collections.unmodifiableList(resampled);
    }
}
