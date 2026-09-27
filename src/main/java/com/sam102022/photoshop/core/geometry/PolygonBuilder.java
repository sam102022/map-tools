package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;

/**
 * Rasterise un contour fermé dans un masque binaire ou un masque de couverture continue [0..255]
 * via Java2D avec gestion de l'anti-aliasing sub-pixel et suréchantillonnage multi-échelles.
 */
public class PolygonBuilder {

    /**
     * Facteur de suréchantillonnage sub-pixel (grille 8x8, soit 64 sous-échantillons par pixel).
     * Garantit une couverture continue et stable même avec des coordonnées entières issues du traçage raster.
     */
    private static final int ANTIALIASING_SUPERSAMPLE = 8;

    /**
     * Boîte englobante restreinte aux dimensions de l'image pour la zone de rastérisation.
     *
     * @param minX Borne minimale en X.
     * @param maxX Borne maximale en X.
     * @param minY Borne minimale en Y.
     * @param maxY Borne maximale en Y.
     */
    private record RenderBounds(int minX, int maxX, int minY, int maxY) {

        /**
         * Vérifie si la zone englobante est valide et non vide.
         *
         * @return Vrai si minX <= maxX et minY <= maxY.
         */
        boolean isValid() {
            return minX <= maxX && minY <= maxY;
        }

        /**
         * Calcule la largeur en pixels de la boîte englobante.
         *
         * @return Largeur inclusive.
         */
        int width() {
            return maxX - minX + 1;
        }

        /**
         * Calcule la hauteur en pixels de la boîte englobante.
         *
         * @return Hauteur inclusive.
         */
        int height() {
            return maxY - minY + 1;
        }
    }

    /**
     * Rasterise un contour polygonal vers un masque binaire standard sans anti-aliasing.
     *
     * @param width   Largeur de l'image cible.
     * @param height  Hauteur de l'image cible.
     * @param polygon Liste ordonnée des sommets du polygone.
     * @return Masque binaire rasterisé.
     * @deprecated Préférer {@link #rasterizeBinary(int, int, List)} ou {@link #rasterizeCoverage(int, int, List, boolean)}.
     */
    @Deprecated(forRemoval = false)
    public BinaryMask rasterize(int width, int height, List<Point> polygon) {
        return rasterizeBinary(width, height, polygon);
    }

    /**
     * Rasterise explicitement un contour vers un masque binaire sans conserver de couverture partielle.
     *
     * @param width   Largeur de l'image cible.
     * @param height  Hauteur de l'image cible.
     * @param polygon Liste ordonnée des sommets du polygone.
     * @return Masque binaire où seuls les pixels pleins sont activés.
     * @throws IllegalArgumentException si width <= 0, height <= 0 ou polygon est null.
     */
    public BinaryMask rasterizeBinary(int width, int height, List<Point> polygon) {
        return rasterizeCoverage(width, height, polygon, false).toBinaryMask(128);
    }

