package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FileSelectionPanelTest {

    @Test
    @DisplayName("Initialisation et mise à jour des libellés de sélection de fichiers")
    void testFileSelectionPanelOperations() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<FileSelectionPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FileSelectionPanel panel = new FileSelectionPanel();
            panelRef.set(panel);
        });

        FileSelectionPanel panel = panelRef.get();
        assertNotNull(panel);

        SwingUtilities.invokeAndWait(() -> {
            panel.setMapLabelText("Carte : test_map.png (800x600)");
            panel.setMaskLabelText("Calque : test_mask.png (800x600)");
        });

        panel.setOnMapFileSelected((File f) -> {});
        panel.setOnMaskFileSelected((File f) -> {});
    }
}
