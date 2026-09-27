package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rasterise un contour fermé dans un masque binaire ou un masque de couverture continue
 * de manière performante via Java2D avec gestion de l'anti-aliasing sub-pixel.
 */
public class PolygonBuilder {
    // 8x gives 64 area samples per output pixel. This makes coverage stable even
    // when the contour has integer coordinates produced by raster contour tracing.
    private static final int ANTIALIASING_SUPERSAMPLE = 8;

    /** Compatibilité historique : rasterisation binaire sans anti-aliasing. */
    @Deprecated(forRemoval = false)
    public BinaryMask rasterize(int width, int height, List<Point> polygon) {
        return rasterizeBinary(width, height, polygon);
    }

    /** Rasterise explicitement vers un masque binaire sans conserver de couverture partielle. */
    public BinaryMask rasterizeBinary(int width, int height, List<Point> polygon) {
        return rasterizeCoverage(width, height, polygon, false).toBinaryMask(128);
    }

    /**
     * Rasterise un contour et conserve les couvertures partielles [0..255] des pixels de bord.
     * En mode anti-aliasing, un suréchantillonnage 8x (grille 8x8, soit 64 sous-échantillons par pixel)
     * calcule directement la couverture de chaque pixel.
     *
     * @param width        Largeur de l'image.
     * @param height       Hauteur de l'image.
     * @param polygon      Liste ordonnée des sommets du polygone.
     * @param antialiasing Vrai pour activer l'anti-aliasing sub-pixel avec suréchantillonnage 8x.
     * @return Masque de couverture géométrique continue [0..255].
     */
    public CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon, boolean antialiasing) {
        return rasterizeCoverage(width, height, polygon, antialiasing, false);
    }

    /** Rasterizes a contour whose integer coordinates denote foreground pixel centres. */
    public CoverageMask rasterizePixelCenterContour(int width, int height, List<Point> polygon, boolean antialiasing) {
        return rasterizeCoverage(width, height, polygon, antialiasing, true);
    }

    private CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon,
                                           boolean antialiasing, boolean pixelCenterCoordinates) {
        if (width <= 0 || height <= 0 || polygon == null) {
            throw new IllegalArgumentException("Dimensions et contour invalides.");
        }
        CoverageMask result = new CoverageMask(width, height);
        if (polygon.size() < 3) {
            return result;
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, polygon.size());
        Point first = polygon.get(0);
        double offset = pixelCenterCoordinates ? 0.5 : 0.0;
        path.moveTo(first.x + offset, first.y + offset);
        for (int i = 1; i < polygon.size(); i++) {
            Point p = polygon.get(i);
            path.lineTo(p.x + offset, p.y + offset);
        }
        path.closePath();

        Rectangle bounds = path.getBounds();
        int minX = Math.max(0, bounds.x - (antialiasing ? 1 : 0));
        int maxX = (int) Math.min(width - 1L, (long) bounds.x + bounds.width + (antialiasing ? 1 : 0));
        int minY = Math.max(0, bounds.y - (antialiasing ? 1 : 0));
        int maxY = (int) Math.min(height - 1L, (long) bounds.y + bounds.height + (antialiasing ? 1 : 0));
        if (minX > maxX || minY > maxY) return result;

        int scale = antialiasing ? ANTIALIASING_SUPERSAMPLE : 1;
        int renderWidth = Math.multiplyExact(maxX - minX + 1, scale);
        int renderHeight = Math.multiplyExact(maxY - minY + 1, scale);
        BufferedImage bi = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bi.createGraphics();
        try {
            if (antialiasing) {
                // Supersampling itself performs the antialiasing. Keeping each
                // high-resolution sample binary avoids applying Java2D AA a
                // second time and then averaging the softened samples again.
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.scale(scale, scale);
            } else {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            }
            g2d.translate(-minX, -minY);
            g2d.setColor(Color.WHITE);
            g2d.fill(path);
        } finally {
            g2d.dispose();
        }

        byte[] samples = ((java.awt.image.DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        int samplesPerPixel = scale * scale;
        int halfSamples = samplesPerPixel / 2;

        for (int y = minY; y <= maxY; y++) {
            int targetRow = y - minY;
            for (int x = minX; x <= maxX; x++) {
                int targetCol = x - minX;
                int coverage;
                if (antialiasing) {
                    int sum = 0;
                    int startSampleY = targetRow * scale;
                    int startSampleX = targetCol * scale;
                    for (int sy = 0; sy < scale; sy++) {
                        int rowOffset = (startSampleY + sy) * renderWidth;
                        for (int sx = 0; sx < scale; sx++) {
                            sum += samples[rowOffset + (startSampleX + sx)] & 0xFF;
                        }
                    }
                    coverage = (sum + halfSamples) / samplesPerPixel;
                } else {
                    coverage = samples[targetRow * renderWidth + targetCol] & 0xFF;
                }
                if (coverage != 0) {
                    result.set(x, y, coverage);
                }
            }
        }

        return result;
    }
}
