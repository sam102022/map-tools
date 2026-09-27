package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest {

    @Test
    @DisplayName("Initialisation des composants graphiques Swing sur l'EDT sans exception")
    void testWindowInitialization() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Environnement headless détecté, test visuel restreint.");
            return;
        }

        AtomicReference<MainWindow> windowRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            windowRef.set(window);
        });

        MainWindow window = windowRef.get();
        assertNotNull(window);
        assertEquals("Photoshop - Détourage de Carte Google Maps", window.getTitle());
        assertTrue(window.getWidth() >= 800);
        assertTrue(window.getHeight() >= 600);

        SwingUtilities.invokeAndWait(window::dispose);
    }

    @Test
    @DisplayName("Vérification de la présence et de la valeur par défaut de la case à cocher Anti-aliasing")
    void testAntialiasingCheckboxComponent() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<MainWindow> windowRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            windowRef.set(window);
        });

        MainWindow window = windowRef.get();
        assertNotNull(window);

        JCheckBox aaCheckbox = window.getAntialiasingCheckbox();
        assertNotNull(aaCheckbox, "La case à cocher anti-aliasing doit exister");
        assertTrue(aaCheckbox.isSelected(), "L'anti-aliasing doit être coché par défaut");
        assertEquals("Anti-aliasing", aaCheckbox.getText());

        SwingUtilities.invokeAndWait(window::dispose);
    }

    @Test
    @DisplayName("Détourage via l'interface GUI avec anti-aliasing actif produit des nuances sub-pixel")
    void testClippingWithAntialiasingEnabled() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<MainWindow> windowRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            // Créer une carte blanche et un calque vert avec un triangle oblique
            BufferedImage map = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            BufferedImage mask = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = mask.createGraphics();
            g.setColor(new Color(0, 220, 0));
            Polygon poly = new Polygon(new int[]{10, 40, 20}, new int[]{10, 15, 40}, 3);
            g.fillPolygon(poly);
            g.dispose();

            window.setTestImages(map, mask);
            window.getAntialiasingCheckbox().setSelected(true);
            window.triggerClipping();
            windowRef.set(window);
        });

        MainWindow window = windowRef.get();

        // Attendre la fin du SwingWorker de détourage
        waitForClippingCompletion(window);

        BufferedImage clipped = window.getClippedImage();
        assertNotNull(clipped, "L'image détourée doit être générée");

        int intermediateAlpha = 0;
        for (int y = 0; y < clipped.getHeight(); y++) {
            for (int x = 0; x < clipped.getWidth(); x++) {
                int alpha = (clipped.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) {
                    intermediateAlpha++;
                }
            }
        }
        assertTrue(intermediateAlpha > 5, "Le résultat GUI avec AA doit comporter des valeurs sub-pixel ]0, 255[, trouvé: " + intermediateAlpha);

        SwingUtilities.invokeAndWait(window::dispose);
    }

    @Test
    @DisplayName("Détourage via l'interface GUI avec anti-aliasing désactivé produit un résultat binaire dur")
    void testClippingWithAntialiasingDisabled() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<MainWindow> windowRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow();
            BufferedImage map = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            BufferedImage mask = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = mask.createGraphics();
            g.setColor(new Color(0, 220, 0));
            Polygon poly = new Polygon(new int[]{10, 40, 20}, new int[]{10, 15, 40}, 3);
            g.fillPolygon(poly);
            g.dispose();

            window.setTestImages(map, mask);
            window.getSmoothSlider().setValue(0);
            window.getAntialiasingCheckbox().setSelected(false);
            window.triggerClipping();
            windowRef.set(window);
        });

        MainWindow window = windowRef.get();

        waitForClippingCompletion(window);

        BufferedImage clipped = window.getClippedImage();
        assertNotNull(clipped, "L'image détourée doit être générée");

        for (int y = 0; y < clipped.getHeight(); y++) {
            for (int x = 0; x < clipped.getWidth(); x++) {
                int alpha = (clipped.getRGB(x, y) >>> 24) & 0xFF;
                assertTrue(alpha == 0 || alpha == 255, "Sans AA et sans lissage, l'alpha doit être strictement 0 ou 255, trouvé: " + alpha);
            }
        }

        SwingUtilities.invokeAndWait(window::dispose);
    }

    private void waitForClippingCompletion(MainWindow window) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (window.getClippedImage() != null) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Le détourage dans l'EDT a dépassé le délai imparti de 5 secondes.");
    }
}
