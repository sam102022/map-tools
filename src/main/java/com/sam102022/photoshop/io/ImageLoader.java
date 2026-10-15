package com.sam102022.photoshop.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Chargement des images cartographiques et calques.
 */
public final class ImageLoader {

    /**
     * Constructeur privé : classe utilitaire.
     */
    private ImageLoader() {
    }

    /**
     * Charge une image depuis le disque.
     *
     * @param path Chemin du fichier image.
     * @return Image chargée.
     * @throws IOException              si le fichier est introuvable, illisible ou dans un format non supporté.
     * @throws IllegalArgumentException si path est null.
     */
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
}
