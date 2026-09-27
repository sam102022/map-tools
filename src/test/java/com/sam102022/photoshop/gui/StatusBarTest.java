package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StatusBarTest {

    @Test
    @DisplayName("Initialisation et modification du message dans StatusBar")
    void testStatusBarOperations() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<StatusBar> barRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            StatusBar bar = new StatusBar();
            barRef.set(bar);
        });

        StatusBar bar = barRef.get();
        assertNotNull(bar);
        assertEquals("Prêt", bar.getMessage());

        SwingUtilities.invokeAndWait(() -> bar.setMessage("Traitement en cours..."));
        assertEquals("Traitement en cours...", bar.getMessage());
    }
}
