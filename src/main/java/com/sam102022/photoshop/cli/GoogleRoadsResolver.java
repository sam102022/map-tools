package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.io.ImageLoader;
import com.sam102022.photoshop.v2.road.GoogleRoadsImage;
import com.sam102022.photoshop.v2.road.RoadDetectorStyle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Résolution de la capture Google « Routes seules » pour la ligne de commande V2.
 * <p>
 * Par défaut, le fichier {@value GoogleRoadsImage#DEFAULT_FILE_NAME} placé à côté de la carte est utilisé
 * s'il existe ({@code --google-roads <png>} pour un autre chemin, {@code --no-google-roads} pour l'ignorer).
 * Il fournit le masque routier (sauf si une source routière est imposée par {@code --road}, {@code --road-source}
 * ou {@code --osm-roads}) et, dans tous les cas, le bord des chaussées utilisé pour accrocher le contour.
 * Les voies rapides, repérées par leur teinte sur la carte, en sont retirées : le pipeline les traite à part
 * comme routes longées.
 */
final class GoogleRoadsResolver {

    /** Option donnant explicitement le chemin de la capture. */
    static final String GOOGLE_ROADS_OPTION = "--google-roads";
    /** Option désactivant l'usage de la capture. */
    static final String NO_GOOGLE_ROADS_OPTION = "--no-google-roads";

    private GoogleRoadsResolver() {
    }

    /**
     * Charge la capture « Routes seules » si elle est disponible.
     *
     * @param args    Arguments CLI.
     * @param mapImg  Carte source.
     * @param mapPath Chemin de la carte.
     * @return Capture nettoyée, ou null si absente, désactivée ou de dimensions différentes de la carte.
     * @throws IOException              en cas d'erreur de lecture.
     * @throws IllegalArgumentException si le chemin explicite n'existe pas ou si ses dimensions diffèrent.
     */
    static GoogleRoadsImage resolve(String[] args, BufferedImage mapImg, Path mapPath) throws IOException {
        if (V2CliRunner.hasOption(args, NO_GOOGLE_ROADS_OPTION)) {
            return null;
        }
        String explicit = V2CliRunner.getOptionValue(args, GOOGLE_ROADS_OPTION, null);
        Path path = explicit != null ? Paths.get(explicit)
                : mapPath.toAbsolutePath().resolveSibling(GoogleRoadsImage.DEFAULT_FILE_NAME);
        if (!Files.exists(path)) {
            if (explicit != null) {
                throw new IllegalArgumentException("La capture « Routes seules » n'existe pas : " + path);
            }
            return null;
        }
        BufferedImage image = ImageLoader.load(path);
        if (image.getWidth() != mapImg.getWidth() || image.getHeight() != mapImg.getHeight()) {
            String message = String.format("La capture « Routes seules » (%dx%d) n'a pas les dimensions de la carte (%dx%d).",
                    image.getWidth(), image.getHeight(), mapImg.getWidth(), mapImg.getHeight());
            if (explicit != null) {
                throw new IllegalArgumentException(message);
            }
            System.out.println("   [Avertissement] " + message + " Elle est ignorée.");
            return null;
        }
        System.out.println("-> [V2] Routes et bords de chaussée lus dans la capture Google : " + path);
        BinaryMask motorways = new RoadDetectorStyle().detectMotorways(mapImg);
        return GoogleRoadsImage.from(image, motorways);
    }

    /**
     * Indique si l'utilisateur impose une autre source pour le masque routier.
     *
     * @param args Arguments CLI.
     * @return true si {@code --road}, {@code --road-source} ou {@code --osm-roads} est fourni.
     */
    static boolean otherRoadSourceRequested(String[] args) {
        return V2CliRunner.getOptionValue(args, "--road", null) != null
                || V2CliRunner.getOptionValue(args, "--road-source", null) != null
                || V2CliRunner.getOptionValue(args, "--osm-roads", null) != null;
    }
}
