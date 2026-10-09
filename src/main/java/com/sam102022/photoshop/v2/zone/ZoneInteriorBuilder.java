package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Construit le masque d'une zone détourée sur le bord intérieur des routes frontières.
 * <p>
 * À la différence d'un territoire, les routes qui bordent la zone n'en font pas partie : le masque est formé
 * des cellules retenues par le vote, complétées par les seules rues intérieures (chaussées comblées par une
 * fermeture des cellules retenues : une rue est intérieure lorsque la zone se trouve des deux côtés). Les
 * giratoires frontaliers sont exclus (le contour suit le bord de l'anneau côté zone) ; un giratoire
 * entièrement intérieur est restitué par le comblement des trous.
 */
public final class ZoneInteriorBuilder {

    private static final Logger LOGGER = Logger.getLogger(ZoneInteriorBuilder.class.getName());

    /** Rayon de fermeture par défaut (px) : rues intérieures jusqu'à 2 × 12 px de large. */
    public static final double DEFAULT_INTERIOR_ROAD_RADIUS = 12.0;
    /** Part du disque d'un giratoire dans le tracé au-delà de laquelle il est considéré comme intérieur. */
    public static final double MAX_POLYGON_COVERAGE = 0.9;

    private final DiskMorphology morphology;

    /**
     * Initialise le constructeur avec la morphologie par défaut.
     */
    public ZoneInteriorBuilder() {
        this(new DiskMorphology());
    }

    /**
     * Initialise le constructeur avec une morphologie injectée.
     *
     * @param morphology Morphologie par disques (non nulle).
     */
    public ZoneInteriorBuilder(DiskMorphology morphology) {
        this.morphology = Objects.requireNonNull(morphology, "morphology ne doit pas être nulle.");
    }

    /**
     * Construit le masque de la zone.
     *
     * @param retained    Cellules retenues par le vote (non nul).
     * @param roadMask    Masque routier (non nul).
     * @param roundabouts Giratoires détectés (non nul).
     * @param roadRadius  Rayon de fermeture des rues intérieures (px).
     * @return Masque de la zone (une seule composante, sans trou).
     */
    public BinaryMask build(BinaryMask retained, BinaryMask roadMask, List<Roundabout> roundabouts, double roadRadius) {
        return build(retained, roadMask, roundabouts, roadRadius, null);
    }

    /**
     * Construit le masque de la zone ; seuls les giratoires réellement traversés par le tracé de la zone (moins de
     * {@link #MAX_POLYGON_COVERAGE} de leur disque à l'intérieur du tracé) sont exclus. Un petit îlot entouré de
     * voies à l'intérieur de la zone, pris à tort pour un giratoire, ne crée donc pas d'encoche.
     *
     * @param retained    Cellules retenues par le vote (non nul).
     * @param roadMask    Masque routier (non nul).
     * @param roundabouts Giratoires détectés (non nul).
     * @param roadRadius  Rayon de fermeture des rues intérieures (px).
     * @param zoneTrace   Tracé de la zone rastérisé (même repère), ou null pour exclure tous les giratoires.
     * @return Masque de la zone (une seule composante, sans trou).
     */
    public BinaryMask build(BinaryMask retained, BinaryMask roadMask, List<Roundabout> roundabouts, double roadRadius,
                            BinaryMask zoneTrace) {
        Objects.requireNonNull(retained, "retained ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(roundabouts, "roundabouts ne doit pas être nul.");
        BinaryMask closed = morphology.close(retained, roadRadius);
        BinaryMask mask = retained.copy();
        long interior = 0;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (!mask.get(x, y) && closed.get(x, y) && roadMask.get(x, y)) {
                    mask.set(x, y, true);
                    interior++;
                }
            }
        }
        List<Roundabout> touched = roundabouts.stream()
                .filter(rb -> zoneTrace == null
                        || traceCoverage(zoneTrace, rb.exteriorEllipse()) < MAX_POLYGON_COVERAGE)
                .filter(rb -> clearEllipse(mask, rb.exteriorEllipse())).toList();
        BinaryMask result = MaskComponents.fillHoles(MaskComponents.largest(mask));
        // Les giratoires intérieurs, retirés puis restitués par le comblement des trous, ne sont pas comptés.
        long excluded = touched.stream().filter(rb -> !result.get((int) Math.round(rb.center().x()),
                (int) Math.round(rb.center().y()))).count();
        long added = interior;
        long count = excluded;
        LOGGER.info(() -> String.format("[Zones] %d px de rues intérieures ajoutés ; %d giratoire(s) frontalier(s) exclu(s).",
                added, count));
        return result;
    }

    /**
     * Part du disque extérieur d'un giratoire située à l'intérieur du tracé de la zone.
     *
     * @param trace Tracé de la zone.
     * @param ell   Ellipse extérieure de l'anneau.
     * @return Part dans [0, 1].
     */
    private double traceCoverage(BinaryMask trace, EllipseModel ell) {
        int r = (int) Math.ceil(Math.max(ell.a(), ell.b())) + 1;
        int inside = 0;
        int total = 0;
        int x0 = Math.max(0, (int) Math.floor(ell.xc()) - r);
        int y0 = Math.max(0, (int) Math.floor(ell.yc()) - r);
        int x1 = Math.min(trace.getWidth() - 1, (int) Math.ceil(ell.xc()) + r);
        int y1 = Math.min(trace.getHeight() - 1, (int) Math.ceil(ell.yc()) + r);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                PixelPoint u = ell.toUnitCircle(new PixelPoint(x, y));
                if (u.x() * u.x() + u.y() * u.y() <= 1.0) {
                    total++;
                    if (trace.get(x, y)) {
                        inside++;
                    }
                }
            }
        }
        return total == 0 ? 0.0 : (double) inside / total;
    }

    /**
     * Retire (en place) le disque extérieur d'un giratoire du masque.
     *
     * @param mask Masque modifié.
     * @param ell  Ellipse extérieure de l'anneau.
     * @return Vrai si au moins un pixel a été retiré.
     */
    private boolean clearEllipse(BinaryMask mask, EllipseModel ell) {
        int r = (int) Math.ceil(Math.max(ell.a(), ell.b())) + 1;
        int x0 = Math.max(0, (int) Math.floor(ell.xc()) - r);
        int y0 = Math.max(0, (int) Math.floor(ell.yc()) - r);
        int x1 = Math.min(mask.getWidth() - 1, (int) Math.ceil(ell.xc()) + r);
        int y1 = Math.min(mask.getHeight() - 1, (int) Math.ceil(ell.yc()) + r);
        boolean cleared = false;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                PixelPoint u = ell.toUnitCircle(new PixelPoint(x, y));
                if (mask.get(x, y) && u.x() * u.x() + u.y() * u.y() <= 1.0) {
                    mask.set(x, y, false);
                    cleared = true;
                }
            }
        }
        return cleared;
    }
}
