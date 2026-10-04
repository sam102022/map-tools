package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.List;

/**
 * Calculateur de fenêtre de découpe d'intérêt (Region Of Interest) et extracteur de sous-masques.
 * Permet d'isoler la boîte englobante augmentée d'une marge de sécurité pour optimiser les performances.
 */
public class BoundingBoxCropper {

    /**
     * Marge de sécurité par défaut appliquée autour de la boîte englobante du polygone (en pixels).
     */
    public static final int DEFAULT_MARGIN = 90;

    /**
     * Calcule la fenêtre de découpe d'un polygone avec la marge de sécurité par défaut de 90 pixels.
     *
     * @param polygonPoints Sommets du polygone dans le repère pixel global.
     * @param imageWidth    Largeur totale de l'image source.
     * @param imageHeight   Hauteur totale de l'image source.
     * @return Fenêtre de découpe calculée et bornée aux dimensions de l'image.
     * @throws IllegalArgumentException si les points sont null ou vides, ou si les dimensions sont <= 0.
     */
    public CropWindow computeCropWindow(List<PixelPoint> polygonPoints, int imageWidth, int imageHeight) {
        return computeCropWindow(polygonPoints, imageWidth, imageHeight, DEFAULT_MARGIN);
    }

    /**
     * Calcule la fenêtre de découpe d'un polygone avec une marge de sécurité configurable.
     *
     * @param polygonPoints Sommets du polygone dans le repère pixel global.
     * @param imageWidth    Largeur totale de l'image source.
     * @param imageHeight   Hauteur totale de l'image source.
     * @param margin        Marge de sécurité en pixels appliquée tout autour du polygone.
     * @return Fenêtre de découpe calculée et bornée aux dimensions de l'image.
     * @throws IllegalArgumentException si les paramètres sont invalides.
     */
    public CropWindow computeCropWindow(List<PixelPoint> polygonPoints, int imageWidth, int imageHeight, int margin) {
        validateInputs(polygonPoints, imageWidth, imageHeight, margin);

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (PixelPoint pt : polygonPoints) {
            if (pt == null) {
                continue;
            }
            if (pt.x() < minX) minX = pt.x();
            if (pt.x() > maxX) maxX = pt.x();
            if (pt.y() < minY) minY = pt.y();
            if (pt.y() > maxY) maxY = pt.y();
        }

        int x0 = Math.max(0, (int) minX - margin);
        int x1 = Math.min(imageWidth, (int) maxX + margin);
        int y0 = Math.max(0, (int) minY - margin);
        int y1 = Math.min(imageHeight, (int) maxY + margin);

        int width = Math.max(1, x1 - x0);
        int height = Math.max(1, y1 - y0);

        return new CropWindow(x0, y0, width, height);
    }

    /**
     * Valide les préconditions des arguments du calcul de découpe.
     *
     * @param points Sommets du polygone d'intention.
     * @param width  Largeur de l'image de référence en pixels.
     * @param height Hauteur de l'image de référence en pixels.
     * @param margin Marge de sécurité en pixels.
     * @throws IllegalArgumentException si la liste de sommets est null ou vide, si les dimensions sont non strictement positives, ou si la marge est négative.
     */
    private void validateInputs(List<PixelPoint> points, int width, int height, int margin) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("La liste des sommets du polygone ne peut pas être null ou vide.");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions de l'image doivent être strictement positives.");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("La marge de découpe ne peut pas être négative : " + margin);
        }
    }

    /**
     * Découpe un masque binaire source selon la fenêtre de recadrage fournie.
     *
     * @param source Masque binaire d'origine (taille image complète).
     * @param window Fenêtre de recadrage à appliquer.
     * @return Nouveau masque binaire aux dimensions de la fenêtre de recadrage.
     * @throws IllegalArgumentException si le masque ou la fenêtre est null, ou si la fenêtre déborde du masque.
     */
    public BinaryMask crop(BinaryMask source, CropWindow window) {
        if (source == null) {
            throw new IllegalArgumentException("Le masque source ne peut pas être null.");
        }
        if (window == null) {
            throw new IllegalArgumentException("La fenêtre de découpe ne peut pas être null.");
        }
        if (window.x1() > source.getWidth() || window.y1() > source.getHeight()) {
            throw new IllegalArgumentException("La fenêtre de découpe dépasse les limites du masque source : "
                    + window.x1() + "x" + window.y1() + " vs " + source.getWidth() + "x" + source.getHeight());
        }

        int targetWidth = window.width();
        int targetHeight = window.height();
        BinaryMask cropped = new BinaryMask(targetWidth, targetHeight);

        int startX = window.x0();
        int startY = window.y0();

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                if (source.get(startX + x, startY + y)) {
                    cropped.set(x, y, true);
                }
            }
        }

        return cropped;
    }
}
