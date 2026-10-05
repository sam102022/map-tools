package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Rastériseur sub-pixel haute-fidélité par supersampling ($SS=4$), filtrage boîte (Box Filter)
 * et modulation spectrale directe (Sprint 9).
 */
public class SupersampleRenderer {

    private static final Logger LOGGER = Logger.getLogger(SupersampleRenderer.class.getName());

    /**
     * Constructeur par défaut.
     */
    public SupersampleRenderer() {
    }

    /**
     * Rastérise le contour vectoriel lissé avec sur-échantillonnage, applique la réduction boîte
     * et module la couverture continue avec la matrice d'affinage spectral.
     *
     * @param contour             Contour vectoriel lissé sub-pixel (non nul).
     * @param refinementMap       Matrice de modulation d'affinage spectral locale (non nulle).
     * @param supersamplingFactor Facteur d'échantillonnage sub-pixel (&gt;= 1, nominalement 4).
     * @param fullWidth           Largeur totale du canevas cartographique global en pixels (&gt; 0).
     * @param fullHeight          Hauteur totale du canevas cartographique global en pixels (&gt; 0).
     * @return Masque matriciel de couverture continue [0..255] conforme à l'ADR-002.
     * @throws NullPointerException     si contour ou refinementMap est nul.
     * @throws IllegalArgumentException si les dimensions ou le facteur SS sont invalides.
     */
    public CoverageMask render(
            SmoothVectorContour contour,
            AlphaRefinementMap refinementMap,
            int supersamplingFactor,
            int fullWidth,
            int fullHeight
    ) {
        validateInputs(contour, refinementMap, supersamplingFactor, fullWidth, fullHeight);

        CropWindow crop = contour.cropWindow();
        int localW = crop.width();
        int localH = crop.height();
        int ssW = localW * supersamplingFactor;
        int ssH = localH * supersamplingFactor;

        LOGGER.info(() -> String.format(
                "[Sprint 9] Rastérisation sub-pixel SS=%d sur ROI (%dx%d px -> %dx%d sous-pixels)...",
                supersamplingFactor, localW, localH, ssW, ssH
        ));

        byte[] highResData = rasterizeHighResPolygon(contour.points(), ssW, ssH, supersamplingFactor);
        CoverageMask globalCoverage = new CoverageMask(fullWidth, fullHeight);

        populateGlobalCoverage(globalCoverage, highResData, refinementMap, crop, supersamplingFactor, fullWidth, fullHeight);
        return globalCoverage;
    }

    /**
     * Remplit le masque de couverture global par Box Filter et modulation spectrale.
     *
     * @param globalCoverage      Masque de couverture global à renseigner.
     * @param highResData         Données de l'image haute résolution sub-pixel.
     * @param refinementMap       Matrice locale des facteurs d'affinage.
     * @param crop                Fenêtre de recadrage locale.
     * @param supersamplingFactor Facteur d'échelle SS.
     * @param fullWidth           Largeur globale.
     * @param fullHeight          Hauteur globale.
     */
    private void populateGlobalCoverage(
            CoverageMask globalCoverage,
            byte[] highResData,
            AlphaRefinementMap refinementMap,
            CropWindow crop,
            int supersamplingFactor,
            int fullWidth,
            int fullHeight
    ) {
        int localW = crop.width();
        int localH = crop.height();
        int ssW = localW * supersamplingFactor;
        float totalSubPixels = (float) (supersamplingFactor * supersamplingFactor);

        for (int ly = 0; ly < localH; ly++) {
            int gy = crop.y0() + ly;
            if (gy < 0 || gy >= fullHeight) {
                continue;
            }
            populateCoverageRow(globalCoverage, highResData, refinementMap, crop, ly, gy, ssW, totalSubPixels, supersamplingFactor, fullWidth);
        }
    }

    /**
     * Remplit une rangée horizontale du masque de couverture global.
     */
    private void populateCoverageRow(
            CoverageMask globalCoverage,
            byte[] highResData,
            AlphaRefinementMap refinementMap,
            CropWindow crop,
            int ly,
            int gy,
            int ssW,
            float totalSubPixels,
            int ss,
            int fullWidth
    ) {
        int localW = crop.width();
        for (int lx = 0; lx < localW; lx++) {
            int gx = crop.x0() + lx;
            if (gx < 0 || gx >= fullWidth) {
                continue;
            }

            int activeCount = countActiveSubPixels(highResData, lx, ly, ssW, ss);
            if (activeCount == 0) {
                continue;
            }

            float alphaSs = activeCount / totalSubPixels;
            float finalAlpha = alphaSs * refinementMap.factorAt(lx, ly);
            int cov = Math.clamp(Math.round(finalAlpha * 255.0f), 0, 255);
            if (cov > 0) {
                globalCoverage.set(gx, gy, cov);
            }
        }
    }

