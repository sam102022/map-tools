package com.sam102022.photoshop.io;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.Objects;

/**
 * Lecture rapide des pixels d'une image au format {@code 0xAARRGGBB}, avec le même résultat que
 * {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)}.
 * <p>
 * Pour les images sRGB 8 bits usuelles (PNG décodés en {@code TYPE_3BYTE_BGR}, {@code TYPE_4BYTE_ABGR},
 * {@code TYPE_INT_RGB}, {@code TYPE_INT_ARGB}), les octets sont lus directement dans le tampon de l'image au lieu de
 * passer par le modèle de couleur pixel par pixel ; les autres images passent par {@code getRGB}.
 * </p>
 */
public final class ImagePixels {

    /**
     * Constructeur privé : classe utilitaire.
     */
    private ImagePixels() {
        // Classe utilitaire.
    }

    /**
     * Lit l'image entière.
     *
     * @param image Image (non nulle).
     * @return Pixels {@code 0xAARRGGBB}, ligne par ligne.
     */
    public static int[] argb(BufferedImage image) {
        Objects.requireNonNull(image, "image ne doit pas être nulle.");
        return argb(image, 0, 0, image.getWidth(), image.getHeight());
    }

    /**
     * Lit un rectangle de l'image.
     *
     * @param image  Image (non nulle).
     * @param x0     Abscisse du coin.
     * @param y0     Ordonnée du coin.
     * @param width  Largeur du rectangle.
     * @param height Hauteur du rectangle.
     * @return Pixels {@code 0xAARRGGBB} du rectangle, ligne par ligne.
     */
    public static int[] argb(BufferedImage image, int x0, int y0, int width, int height) {
        Objects.requireNonNull(image, "image ne doit pas être nulle.");
        Raster raster = image.getRaster();
        boolean direct = image.getColorModel().getColorSpace().isCS_sRGB()
                && raster.getSampleModelTranslateX() == 0 && raster.getSampleModelTranslateY() == 0
                && x0 >= 0 && y0 >= 0 && x0 + width <= image.getWidth() && y0 + height <= image.getHeight();
        if (direct) {
            switch (image.getType()) {
                case BufferedImage.TYPE_3BYTE_BGR:
                    return fromBytes(((DataBufferByte) raster.getDataBuffer()).getData(), image.getWidth(), 3,
                            x0, y0, width, height);
                case BufferedImage.TYPE_4BYTE_ABGR:
                    return fromBytes(((DataBufferByte) raster.getDataBuffer()).getData(), image.getWidth(), 4,
                            x0, y0, width, height);
                case BufferedImage.TYPE_INT_RGB:
                case BufferedImage.TYPE_INT_ARGB:
                    return fromInts(((DataBufferInt) raster.getDataBuffer()).getData(), image.getWidth(),
                            image.getType() == BufferedImage.TYPE_INT_RGB, x0, y0, width, height);
                default:
                    break;
            }
        }
        return image.getRGB(x0, y0, width, height, null, 0, width);
    }

    /**
     * Convertit un tampon d'octets BGR ou ABGR entrelacés.
     *
     * @param data       Octets de l'image.
     * @param imageWidth Largeur de l'image.
     * @param bands      3 (BGR) ou 4 (ABGR).
     * @param x0         Abscisse du coin.
     * @param y0         Ordonnée du coin.
     * @param width      Largeur du rectangle.
     * @param height     Hauteur du rectangle.
     * @return Pixels {@code 0xAARRGGBB}.
     */
    private static int[] fromBytes(byte[] data, int imageWidth, int bands, int x0, int y0, int width, int height) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            int src = ((y0 + y) * imageWidth + x0) * bands;
            int dst = y * width;
            for (int x = 0; x < width; x++, src += bands) {
                int a = bands == 4 ? data[src] & 0xFF : 0xFF;
                int b = data[src + bands - 3] & 0xFF;
                int g = data[src + bands - 2] & 0xFF;
                int r = data[src + bands - 1] & 0xFF;
                out[dst + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }

    /**
     * Copie un tampon d'entiers RGB ou ARGB.
     *
     * @param data       Entiers de l'image.
     * @param imageWidth Largeur de l'image.
     * @param opaque     true pour RGB (alpha forcé à 255).
     * @param x0         Abscisse du coin.
     * @param y0         Ordonnée du coin.
     * @param width      Largeur du rectangle.
     * @param height     Hauteur du rectangle.
     * @return Pixels {@code 0xAARRGGBB}.
     */
    private static int[] fromInts(int[] data, int imageWidth, boolean opaque, int x0, int y0, int width, int height) {
        int[] out = new int[width * height];
        for (int y = 0; y < height; y++) {
            System.arraycopy(data, (y0 + y) * imageWidth + x0, out, y * width, width);
            if (opaque) {
                for (int x = 0; x < width; x++) {
                    out[y * width + x] |= 0xFF000000;
                }
            }
        }
        return out;
    }
}
