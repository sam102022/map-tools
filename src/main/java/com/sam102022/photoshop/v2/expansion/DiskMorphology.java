package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.Objects;

/**
 * Morphologie mathématique par disques euclidiens exacts (dilatation, érosion, fermeture), calculée par
 * transformée de distance en O(N) quel que soit le rayon.
 * <p>
 * La dilatation et l'érosion de rayon {@code r} ne dépendent que des pixels situés à moins de {@code r} de
 * l'ensemble : elles sont calculées dans le rectangle englobant de l'ensemble élargi de {@code r + 2} px, puis
 * recopiées dans un masque de la taille d'origine. Le résultat est identique au calcul sur l'image entière, pour
 * un coût proportionnel à la taille de l'ensemble et non à celle de l'image.
 * </p>
 */
public final class DiskMorphology {

    /** Marge (px) ajoutée au rayon autour du rectangle englobant. */
    private static final int WINDOW_MARGIN = 2;

    private final EuclideanDistanceTransform distanceTransform;

    /**
     * Initialise l'opérateur avec la transformée de distance par défaut.
     */
    public DiskMorphology() {
        this(new EuclideanDistanceTransform());
    }

    /**
     * Initialise l'opérateur avec une transformée de distance injectée.
     *
     * @param distanceTransform Transformée de distance euclidienne exacte (non nulle).
     */
    public DiskMorphology(EuclideanDistanceTransform distanceTransform) {
        this.distanceTransform = Objects.requireNonNull(distanceTransform, "distanceTransform ne doit pas être nulle.");
    }

    /**
     * Distance euclidienne de chaque pixel à l'ensemble (0 sur l'ensemble ; 0 partout si l'ensemble est vide).
     *
     * @param set Ensemble.
     * @return Carte des distances à l'ensemble.
     */
    public DistanceMap distanceTo(BinaryMask set) {
        return distanceTransform.compute(set.not());
    }

    /**
     * Dilatation par un disque de rayon r : pixels à distance euclidienne &lt;= r de l'ensemble.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble dilaté (vide si l'ensemble est vide).
     */
    public BinaryMask dilate(BinaryMask set, double r) {
        int[] window = window(set, r);
        if (window == null) {
            return new BinaryMask(set.getWidth(), set.getHeight());
        }
        BinaryMask local = crop(set, window);
        return paste(within(local, distanceTo(local), r), window, set.getWidth(), set.getHeight());
    }

    /**
     * Pixels à distance &lt;= r de l'ensemble, à partir d'une carte de distances déjà calculée.
     *
     * @param set   Ensemble.
     * @param toSet Distance à l'ensemble ({@link #distanceTo(BinaryMask)}).
     * @param r     Rayon (px).
     * @return Ensemble dilaté.
     */
    public BinaryMask within(BinaryMask set, DistanceMap toSet, double r) {
        BinaryMask out = new BinaryMask(set.getWidth(), set.getHeight());
        for (int y = 0; y < set.getHeight(); y++) {
            for (int x = 0; x < set.getWidth(); x++) {
                float d = toSet.get(x, y);
                // d == 0 hors de l'ensemble signifie « aucun pixel de l'ensemble » (ensemble vide)
                if (set.get(x, y) || (d > 0.0f && d <= r)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Érosion par un disque de rayon r : pixels de l'ensemble à distance &gt; r de son complément (l'ensemble
     * est conservé tel quel s'il couvre toute l'image).
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble érodé.
     */
    public BinaryMask erode(BinaryMask set, double r) {
        int[] window = window(set, r);
        if (window == null) {
            return new BinaryMask(set.getWidth(), set.getHeight());
        }
        BinaryMask local = crop(set, window);
        DistanceMap toComplement = distanceTransform.compute(local);
        BinaryMask out = new BinaryMask(local.getWidth(), local.getHeight());
        for (int y = 0; y < local.getHeight(); y++) {
            for (int x = 0; x < local.getWidth(); x++) {
                float d = toComplement.get(x, y);
                if (local.get(x, y) && (d > r || d == 0.0f)) {
                    out.set(x, y, true);
                }
            }
        }
        return paste(out, window, set.getWidth(), set.getHeight());
    }

    /**
     * Fermeture par un disque (dilatation puis érosion).
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble fermé.
     */
    public BinaryMask close(BinaryMask set, double r) {
        return erode(dilate(set, r), r);
    }

    /**
     * Ouverture par un disque (érosion puis dilatation).
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble ouvert.
     */
    public BinaryMask open(BinaryMask set, double r) {
        return dilate(erode(set, r), r);
    }

    /**
     * Rectangle englobant de l'ensemble élargi de {@code ceil(r) + 2} px et borné à l'image.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return {x0, y0, x1, y1} inclusifs, ou null si l'ensemble est vide.
     */
    public static int[] window(BinaryMask set, double r) {
        int w = set.getWidth();
        int h = set.getHeight();
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (set.get(x, y)) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        int margin = (int) Math.ceil(Math.max(0.0, r)) + WINDOW_MARGIN;
        return new int[]{Math.max(0, minX - margin), Math.max(0, minY - margin),
                Math.min(w - 1, maxX + margin), Math.min(h - 1, maxY + margin)};
    }

    /**
     * Extrait la partie d'un masque située dans un rectangle.
     *
     * @param mask   Masque.
     * @param window Rectangle {x0, y0, x1, y1} inclusif.
     * @return Masque local.
     */
    public static BinaryMask crop(BinaryMask mask, int[] window) {
        BinaryMask out = new BinaryMask(window[2] - window[0] + 1, window[3] - window[1] + 1);
        for (int y = window[1]; y <= window[3]; y++) {
            for (int x = window[0]; x <= window[2]; x++) {
                if (mask.get(x, y)) {
                    out.set(x - window[0], y - window[1], true);
                }
            }
        }
        return out;
    }

    /**
     * Replace un masque local dans un masque vide de la taille d'origine.
     *
     * @param local  Masque local.
     * @param window Rectangle {x0, y0, x1, y1} d'où il provient.
     * @param width  Largeur d'origine.
     * @param height Hauteur d'origine.
     * @return Masque pleine taille.
     */
    public static BinaryMask paste(BinaryMask local, int[] window, int width, int height) {
        BinaryMask out = new BinaryMask(width, height);
        for (int y = 0; y < local.getHeight(); y++) {
            for (int x = 0; x < local.getWidth(); x++) {
                if (local.get(x, y)) {
                    out.set(x + window[0], y + window[1], true);
                }
            }
        }
        return out;
    }
}
