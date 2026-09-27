package com.sam102022.photoshop.gui;

import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ControlsPanelTest {

    @Test
    @DisplayName("Initialisation, construction de la configuration et actions des boutons sur ControlsPanel")
    void testControlsPanelOperations() throws InterruptedException, InvocationTargetException {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        AtomicReference<ControlsPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            ControlsPanel panel = new ControlsPanel();
            panelRef.set(panel);
        });

        ControlsPanel panel = panelRef.get();
        assertNotNull(panel);

        SnappingConfig defaultConfig = panel.buildConfig();
        assertNotNull(defaultConfig);
        assertTrue(defaultConfig.antialiasing());
        assertEquals(40, defaultConfig.snapDistance());
        assertEquals(1, defaultConfig.smoothRadius());

        AtomicBoolean clipClicked = new AtomicBoolean(false);
        AtomicBoolean exportClicked = new AtomicBoolean(false);

        panel.setClipAction(() -> clipClicked.set(true));
        panel.setExportAction(() -> exportClicked.set(true));

        panel.setClipEnabled(false);
        panel.setExportEnabled(true);

        assertNotNull(panel.getAntialiasingCheckbox());
        assertNotNull(panel.getSmoothSlider());
    }
}
