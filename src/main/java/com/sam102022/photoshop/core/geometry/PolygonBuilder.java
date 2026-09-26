package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;

/**
 * Rasterise un contour fermé dans un masque binaire de manière performante via Java2D.
 */
public class PolygonBuilder {

    public BinaryMask rasterize(int width, int height, List<Point> polygon) {
        if (width <= 0 || height <= 0 || polygon == null) {
            throw new IllegalArgumentException("Dimensions et contour invalides.");
        }
        BinaryMask result = new BinaryMask(width, height);
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

        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bi.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fill(path);
        g2d.dispose();

        byte[] pixels = ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
        Rectangle bounds = path.getBounds();
        int minX = Math.max(0, bounds.x);
        int maxX = Math.min(width - 1, bounds.x + bounds.width);
        int minY = Math.max(0, bounds.y);
        int maxY = Math.min(height - 1, bounds.y + bounds.height);

        for (int y = minY; y <= maxY; y++) {
            int rowOffset = y * width;
            for (int x = minX; x <= maxX; x++) {
                if (pixels[rowOffset + x] != 0) {
                    result.set(x, y, true);
                }
            }
        }

        return result;
    }
}
