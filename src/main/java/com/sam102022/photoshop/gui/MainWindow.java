package com.sam102022.photoshop.gui;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Fenêtre principale de l'interface graphique Swing orchestrant les sous-composants visuels,
 * le chargement des cartes/calques, le calcul asynchrone du détourage et l'exportation.
 */
public class MainWindow extends JFrame {

    /**
     * Résultat intermédiaire complet du calcul de détourage.
     *
     * @param image        Image finale détourée au format ARGB.
     * @param coverageMask Masque de couverture sub-pixel continue.
     * @param mask         Masque binaire seuillé correspondant.
     */
    private record ClippingResult(BufferedImage image, CoverageMask coverageMask, BinaryMask mask) {}

    private BufferedImage mapImage;
    private BufferedImage maskImage;
    private BufferedImage clippedImage;
    private CoverageMask resultCoverage;
    private BinaryMask resultMask;

    private final FileSelectionPanel fileSelectionPanel = new FileSelectionPanel();
    private final ImagePanel previewOriginalPanel = new ImagePanel("Aperçu Original (Carte + Masque)");
    private final ImagePanel previewResultPanel = new ImagePanel("Résultat Détouré (Transparence)", true);
    private final ControlsPanel controlsPanel = new ControlsPanel();
    private final StatusBar statusBar = new StatusBar();

    /**
     * Initialise la fenêtre principale, positionne les sous-composants et connecte les gestionnaires d'événements.
     */
    public MainWindow() {
        setTitle("Photoshop - Détourage de Carte Google Maps");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        initUi();
        initEventHandlers();
    }

