package com.sam102022.photoshop.v2.zone;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.expansion.DistanceMap;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Extrait le polygone d'intention d'une zone à partir de son trait de couleur sur le plan des zones.
 * <p>
 * Étapes : (1) pixels proches de la couleur de la zone ; (2) seuls les traits longs sont conservés (les
 * pictogrammes et libellés de même teinte forment de petites composantes) ; (3) le trait est épaissi d'un
 * rayon croissant jusqu'à ce qu'il enclose au moins une région (les interruptions du trait sous les
 * pictogrammes sont ainsi refermées) ; (4) chaque région enclose est élargie jusqu'à l'axe du trait.
 * Une zone peut comporter plusieurs parties lorsque son trait en délimite plusieurs.
 */
public final class ZoneExtractor {

    private static final Logger LOGGER = Logger.getLogger(ZoneExtractor.class.getName());

    /**
     * Distance RVB maximale à la couleur de référence d'une zone (le liseré d'anti-crénelage des traits
     * s'écarte jusqu'à ~58 de la couleur pure).
     */
    public static final double DEFAULT_COLOR_TOLERANCE = 60.0;
    /** Étendue minimale (px) d'une composante pour être considérée comme un trait de zone. */
    public static final int DEFAULT_MIN_STROKE_EXTENT = 150;
    /** Surface minimale (px) d'une région enclose pour constituer une partie de zone. */
    public static final long DEFAULT_MIN_ZONE_AREA = 20_000L;
    /** Demi-épaisseur du trait (px) : distance entre son bord et son axe. */
    static final double STROKE_HALF_WIDTH = 1.5;
    /** Rayons successifs (px) d'épaississement du trait pour refermer ses interruptions. */
    private static final double[] GAP_RADII = {2, 3, 4, 6, 8, 10, 12};

    private final DiskMorphology morphology;

    /**
     * Initialise l'extracteur avec la morphologie par défaut.
     */
    public ZoneExtractor() {
        this(new DiskMorphology());
    }

    /**
     * Initialise l'extracteur avec une morphologie injectée.
     *
     * @param morphology Morphologie par disques (non nulle).
     */
    public ZoneExtractor(DiskMorphology morphology) {
        this.morphology = Objects.requireNonNull(morphology, "morphology ne doit pas être nulle.");
    }

    /**
     * Extrait les parties d'une zone (paramètres par défaut).
     *
     * @param zonesImage Plan des zones (non nul).
     * @param zone       Zone recherchée (non nulle).
     * @return Parties de la zone (masques pleine image) ; liste vide si la couleur est absente.
     */
    public List<BinaryMask> extract(BufferedImage zonesImage, ZoneColor zone) {
        return extract(zonesImage, zone, DEFAULT_COLOR_TOLERANCE, DEFAULT_MIN_STROKE_EXTENT, DEFAULT_MIN_ZONE_AREA);
    }

    /**
     * Extrait les parties d'une zone.
     *
     * @param zonesImage     Plan des zones (non nul).
     * @param zone           Zone recherchée (non nulle).
     * @param colorTolerance Distance RVB maximale à la couleur de référence.
     * @param minExtent      Étendue minimale d'un trait (px).
     * @param minArea        Surface minimale d'une partie (px).
     * @return Parties de la zone (masques pleine image), triées par surface décroissante.
     */
    public List<BinaryMask> extract(BufferedImage zonesImage, ZoneColor zone, double colorTolerance, int minExtent,
                                    long minArea) {
        Objects.requireNonNull(zonesImage, "zonesImage ne doit pas être nulle.");
        Objects.requireNonNull(zone, "zone ne doit pas être nulle.");
        BinaryMask stroke = longStrokes(colorMask(zonesImage, zone, colorTolerance), minExtent);
        if (stroke.countActivePixels() == 0) {
            return List.of();
        }
        DistanceMap toStroke = morphology.distanceTo(stroke);
        for (double r : GAP_RADII) {
            BinaryMask barrier = morphology.within(stroke, toStroke, r);
            List<MaskComponents.Component> regions = enclosedRegions(barrier, minArea);
            if (!regions.isEmpty()) {
                List<BinaryMask> parts = new ArrayList<>();
                for (MaskComponents.Component region : regions) {
                    BinaryMask part = region.toMask(stroke.getWidth(), stroke.getHeight());
                    parts.add(morphology.dilate(part, r + STROKE_HALF_WIDTH));
                }
                LOGGER.info(() -> String.format("[Zones] %s : %d partie(s), trait refermé au rayon %.0f px.",
                        zone.fileLabel(), parts.size(), r));
                return parts;
            }
        }
        LOGGER.warning(() -> "[Zones] " + zone.fileLabel() + " : trait trouvé mais non fermé, zone ignorée.");
        return List.of();
    }

    /**
     * Pixels dont la couleur est proche de la couleur de référence de la zone, et plus proche de celle-ci que
     * de celle de toute autre zone (les zones rouge et orange, distantes de ~100, ne se disputent aucun pixel).
     *
     * @param image     Image.
     * @param zone      Zone.
     * @param tolerance Distance RVB maximale.
     * @return Masque des pixels de la couleur.
     */
    BinaryMask colorMask(BufferedImage image, ZoneColor zone, double tolerance) {
        int w = image.getWidth();
        int h = image.getHeight();
        BinaryMask out = new BinaryMask(w, h);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                if (zone.distance(row[x]) <= tolerance && isNearestZone(zone, row[x])) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Indique si la zone est la plus proche (au sens RVB) de la couleur.
     *
     * @param zone Zone candidate.
     * @param rgb  Couleur RVB.
     * @return Vrai si aucune autre zone n'est strictement plus proche.
     */
    private static boolean isNearestZone(ZoneColor zone, int rgb) {
        double d = zone.distance(rgb);
        for (ZoneColor other : ZoneColor.values()) {
            if (other != zone && other.distance(rgb) < d) {
                return false;
            }
        }
        return true;
    }

    /**
     * Conserve les composantes 8-connexes dont l'étendue atteint {@code minExtent}.
     *
     * @param mask      Pixels de la couleur.
     * @param minExtent Étendue minimale (px).
     * @return Masque des traits longs.
     */
    BinaryMask longStrokes(BinaryMask mask, int minExtent) {
        int w = mask.getWidth();
        BinaryMask out = new BinaryMask(w, mask.getHeight());
        for (MaskComponents.Component c : MaskComponents.components(mask, true)) {
            if (c.extent() >= minExtent) {
                for (int k = 0; k < c.size(); k++) {
                    out.set(c.pixels()[k] % w, c.pixels()[k] / w, true);
                }
            }
        }
        return out;
    }

    /**
     * Régions encloses par la barrière (composantes 4-connexes du complément ne touchant pas le bord) de
     * surface suffisante, triées par surface décroissante.
     *
     * @param barrier Trait épaissi.
     * @param minArea Surface minimale (px).
     * @return Régions encloses.
     */
    private List<MaskComponents.Component> enclosedRegions(BinaryMask barrier, long minArea) {
        List<MaskComponents.Component> regions = new ArrayList<>();
        for (MaskComponents.Component c : MaskComponents.components(barrier.not(), false)) {
            if (!c.onBorder() && c.size() >= minArea) {
                regions.add(c);
            }
        }
        regions.sort((a, b) -> Integer.compare(b.size(), a.size()));
        return regions;
    }
}
