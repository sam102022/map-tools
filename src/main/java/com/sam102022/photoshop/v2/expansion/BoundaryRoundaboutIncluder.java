package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.contour.RoundaboutDetector;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Inclut en entier les giratoires englobés ou traversés par le polygone d'intention.
 * <p>
 * Règle de détourage (choix utilisateur, Territoire CA02) : si le polygone traverse un giratoire, ou
 * l'englobe, le détourage suit l'extérieur de son anneau. Tout giratoire dont le disque extérieur est
 * recouvert par le polygone à au moins {@code minPolygonCoverage}, qui touche le territoire sans y être
 * entièrement contenu, est donc ajouté (disque extérieur complet).
 * </p>
 */
public class BoundaryRoundaboutIncluder {

    private static final Logger LOGGER = Logger.getLogger(BoundaryRoundaboutIncluder.class.getName());

    /** Recouvrement minimal du disque extérieur du giratoire par le polygone d'intention. */
    public static final double DEFAULT_MIN_POLYGON_COVERAGE = 0.3;

    /**
     * Au-delà de ce recouvrement par le territoire, l'anneau est déjà inclus : seul le disque ajusté, un peu plus
     * large que l'anneau, déborderait sur les branches du giratoire.
     */
    static final double MAX_TERRITORY_COVERAGE = 0.9;

    private final RoundaboutDetector detector;

    /**
     * Construit l'inclueur avec le détecteur de giratoires standard.
     */
    public BoundaryRoundaboutIncluder() {
        this(new RoundaboutDetector());
    }

    /**
     * Construit l'inclueur avec un détecteur injecté.
     *
     * @param detector Détecteur de giratoires.
     * @throws NullPointerException si detector est null.
     */
    public BoundaryRoundaboutIncluder(RoundaboutDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector ne doit pas être nul.");
    }

    /**
     * Ajoute au masque consolidé les giratoires englobés ou traversés par le polygone.
     *
     * @param consolidated       Masque consolidé.
     * @param roadMask           Masque routier recadré.
     * @param polygonMask        Polygone d'intention recadré.
     * @param labelMap           Étiquettes des cellules.
     * @param cells              Cellules.
     * @param minPolygonCoverage Recouvrement minimal par le polygone ; 0 ou moins désactive l'inclusion.
     * @return Nouveau masque consolidé, ou l'original si aucun giratoire n'est ajouté.
     * @throws NullPointerException si un argument est null.
     */
    public ConsolidatedMask include(ConsolidatedMask consolidated, BinaryMask roadMask, BinaryMask polygonMask,
                                    CellLabelMap labelMap, List<Cell> cells, double minPolygonCoverage) {
        Objects.requireNonNull(consolidated, "consolidated ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(polygonMask, "polygonMask ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "labelMap ne doit pas être nulle.");
        Objects.requireNonNull(cells, "cells ne doit pas être nulle.");
        if (minPolygonCoverage <= 0.0) {
            return consolidated;
        }
        BinaryMask mask = consolidated.mask().copy();
        int included = 0;
        for (Roundabout rb : detector.detect(roadMask, labelMap, cells, 90.0, 240)) {
            EllipseModel ell = rb.exteriorEllipse();
            int[] box = boundingBox(ell, mask.getWidth(), mask.getHeight());
            double inTerritory = coverage(mask, ell, box);
            double inPolygon = coverage(polygonMask, ell, box);
            if (inPolygon >= minPolygonCoverage && inTerritory > 0.0 && inTerritory < MAX_TERRITORY_COVERAGE) {
                fillEllipse(mask, ell, box);
                included++;
            }
        }
        if (included == 0) {
            return consolidated;
        }
        int count = included;
        LOGGER.info(() -> String.format("[Giratoires] %d giratoire(s) frontalier(s) inclus en entier.", count));
        return new ConsolidatedMask(consolidated.width(), consolidated.height(), mask, consolidated.cropWindow());
    }

    /**
     * Boîte englobante {x0, y0, x1, y1} (bornes incluses) de l'ellipse, bornée à l'image.
     *
     * @param ell Ellipse.
     * @param w   Largeur.
     * @param h   Hauteur.
     * @return Boîte englobante.
     */
    private int[] boundingBox(EllipseModel ell, int w, int h) {
        int r = (int) Math.ceil(ell.a()) + 1;
        return new int[]{
                Math.max(0, (int) Math.floor(ell.xc()) - r), Math.max(0, (int) Math.floor(ell.yc()) - r),
                Math.min(w - 1, (int) Math.ceil(ell.xc()) + r), Math.min(h - 1, (int) Math.ceil(ell.yc()) + r)};
    }

    /**
     * Fraction des pixels du disque extérieur actifs dans un masque.
     *
     * @param mask Masque.
     * @param ell  Ellipse extérieure.
     * @param box  Boîte englobante.
     * @return Recouvrement dans [0, 1].
     */
    private double coverage(BinaryMask mask, EllipseModel ell, int[] box) {
        int inside = 0;
        int total = 0;
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (inEllipse(ell, x, y)) {
                    total++;
                    if (mask.get(x, y)) {
                        inside++;
                    }
                }
            }
        }
        return total == 0 ? 0.0 : (double) inside / total;
    }

    /**
     * Ajoute au masque les pixels du disque extérieur.
     *
     * @param mask Masque modifié en place.
     * @param ell  Ellipse extérieure.
     * @param box  Boîte englobante.
     */
    private void fillEllipse(BinaryMask mask, EllipseModel ell, int[] box) {
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (inEllipse(ell, x, y)) {
                    mask.set(x, y, true);
                }
            }
        }
    }

    /**
     * Indique si le centre du pixel est dans l'ellipse.
     *
     * @param ell Ellipse.
     * @param x   Abscisse.
     * @param y   Ordonnée.
     * @return true si à l'intérieur.
     */
    private boolean inEllipse(EllipseModel ell, int x, int y) {
        PixelPoint u = ell.toUnitCircle(new PixelPoint(x, y));
        return u.x() * u.x() + u.y() * u.y() <= 1.0;
    }
}
