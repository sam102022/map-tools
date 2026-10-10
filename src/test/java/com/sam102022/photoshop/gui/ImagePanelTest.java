package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ImagePanelTest {

    @Test
    @DisplayName("Initialisation et manipulation de l'image sur ImagePanel")
    void testImagePanelOperations() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<ImagePanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ImagePanel panel = new ImagePanel("Aperçu Test", true);
            panelRef.set(panel);
        });

        ImagePanel panel = panelRef.get();
        assertNotNull(panel);
        assertNull(panel.getImage());

        BufferedImage testImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> panel.setImage(testImg));

        assertSame(testImg, panel.getImage());

        SwingUtilities.invokeAndWait(() -> panel.setImage(null));
        assertNull(panel.getImage());
    }
}
