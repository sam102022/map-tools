package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;

import java.awt.image.BufferedImage;

/**
 * Contrat immuable regroupant l'ensemble des livrables graphiques finaux générés par le moteur V2.
 *
 * @param clipped      Image cartographique détourée avec couche alpha continue 32-bit (ARGB).
 * @param mask         Masque en niveaux de gris 8-bit (TYPE_BYTE_GRAY) reflétant la couverture.
 * @param overlay      Image de contrôle superposant le tracé du contour frontière rouge vif sur la carte.
 * @param coverageMask Masque matriciel continu de couverture sub-pixel [0..255].
 */
public record RenderResult(
        BufferedImage clipped,
        BufferedImage mask,
        BufferedImage overlay,
        CoverageMask coverageMask
) {

    /**
     * Valide l'intégrité du résultat en interdisant tout composant nul.
     *
     * @param clipped      Image détourée ARGB non nulle.
     * @param mask         Masque monochrome 8-bit non nul.
     * @param overlay      Image de contrôle rouge vif non nulle.
     * @param coverageMask Masque de couverture géométrique sub-pixel non nul.
     * @throws IllegalArgumentException si l'un des paramètres est nul.
     */
    public RenderResult {
        if (clipped == null || mask == null || overlay == null || coverageMask == null) {
            throw new IllegalArgumentException("Aucun composant de RenderResult ne peut être null.");
        }
    }
}
