package com.sam102022.photoshop.v2.edge;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Garde opaque, sous le contour vectoriel, le terrain des îlots déjà retenus (en tout ou en partie) dans le territoire.
 * <p>
 * Quand le tracé de l'utilisateur coupe un îlot en zigzag, le contour lissé enjambe les creux du tracé, mais le
 * facteur d'affinage, calculé sur le masque consolidé (îlot ∩ tracé), y retombait à zéro : une encoche transparente
 * apparaissait à l'intérieur du territoire. Tout pixel situé à l'intérieur du contour et appartenant à un îlot dont
 * au moins {@value #MIN_OCCUPIED_AREA} px sont dans le territoire reçoit un facteur de 1. Le terrain des îlots voisins
 * (au-delà d'une route, autour des giratoires) et la chaussée restent soumis à l'affinage.
 * </p>
 */
public class PartialCellGuard {

    /** Surface minimale (px) d'un îlot dans le territoire pour que son terrain sous le contour soit gardé. */
    public static final int MIN_OCCUPIED_AREA = 500;

    /**
     * Construit une garde sans état.
     */
    public PartialCellGuard() {
        // Constructeur explicite sans état interne.
    }

    /**
     * Applique la garde à la carte de facteurs d'affinage.
     *
     * @param map          Carte de facteurs (repère de la fenêtre).
     * @param contour      Contour vectoriel final (repère de la fenêtre).
     * @param consolidated Territoire consolidé (repère de la fenêtre).
     * @param labelMap     Étiquettes des îlots (repère de la fenêtre).
     * @return Nouvelle carte de facteurs.
     * @throws NullPointerException si un argument est null.
     */
    public AlphaRefinementMap protect(AlphaRefinementMap map, SmoothVectorContour contour, BinaryMask consolidated,
                                      CellLabelMap labelMap) {
        Objects.requireNonNull(map, "map ne doit pas être nulle.");
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(consolidated, "consolidated ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "labelMap ne doit pas être nulle.");
        List<PixelPoint> points = contour.points();
        if (points.size() < 3) {
            return map;
        }
        int width = Math.min(map.width(), Math.min(consolidated.getWidth(), labelMap.width()));
        int height = Math.min(map.height(), Math.min(consolidated.getHeight(), labelMap.height()));
        boolean[] occupied = occupiedCells(consolidated, labelMap, width, height);
        float[][] factor = map.factor();
        double[] crossings = new double[points.size()];
        for (int y = 0; y < height; y++) {
            int count = rowCrossings(points, y + 0.5, crossings);
            for (int k = 0; k + 1 < count; k += 2) {
                int from = Math.max(0, (int) Math.ceil(crossings[k] - 0.5));
                int to = Math.min(width - 1, (int) Math.floor(crossings[k + 1] - 0.5));
                for (int x = from; x <= to; x++) {
                    int cellId = labelMap.getLabel(x, y);
                    if (cellId > 0 && cellId < occupied.length && occupied[cellId]) {
                        factor[y][x] = 1.0f;
                    }
                }
            }
        }
        return new AlphaRefinementMap(map.width(), map.height(), factor, map.cropWindow());
    }

    /**
     * Repère les îlots dont au moins {@link #MIN_OCCUPIED_AREA} px sont dans le territoire.
     *
     * @param consolidated Territoire consolidé.
     * @param labelMap     Étiquettes des îlots.
     * @param width        Largeur parcourue.
     * @param height       Hauteur parcourue.
     * @return Indicateur par étiquette.
     */
    private boolean[] occupiedCells(BinaryMask consolidated, CellLabelMap labelMap, int width, int height) {
        int[] area = new int[labelMap.cellCount() + 1];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cellId = labelMap.getLabel(x, y);
                if (cellId > 0 && cellId < area.length && consolidated.get(x, y)) {
                    area[cellId]++;
                }
            }
        }
        boolean[] occupied = new boolean[area.length];
        for (int id = 1; id < area.length; id++) {
            occupied[id] = area[id] >= MIN_OCCUPIED_AREA;
        }
        return occupied;
    }

    /**
     * Abscisses (triées) où le contour fermé coupe la ligne horizontale d'ordonnée y.
     *
     * @param points    Sommets du contour.
     * @param y         Ordonnée de la ligne.
     * @param crossings Tableau de sortie (au moins autant de cases que de sommets).
     * @return Nombre d'intersections.
     */
    private int rowCrossings(List<PixelPoint> points, double y, double[] crossings) {
        int n = points.size();
        int count = 0;
        for (int i = 0; i < n; i++) {
            PixelPoint a = points.get(i);
            PixelPoint b = points.get((i + 1) % n);
            if ((a.y() <= y) != (b.y() <= y)) {
                crossings[count++] = a.x() + (y - a.y()) / (b.y() - a.y()) * (b.x() - a.x());
            }
        }
        Arrays.sort(crossings, 0, count);
        return count;
    }
}
