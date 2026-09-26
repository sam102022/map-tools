package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
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
}
