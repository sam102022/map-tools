package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Export des images détourées en PNG transparent (ARGB) avec lissage progressif et export des masques.
 */
public final class ImageExporter {

    private ImageExporter() {
    }

    /** Compatibilité historique : un masque binaire ne contient pas de couverture sub-pixel. */
    @Deprecated(forRemoval = false)
    public static BufferedImage createClippedImage(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
        return createClippedImageBinary(mapImage, mask, smoothRadius);
    }

    /** Crée une image détourée à partir d'un masque binaire ; préférer la surcharge CoverageMask pour l'anti-aliasing. */
    public static BufferedImage createClippedImageBinary(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
        if (mask == null) throw new IllegalArgumentException("mask ne peut pas être null.");
        return createClippedImage(mapImage, CoverageMask.fromBinaryMask(mask), smoothRadius);
    }

    /**
     * Crée une image détourée ARGB à partir d'un masque de couverture continue [0..255]
     * avec application éventuelle d'un adoucissement progressif (flou gaussien binomial).
     *
     * @param mapImage     Image source de la carte.
     * @param coverageMask Masque de couverture sub-pixel.
     * @param smoothRadius Rayon d'adoucissement des bords en pixels (0 pour sub-pixel pur, >= 1 pour progressif).
     * @return Image détourée avec transparence ARGB fluide sans effet d'escalier.
     */
    public static BufferedImage createClippedImage(BufferedImage mapImage, CoverageMask coverageMask, int smoothRadius) {
        if (mapImage == null || coverageMask == null) {
            throw new IllegalArgumentException("mapImage et coverageMask ne peuvent pas être null.");
        }
        if (mapImage.getWidth() != coverageMask.getWidth() || mapImage.getHeight() != coverageMask.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre mapImage et coverageMask.");
        }

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        CoverageMask effectiveMask = smoothRadius > 0
                ? smoothCoverage(coverageMask, smoothRadius)
                : coverageMask;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int coverage = effectiveMask.get(x, y);
                if (coverage == 0) continue;
                int sourcePixel = mapImage.getRGB(x, y);
                int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
                int finalAlpha = (sourceAlpha * coverage + 127) / 255;
                if (finalAlpha != 0) {
                    clipped.setRGB(x, y, (finalAlpha << 24) | (sourcePixel & 0x00FFFFFF));
                }
            }
        }

        return clipped;
    }

    /**
     * Applique un filtre d'adoucissement progressif séparable binomial (approximation gaussienne)
     * sur les valeurs de couverture [0..255].
     *
     * @param mask   Masque de couverture source.
     * @param radius Rayon du filtre en pixels (>= 1).
     * @return Nouveau masque de couverture adouci.
     * @throws IllegalArgumentException si mask est null.
     */
    public static CoverageMask smoothCoverage(CoverageMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("mask ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask;
        }

        int effectiveRadius = Math.min(radius, 5); // Limiter à 5 pour garantir la stabilité du noyau binomial
        int[] kernel = computeBinomialKernel(effectiveRadius);
        int shift = 2 * effectiveRadius;

        int[] hPass = applyHorizontalBlurPass(mask, kernel, effectiveRadius, shift);
        return applyVerticalBlurPass(hPass, mask.getWidth(), mask.getHeight(), kernel, effectiveRadius, shift);
    }

    /**
     * Génère le noyau de convolution binomial normalisé d'ordre 2R.
     *
     * @param effectiveRadius Rayon effectif de lissage [1..5].
     * @return Tableau des coefficients binomiaux de taille 2R + 1.
     */
    private static int[] computeBinomialKernel(int effectiveRadius) {
        int n = 2 * effectiveRadius;
        int[] kernel = new int[n + 1];
        for (int k = 0; k <= n; k++) {
            kernel[k] = binomialCoeff(n, k);
        }
        return kernel;
    }

    /**
     * Exécute la passe horizontale du filtre séparable binomial sur la couverture.
     *
     * @param mask            Masque de couverture source.
     * @param kernel          Noyau de convolution.
     * @param effectiveRadius Rayon effectif de lissage.
     * @param shift           Décalage binaire de normalisation.
     * @return Tableau linéaire 1D des valeurs filtrées horizontalement.
     */
    private static int[] applyHorizontalBlurPass(CoverageMask mask, int[] kernel, int effectiveRadius, int shift) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int halfSum = 1 << (shift - 1);
        int[] hPass = new int[w * h];

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int k = -effectiveRadius; k <= effectiveRadius; k++) {
                    int sx = Math.max(0, Math.min(w - 1, x + k));
                    sum += mask.get(sx, y) * kernel[k + effectiveRadius];
                }
                hPass[rowOffset + x] = (sum + halfSum) >> shift;
            }
        }
        return hPass;
    }

    /**
     * Exécute la passe verticale du filtre séparable binomial et peuple le masque de couverture résultant.
     *
     * @param hPass           Données issues de la passe horizontale.
     * @param w               Largeur de l'image.
     * @param h               Hauteur de l'image.
     * @param kernel          Noyau de convolution.
     * @param effectiveRadius Rayon effectif de lissage.
     * @param shift           Décalage binaire de normalisation.
     * @return Nouveau masque de couverture adouci.
     */
    private static CoverageMask applyVerticalBlurPass(int[] hPass, int w, int h, int[] kernel, int effectiveRadius, int shift) {
        int halfSum = 1 << (shift - 1);
        CoverageMask result = new CoverageMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                for (int k = -effectiveRadius; k <= effectiveRadius; k++) {
                    int sy = Math.max(0, Math.min(h - 1, y + k));
                    sum += hPass[sy * w + x] * kernel[k + effectiveRadius];
                }
                int finalCov = (sum + halfSum) >> shift;
                if (finalCov > 0) {
                    result.set(x, y, finalCov);
                }
            }
        }
        return result;
    }

    /**
     * Calcule le coefficient binomial C(n, k).
     *
     * @param n Ordre total.
     * @param k Indice d'échantillonnage.
     * @return Valeur entière du coefficient binomial.
     */
    private static int binomialCoeff(int n, int k) {
        if (k < 0 || k > n) return 0;
        if (k == 0 || k == n) return 1;
        long res = 1;
        for (int i = 1; i <= k; i++) {
            res = res * (n - i + 1) / i;
        }
        return (int) res;
    }

    /**
     * Crée une image RVB en noir et blanc à partir d'un masque binaire (blanc = actif, noir = inactif).
     *
     * @param mask Masque binaire d'entrée.
     * @return Image BufferedImage au format TYPE_INT_RGB.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BufferedImage createMaskImage(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("mask ne peut pas être null.");
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, mask.get(x, y) ? 0xFFFFFF : 0x000000);
            }
        }
        return image;
    }

    /**
     * Crée une image en niveaux de gris (BYTE_GRAY) préservant les couvertures sub-pixel partielles [0..255].
     *
     * @param mask Masque de couverture continue d'entrée.
     * @return Image BufferedImage en niveaux de gris au format TYPE_BYTE_GRAY.
     * @throws IllegalArgumentException si mask est null.
     */
    public static BufferedImage createCoverageMaskImage(CoverageMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("mask ne peut pas être null.");
        }
        BufferedImage image = new BufferedImage(mask.getWidth(), mask.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                image.getRaster().setSample(x, y, 0, mask.get(x, y));
            }
        }
        return image;
    }

    /**
     * Enregistre une image au format PNG vers le chemin de destination spécifié.
     *
     * @param image      Image à sauvegarder.
     * @param outputPath Chemin du fichier PNG de destination.
     * @throws IOException              si l'écriture disque échoue.
     * @throws IllegalArgumentException si image ou outputPath est null.
     */
    public static void savePng(BufferedImage image, Path outputPath) throws IOException {
        if (image == null || outputPath == null) {
            throw new IllegalArgumentException("image et outputPath ne peuvent pas être null.");
        }
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        File file = outputPath.toFile();
        boolean written = ImageIO.write(image, "PNG", file);
        if (!written) {
            throw new IOException("Impossible d'écrire l'image au format PNG vers : " + outputPath);
        }
    }
}
