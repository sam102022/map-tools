package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Champ continu de « routéité » extrait de l'anti-crénelage de la carte source.
 * <p>
 * La carte Google étant rendue en vectoriel, le bord d'une chaussée y est anti-crénelé : la valeur
 * {@code clamp((B - R) / fieldScale, 0, 1)} varie continûment de 1 (chaussée) à 0 (fond) et son iso-ligne
 * 0,5 restitue le bord de route avec une précision sub-pixel. Le champ est stocké dans le repère local
 * de la {@link CropWindow} ; les coordonnées sont celles des centres de pixels (pixel (i, j) en (i, j)).
 * </p>
 */
public final class RoadEdgeField {

    private final int width;
    private final int height;
    private final float[] values;

    /**
     * Construit le champ sur la fenêtre de recadrage.
     *
     * @param mapImage   Carte source pleine résolution.
     * @param cropWindow Fenêtre de recadrage définissant le repère local.
     * @param fieldScale Écart (B - R) d'une chaussée pleine.
     * @throws NullPointerException     si mapImage ou cropWindow est null.
     * @throws IllegalArgumentException si fieldScale n'est pas strictement positif.
     */
    public RoadEdgeField(BufferedImage mapImage, CropWindow cropWindow, double fieldScale) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nulle.");
        if (fieldScale <= 0.0) {
            throw new IllegalArgumentException("fieldScale doit être strictement positif.");
        }
        this.width = cropWindow.width();
        this.height = cropWindow.height();
        this.values = new float[width * height];
        int[] rgb = mapImage.getRGB(cropWindow.x0(), cropWindow.y0(), width, height, null, 0, width);
        float inv = (float) (1.0 / fieldScale);
        for (int i = 0; i < rgb.length; i++) {
            int r = (rgb[i] >>> 16) & 0xFF;
            int b = rgb[i] & 0xFF;
            values[i] = Math.clamp((b - r) * inv, 0.0f, 1.0f);
        }
    }

    /**
     * Construit le champ sur la fenêtre de recadrage à partir de la capture Google « Routes seules » : la part
     * de chaussée de chaque pixel est lue directement (aucune estimation colorimétrique).
     *
     * @param roads      Capture « Routes seules » nettoyée (non nulle).
     * @param cropWindow Fenêtre de recadrage définissant le repère local (non nulle).
     */
    public RoadEdgeField(GoogleRoadsImage roads, CropWindow cropWindow) {
        Objects.requireNonNull(roads, "roads ne doit pas être nulle.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nulle.");
        this.width = cropWindow.width();
        this.height = cropWindow.height();
        this.values = new float[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                values[y * width + x] = roads.value(cropWindow.x0() + x, cropWindow.y0() + y);
            }
        }
    }

    /**
     * Échantillonne le champ par interpolation bilinéaire (bords répliqués).
     *
     * @param x Abscisse locale (centre de pixel = entier).
     * @param y Ordonnée locale (centre de pixel = entier).
     * @return Valeur du champ dans [0, 1].
     */
    public double sample(double x, double y) {
        double cx = Math.clamp(x, 0.0, width - 1.0);
        double cy = Math.clamp(y, 0.0, height - 1.0);
        int x0 = (int) Math.floor(cx);
        int y0 = (int) Math.floor(cy);
        int x1 = Math.min(x0 + 1, width - 1);
        int y1 = Math.min(y0 + 1, height - 1);
        double fx = cx - x0;
        double fy = cy - y0;
        double top = values[y0 * width + x0] * (1.0 - fx) + values[y0 * width + x1] * fx;
        double bottom = values[y1 * width + x0] * (1.0 - fx) + values[y1 * width + x1] * fx;
        return top * (1.0 - fy) + bottom * fy;
    }

    /**
     * @return Largeur du repère local.
     */
    public int width() {
        return width;
    }

    /**
     * @return Hauteur du repère local.
     */
    public int height() {
        return height;
    }
}
