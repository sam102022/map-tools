package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest {

    @Test
    @DisplayName("Initialisation des composants graphiques Swing sans exception")
    void testWindowInitialization() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Environnement headless détecté, test visuel restreint.");
            return;
        }

        MainWindow window = new MainWindow();
        assertNotNull(window);
        assertEquals("Photoshop - Détourage de Carte Google Maps", window.getTitle());
        assertTrue(window.getWidth() >= 800);
        assertTrue(window.getHeight() >= 600);
        window.dispose();
    }
}
