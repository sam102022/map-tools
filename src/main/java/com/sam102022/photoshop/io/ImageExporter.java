package com.sam102022.photoshop.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Export des livrables (images détourées, masques, contrôles) au format PNG.
 */
public final class ImageExporter {

    /**
     * Constructeur privé : classe utilitaire.
     */
    private ImageExporter() {
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
