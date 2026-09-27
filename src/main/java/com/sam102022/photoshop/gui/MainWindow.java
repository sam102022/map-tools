package com.sam102022.photoshop.gui;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Interface graphique Swing pour la visualisation et le détourage interactif.
 */
public class MainWindow extends JFrame {

    private record ClippingResult(BufferedImage image, CoverageMask coverageMask, BinaryMask mask) {}

    private BufferedImage mapImage;
    private BufferedImage maskImage;
    private BufferedImage clippedImage;
    private CoverageMask resultCoverage;
    private BinaryMask resultMask;

    private final JLabel mapLabel = new JLabel("Carte : Aucune sélectionnée");
    private final JLabel maskLabel = new JLabel("Calque : Aucun sélectionné");
    private final JLabel statusLabel = new JLabel("Prêt");

    private final ImagePanel previewOriginalPanel = new ImagePanel("Aperçu Original (Carte + Masque)");
    private final ImagePanel previewResultPanel = new ImagePanel("Résultat Détouré (Transparence)", true);

    private final JSlider snapDistanceSlider = new JSlider(5, 100, 40);
    private final JSlider roadSensitivitySlider = new JSlider(5, 20, 10);
    private final JSlider smoothSlider = new JSlider(0, 5, 1);
    private final JCheckBox antialiasingCheckbox = new JCheckBox("Anti-aliasing", true);

    private final JButton clipButton = new JButton("Détourer");
    private final JButton exportButton = new JButton("Exporter...");

    public MainWindow() {
        setTitle("Photoshop - Détourage de Carte Google Maps");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());

        // 1. Panneau Supérieur : Sélecteurs de Fichiers
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMapBtn = new JButton("Parcourir Carte...");
        loadMapBtn.addActionListener(e -> chooseMapFile());
        mapRow.add(loadMapBtn);
        mapRow.add(mapLabel);

        JPanel maskRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMaskBtn = new JButton("Parcourir Calque...");
        loadMaskBtn.addActionListener(e -> chooseMaskFile());
        maskRow.add(loadMaskBtn);
        maskRow.add(maskLabel);

        topPanel.add(mapRow);
        topPanel.add(maskRow);
        add(topPanel, BorderLayout.NORTH);

        // 2. Panneau Central : Affichage Avant / Après en Split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewOriginalPanel, previewResultPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        // 3. Panneau Inférieur : Commandes, Sliders et Barre d'État
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        snapDistanceSlider.setPaintTicks(true);
        snapDistanceSlider.setMajorTickSpacing(25);
        controlsPanel.add(createSliderBox("Distance Snap (px):", snapDistanceSlider));

        roadSensitivitySlider.setPaintTicks(true);
        roadSensitivitySlider.setMajorTickSpacing(5);
        controlsPanel.add(createSliderBox("Sensibilité Routes (x0.1):", roadSensitivitySlider));

        smoothSlider.setPaintTicks(true);
        smoothSlider.setMajorTickSpacing(1);
        controlsPanel.add(createSliderBox("Lissage Bords (px):", smoothSlider));
        controlsPanel.add(antialiasingCheckbox);

        clipButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        clipButton.addActionListener(e -> runClipping());
        controlsPanel.add(clipButton);

        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportResult());
        controlsPanel.add(exportButton);

        bottomPanel.add(controlsPanel, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.add(statusLabel);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createSliderBox(String title, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    private void chooseMapFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                mapImage = ImageLoader.load(file.toPath());
                mapLabel.setText(String.format("Carte : %s (%dx%d)", file.getName(), mapImage.getWidth(), mapImage.getHeight()));
                updateOriginalPreview();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void chooseMaskFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (*.png, *.jpg, *.jpeg)", "png", "jpg", "jpeg"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                maskImage = ImageLoader.load(file.toPath());
                maskLabel.setText(String.format("Calque : %s (%dx%d)", file.getName(), maskImage.getWidth(), maskImage.getHeight()));
                updateOriginalPreview();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

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

        clipButton.setEnabled(false);
        exportButton.setEnabled(false);
        statusLabel.setText("Détourage en cours...");

        int snapDist = snapDistanceSlider.getValue();
        float sensitivity = roadSensitivitySlider.getValue() / 10.0f;
        int smooth = smoothSlider.getValue();
        boolean antialiasing = antialiasingCheckbox.isSelected();

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(snapDist)
                .roadSensitivity(sensitivity)
                .smoothRadius(smooth)
                .antialiasing(antialiasing)
                .build();

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
                    exportButton.setEnabled(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    statusLabel.setText(String.format("Détourage terminé en %d ms (Pixels détourés : %d)", elapsed, resultMask.countActivePixels()));
                } catch (InterruptedException | ExecutionException ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(MainWindow.this, "Erreur de détourage : " + cause.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Erreur");
                } finally {
                    clipButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void exportResult() {
        if (clippedImage == null || resultCoverage == null || resultMask == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Image PNG (*.png)", "png"));
        chooser.setSelectedFile(new File("clipped_output.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path outPath = chooser.getSelectedFile().toPath();
                ImageExporter.savePng(clippedImage, outPath);

                // Sauvegarder également le masque associé
                Path maskPath = outPath.resolveSibling("mask_" + outPath.getFileName());
                ImageExporter.savePng(ImageExporter.createCoverageMaskImage(resultCoverage), maskPath);

                JOptionPane.showMessageDialog(this, "Images exportées avec succès !", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'export : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Panneau d'affichage d'image avec fond en damier transparent, lissage bilinéaire,
     * zoom interactif à la molette de souris et déplacement par glisser-déposer.
     */
    private static class ImagePanel extends JPanel {
        private final String title;
        private final boolean showCheckerboard;
        private BufferedImage image;

        private double zoomFactor = 1.0;
        private double panX = 0;
        private double panY = 0;
        private Point dragStart = null;

        /**
         * Constructeur sans damier de fond.
         *
         * @param title Titre du panneau.
         */
        public ImagePanel(String title) {
            this(title, false);
        }

        /**
         * Constructeur avec option de damier de fond pour la transparence.
         *
         * @param title            Titre du panneau.
         * @param showCheckerboard Vrai pour afficher un damier blanc/gris clair sous l'image.
         */
        public ImagePanel(String title, boolean showCheckerboard) {
            this.title = title;
            this.showCheckerboard = showCheckerboard;
            setBorder(BorderFactory.createTitledBorder(title));

            addMouseWheelListener(e -> {
                if (image == null) return;
                double oldZoom = zoomFactor;
                if (e.getWheelRotation() < 0) {
                    zoomFactor = Math.min(25.0, zoomFactor * 1.25);
                } else if (e.getWheelRotation() > 0) {
                    zoomFactor = Math.max(1.0, zoomFactor / 1.25);
                }
                if (zoomFactor == 1.0) {
                    panX = 0;
                    panY = 0;
                } else {
                    double factorChange = zoomFactor / oldZoom;
                    Point p = e.getPoint();
                    panX = p.x - factorChange * (p.x - panX);
                    panY = p.y - factorChange * (p.y - panY);
                }
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (zoomFactor > 1.0) {
                        dragStart = e.getPoint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 || SwingUtilities.isRightMouseButton(e)) {
                        zoomFactor = 1.0;
                        panX = 0;
                        panY = 0;
                        repaint();
                    }
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragStart != null && zoomFactor > 1.0) {
                        panX += e.getX() - dragStart.x;
                        panY += e.getY() - dragStart.y;
                        dragStart = e.getPoint();
                        repaint();
                    }
                }
            });
        }

        /**
         * Définit l'image à afficher et réinitialise le zoom.
         *
         * @param image Nouvelle image ou null.
         */
        public void setImage(BufferedImage image) {
            this.image = image;
            this.zoomFactor = 1.0;
            this.panX = 0;
            this.panY = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            if (showCheckerboard) {
                drawCheckerboard(g, w, h);
            }

            Insets insets = getInsets();
            int availW = w - insets.left - insets.right;
            int availH = h - insets.top - insets.bottom;
            if (availW <= 0 || availH <= 0) {
                return;
            }

            if (image != null) {
                Graphics2D g2d = (Graphics2D) g.create();
                try {
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    double baseScale = Math.min((double) availW / image.getWidth(), (double) availH / image.getHeight());
                    double totalScale = baseScale * zoomFactor;

                    int baseW = Math.max(1, (int) (image.getWidth() * baseScale));
                    int baseH = Math.max(1, (int) (image.getHeight() * baseScale));
                    int baseX = insets.left + (availW - baseW) / 2;
                    int baseY = insets.top + (availH - baseH) / 2;

                    if (zoomFactor == 1.0) {
                        g2d.drawImage(image, baseX, baseY, baseW, baseH, null);
                    } else {
                        g2d.clipRect(insets.left, insets.top, availW, availH);
                        int drawW = (int) Math.round(image.getWidth() * totalScale);
                        int drawH = (int) Math.round(image.getHeight() * totalScale);
                        int drawX = (int) Math.round(baseX + panX);
                        int drawY = (int) Math.round(baseY + panY);
                        g2d.drawImage(image, drawX, drawY, drawW, drawH, null);

                        // Afficher l'indicateur de zoom
                        String zoomText = String.format("Zoom: %d%%", (int) Math.round(zoomFactor * 100));
                        g2d.setClip(null);
                        g2d.setColor(new Color(0, 0, 0, 160));
                        g2d.fillRoundRect(w - insets.right - 90, insets.top + 10, 80, 24, 8, 8);
                        g2d.setColor(Color.WHITE);
                        g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
                        g2d.drawString(zoomText, w - insets.right - 80, insets.top + 26);
                    }
                } finally {
                    g2d.dispose();
                }
            } else {
                g.setColor(Color.GRAY);
                g.drawString("Aucune image", Math.max(10, w / 2 - 40), Math.max(20, h / 2));
            }
        }

        /**
         * Dessine le fond en damier pour signaler visuellement la transparence ARGB.
         *
         * @param g Contexte graphique.
         * @param w Largeur de la zone.
         * @param h Hauteur de la zone.
         */
        private void drawCheckerboard(Graphics g, int w, int h) {
            int cellSize = 12;
            for (int y = 0; y < h; y += cellSize) {
                for (int x = 0; x < w; x += cellSize) {
                    g.setColor(((x / cellSize) + (y / cellSize)) % 2 == 0 ? new Color(220, 220, 220) : Color.WHITE);
                    g.fillRect(x, y, cellSize, cellSize);
                }
            }
        }
    }
}
