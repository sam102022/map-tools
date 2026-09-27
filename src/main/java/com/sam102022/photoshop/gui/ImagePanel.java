package com.sam102022.photoshop.gui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;

/**
 * Panneau d'affichage graphique d'image avec fond en damier pour la transparence,
 * interpolation bilinéaire, zoom interactif à la molette et déplacement panoramique.
 */
public class ImagePanel extends JPanel {
    private final String title;
    private final boolean showCheckerboard;
    private BufferedImage image;

    private double zoomFactor = 1.0;
    private double panX = 0;
    private double panY = 0;
    private Point dragStart = null;

    /**
     * Constructeur créant un panneau d'affichage sans damier de transparence.
     *
     * @param title Titre affiché dans la bordure supérieure du panneau.
     */
    public ImagePanel(String title) {
        this(title, false);
    }

    /**
     * Constructeur créant un panneau d'affichage avec option de damier de fond.
     *
     * @param title            Titre affiché dans la bordure supérieure du panneau.
     * @param showCheckerboard Vrai pour dessiner un motif en damier révélant les zones transparentes.
     */
    public ImagePanel(String title, boolean showCheckerboard) {
        this.title = title;
        this.showCheckerboard = showCheckerboard;
        setBorder(BorderFactory.createTitledBorder(title));

        initInteractionListeners();
    }

    /**
     * Initialise les écouteurs d'événements pour le zoom à la molette et le glisser panoramique.
     */
    private void initInteractionListeners() {
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
     * Définit l'image à afficher et réinitialise le facteur de zoom et le déplacement.
     *
     * @param image Nouvelle image à afficher, ou null pour effacer l'affichage.
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        this.zoomFactor = 1.0;
        this.panX = 0;
        this.panY = 0;
        repaint();
    }

    /**
     * Récupère l'image actuellement affichée.
     *
     * @return Image courante ou null.
     */
    public BufferedImage getImage() {
        return image;
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
            paintImageWithZoom(g, w, insets, availW, availH);
        } else {
            g.setColor(Color.GRAY);
            g.drawString("Aucune image", Math.max(10, w / 2 - 40), Math.max(20, h / 2));
        }
    }

    /**
     * Effectue le dessin de l'image en appliquant l'échelle de cadrage et le zoom utilisateur.
     *
     * @param g      Contexte graphique.
     * @param w      Largeur totale du panneau.
     * @param insets Marges intérieures du panneau.
     * @param availW Largeur utile disponible.
     * @param availH Hauteur utile disponible.
     */
    private void paintImageWithZoom(Graphics g, int w, Insets insets, int availW, int availH) {
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

                // Afficher l'indicateur de zoom discret
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
