package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.v2.zone.ZoneColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests du mode « zones » de la ligne de commande V2 {@link V2ZoneCliRunner}.
 */
@DisplayName("Tests du mode zones de la CLI V2")
class V2ZoneCliRunnerTest {

    /**
     * L'option {@code --zone} accepte numéros et noms ; sans elle, les six zones sont traitées.
     */
    @Test
    @DisplayName("Sélection des zones")
    void testSelectedZones() {
        assertEquals(List.of(ZoneColor.ROUGE, ZoneColor.MAUVE),
                V2ZoneCliRunner.selectedZones(new String[]{"--zone", "1,mauve"}));
        assertEquals(6, V2ZoneCliRunner.selectedZones(new String[]{}).size());
        assertThrows(IllegalArgumentException.class,
                () -> V2ZoneCliRunner.selectedZones(new String[]{"--zone", "7"}));
    }

    /**
     * Un plan des zones sans aucun trait de zone donne un code de sortie 1 ; un plan absent est une erreur.
     *
     * @param dir Dossier temporaire.
     * @throws IOException en cas d'erreur d'écriture.
     */
    @Test
    @DisplayName("Plan sans zone ou introuvable")
    void testNoZone(@TempDir Path dir) throws IOException {
        BufferedImage blank = new BufferedImage(200, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = blank.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 150);
        g.dispose();
        Path map = dir.resolve("map.png");
        Path zones = dir.resolve("zones.png");
        ImageIO.write(blank, "png", map.toFile());
        ImageIO.write(blank, "png", zones.toFile());

        assertEquals(1, V2CliRunner.run(new String[]{"--map", map.toString(), "--zones", zones.toString(),
                "--out-dir", dir.toString()}));
        assertEquals(1, V2CliRunner.run(new String[]{"--map", map.toString(), "--zones",
                dir.resolve("absent.png").toString()}));
    }

    /**
     * {@code --mode zone} active le mode zones ; sans {@code --zones}, le plan des zones est cherché à côté de la
     * carte.
     */
    @Test
    @DisplayName("--mode zone et plan des zones par défaut")
    void testModeZone() {
        assertEquals(true, V2ZoneCliRunner.isZoneRequest(new String[]{"--mode", "zone"}));
        assertEquals(false, V2ZoneCliRunner.isZoneRequest(new String[]{"--mode", "territory"}));
        assertEquals(Path.of("cartes", "02_plan_avec_zones.png").toAbsolutePath(),
                V2ZoneCliRunner.resolveZonesPath(new String[]{}, Path.of("cartes", "05.png")));
    }

    /**
     * En mode zones, le polygone à grands segments est plus tolérant par défaut (5 px) ; une valeur explicite reste
     * prioritaire et le mode territoire garde 1 px.
     */
    @Test
    @DisplayName("Tolérance du polygone par défaut en mode zones")
    void testZonePolygonTolerance() {
        assertEquals(5.0, V2CliRunner.parseConfig(new String[]{"--zones", "z.png"}).polygonTolerance(), 1e-9);
        assertEquals(2.0, V2CliRunner.parseConfig(new String[]{"--mode", "zone", "--polygon-tolerance", "2"})
                .polygonTolerance(), 1e-9);
        assertEquals(1.0, V2CliRunner.parseConfig(new String[]{}).polygonTolerance(), 1e-9);
    }

    /**
     * Sans {@code --json}, le fichier de géométrie est cherché à côté de la carte ; absent (ou chemin explicite
     * inexistant), une erreur est levée.
     *
     * @param dir Dossier temporaire.
     * @throws IOException en cas d'erreur d'écriture.
     */
    @Test
    @DisplayName("--json facultatif pour les territoires")
    void testDefaultJson(@TempDir Path dir) throws IOException {
        Path map = dir.resolve("05_style_contraste_sans_rien.png");
        assertThrows(IllegalArgumentException.class, () -> V2CliRunner.resolveJsonPath(new String[]{}, map));
        Files.writeString(dir.resolve("01_plan_avec_territoires.json"), "{}");
        assertEquals(dir.resolve("01_plan_avec_territoires.json").toAbsolutePath(),
                V2CliRunner.resolveJsonPath(new String[]{}, map));
        assertThrows(IllegalArgumentException.class,
                () -> V2CliRunner.resolveJsonPath(new String[]{"--json", dir.resolve("autre.json").toString()}, map));
    }
}
