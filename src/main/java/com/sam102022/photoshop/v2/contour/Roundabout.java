package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.Objects;

/**
 * Modèle immuable d'un carrefour giratoire détecté.
 *
 * @param cellId          Identifiant de la cellule topologique d'origine (-1 si non spécifié).
 * @param center          Centroïde de l'îlot central.
 * @param exteriorEllipse Ellipse ajustée sur le bord extérieur de la chaussée.
 * @param islandArea      Surface en pixels de l'îlot central d'origine.
 * @param inlierRatio     Taux de concordance des rayons lors de l'ajustement de l'anneau.
 */
public record Roundabout(
        int cellId,
        PixelPoint center,
        EllipseModel exteriorEllipse,
        double islandArea,
        double inlierRatio
) {

    /**
     * Constructeur rétrocompatible sans identifiant de cellule topologique.
     *
     * @param center          Centroïde de l'îlot central.
     * @param exteriorEllipse Ellipse ajustée sur le bord extérieur de la chaussée.
     * @param islandArea      Surface en pixels de l'îlot central d'origine.
     * @param inlierRatio     Taux de concordance des rayons lors de l'ajustement de l'anneau.
     */
    public Roundabout(PixelPoint center, EllipseModel exteriorEllipse, double islandArea, double inlierRatio) {
        this(-1, center, exteriorEllipse, islandArea, inlierRatio);
    }

    /**
     * Valide les invariants du rond-point détecté.
     */
    public Roundabout {
        Objects.requireNonNull(center, "Le centroïde 'center' ne doit pas être nul.");
        Objects.requireNonNull(exteriorEllipse, "L'ellipse extérieure 'exteriorEllipse' ne doit pas être nulle.");
        if (islandArea <= 0.0) {
            throw new IllegalArgumentException("La surface de l'îlot doit être strictement positive.");
        }
        if (inlierRatio < 0.0 || inlierRatio > 1.0) {
            throw new IllegalArgumentException("Le taux d'inliers doit être compris entre 0.0 et 1.0.");
        }
    }
}
