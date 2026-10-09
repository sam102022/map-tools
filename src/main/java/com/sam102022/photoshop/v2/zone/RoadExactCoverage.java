package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.EllipseModel;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;

import java.util.List;
import java.util.Objects;

/**
 * Découpe exacte d'une zone le long des routes qui la bordent, à partir de la capture Google « Routes seules ».
 * <p>
 * Le contour vectoriel en grands segments suit bien les bords rectilignes mais s'écarte du bord de chaussée dans
 * les courbes serrées (anneaux des giratoires). Or la capture donne, pour chaque pixel, la part de
 * chaussée {@code g} : à proximité d'une route frontière, l'opacité est donc remplacée par {@code 1 - g} dans
 * une bande de {@value #EDGE_BAND} px autour du masque de zone, ce qui reproduit au sub-pixel près le bord dessiné
 * par Google, cercles compris. Ailleurs (tracé coupant un îlot, rues intérieures) la couverture vectorielle est
 * conservée ; dans les débouchés des rues intérieures, la chaussée appartenant à la zone est pleinement opaque.
 */
public final class RoadExactCoverage {

    /** Largeur (px) de la bande autour du masque de zone où le bord de chaussée est lu dans la capture. */
    public static final double EDGE_BAND = 4.0;
    /** Épaisseur (px) de la lisière intérieure de la zone où l'anti-crénelage de la capture s'applique. */
    public static final double INNER_EDGE = 2.0;
    /** Marge (px) autour du disque extérieur d'un giratoire où la découpe exacte s'applique. */
    public static final double ROUNDABOUT_MARGIN = 15.0;
    /** Surface maximale (px) d'un trou transparent comblé dans la zone. */
    public static final int MAX_HOLE_AREA = 200;
    /** Distance (px) à une route frontière en deçà de laquelle la découpe exacte s'applique. */
    public static final double ROAD_REACH = 6.0;

    private final DiskMorphology morphology;

    /**
     * Initialise la découpe avec la morphologie par défaut.
     */
    public RoadExactCoverage() {
        this(new DiskMorphology());
    }

    /**
     * Initialise la découpe avec une morphologie injectée.
     *
     * @param morphology Morphologie par disques (non nulle).
     */
    public RoadExactCoverage(DiskMorphology morphology) {
        this.morphology = Objects.requireNonNull(morphology, "morphology ne doit pas être nulle.");
    }

    /**
     * Remplace (en place) l'opacité au voisinage des routes frontières par la découpe exacte.
     *
     * @param coverage   Couverture pleine image issue du contour vectoriel (modifiée en place).
     * @param zoneMask   Masque de la zone dans le repère de la fenêtre (rues intérieures comprises).
     * @param roadMask   Masque routier dans le repère de la fenêtre.
     * @param roads      Capture « Routes seules » (pleine image).
     * @param cropWindow Fenêtre de recadrage.
     * @return La couverture modifiée.
     */
    public CoverageMask refine(CoverageMask coverage, BinaryMask zoneMask, BinaryMask roadMask, GoogleRoadsImage roads,
                               CropWindow cropWindow) {
        return refine(coverage, zoneMask, roadMask, roads, cropWindow, null);
    }

