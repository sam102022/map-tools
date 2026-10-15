package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.io.ImagePixels;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Capture Google « Routes seules » ({@code 06_routes_seules.png}) : routes blanches sur fond noir, alignée au
 * pixel sur les autres captures et de même largeur de chaussée que la carte détourée.
 * <p>
 * Le niveau de gris, normalisé dans [0, 1], est directement la part de chaussée de chaque pixel
 * (anti-crénelage) : il fournit à la fois le masque routier (seuil 0,5) et le champ continu dont l'iso-ligne
 * 0,5 est le bord de chaussée au sub-pixel. Deux nettoyages en niveaux de gris, qui laissent intacts les bords
 * rectilignes, sont appliqués :
 * <ul>
 *   <li>une fermeture (rayon {@value #LANE_LINE_RADIUS} px) efface les lignes sombres de 2 px au plus tracées
 *       à l'intérieur des chaussées (marquages), sans fusionner les chaussées séparées par un terre-plein ;</li>
 *   <li>une ouverture (rayon {@value #FOOTPATH_RADIUS} px) efface les traits clairs de moins de 3 px
 *       (sentiers, limites administratives), qui morcelleraient les îlots.</li>
 * </ul>
 */
public final class GoogleRoadsImage {

    /** Nom de fichier de la capture produite par {@code capture_territoires.js}. */
    public static final String DEFAULT_FILE_NAME = "06_routes_seules.png";
    /** Rayon (px) de la fermeture effaçant les marquages sombres intérieurs aux chaussées. */
    public static final int LANE_LINE_RADIUS = 1;
    /** Rayon (px) de l'ouverture effaçant les traits clairs trop fins pour être des routes. */
    public static final int FOOTPATH_RADIUS = 1;

    private final int width;
    private final int height;
    private final float[] values;

    /**
     * Construit le champ nettoyé.
     *
     * @param width  Largeur.
     * @param height Hauteur.
     * @param values Part de chaussée de chaque pixel, dans [0, 1] (ligne par ligne).
     */
    private GoogleRoadsImage(int width, int height, float[] values) {
        this.width = width;
        this.height = height;
        this.values = values;
    }

    /**
     * Lit une capture « Routes seules » et la nettoie.
     *
     * @param image Capture (routes claires sur fond sombre, non nulle).
     * @return Champ de chaussée nettoyé.
     */
    public static GoogleRoadsImage from(BufferedImage image) {
        return from(image, null);
    }

    /**
     * Lit une capture « Routes seules », en retire les pixels exclus (par exemple les voies rapides repérées par
     * leur teinte sur la carte, que le pipeline traite à part) puis la nettoie : les liserés résiduels des voies
     * retirées disparaissent avec l'ouverture.
     *
     * @param image    Capture (routes claires sur fond sombre, non nulle).
     * @param excluded Pixels à retirer (mêmes dimensions), ou null.
     * @return Champ de chaussée nettoyé.
     * @throws IllegalArgumentException si les dimensions de {@code excluded} diffèrent de celles de la capture.
     */
    public static GoogleRoadsImage from(BufferedImage image, BinaryMask excluded) {
        Objects.requireNonNull(image, "image ne doit pas être nulle.");
        if (excluded != null && (excluded.getWidth() != image.getWidth() || excluded.getHeight() != image.getHeight())) {
            throw new IllegalArgumentException("Le masque d'exclusion doit avoir les dimensions de la capture.");
        }
        int w = image.getWidth();
        int h = image.getHeight();
        float[] gray = new float[w * h];
        int[] pixels = ImagePixels.argb(image);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = pixels[y * w + x];
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[y * w + x] = excluded != null && excluded.get(x, y) ? 0.0f : (r + g + b) / (3.0f * 255.0f);
            }
        }
        float[] closed = erode(dilate(gray, w, h, LANE_LINE_RADIUS), w, h, LANE_LINE_RADIUS);
        float[] opened = dilate(erode(closed, w, h, FOOTPATH_RADIUS), w, h, FOOTPATH_RADIUS);
        return new GoogleRoadsImage(w, h, opened);
    }

    /**
     * Masque routier : pixels dont la part de chaussée atteint 0,5.
     *
     * @return Masque binaire des routes.
     */
    public BinaryMask toMask() {
        BinaryMask mask = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (values[y * width + x] >= 0.5f) {
                    mask.set(x, y, true);
                }
            }
        }
        return mask;
    }

    /**
     * Part de chaussée d'un pixel.
     *
     * @param x Abscisse.
     * @param y Ordonnée.
     * @return Valeur dans [0, 1].
     */
    public float value(int x, int y) {
        return values[y * width + x];
    }

    /**
     * @return Largeur de la capture.
     */
    public int width() {
        return width;
    }

    /**
     * @return Hauteur de la capture.
     */
    public int height() {
        return height;
    }

    /**
     * Dilatation en niveaux de gris (maximum) par un carré de demi-côté r, calculée de façon séparable.
     *
     * @param v Valeurs.
     * @param w Largeur.
     * @param h Hauteur.
     * @param r Demi-côté (px).
     * @return Valeurs dilatées.
     */
    static float[] dilate(float[] v, int w, int h, int r) {
        return filter(v, w, h, r, true);
    }

    /**
     * Érosion en niveaux de gris (minimum) par un carré de demi-côté r, calculée de façon séparable.
     *
     * @param v Valeurs.
     * @param w Largeur.
     * @param h Hauteur.
     * @param r Demi-côté (px).
     * @return Valeurs érodées.
     */
    static float[] erode(float[] v, int w, int h, int r) {
        return filter(v, w, h, r, false);
    }

    /**
     * Filtre min/max séparable (horizontal puis vertical, lignes traitées en parallèle), bords répliqués.
     *
     * @param v   Valeurs.
     * @param w   Largeur.
     * @param h   Hauteur.
     * @param r   Demi-côté (px).
     * @param max Vrai pour le maximum, faux pour le minimum.
     * @return Valeurs filtrées.
     */
    private static float[] filter(float[] v, int w, int h, int r, boolean max) {
        float[] tmp = new float[v.length];
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                tmp[row + x] = extremum(v, row + Math.max(0, x - r), row + Math.min(w - 1, x + r), 1, max);
            }
        });
        float[] out = new float[v.length];
        IntStream.range(0, h).parallel().forEach(y -> {
            int y0 = Math.max(0, y - r);
            int y1 = Math.min(h - 1, y + r);
            for (int x = 0; x < w; x++) {
                out[y * w + x] = extremum(tmp, y0 * w + x, y1 * w + x, w, max);
            }
        });
        return out;
    }

    /**
     * Maximum ou minimum des valeurs d'indices {@code from, from + step, …, to}.
     *
     * @param v    Valeurs.
     * @param from Premier indice.
     * @param to   Dernier indice (inclus).
     * @param step Pas.
     * @param max  Vrai pour le maximum, faux pour le minimum.
     * @return Extremum.
     */
    private static float extremum(float[] v, int from, int to, int step, boolean max) {
        float best = v[from];
        if (max) {
            for (int k = from + step; k <= to; k += step) {
                best = Math.max(best, v[k]);
            }
        } else {
            for (int k = from + step; k <= to; k += step) {
                best = Math.min(best, v[k]);
            }
        }
        return best;
    }
}
