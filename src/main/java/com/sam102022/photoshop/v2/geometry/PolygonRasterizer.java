package com.sam102022.photoshop.v2.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Convertit des anneaux polygonaux continus en coordonnées pixels en un masque binaire exact (P[x,y] ∈ {0, 1})
 * en gérant les anneaux extérieurs et les trous intérieurs selon la règle de remplissage pair-impair (Even-Odd).
 */
public final class PolygonRasterizer {

    /**
     * Initialise une nouvelle instance du rasteriseur de polygones.
     */
    public PolygonRasterizer() {
    }

    /**
     * Rasterise un polygone pixel en masque binaire.
     *
     * @param width      Largeur de l'image de destination (> 0).
     * @param height     Hauteur de l'image de destination (> 0).
     * @param outerRings Anneaux extérieurs (liste de listes de points pixels).
     * @param innerRings Trous intérieurs (liste de listes de points pixels).
     * @return Masque binaire où les pixels à l'intérieur du polygone sont à true.
     * @throws IllegalArgumentException si les dimensions sont inférieures ou égales à zéro.
     */
    public BinaryMask rasterize(int width, int height,
                                List<List<PixelPoint>> outerRings,
                                List<List<PixelPoint>> innerRings) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides : " + width + "x" + height);
        }
        if (outerRings == null || outerRings.isEmpty()) {
            return new BinaryMask(width, height);
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        for (List<PixelPoint> ring : outerRings) {
            addRingToPath(path, ring);
        }
        if (innerRings != null) {
            for (List<PixelPoint> hole : innerRings) {
                addRingToPath(path, hole);
            }
        }

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2 = canvas.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.WHITE);
            g2.fill(path);
        } finally {
            g2.dispose();
        }

        boolean[] data = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int rgb = canvas.getRGB(x, y) & 0xFFFFFF;
                if (rgb != 0) {
                    data[rowOffset + x] = true;
                }
            }
        }
        return new BinaryMask(width, height, data);
    }

    /**
     * Ajoute les segments d'un anneau polygonal au chemin 2D.
     *
     * @param path   Chemin géométrique Java2D à enrichir.
     * @param points Sommets consécutifs formant l'anneau fermé.
     */
    private void addRingToPath(Path2D.Double path, List<PixelPoint> points) {
        if (points == null || points.size() < 3) {
            return;
        }
        PixelPoint first = points.get(0);
        path.moveTo(first.x(), first.y());
        for (int i = 1; i < points.size(); i++) {
            PixelPoint pt = points.get(i);
            path.lineTo(pt.x(), pt.y());
        }
        path.closePath();
    }
}
