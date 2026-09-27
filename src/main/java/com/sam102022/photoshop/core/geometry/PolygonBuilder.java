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
 * Rasterise un contour fermé dans un masque binaire de manière performante via Java2D.
 */
public class PolygonBuilder {
    private static final int ANTIALIASING_SUPERSAMPLE = 4;

    public BinaryMask rasterize(int width, int height, List<Point> polygon) {
        return rasterizeCoverage(width, height, polygon, false).toBinaryMask(128);
    }

    /** Rasterise un contour et conserve les couvertures partielles des pixels de bord. */
    public CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon, boolean antialiasing) {
        if (width <= 0 || height <= 0 || polygon == null) {
            throw new IllegalArgumentException("Dimensions et contour invalides.");
        }
        CoverageMask result = new CoverageMask(width, height);
        if (polygon.size() < 3) {
            return result;
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, polygon.size());
        Point first = polygon.get(0);
        path.moveTo(first.x, first.y);
        for (int i = 1; i < polygon.size(); i++) {
            Point p = polygon.get(i);
            path.lineTo(p.x, p.y);
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
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
            if (antialiasing) {
                g2d.scale(scale, scale);
                g2d.translate(-minX, -minY);
            } else {
                g2d.translate(-minX, -minY);
            }
            g2d.setColor(Color.WHITE);
            g2d.fill(path);
        } finally {
            g2d.dispose();
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int coverage;
                if (antialiasing) {
                    int sum = 0;
                    int sampleX = (x - minX) * scale;
                    int sampleY = (y - minY) * scale;
                    for (int sy = 0; sy < scale; sy++) {
                        for (int sx = 0; sx < scale; sx++) {
                            sum += bi.getRaster().getSample(sampleX + sx, sampleY + sy, 0) & 0xFF;
                        }
                    }
                    coverage = (sum + (scale * scale) / 2) / (scale * scale);
                } else {
                    coverage = bi.getRaster().getSample(x - minX, y - minY, 0) & 0xFF;
                }
                if (coverage != 0) result.set(x, y, coverage);
            }
        }

        return result;
    }
}
