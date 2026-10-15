package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Nettoyeur morphologique de masque routier.
 * Assure la suppression du micro-bruit par ouverture 2×2 et le colmatage des discontinuités
 * d'anti-aliasing de 1 pixel par fermeture morphologique minimale (rayon 1 / 4-voisinage ou 8-voisinage),
 * conformément aux étapes du prototype Python script.py :
 * {@code road = ndi.binary_closing(road, iterations=1)}
 * {@code road = ndi.binary_opening(road, structure=np.ones((2, 2)))}.
 */
public final class RoadMaskCleaner {

    /**
     * Constructeur par défaut.
     */
    public RoadMaskCleaner() {
    }

    /**
     * Traite un masque routier brut pour produire le contrat immuable RoadMask.
     *
     * @param raw Masque binaire brut issu de la détection.
     * @return Instance RoadMask contenant le masque brut et le masque fermé étanche.
     * @throws IllegalArgumentException si raw est null.
     */
    public RoadMask clean(BinaryMask raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Le masque brut ne peut pas être null.");
        }

        int width = raw.getWidth();
        int height = raw.getHeight();

        // 1. Fermeture morphologique minimale (dilatation r=1 puis érosion r=1 avec 4-voisinage standard scipy)
        BinaryMask closed = binaryClosing(raw, width, height);

        // 2. Ouverture morphologique avec structurant carré 2x2 (érosion 2x2 puis dilatation 2x2)
        BinaryMask cleaned = binaryOpening2x2(closed, width, height);

        return new RoadMask(width, height, raw, cleaned);
    }

    /**
     * Réalise une fermeture binaire avec structurant en croix (rayon 1, 4-connexité).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque après dilatation puis érosion.
     */
    public static BinaryMask binaryClosing(BinaryMask mask, int width, int height) {
        BinaryMask dilated = dilateCross(mask, width, height);
        return erodeCross(dilated, width, height);
    }

    /**
     * Dilatation avec élément structurant en croix (4-voisins).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque dilaté.
     */
    private static BinaryMask dilateCross(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, true);
                    if (x > 0) result.set(x - 1, y, true);
                    if (x < width - 1) result.set(x + 1, y, true);
                    if (y > 0) result.set(x, y - 1, true);
                    if (y < height - 1) result.set(x, y + 1, true);
                }
            }
        }
        return result;
    }

    /**
     * Érosion avec élément structurant en croix (4-voisins).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque érodé.
     */
    private static BinaryMask erodeCross(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)
                        && (x == 0 || mask.get(x - 1, y))
                        && (x == width - 1 || mask.get(x + 1, y))
                        && (y == 0 || mask.get(x, y - 1))
                        && (y == height - 1 || mask.get(x, y + 1))) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Réalise une ouverture binaire avec structurant carré 2×2 (np.ones((2, 2))).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque après érosion 2x2 puis dilatation 2x2.
     */
    public static BinaryMask binaryOpening2x2(BinaryMask mask, int width, int height) {
        BinaryMask eroded = erode2x2(mask, width, height);
        return dilate2x2(eroded, width, height);
    }

    /**
     * Érosion par un pavé 2×2 : un pixel (x, y) est conservé si (x, y), (x+1, y), (x, y+1) et (x+1, y+1) sont actifs.
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque érodé 2x2.
     */
    private static BinaryMask erode2x2(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                if (mask.get(x, y)
                        && mask.get(x + 1, y)
                        && mask.get(x, y + 1)
                        && mask.get(x + 1, y + 1)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Dilatation par un pavé 2×2 : si un pixel (x, y) est actif, il active (x, y), (x+1, y), (x, y+1) et (x+1, y+1).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque dilaté 2x2.
     */
    private static BinaryMask dilate2x2(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, true);
                    if (x < width - 1) result.set(x + 1, y, true);
                    if (y < height - 1) result.set(x, y + 1, true);
                    if (x < width - 1 && y < height - 1) result.set(x + 1, y + 1, true);
                }
            }
        }
        return result;
    }
}