    /**
     * Rasterise un contour polygonal et conserve les couvertures partielles [0..255] des pixels de bord.
     * En mode anti-aliasing, un suréchantillonnage 8x (grille 8x8, soit 64 sous-échantillons par pixel)
     * calcule avec précision la fraction surfacique de chaque pixel.
     *
     * @param width        Largeur de l'image cible.
     * @param height       Hauteur de l'image cible.
     * @param polygon      Liste ordonnée des sommets du polygone.
     * @param antialiasing Vrai pour activer l'anti-aliasing sub-pixel avec suréchantillonnage 8x.
     * @return Masque de couverture géométrique continue [0..255].
     * @throws IllegalArgumentException si width <= 0, height <= 0 ou polygon est null.
     */
    public CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon, boolean antialiasing) {
        return rasterizeCoverage(width, height, polygon, antialiasing, false);
    }

    /**
     * Rasterise un contour dont les coordonnées entières désignent le centre des pixels du premier plan.
     *
     * @param width        Largeur de l'image cible.
     * @param height       Hauteur de l'image cible.
     * @param polygon      Liste ordonnée des sommets du polygone.
     * @param antialiasing Vrai pour activer l'anti-aliasing sub-pixel avec suréchantillonnage 8x.
     * @return Masque de couverture géométrique continue [0..255].
     * @throws IllegalArgumentException si width <= 0, height <= 0 ou polygon est null.
     */
    public CoverageMask rasterizePixelCenterContour(int width, int height, List<Point> polygon, boolean antialiasing) {
        return rasterizeCoverage(width, height, polygon, antialiasing, true);
    }

    /**
     * Méthode interne de rasterisation de couverture supportant le suréchantillonnage et les coordonnées de centre de pixel.
     *
     * @param width                  Largeur de l'image cible.
     * @param height                 Hauteur de l'image cible.
     * @param polygon                Liste des sommets du polygone.
     * @param antialiasing           Vrai pour activer le suréchantillonnage sub-pixel anti-aliasé.
     * @param pixelCenterCoordinates Vrai pour appliquer le décalage de coordonnées au centre des pixels (+0.5).
     * @return Masque de couverture géométrique continue [0..255].
     * @throws IllegalArgumentException si les dimensions sont <= 0 ou si le polygone est null.
     */
    private CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon,
                                           boolean antialiasing, boolean pixelCenterCoordinates) {
        validateRasterizeArguments(width, height, polygon);
        CoverageMask result = new CoverageMask(width, height);
        if (polygon.size() < 3) {
            return result;
        }

        Path2D.Double path = createPath(polygon, pixelCenterCoordinates);
        RenderBounds bounds = computeRenderBounds(path, width, height, antialiasing);
        if (!bounds.isValid()) {
            return result;
        }

        int scale = antialiasing ? ANTIALIASING_SUPERSAMPLE : 1;
        byte[] samples = renderPolygonToBuffer(path, bounds, scale);
        populateCoverageMask(result, samples, bounds, scale, antialiasing);

        return result;
    }

    /**
     * Valide les dimensions de l'image et la non-nullité du polygone.
     *
     * @param width   Largeur de l'image.
     * @param height  Hauteur de l'image.
     * @param polygon Liste des sommets.
     * @throws IllegalArgumentException si les dimensions ou le polygone sont invalides.
     */
    private void validateRasterizeArguments(int width, int height, List<Point> polygon) {
        if (width <= 0 || height <= 0 || polygon == null) {
            throw new IllegalArgumentException("Dimensions et contour invalides.");
        }
    }

    /**
     * Construit le tracé géométrique fermé Java2D à partir de la liste des sommets du polygone.
     *
     * @param polygon                Liste des sommets.
     * @param pixelCenterCoordinates Vrai pour appliquer un décalage de +0.5 au centre des pixels.
     * @return Tracé Path2D.Double fermé.
     */
    private Path2D.Double createPath(List<Point> polygon, boolean pixelCenterCoordinates) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO, polygon.size());
        Point first = polygon.getFirst();
        double offset = pixelCenterCoordinates ? 0.5 : 0.0;
        path.moveTo(first.x + offset, first.y + offset);
        for (int i = 1; i < polygon.size(); i++) {
            Point p = polygon.get(i);
            path.lineTo(p.x + offset, p.y + offset);
        }
        path.closePath();
        return path;
    }

    /**
     * Calcule la boîte englobante de dessin en la bornant aux dimensions maximales de l'image.
     *
     * @param path         Tracé vectoriel.
     * @param width        Largeur de l'image cible.
     * @param height       Hauteur de l'image cible.
     * @param antialiasing Vrai si l'anti-aliasing nécessite une marge d'un pixel.
     * @return Rectangle englobant borné.
     */
    private RenderBounds computeRenderBounds(Path2D path, int width, int height, boolean antialiasing) {
        Rectangle bounds = path.getBounds();
        int margin = antialiasing ? 1 : 0;
        int minX = Math.max(0, bounds.x - margin);
        int maxX = (int) Math.min(width - 1L, (long) bounds.x + bounds.width + margin);
        int minY = Math.max(0, bounds.y - margin);
        int maxY = (int) Math.min(height - 1L, (long) bounds.y + bounds.height + margin);
        return new RenderBounds(minX, maxX, minY, maxY);
    }

    /**
     * Rendu vectoriel du polygone dans un tampon en niveaux de gris à l'échelle spécifiée.
     *
     * @param path   Tracé vectoriel.
     * @param bounds Boîte englobante de rendu.
     * @param scale  Facteur d'échelle de suréchantillonnage.
     * @return Tableau d'octets contenant les échantillons bruts de l'image de travail.
     */
    private byte[] renderPolygonToBuffer(Path2D path, RenderBounds bounds, int scale) {
        int renderWidth = Math.multiplyExact(bounds.width(), scale);
        int renderHeight = Math.multiplyExact(bounds.height(), scale);
        BufferedImage bi = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = bi.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (scale > 1) {
                g2d.scale(scale, scale);
            }
            g2d.translate(-bounds.minX(), -bounds.minY());
            g2d.setColor(Color.WHITE);
            g2d.fill(path);
        } finally {
            g2d.dispose();
        }
        return ((DataBufferByte) bi.getRaster().getDataBuffer()).getData();
    }

    /**
     * Remplit le masque de couverture en extrayant la couverture de chaque pixel dans la boîte englobante.
     *
     * @param result       Masque de couverture cible.
     * @param samples      Échantillons du tampon haute résolution.
     * @param bounds       Boîte englobante de rendu.
     * @param scale        Facteur d'échelle de suréchantillonnage (1 si aucun).
     * @param antialiasing Vrai si le suréchantillonnage doit être moyenné.
     */
    private void populateCoverageMask(CoverageMask result, byte[] samples, RenderBounds bounds, int scale, boolean antialiasing) {
        int renderWidth = Math.multiplyExact(bounds.width(), scale);

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            int targetRow = y - bounds.minY();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int targetCol = x - bounds.minX();
                int coverage = antialiasing
                        ? computeAverageCoverage(samples, renderWidth, targetCol, targetRow, scale)
                        : samples[targetRow * renderWidth + targetCol] & 0xFF;

                if (coverage != 0) {
                    result.set(x, y, coverage);
                }
            }
        }
    }

    /**
     * Calcule la couverture moyenne d'un pixel cible en moyennant sa grille de sous-échantillons.
     *
     * @param samples     Échantillons bruts du tampon haute résolution.
     * @param renderWidth Largeur du tampon haute résolution.
     * @param targetCol   Colonne locale du pixel dans la boîte englobante.
     * @param targetRow   Ligne locale du pixel dans la boîte englobante.
     * @param scale       Facteur d'échelle (nombre de sous-échantillons par dimension).
     * @return Valeur de couverture moyenne dans [0..255].
     */
    private int computeAverageCoverage(byte[] samples, int renderWidth, int targetCol, int targetRow, int scale) {
        int sum = 0;
        int startSampleY = targetRow * scale;
        int startSampleX = targetCol * scale;
        int samplesPerPixel = scale * scale;

        for (int sy = 0; sy < scale; sy++) {
            int rowOffset = (startSampleY + sy) * renderWidth;
            for (int sx = 0; sx < scale; sx++) {
                sum += samples[rowOffset + (startSampleX + sx)] & 0xFF;
            }
        }
        return (sum + samplesPerPixel / 2) / samplesPerPixel;
    }
}
