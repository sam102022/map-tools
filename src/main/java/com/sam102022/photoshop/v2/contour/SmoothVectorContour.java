package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.List;
import java.util.Objects;

/**
 * Modèle immuable du contour vectoriel lissé sub-pixel.
 * Les coordonnées des points sont exprimées dans le repère local de la fenêtre de recadrage (CropWindow).
 *
 * @param points                 Coordonnées sub-pixels ordonnées formant la boucle fermée extérieure.
 * @param cornerIndices          Indices des sommets identifiés comme angles vifs préservés.
 * @param width                  Largeur de la fenêtre locale de calcul.
 * @param height                 Hauteur de la fenêtre locale de calcul.
 * @param cropWindow             Fenêtre de découpe englobante permettant le repositionnement dans l'image globale.
 * @param substitutedRoundabouts Ronds-points ayant été substitués par des arcs d'ellipse dans ce contour.
 */
public record SmoothVectorContour(
        List<PixelPoint> points,
        List<Integer> cornerIndices,
        int width,
        int height,
        CropWindow cropWindow,
        List<Roundabout> substitutedRoundabouts
) {

    /**
     * Constructeur rétrocompatible sans liste de ronds-points substitués.
     *
     * @param points        Coordonnées sub-pixels ordonnées formant la boucle fermée extérieure.
     * @param cornerIndices Indices des sommets identifiés comme angles vifs préservés.
     * @param width         Largeur de la fenêtre locale de calcul.
     * @param height        Hauteur de la fenêtre locale de calcul.
     * @param cropWindow    Fenêtre de découpe englobante permettant le repositionnement dans l'image globale.
     */
    public SmoothVectorContour(List<PixelPoint> points, List<Integer> cornerIndices,
                               int width, int height, CropWindow cropWindow) {
        this(points, cornerIndices, width, height, cropWindow, List.of());
    }

    /**
     * Constructeur canonique garantissant l'immuabilité défensive des collections.
     */
    public SmoothVectorContour {
        Objects.requireNonNull(points, "La liste 'points' ne doit pas être nulle.");
        Objects.requireNonNull(cornerIndices, "La liste 'cornerIndices' ne doit pas être nulle.");
        Objects.requireNonNull(cropWindow, "La fenêtre 'cropWindow' ne doit pas être nulle.");
        Objects.requireNonNull(substitutedRoundabouts, "La liste 'substitutedRoundabouts' ne doit pas être nulle.");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions width et height doivent être strictement positives.");
        }
        points = List.copyOf(points);
        cornerIndices = List.copyOf(cornerIndices);
        substitutedRoundabouts = List.copyOf(substitutedRoundabouts);
    }
}
