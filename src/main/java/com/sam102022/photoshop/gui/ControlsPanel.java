package com.sam102022.photoshop.gui;

import com.sam102022.photoshop.core.model.SnappingConfig;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Panneau de commandes regroupant les curseurs de paramétrage, l'option d'anti-aliasing
 * et les boutons d'action (Détourer, Exporter).
 */
public class ControlsPanel extends JPanel {
    private final JSlider snapDistanceSlider = new JSlider(5, 100, 40);
    private final JSlider roadSensitivitySlider = new JSlider(5, 20, 10);
    private final JSlider smoothSlider = new JSlider(0, 5, 1);
    private final JCheckBox antialiasingCheckbox = new JCheckBox("Anti-aliasing", true);

    private final JButton clipButton = new JButton("Détourer");
    private final JButton exportButton = new JButton("Exporter...");

    /**
     * Initialise le panneau de commandes avec ses curseurs et boutons.
     */
    public ControlsPanel() {
        setLayout(new FlowLayout(FlowLayout.CENTER, 15, 10));

        snapDistanceSlider.setPaintTicks(true);
        snapDistanceSlider.setMajorTickSpacing(25);
        add(createSliderBox("Distance Snap (px):", snapDistanceSlider));

        roadSensitivitySlider.setPaintTicks(true);
        roadSensitivitySlider.setMajorTickSpacing(5);
        add(createSliderBox("Sensibilité Routes (x0.1):", roadSensitivitySlider));

        smoothSlider.setPaintTicks(true);
        smoothSlider.setMajorTickSpacing(1);
        add(createSliderBox("Lissage Bords (px):", smoothSlider));
        add(antialiasingCheckbox);

        clipButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(clipButton);

        exportButton.setEnabled(false);
        add(exportButton);
    }

    /**
     * Crée un conteneur vertical regroupant un libellé de titre et un curseur de réglage.
     *
     * @param title  Titre du réglage.
     * @param slider Curseur Swing à encapsuler.
     * @return Panneau contenant le libellé et le curseur.
     */
    private JPanel createSliderBox(String title, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Construit une instance immuable de {@link SnappingConfig} à partir des réglages actuels de l'interface.
     *
     * @return Configuration de recalage prête pour l'exécution.
     */
    public SnappingConfig buildConfig() {
        int snapDist = snapDistanceSlider.getValue();
        float sensitivity = roadSensitivitySlider.getValue() / 10.0f;
        int smooth = smoothSlider.getValue();
        boolean antialiasing = antialiasingCheckbox.isSelected();

        return SnappingConfig.builder()
                .snapDistance(snapDist)
                .roadSensitivity(sensitivity)
                .smoothRadius(smooth)
                .antialiasing(antialiasing)
                .build();
    }

    /**
     * Définit l'action à exécuter lors du clic sur le bouton "Détourer".
     *
     * @param action Action exécutable.
     */
    public void setClipAction(Runnable action) {
        clipButton.addActionListener(e -> {
            if (action != null) action.run();
        });
    }

    /**
     * Définit l'action à exécuter lors du clic sur le bouton "Exporter...".
     *
     * @param action Action exécutable.
     */
    public void setExportAction(Runnable action) {
        exportButton.addActionListener(e -> {
            if (action != null) action.run();
        });
    }

    /**
     * Active ou désactive le bouton "Détourer".
     *
     * @param enabled Vrai pour activer, faux pour désactiver.
     */
    public void setClipEnabled(boolean enabled) {
        clipButton.setEnabled(enabled);
    }

    /**
     * Active ou désactive le bouton "Exporter...".
     *
     * @param enabled Vrai pour activer, faux pour désactiver.
     */
    public void setExportEnabled(boolean enabled) {
        exportButton.setEnabled(enabled);
    }

    /**
     * Accesseur au composant de case à cocher anti-aliasing.
     *
     * @return Composant JCheckBox de l'anti-aliasing.
     */
    public JCheckBox getAntialiasingCheckbox() {
        return antialiasingCheckbox;
    }

    /**
     * Accesseur au curseur de réglage du lissage des bords.
     *
     * @return Composant JSlider du lissage.
     */
    public JSlider getSmoothSlider() {
        return smoothSlider;
    }
}
