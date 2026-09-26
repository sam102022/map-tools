package com.sam102022.photoshop.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Chargement et validation des dimensions des images cartographiques et calques.
 */
public final class ImageLoader {

    private ImageLoader() {
    }

    public static BufferedImage load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Le chemin de fichier ne peut pas être null.");
        }
        File file = path.toFile();
        if (!file.exists()) {
            throw new IOException("Fichier introuvable : " + path);
        }
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Format d'image non supporté ou fichier corrompu : " + path);
        }
        return image;
    }

    public static void validateDimensions(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) {
            throw new IllegalArgumentException("Les images à comparer ne peuvent pas être null.");
        }
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions discordantes : carte (%dx%d) vs masque (%dx%d)",
                    img1.getWidth(), img1.getHeight(), img2.getWidth(), img2.getHeight()
            ));
        }
    }
}
