package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Calculateur linéaire en temps O(N) de la transformée de distance euclidienne exacte (EDT).
 * <p>
 * Implémente l'algorithme séparable de Meijster, Roerdink et Hesselink (2000), entièrement en arithmétique
 * entière (distances au carré exactes). Calcule pour chaque pixel où le masque
 * routier est actif sa distance euclidienne exacte au pixel non-routier le plus proche.
 */
public class EuclideanDistanceTransform {

    private static final int INF = 1_000_000_000;
    /** Distance au carré au-delà de laquelle aucun pixel cible n'existe sur la ligne ni la colonne. */
    private static final long UNREACHABLE = (long) INF * INF;
    /** Nombre de lignes par bloc de la passe horizontale parallèle. */
    private static final int ROWS_PER_BLOCK = 64;
    /** Nombre de colonnes par bande de la passe verticale parallèle. */
    private static final int COLUMNS_PER_BAND = 256;

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
        computeHorizontalPass(g, output, width, height);

        return new DistanceMap(width, height, output);
    }

    /**
     * Effectue la première passe 1D verticale (balayages haut-bas puis bas-haut) de toutes les colonnes.
     * Les colonnes sont réparties en bandes indépendantes traitées en parallèle ; dans une bande, elles sont
     * parcourues ligne par ligne pour un accès mémoire contigu (résultat identique au traitement séquentiel).
     *
     * @param roadMask Masque binaire d'entrée.
     * @param g        Tableau de sortie 1D pour les distances verticales.
     * @param width    Largeur de la grille.
     * @param height   Hauteur de la grille.
     */
    private void computeVerticalDistances(BinaryMask roadMask, int[] g, int width, int height) {
        int bands = (width + COLUMNS_PER_BAND - 1) / COLUMNS_PER_BAND;
        IntStream.range(0, bands).parallel().forEach(band -> {
            int x0 = band * COLUMNS_PER_BAND;
            int x1 = Math.min(width, x0 + COLUMNS_PER_BAND);
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = x0; x < x1; x++) {
                    int above = y == 0 ? INF : g[row - width + x];
                    g[row + x] = !roadMask.get(x, y) ? 0 : (above < INF ? above + 1 : INF);
                }
            }
            for (int y = height - 2; y >= 0; y--) {
                int row = y * width;
                for (int x = x0; x < x1; x++) {
                    int below = g[row + width + x];
                    if (below < INF && below + 1 < g[row + x]) {
                        g[row + x] = below + 1;
                    }
                }
            }
        });
    }

    /**
     * Passe horizontale (phase 2 de Meijster) parallélisée par blocs de lignes indépendants.
     *
     * @param g      Distances verticales.
     * @param output Distances euclidiennes (sortie).
     * @param width  Largeur.
     * @param height Hauteur.
     */
    private void computeHorizontalPass(int[] g, float[] output, int width, int height) {
        int blocks = (height + ROWS_PER_BLOCK - 1) / ROWS_PER_BLOCK;
        IntStream.range(0, blocks).parallel().forEach(block -> {
            int[] s = new int[width];
            int[] t = new int[width];
            long[] g2 = new long[width];
            int end = Math.min(height, (block + 1) * ROWS_PER_BLOCK);
            for (int y = block * ROWS_PER_BLOCK; y < end; y++) {
                processRow(y, g, output, width, s, t, g2);
            }
        });
    }

    /**
     * Calcule une ligne : enveloppe inférieure des paraboles {@code (x - i)² + g(i)²} en arithmétique entière
     * (balayage avant), puis évaluation du minimum en chaque colonne (balayage arrière).
     *
     * @param y        Ligne.
     * @param g        Distances verticales (0 hors masque).
     * @param output   Distances euclidiennes (sortie).
     * @param width    Largeur.
     * @param s        Abscisses des paraboles de l'enveloppe (tampon).
     * @param t        Début de la zone d'influence de chaque parabole (tampon).
     * @param g2       Carrés des distances verticales de la ligne (tampon).
     */
    private void processRow(int y, int[] g, float[] output, int width,
                            int[] s, int[] t, long[] g2) {
        int rowOffset = y * width;
        for (int x = 0; x < width; x++) {
            long d = g[rowOffset + x];
            g2[x] = d * d;
        }
        int q = 0;
        s[0] = 0;
        t[0] = 0;
        for (int u = 1; u < width; u++) {
            while (q >= 0 && parabola(t[q], s[q], g2) > parabola(t[q], u, g2)) {
                q--;
            }
            if (q < 0) {
                q = 0;
                s[0] = u;
            } else {
                long w = 1 + separator(s[q], u, g2);
                if (w < width) {
                    q++;
                    s[q] = u;
                    t[q] = (int) w;
                }
            }
        }
        for (int u = width - 1; u >= 0; u--) {
            int idx = rowOffset + u;
            if (g[idx] == 0) {
                // g == 0 exactement sur les pixels hors masque (la passe verticale y écrit 0).
                output[idx] = 0.0f;
            } else {
                long distSq = parabola(u, s[q], g2);
                output[idx] = distSq >= UNREACHABLE ? 0.0f : (float) Math.sqrt((double) distSq);
            }
            if (u == t[q]) {
                q--;
            }
        }
    }

    /**
     * Valeur en x de la parabole issue de la colonne i : {@code (x - i)² + g(i)²}.
     *
     * @param x  Colonne évaluée.
     * @param i  Colonne de la parabole.
     * @param g2 Carrés des distances verticales.
     * @return Distance euclidienne au carré.
     */
    private static long parabola(int x, int i, long[] g2) {
        long dx = x - i;
        return dx * dx + g2[i];
    }

    /**
     * Première colonne (moins un) où la parabole u devient inférieure ou égale à la parabole i (i &lt; u).
     *
     * @param i  Colonne de gauche.
     * @param u  Colonne de droite.
     * @param g2 Carrés des distances verticales.
     * @return Abscisse de séparation (division entière par défaut).
     */
    private static long separator(int i, int u, long[] g2) {
        long numerator = (long) u * u - (long) i * i + g2[u] - g2[i];
        return Math.floorDiv(numerator, 2L * (u - i));
    }
}