    /**
     * Remplace (en place) l'opacité par la découpe exacte, au voisinage des routes frontières et, si une liste de
     * giratoires est fournie, uniquement autour de ces giratoires : ailleurs, le contour vectoriel en grands
     * segments rectilignes est conservé (il ignore les entrées charretières et petits décrochements du bord de
     * chaussée, que la découpe exacte reproduirait).
     *
     * @param coverage    Couverture pleine image issue du contour vectoriel (modifiée en place).
     * @param zoneMask    Masque de la zone dans le repère de la fenêtre (rues intérieures comprises).
     * @param roadMask    Masque routier dans le repère de la fenêtre.
     * @param roads       Capture « Routes seules » (pleine image).
     * @param cropWindow  Fenêtre de recadrage.
     * @param roundabouts Giratoires (repère de la fenêtre) autour desquels appliquer la découpe, ou null (partout).
     * @return La couverture modifiée.
     */
    public CoverageMask refine(CoverageMask coverage, BinaryMask zoneMask, BinaryMask roadMask, GoogleRoadsImage roads,
                               CropWindow cropWindow, List<Roundabout> roundabouts) {
        Objects.requireNonNull(coverage, "coverage ne doit pas être nulle.");
        Objects.requireNonNull(zoneMask, "zoneMask ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(roads, "roads ne doit pas être nulle.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nulle.");
        int w = zoneMask.getWidth();
        int h = zoneMask.getHeight();
        BinaryMask boundaryRoads = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (roadMask.get(x, y) && !zoneMask.get(x, y)) {
                    boundaryRoads.set(x, y, true);
                }
            }
        }
        BinaryMask nearRoad = morphology.dilate(boundaryRoads, ROAD_REACH);
        if (roundabouts != null) {
            restrictToRoundabouts(nearRoad, roundabouts);
        }
        BinaryMask band = morphology.dilate(zoneMask, EDGE_BAND);
        BinaryMask core = morphology.erode(zoneMask, INNER_EDGE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!nearRoad.get(x, y)) {
                    continue;
                }
                int gx = x + cropWindow.x0();
                int gy = y + cropWindow.y0();
                if (gx >= coverage.getWidth() || gy >= coverage.getHeight()) {
                    continue;
                }
                float g = roads.value(gx, gy);
                int exact = Math.round(255.0f * (1.0f - g));
                int value;
                if (zoneMask.get(x, y)) {
                    // Chaussée intérieure (débouché de rue) et cœur de la zone : pleins ; lisière : anti-crénelée.
                    value = roadMask.get(x, y) || core.get(x, y) ? 255 : exact;
                } else if (g >= 0.5f) {
                    // Chaussée frontière : seul son liseré anti-crénelé, côté zone, reste partiellement opaque.
                    value = band.get(x, y) ? exact : 0;
                } else {
                    // Terrain hors du masque de zone (îlot au-delà d'une route étroite, coupe d'un îlot) : contour
                    // vectoriel conservé.
                    value = coverage.get(gx, gy);
                }
                coverage.set(gx, gy, value);
            }
        }
        removeFragments(coverage, cropWindow);
        return coverage;
    }

    /**
     * Restreint (en place) une zone aux disques des giratoires élargis de {@value #ROUNDABOUT_MARGIN} px.
     *
     * @param area        Zone à restreindre.
     * @param roundabouts Giratoires.
     */
    private static void restrictToRoundabouts(BinaryMask area, List<Roundabout> roundabouts) {
        BinaryMask keep = new BinaryMask(area.getWidth(), area.getHeight());
        for (Roundabout rb : roundabouts) {
            EllipseModel ell = rb.exteriorEllipse();
            double radius = ell.a() + ROUNDABOUT_MARGIN;
            int x0 = Math.max(0, (int) Math.floor(ell.xc() - radius));
            int y0 = Math.max(0, (int) Math.floor(ell.yc() - radius));
            int x1 = Math.min(area.getWidth() - 1, (int) Math.ceil(ell.xc() + radius));
            int y1 = Math.min(area.getHeight() - 1, (int) Math.ceil(ell.yc() + radius));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (Math.hypot(x - ell.xc(), y - ell.yc()) <= radius) {
                        keep.set(x, y, true);
                    }
                }
            }
        }
        for (int y = 0; y < area.getHeight(); y++) {
            for (int x = 0; x < area.getWidth(); x++) {
                if (!keep.get(x, y)) {
                    area.set(x, y, false);
                }
            }
        }
    }

    /**
     * Nettoie (en place) la couverture dans la fenêtre : seule la plus grande composante 8-connexe de pixels
     * non transparents est conservée (fragments isolés au-delà d'une route étroite) et les petits trous
     * transparents qu'elle enclôt (au plus {@value #MAX_HOLE_AREA} px) sont rendus opaques.
     *
     * @param coverage   Couverture pleine image (modifiée en place).
     * @param cropWindow Fenêtre de recadrage.
     */
    private void removeFragments(CoverageMask coverage, CropWindow cropWindow) {
        int w = Math.min(cropWindow.width(), coverage.getWidth() - cropWindow.x0());
        int h = Math.min(cropWindow.height(), coverage.getHeight() - cropWindow.y0());
        BinaryMask visible = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                visible.set(x, y, coverage.get(x + cropWindow.x0(), y + cropWindow.y0()) > 0);
            }
        }
        MaskComponents.Component largest = null;
        List<MaskComponents.Component> components = MaskComponents.components(visible, true);
        for (MaskComponents.Component c : components) {
            if (largest == null || c.size() > largest.size()) {
                largest = c;
            }
        }
        for (MaskComponents.Component c : components) {
            if (c != largest) {
                setAll(coverage, cropWindow, c, w, 0);
            }
        }
        for (MaskComponents.Component hole : MaskComponents.components(visible.not(), false)) {
            if (!hole.onBorder() && hole.size() <= MAX_HOLE_AREA) {
                setAll(coverage, cropWindow, hole, w, 255);
            }
        }
    }

    /**
     * Affecte une opacité à tous les pixels d'une composante (repère de la fenêtre).
     *
     * @param coverage   Couverture pleine image.
     * @param cropWindow Fenêtre de recadrage.
     * @param component  Composante.
     * @param w          Largeur du repère de la composante.
     * @param value      Opacité (0 à 255).
     */
    private static void setAll(CoverageMask coverage, CropWindow cropWindow, MaskComponents.Component component, int w,
                               int value) {
        for (int k = 0; k < component.size(); k++) {
            int idx = component.pixels()[k];
            coverage.set(idx % w + cropWindow.x0(), idx / w + cropWindow.y0(), value);
        }
    }
}
