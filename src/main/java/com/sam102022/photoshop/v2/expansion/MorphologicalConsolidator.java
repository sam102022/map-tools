package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Régularisateur morphologique par ouverture circulaire garantissant la sanctuarisation de T.
 * <p>
 * Applique une ouverture morphologique (érosion puis dilatation par un disque euclidien) sur
 * l'union {@code U = T | ext} pour éliminer les excroissances et dentelures de voirie, puis
 * réinjecte inconditionnellement {@code T} afin de sanctuariser intégralement les îlots intérieurs.
 */
public class MorphologicalConsolidator {

    /**
     * Consolide et lisse la bordure routière par ouverture circulaire tout en sanctuarisant T.
     *
     * @param retainedMask  Masque des parcelles intérieures sélectionnées T (non nul).
     * @param roadExtension Masque de l'extension routière géodésique ext (non nul).
     * @param diskRadius    Rayon du disque euclidien d'ouverture (>= 0).
     * @return Masque binaire consolidé {@code Mc = T | open(T | ext, disk)}.
     */
    public BinaryMask consolidate(BinaryMask retainedMask, BinaryMask roadExtension, int diskRadius) {
        validateInputs(retainedMask, roadExtension, diskRadius);
        int width = retainedMask.getWidth();
        int height = retainedMask.getHeight();

        if (diskRadius == 0) {
            return buildDirectUnion(retainedMask, roadExtension, width, height);
        }

        int[][] diskOffsets = createDiskOffsets(diskRadius);
        int pad = diskRadius + 2;

        BinaryMask padded = createPaddedUnion(retainedMask, roadExtension, width, height, pad);
        BinaryMask eroded = erode(padded, diskOffsets);
        BinaryMask dilated = dilate(eroded, diskOffsets);

        return buildSanctuarizedResult(retainedMask, dilated, width, height, pad);
    }

    /**
     * Valide les paramètres d'entrée et la cohérence dimensionnelle des masques.
     *
     * @param retainedMask  Masque T.
     * @param roadExtension Masque ext.
     * @param diskRadius    Rayon du disque.
     */
    private void validateInputs(BinaryMask retainedMask, BinaryMask roadExtension, int diskRadius) {
        Objects.requireNonNull(retainedMask, "retainedMask ne doit pas être nul.");
        Objects.requireNonNull(roadExtension, "roadExtension ne doit pas être nul.");
        if (retainedMask.getWidth() != roadExtension.getWidth()
                || retainedMask.getHeight() != roadExtension.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre retainedMask et roadExtension.");
        }
        if (diskRadius < 0) {
            throw new IllegalArgumentException("diskRadius doit être positif ou nul.");
        }
    }

    /**
     * Génère la liste des décalages relatifs (dx, dy) composant le disque euclidien.
     *
     * @param radius Rayon du disque en pixels.
     * @return Tableau 2D des coordonnées relatives contenues dans le disque.
     */
    private int[][] createDiskOffsets(int radius) {
        List<int[]> list = new ArrayList<>();
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= r2) {
                    list.add(new int[]{dx, dy});
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    /**
     * Construit l'union brute sans padding lorsque diskRadius = 0.
     *
     * @param t      Masque intérieur T.
     * @param ext    Masque d'extension.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque binaire d'union brute.
     */
    private BinaryMask buildDirectUnion(BinaryMask t, BinaryMask ext, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (t.get(x, y) || ext.get(x, y)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Construit l'union {@code U = T | ext} avec marge périphérique de padding.
     *
     * @param t      Masque intérieur.
     * @param ext    Extension routière.
     * @param width  Largeur utile.
     * @param height Hauteur utile.
     * @param pad    Épaisseur de la bordure de padding.
     * @return Masque élargi avec padding nul.
     */
    private BinaryMask createPaddedUnion(BinaryMask t, BinaryMask ext,
                                         int width, int height, int pad) {
        int paddedW = width + 2 * pad;
        int paddedH = height + 2 * pad;
        BinaryMask padded = new BinaryMask(paddedW, paddedH);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (t.get(x, y) || ext.get(x, y)) {
                    padded.set(x + pad, y + pad, true);
                }
            }
        }
        return padded;
    }

    /**
     * Érode le masque binaire par l'élément structurant fourni.
     *
     * @param src     Masque source.
     * @param offsets Décalages relatifs de l'élément structurant.
     * @return Nouveau masque érodé.
     */
    private BinaryMask erode(BinaryMask src, int[][] offsets) {
        int w = src.getWidth();
        int h = src.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (src.get(x, y) && checkAllOffsetsTrue(src, x, y, offsets, w, h)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Vérifie si tous les voisins de l'élément structurant sont actifs.
     *
     * @param src     Masque binaire.
     * @param x       Coordonnée X centrale.
     * @param y       Coordonnée Y centrale.
     * @param offsets Voisins relatifs.
     * @param w       Largeur.
     * @param h       Hauteur.
     * @return true si tous les pixels correspondants sont à true, false sinon.
     */
    private boolean checkAllOffsetsTrue(BinaryMask src, int x, int y,
                                        int[][] offsets, int w, int h) {
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (nx < 0 || nx >= w || ny < 0 || ny >= h || !src.get(nx, ny)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Dilate le masque binaire par l'élément structurant fourni.
     *
     * @param src     Masque source.
     * @param offsets Décalages relatifs du disque.
     * @return Nouveau masque dilaté.
     */
    private BinaryMask dilate(BinaryMask src, int[][] offsets) {
        int w = src.getWidth();
        int h = src.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (src.get(x, y)) {
                    applyDilationFootprint(result, x, y, offsets, w, h);
                }
            }
        }
        return result;
    }

    /**
     * Active les pixels voisins lors de la dilatation d'un pixel actif.
     *
     * @param result  Masque cible.
     * @param x       Origine X.
     * @param y       Origine Y.
     * @param offsets Décalages du disque.
     * @param w       Largeur.
     * @param h       Hauteur.
     */
    private void applyDilationFootprint(BinaryMask result, int x, int y,
                                       int[][] offsets, int w, int h) {
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                result.set(nx, ny, true);
            }
        }
    }

    /**
     * Construit le masque final en extrayant la zone utile et en sanctuarisant T.
     *
     * @param t       Masque intérieur authentique T.
     * @param dilated Masque issu de l'ouverture morphologique avec padding.
     * @param width   Largeur de sortie.
     * @param height  Hauteur de sortie.
     * @param pad     Épaisseur du padding appliqué.
     * @return Masque consolidé final.
     */
    private BinaryMask buildSanctuarizedResult(BinaryMask t, BinaryMask dilated,
                                              int width, int height, int pad) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (t.get(x, y) || dilated.get(x + pad, y + pad)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }
}