    /**
     * Rastérise le polygone continu avec décalage de demi-pixel sur une image haute résolution monochrome.
     *
     * @param points Sommets du contour vectoriel dans le repère local de la ROI.
     * @param ssW    Largeur haute résolution.
     * @param ssH    Hauteur haute résolution.
     * @param ss     Facteur d'échelle de supersampling.
     * @return Tableau linéaire des octets de l'image (0 ou 255).
     */
    private byte[] rasterizeHighResPolygon(List<PixelPoint> points, int ssW, int ssH, int ss) {
        BufferedImage highRes = new BufferedImage(ssW, ssH, BufferedImage.TYPE_BYTE_GRAY);
        if (points.size() < 3) {
            return ((DataBufferByte) highRes.getRaster().getDataBuffer()).getData();
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD, points.size());
        PixelPoint first = points.get(0);
        path.moveTo((first.x() + 0.5) * ss, (first.y() + 0.5) * ss);

        for (int i = 1; i < points.size(); i++) {
            PixelPoint p = points.get(i);
            path.lineTo((p.x() + 0.5) * ss, (p.y() + 0.5) * ss);
        }
        path.closePath();

        Graphics2D g2 = highRes.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.WHITE);
            g2.fill(path);
        } finally {
            g2.dispose();
        }

        return ((DataBufferByte) highRes.getRaster().getDataBuffer()).getData();
    }

    /**
     * Dénombre le nombre de sous-pixels actifs dans le bloc carré SS x SS.
     *
     * @param data Tableau d'octets de l'image haute résolution.
     * @param lx   Abscisse locale du pixel cible.
     * @param ly   Ordonnée locale du pixel cible.
     * @param ssW  Largeur haute résolution.
     * @param ss   Facteur d'échelle.
     * @return Nombre de sous-pixels non nuls compris entre 0 et ss*ss.
     */
    private int countActiveSubPixels(byte[] data, int lx, int ly, int ssW, int ss) {
        if (ss == 4) {
            int startY = ly << 2;
            int startX = lx << 2;
            int r0 = startY * ssW + startX;
            int r1 = r0 + ssW;
            int r2 = r1 + ssW;
            int r3 = r2 + ssW;

            int count = 0;
            if (data[r0] != 0) count++;
            if (data[r0 + 1] != 0) count++;
            if (data[r0 + 2] != 0) count++;
            if (data[r0 + 3] != 0) count++;

            if (data[r1] != 0) count++;
            if (data[r1 + 1] != 0) count++;
            if (data[r1 + 2] != 0) count++;
            if (data[r1 + 3] != 0) count++;

            if (data[r2] != 0) count++;
            if (data[r2 + 1] != 0) count++;
            if (data[r2 + 2] != 0) count++;
            if (data[r2 + 3] != 0) count++;

            if (data[r3] != 0) count++;
            if (data[r3 + 1] != 0) count++;
            if (data[r3 + 2] != 0) count++;
            if (data[r3 + 3] != 0) count++;
            return count;
        }

        int count = 0;
        int startY = ly * ss;
        int startX = lx * ss;

        for (int dy = 0; dy < ss; dy++) {
            int rowOffset = (startY + dy) * ssW;
            for (int dx = 0; dx < ss; dx++) {
                if (data[rowOffset + startX + dx] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Valide les préconditions et arguments d'entrée.
     *
     * @param contour             Contour vectoriel lissé.
     * @param refinementMap       Matrice d'affinage spectral.
     * @param supersamplingFactor Facteur SS.
     * @param fullWidth           Largeur totale.
     * @param fullHeight          Hauteur totale.
     */
    private void validateInputs(
            SmoothVectorContour contour,
            AlphaRefinementMap refinementMap,
            int supersamplingFactor,
            int fullWidth,
            int fullHeight
    ) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(refinementMap, "refinementMap ne doit pas être nulle.");
        if (supersamplingFactor < 1) {
            throw new IllegalArgumentException("supersamplingFactor doit être >= 1 : " + supersamplingFactor);
        }
        if (fullWidth <= 0 || fullHeight <= 0) {
            throw new IllegalArgumentException("Dimensions du canevas invalides : " + fullWidth + "x" + fullHeight);
        }
    }
}
