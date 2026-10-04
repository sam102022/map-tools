package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.Objects;

/**
 * Calculateur linéaire en temps O(N) de la transformée de distance euclidienne exacte (EDT).
 * <p>
 * Implémente l'algorithme séparable de Meijster, Roerdink et Hesselink (2000) étendu par
 * l'enveloppe parabolique de Felzenszwalb et Huttenlocher. Calcule pour chaque pixel où le masque
 * routier est actif sa distance euclidienne exacte au pixel non-routier le plus proche.
 */
public class EuclideanDistanceTransform {

    private static final int INF = 1_000_000_000;
    private static final double LARGE_VALUE = 1e12;

    /**
     * Calcule la carte de distance euclidienne exacte sur le masque routier fourni.
     *
     * @param roadMask Masque binaire du réseau routier (non nul).
     * @return Matrice {@link DistanceMap} contenant les distances euclidiennes en pixels.
     */
    public DistanceMap compute(BinaryMask roadMask) {
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        int width = roadMask.getWidth();
        int height = roadMask.getHeight();

        int[] g = new int[width * height];
        computeVerticalDistances(roadMask, g, width, height);

        float[] output = new float[width * height];
        computeHorizontalPass(roadMask, g, output, width, height);

        return new DistanceMap(width, height, output);
    }

    /**
     * Effectue la première passe 1D verticale pour chaque colonne indépendamment.
     *
     * @param roadMask Masque binaire d'entrée.
     * @param g        Tableau de sortie 1D pour les distances verticales.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     */
    private void computeVerticalDistances(BinaryMask roadMask, int[] g, int width, int height) {
        for (int x = 0; x < width; x++) {
            computeColumnVerticalPass(roadMask, g, x, width, height);
        }
    }

    /**
     * Traite les deux balayages verticaux (haut-bas et bas-haut) pour une colonne donnée.
     *
     * @param roadMask Masque binaire d'entrée.
     * @param g        Tableau des distances verticales.
     * @param x        Index de colonne courante.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     */
    private void computeColumnVerticalPass(BinaryMask roadMask, int[] g, int x, int width, int height) {
        int dist = INF;
        for (int y = 0; y < height; y++) {
            if (!roadMask.get(x, y)) {
                dist = 0;
            } else if (dist < INF) {
                dist++;
            }
            g[y * width + x] = dist;
        }

        dist = INF;
        for (int y = height - 1; y >= 0; y--) {
            int idx = y * width + x;
            if (!roadMask.get(x, y)) {
                dist = 0;
            } else if (dist < INF) {
                dist++;
            }
            if (dist < g[idx]) {
                g[idx] = dist;
            }
        }
    }

    /**
     * Effectue la seconde passe 1D horizontale pour chaque ligne via l'enveloppe parabolique.
     *
     * @param roadMask Masque binaire d'entrée.
     * @param g        Distances verticales précalculées.
     * @param output   Tableau récepteur des distances euclidiennes finales.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     */
    private void computeHorizontalPass(BinaryMask roadMask, int[] g, float[] output, int width, int height) {
        int[] v = new int[width];
        double[] z = new double[width + 1];
        double[] f = new double[width];

        for (int y = 0; y < height; y++) {
            processRow(y, roadMask, g, output, width, v, z, f);
        }
    }

    /**
     * Traite une ligne horizontale complète par minimisation d'enveloppe parabolique.
     *
     * @param y        Index de la ligne courante.
     * @param roadMask Masque binaire d'entrée.
     * @param g        Distances verticales.
     * @param output   Tableau de sortie float[].
     * @param width    Largeur de la grille.
     * @param v        Buffer d'emplacements des paraboles actives.
     * @param z        Buffer des points de transition d'enveloppe.
     * @param f        Buffer des valeurs f(x) = g(x, y)^2.
     */
    private void processRow(int y, BinaryMask roadMask, int[] g, float[] output, int width,
                            int[] v, double[] z, double[] f) {
        int rowOffset = y * width;
        for (int x = 0; x < width; x++) {
            int d = g[rowOffset + x];
            f[x] = (d >= INF) ? LARGE_VALUE : (double) d * d;
        }

        int k = 0;
        v[0] = 0;
        z[0] = -LARGE_VALUE;
        z[1] = LARGE_VALUE;

        for (int q = 1; q < width; q++) {
            k = updateEnvelope(q, k, v, z, f);
        }

        evaluateRowDistances(y, roadMask, output, width, v, z, f);
    }

    /**
     * Met à jour la file monotone des paraboles actives pour un nouvel index q.
     *
     * @param q Index de colonne candidate.
     * @param k Index courant du sommet de pile des paraboles actives.
     * @param v Tableau des positions de paraboles.
     * @param z Tableau des limites d'intervalles.
     * @param f Tableau des valeurs f(x).
     * @return Nouvel index k après insertion de q.
     */
    private int updateEnvelope(int q, int k, int[] v, double[] z, double[] f) {
        double s = computeIntersection(q, v[k], f);
        while (k > 0 && s <= z[k]) {
            k--;
            s = computeIntersection(q, v[k], f);
        }
        k++;
        v[k] = q;
        z[k] = s;
        z[k + 1] = LARGE_VALUE;
        return k;
    }

    /**
     * Calcule le point d'intersection horizontal entre deux paraboles p et q.
     *
     * @param q Index de la seconde parabole (q &gt; p).
     * @param p Index de la première parabole.
     * @param f Tableau des carrés de distance.
     * @return Abscisse flottante d'intersection s.
     */
    private double computeIntersection(int q, int p, double[] f) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * (q - p));
    }

    /**
     * Évalue la distance euclidienne finale pour tous les pixels de la ligne y.
     *
     * @param y        Index de ligne.
     * @param roadMask Masque binaire.
     * @param output   Tableau de sortie float[].
     * @param width    Largeur de la grille.
     * @param v        Positions des paraboles actives.
     * @param z        Limites d'intervalles.
     * @param f        Valeurs au carré f(x).
     */
    private void evaluateRowDistances(int y, BinaryMask roadMask, float[] output, int width,
                                      int[] v, double[] z, double[] f) {
        int rowOffset = y * width;
        int k = 0;
        for (int x = 0; x < width; x++) {
            int idx = rowOffset + x;
            if (!roadMask.get(x, y)) {
                output[idx] = 0.0f;
                continue;
            }
            while (z[k + 1] < x) {
                k++;
            }
            double dx = x - v[k];
            double distSq = dx * dx + f[v[k]];
            output[idx] = (distSq >= LARGE_VALUE) ? 0.0f : (float) Math.sqrt(distSq);
        }
    }
}
