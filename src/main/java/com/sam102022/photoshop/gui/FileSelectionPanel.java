package com.sam102022.photoshop.gui;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.util.function.Consumer;

/**
 * Panneau supérieur d'interface graphique permettant la sélection des fichiers de carte, de calque
 * et optionnellement du masque de territoire préexistant.
 */
public class FileSelectionPanel extends JPanel {
    private final JLabel mapLabel = new JLabel("Carte : Aucune sélectionnée");
    private final JLabel maskLabel = new JLabel("Calque : Aucun sélectionné");
    private final JLabel territoryMaskLabel = new JLabel("Masque Territoire (optionnel) : Aucun");

    private Consumer<File> mapFileConsumer;
    private Consumer<File> maskFileConsumer;
    private Consumer<File> territoryMaskFileConsumer;

    /**
     * Initialise le panneau de sélection de fichiers avec ses boutons et étiquettes associées.
     */
    public FileSelectionPanel() {
        setLayout(new GridLayout(3, 1, 5, 5));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMapBtn = new JButton("Parcourir Carte...");
        loadMapBtn.addActionListener(e -> chooseFile("Sélectionner la carte", this, mapFileConsumer));
        mapRow.add(loadMapBtn);
        mapRow.add(mapLabel);

        JPanel maskRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMaskBtn = new JButton("Parcourir Calque...");
        loadMaskBtn.addActionListener(e -> chooseFile("Sélectionner le calque", this, maskFileConsumer));
        maskRow.add(loadMaskBtn);
        maskRow.add(maskLabel);

        JPanel territoryRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadTerritoryBtn = new JButton("Parcourir Masque Territoire...");
        loadTerritoryBtn.addActionListener(e -> chooseFile("Sélectionner le masque de territoire", this, territoryMaskFileConsumer));
        territoryRow.add(loadTerritoryBtn);
        territoryRow.add(territoryMaskLabel);

        add(mapRow);
        add(maskRow);
        add(territoryRow);
    }

    /**
     * Ouvre une boîte de dialogue pour sélectionner un fichier image PNG ou JPEG.
     *
     * @param dialogTitle Titre de la boîte de dialogue.
     * @param parent      Composant parent pour l'affichage modal.
     * @param consumer    Action à exécuter avec le fichier choisi.
     */
    private void chooseFile(String dialogTitle, Component parent, Consumer<File> consumer) {
        if (consumer == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            consumer.accept(chooser.getSelectedFile());
        }
    }

    /**
     * Enregistre l'action exécutée lors de la sélection d'un fichier de carte.
     *
     * @param consumer Consommateur recevant le fichier de carte sélectionné.
     */
    public void setOnMapFileSelected(Consumer<File> consumer) {
        this.mapFileConsumer = consumer;
    }

    /**
     * Enregistre l'action exécutée lors de la sélection d'un fichier de calque.
     *
     * @param consumer Consommateur recevant le fichier de calque sélectionné.
     */
    public void setOnMaskFileSelected(Consumer<File> consumer) {
        this.maskFileConsumer = consumer;
    }

    /**
     * Enregistre l'action exécutée lors de la sélection d'un fichier de masque de territoire.
     *
     * @param consumer Consommateur recevant le fichier de masque de territoire sélectionné.
     */
    public void setOnTerritoryMaskFileSelected(Consumer<File> consumer) {
        this.territoryMaskFileConsumer = consumer;
    }

    /**
     * Met à jour le texte du libellé d'information de la carte.
     *
     * @param text Texte à afficher.
     */
    public void setMapLabelText(String text) {
        mapLabel.setText(text);
    }

    /**
     * Met à jour le texte du libellé d'information du calque.
     *
     * @param text Texte à afficher.
     */
    public void setMaskLabelText(String text) {
        maskLabel.setText(text);
    }

    /**
     * Met à jour le texte du libellé d'information du masque de territoire.
     *
     * @param text Texte à afficher.
     */
    public void setTerritoryMaskLabelText(String text) {
        territoryMaskLabel.setText(text);
    }
}
