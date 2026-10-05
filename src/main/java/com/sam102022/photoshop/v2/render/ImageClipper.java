package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Assembleur et découpeur d'images produisant le trio de livrables graphiques de production :
 * image détourée ARGB, masque monochrome 8-bit et image de contrôle avec contour rouge vif (Sprint 9).
 */
public class ImageClipper {

    private static final Logger LOGGER = Logger.getLogger(ImageClipper.class.getName());

    /**
     * Constructeur par défaut.
     */
    public ImageClipper() {
    }

    /**
     * Découpe l'image source selon le masque de couverture et assemble les trois images de sortie.
     *
     * @param mapImage     Image source cartographique (non nulle).
     * @param coverageMask Masque de couverture sub-pixel continue [0..255] (non nul).
     * @return Record immuable RenderResult contenant les trois images et le masque de couverture.
     * @throws NullPointerException     si mapImage ou coverageMask est nul.
     * @throws IllegalArgumentException si les dimensions sont divergentes.
     */
    public RenderResult clip(BufferedImage mapImage, CoverageMask coverageMask) {
        validateInputs(mapImage, coverageMask);

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();

        LOGGER.info(() -> String.format(
                "[Sprint 9] Assemblage des livrables finaux (%dx%d px)...", w, h
        ));

        BufferedImage clipped = createClippedImage(mapImage, coverageMask, w, h);
        BufferedImage mask = createMaskImage(coverageMask, w, h);
        BufferedImage overlay = createOverlayImage(mapImage, coverageMask, w, h);

        return new RenderResult(clipped, mask, overlay, coverageMask);
    }

    /**
     * Crée l'image cartographique détourée ARGB avec canal alpha calculé.
     */
    private BufferedImage createClippedImage(BufferedImage mapImage, CoverageMask coverageMask, int w, int h) {
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        boolean hasAlpha = mapImage.getColorModel().hasAlpha();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cov = coverageMask.get(x, y);
                if (cov == 0) {
                    continue;
                }
                int rgb = mapImage.getRGB(x, y);
                int srcAlpha = hasAlpha ? ((rgb >>> 24) & 0xFF) : 255;
                int finalAlpha = (srcAlpha * cov + 127) / 255;
                if (finalAlpha > 0) {
                    clipped.setRGB(x, y, (finalAlpha << 24) | (rgb & 0x00FFFFFF));
                }
            }
        }
        return clipped;
    }

    /**
     * Crée le masque monochrome 8-bit TYPE_BYTE_GRAY reflétant les niveaux de couverture.
     */
    private BufferedImage createMaskImage(CoverageMask coverageMask, int w, int h) {
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask.getRaster().setSample(x, y, 0, coverageMask.get(x, y));
            }
        }
        return mask;
    }

    /**
     * Crée l'image de contrôle overlay superposant la bordure rouge vif (#FF0000) sur la carte.
     */
    private BufferedImage createOverlayImage(BufferedImage mapImage, CoverageMask coverageMask, int w, int h) {
        BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // 1. Recopie rapide du fond cartographique
        Graphics2D g2 = overlay.createGraphics();
        try {
            g2.drawImage(mapImage, 0, 0, null);
        } finally {
            g2.dispose();
        }

        // 2. Détection de bordure morphologique : Edge = Mfull & ~erode(Mfull)
        boolean[] mFull = buildThresholdMask(coverageMask, w, h, 128);

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                if (mFull[rowOffset + x] && isEdgePixel(mFull, x, y, w, h)) {
                    overlay.setRGB(x, y, 0xFF0000);
                }
            }
        }

        return overlay;
    }

    /**
     * Construit un tableau booléen aplati à partir du seuillage de couverture.
     */
    private boolean[] buildThresholdMask(CoverageMask coverageMask, int w, int h, int threshold) {
        boolean[] mask = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                mask[rowOffset + x] = coverageMask.get(x, y) >= threshold;
            }
        }
        return mask;
    }

    /**
     * Détermine si un pixel actif possède au moins un voisin orthogonal inactif (bordure).
     */
    private boolean isEdgePixel(boolean[] mask, int x, int y, int w, int h) {
        if (x == 0 || x == w - 1 || y == 0 || y == h - 1) {
            return true;
        }
        return !mask[y * w + (x - 1)]
                || !mask[y * w + (x + 1)]
                || !mask[(y - 1) * w + x]
                || !mask[(y + 1) * w + x];
    }

    /**
     * Valide la cohérence des arguments d'entrée.
     */
    private void validateInputs(BufferedImage mapImage, CoverageMask coverageMask) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(coverageMask, "coverageMask ne doit pas être nul.");
        if (mapImage.getWidth() != coverageMask.getWidth() || mapImage.getHeight() != coverageMask.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions incompatibles entre mapImage (%dx%d) et coverageMask (%dx%d).",
                    mapImage.getWidth(), mapImage.getHeight(),
                    coverageMask.getWidth(), coverageMask.getHeight()
            ));
        }
    }
}