    /**
     * Configure l'agencement BorderLayout et intègre les sous-composants modulaires.
     */
    private void initUi() {
        setLayout(new BorderLayout());

        // 1. Panneau Supérieur : Sélecteurs de fichiers
        add(fileSelectionPanel, BorderLayout.NORTH);

        // 2. Panneau Central : Affichage Avant / Après en panneau divisé
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewOriginalPanel, previewResultPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        // 3. Panneau Inférieur : Commandes et Barre d'état
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.add(controlsPanel, BorderLayout.CENTER);
        bottomContainer.add(statusBar, BorderLayout.SOUTH);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    /**
     * Connecte les rappels d'événements des sous-composants aux actions métier de la fenêtre.
     */
    private void initEventHandlers() {
        fileSelectionPanel.setOnMapFileSelected(this::loadMapFile);
        fileSelectionPanel.setOnMaskFileSelected(this::loadMaskFile);

        controlsPanel.setClipAction(this::runClipping);
        controlsPanel.setExportAction(this::exportResult);
    }

    /**
     * Charge une image de carte depuis un fichier sélectionné par l'utilisateur.
     *
     * @param file Fichier image de la carte.
     */
    private void loadMapFile(File file) {
        try {
            mapImage = ImageLoader.load(file.toPath());
            fileSelectionPanel.setMapLabelText(String.format("Carte : %s (%dx%d)", file.getName(), mapImage.getWidth(), mapImage.getHeight()));
            updateOriginalPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Charge une image de calque depuis un fichier sélectionné par l'utilisateur.
     *
     * @param file Fichier image du calque.
     */
    private void loadMaskFile(File file) {
        try {
            maskImage = ImageLoader.load(file.toPath());
            fileSelectionPanel.setMaskLabelText(String.format("Calque : %s (%dx%d)", file.getName(), maskImage.getWidth(), maskImage.getHeight()));
            updateOriginalPreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Met à jour l'aperçu composite original en superposant le calque sur la carte avec transparence.
     */
    private void updateOriginalPreview() {
        if (mapImage == null) return;
        if (maskImage == null) {
            previewOriginalPanel.setImage(mapImage);
            return;
        }

        if (mapImage.getWidth() != maskImage.getWidth() || mapImage.getHeight() != maskImage.getHeight()) {
            previewOriginalPanel.setImage(mapImage);
            JOptionPane.showMessageDialog(this, "Attention : Les dimensions de la carte et du calque diffèrent.", "Avertissement", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BufferedImage composite = new BufferedImage(mapImage.getWidth(), mapImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        g.drawImage(mapImage, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.drawImage(maskImage, 0, 0, null);
        g.dispose();

        previewOriginalPanel.setImage(composite);
    }

    /**
     * Lance le traitement asynchrone de détourage via un {@link SwingWorker}.
     */
    private void runClipping() {
        if (mapImage == null || maskImage == null) {
            JOptionPane.showMessageDialog(this, "Veuillez charger une carte ET un calque.", "Attention", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            ImageLoader.validateDimensions(mapImage, maskImage);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erreur de dimensions", JOptionPane.ERROR_MESSAGE);
            return;
        }

        controlsPanel.setClipEnabled(false);
        controlsPanel.setExportEnabled(false);
        statusBar.setMessage("Détourage en cours...");

        SnappingConfig config = controlsPanel.buildConfig();
        long startTime = System.currentTimeMillis();

        SwingWorker<ClippingResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ClippingResult doInBackground() {
                GreenMaskExtractor greenExtractor = new GreenMaskExtractor();
                BinaryMask roughMask = greenExtractor.extract(maskImage);

                RoadDetector roadDetector = new RoadDetector();
                BinaryMask roadCandidates = roadDetector.detectRoads(mapImage, config);

                RoadSnappingEngine engine = new RoadSnappingEngine();
                CoverageMask computedCoverage = engine.snapCoverage(roughMask, roadCandidates, config);
                BinaryMask computedMask = computedCoverage.toBinaryMask(128);

                BufferedImage renderedImage = ImageExporter.createClippedImage(mapImage, computedCoverage, config.smoothRadius());
                return new ClippingResult(renderedImage, computedCoverage, computedMask);
            }

            @Override
            protected void done() {
                try {
                    ClippingResult res = get();
                    clippedImage = res.image();
                    resultCoverage = res.coverageMask();
                    resultMask = res.mask();
                    previewResultPanel.setImage(clippedImage);
                    controlsPanel.setExportEnabled(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    statusBar.setMessage(String.format("Détourage terminé en %d ms (Pixels détourés : %d)", elapsed, resultMask.countActivePixels()));
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(MainWindow.this, "Erreur de détourage : " + cause.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    statusBar.setMessage("Erreur");
                } finally {
                    controlsPanel.setClipEnabled(true);
                }
            }
        };

        worker.execute();
    }

    /**
     * Ouvre une boîte de dialogue pour exporter l'image détourée et son masque de couverture associé.
     */
    private void exportResult() {
        if (clippedImage == null || resultCoverage == null || resultMask == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image PNG (*.png)", "png"));
        chooser.setSelectedFile(new File("clipped_output.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path outPath = chooser.getSelectedFile().toPath();
                ImageExporter.savePng(clippedImage, outPath);

                // Sauvegarder également le masque associé en niveaux de gris sub-pixel
                Path maskPath = outPath.resolveSibling("mask_" + outPath.getFileName());
                ImageExporter.savePng(ImageExporter.createCoverageMaskImage(resultCoverage), maskPath);

                JOptionPane.showMessageDialog(this, "Images exportées avec succès !", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'export : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Accesseur au composant de case à cocher anti-aliasing pour les tests.
     *
     * @return Case à cocher anti-aliasing.
     */
    JCheckBox getAntialiasingCheckbox() {
        return controlsPanel.getAntialiasingCheckbox();
    }

    /**
     * Accesseur au curseur de lissage pour les tests.
     *
     * @return Curseur de lissage.
     */
    JSlider getSmoothSlider() {
        return controlsPanel.getSmoothSlider();
    }

    /**
     * Définit directement les images pour les tests unitaires et d'intégration.
     *
     * @param map  Image de carte.
     * @param mask Image de calque.
     */
    void setTestImages(BufferedImage map, BufferedImage mask) {
        this.mapImage = map;
        this.maskImage = mask;
        updateOriginalPreview();
    }

    /**
     * Déclenche l'action de détourage pour les tests.
     */
    void triggerClipping() {
        runClipping();
    }

    /**
     * Accesseur au résultat détouré pour les tests.
     *
     * @return Image détourée ou null.
     */
    BufferedImage getClippedImage() {
        return clippedImage;
    }

    /**
     * Accesseur au masque de couverture résultant pour les tests.
     *
     * @return Masque de couverture ou null.
     */
    CoverageMask getResultCoverage() {
        return resultCoverage;
    }

    /**
     * Accesseur au panneau de visualisation original pour les tests.
     *
     * @return Panneau d'aperçu original.
     */
    ImagePanel getPreviewOriginalPanel() {
        return previewOriginalPanel;
    }

    /**
     * Accesseur au panneau de visualisation du résultat pour les tests.
     *
     * @return Panneau d'aperçu résultat.
     */
    ImagePanel getPreviewResultPanel() {
        return previewResultPanel;
    }

    /**
     * Accesseur au panneau de sélection de fichiers pour les tests.
     *
     * @return Panneau de sélection de fichiers.
     */
    FileSelectionPanel getFileSelectionPanel() {
        return fileSelectionPanel;
    }

    /**
     * Accesseur au panneau de commandes pour les tests.
     *
     * @return Panneau de commandes.
     */
    ControlsPanel getControlsPanel() {
        return controlsPanel;
    }

    /**
     * Accesseur à la barre d'état pour les tests.
     *
     * @return Barre d'état.
     */
    StatusBar getStatusBar() {
        return statusBar;
    }
}
