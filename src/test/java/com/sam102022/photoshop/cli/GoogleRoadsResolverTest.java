package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la résolution de la capture « Routes seules » {@link GoogleRoadsResolver}.
 */
@DisplayName("Tests de GoogleRoadsResolver")
class GoogleRoadsResolverTest {

    /**
     * La capture voisine de la carte est utilisée par défaut, ignorée avec {@code --no-google-roads} ; un chemin
     * explicite absent est une erreur ; une capture de dimensions différentes est ignorée.
     *
     * @param dir Dossier temporaire.
     * @throws IOException en cas d'erreur d'écriture.
     */
    @Test
    @DisplayName("Détection automatique, désactivation et erreurs")
    void testResolve(@TempDir Path dir) throws IOException {
        BufferedImage map = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        Path mapPath = dir.resolve("05_style_contraste_sans_rien.png");
        ImageIO.write(map, "png", mapPath.toFile());

        assertNull(GoogleRoadsResolver.resolve(new String[]{}, map, mapPath), "aucune capture voisine");

        ImageIO.write(new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB), "png",
                dir.resolve("06_routes_seules.png").toFile());
        assertNotNull(GoogleRoadsResolver.resolve(new String[]{}, map, mapPath));
        assertNull(GoogleRoadsResolver.resolve(new String[]{"--no-google-roads"}, map, mapPath));
        assertThrows(IllegalArgumentException.class, () -> GoogleRoadsResolver.resolve(
                new String[]{"--google-roads", dir.resolve("absent.png").toString()}, map, mapPath));

        ImageIO.write(new BufferedImage(30, 40, BufferedImage.TYPE_INT_RGB), "png",
                dir.resolve("06_routes_seules.png").toFile());
        assertNull(GoogleRoadsResolver.resolve(new String[]{}, map, mapPath), "dimensions différentes : ignorée");

        assertTrue(GoogleRoadsResolver.otherRoadSourceRequested(new String[]{"--road-source", "style"}));
        assertFalse(GoogleRoadsResolver.otherRoadSourceRequested(new String[]{"--ss", "4"}));
    }
}
