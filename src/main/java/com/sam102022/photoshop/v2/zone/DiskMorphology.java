package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.expansion.DistanceMap;
import com.sam102022.photoshop.v2.expansion.EuclideanDistanceTransform;

import java.util.Objects;

/**
 * Morphologie mathématique par disques euclidiens exacts (dilatation, érosion, fermeture), calculée par
 * transformée de distance en O(N) quel que soit le rayon.
 */
public final class DiskMorphology {

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
     * Dilatation par un disque de rayon r : pixels à distance &lt;= r de l'ensemble.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble dilaté.
     */
    public BinaryMask dilate(BinaryMask set, double r) {
        return within(set, distanceTo(set), r);
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
                if (set.get(x, y) || (d > 0.0f && d <= r)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Érosion par un disque de rayon r : pixels de l'ensemble à distance &gt; r de son complément
     * (les bords de l'image ne sont pas considérés comme complément).
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble érodé.
     */
    public BinaryMask erode(BinaryMask set, double r) {
        DistanceMap toComplement = distanceTransform.compute(set);
        BinaryMask out = new BinaryMask(set.getWidth(), set.getHeight());
        for (int y = 0; y < set.getHeight(); y++) {
            for (int x = 0; x < set.getWidth(); x++) {
                float d = toComplement.get(x, y);
                if (set.get(x, y) && (d > r || d == 0.0f)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Fermeture par un disque de rayon r (dilatation puis érosion) : comble les interstices de largeur &lt; 2r.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble fermé.
     */
    public BinaryMask close(BinaryMask set, double r) {
        return erode(dilate(set, r), r);
    }
}
