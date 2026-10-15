package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import java.util.Objects;

/**
 * Masque matriciel binaire consolidé après intégration des routes frontières,
 * régularisation morphologique et résolution des îlots résiduels.
 *
 * @param width      Largeur du masque local en pixels.
 * @param height     Hauteur du masque local en pixels.
 * @param mask       Masque binaire étanche (1 = territoire consolidé, 0 = extérieur).
 * @param cropWindow Fenêtre de recadrage d'origine par rapport à l'image cartographique globale.
 */
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        CropWindow cropWindow
) {
    /**
     * Constructeur canonique avec validations défensives de cohérence géométrique.
     *
     * @param width      Largeur (> 0).
     * @param height     Hauteur (> 0).
     * @param mask       Masque binaire non nul.
     * @param cropWindow Fenêtre de recadrage non nulle.
     */
    public ConsolidatedMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides pour ConsolidatedMask.");
        }
        Objects.requireNonNull(mask, "mask ne doit pas être nul.");
        Objects.requireNonNull(cropWindow, "cropWindow ne doit pas être nul.");
        if (mask.getWidth() != width || mask.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions du masque incohérentes avec width/height.");
        }
        if (cropWindow.width() != width || cropWindow.height() != height) {
            throw new IllegalArgumentException("Dimensions de CropWindow incohérentes avec le masque local.");
        }
    }

    /**
     * Restitue le décalage horizontal (X) par rapport au repère global de l'image.
     *
     * @return Décalage horizontal (X) par rapport au repère global de l'image.
     */
    public int offsetX() {
        return cropWindow.x0();
    }

    /**
     * Restitue le décalage vertical (Y) par rapport au repère global de l'image.
     *
     * @return Décalage vertical (Y) par rapport au repère global de l'image.
     */
    public int offsetY() {
        return cropWindow.y0();
    }
}
