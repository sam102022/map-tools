package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.util.BitSet;
import java.util.Objects;

/**
 * Détecteur et combleur sélectif des îlots résiduels, cours intérieures et centres de ronds-points.
 * <p>
 * Identifie les cavités fermées par inondation inverse (marquage de l'extérieur infini depuis
 * les bordures) et comble uniquement les composantes compactes de surface inférieure ou égale
 * au seuil d'aire spécifié (typiquement 15 000 px).
 */
public class ResidualHoleResolver {

    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    /**
     * Comble les trous fermés dont la surface en pixels est inférieure ou égale à {@code maxHoleArea}.
     *
     * @param mask        Masque binaire d'entrée (non nul).
     * @param maxHoleArea Aire maximale en pixels des trous à combler (>= 0).
     * @return Nouveau masque binaire avec les îlots compacts comblés.
     */
    public BinaryMask fillCompactHoles(BinaryMask mask, long maxHoleArea) {
        validateInputs(mask, maxHoleArea);
        int width = mask.getWidth();
        int height = mask.getHeight();

        BinaryMask result = cloneMask(mask, width, height);
        BitSet visited = new BitSet(width * height);
        int[] queue = new int[width * height];

        markExteriorBackground(mask, visited, queue, width, height);
        processHoleComponents(mask, result, visited, queue, maxHoleArea, width, height);

        return result;
    }

    /**
     * Valide les paramètres d'entrée.
     *
     * @param mask        Masque binaire.
     * @param maxHoleArea Surface maximale autorisée.
     */
    private void validateInputs(BinaryMask mask, long maxHoleArea) {
        Objects.requireNonNull(mask, "mask ne doit pas être nul.");
        if (maxHoleArea < 0) {
            throw new IllegalArgumentException("maxHoleArea doit être positif ou nul.");
        }
    }

    /**
     * Clone le masque binaire d'origine.
     *
     * @param mask   Masque source.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Copie indépendante.
     */
    private BinaryMask cloneMask(BinaryMask mask, int width, int height) {
        BinaryMask clone = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    clone.set(x, y, true);
                }
            }
        }
        return clone;
    }

    /**
     * Marque par parcours en largeur (BFS) l'ensemble des pixels de fond connectés au périmètre.
     *
     * @param mask    Masque source.
     * @param visited Marqueur des pixels explorés.
     * @param queue   File de parcours BFS.
     * @param width   Largeur.
     * @param height  Hauteur.
     */
    private void markExteriorBackground(BinaryMask mask, BitSet visited, int[] queue,
                                        int width, int height) {
        int head = 0;
        int tail = 0;

        tail = enqueueHorizontalBorders(mask, visited, queue, tail, width, height);
        tail = enqueueVerticalBorders(mask, visited, queue, tail, width, height);

        while (head < tail) {
            int curr = queue[head++];
            int cx = curr % width;
            int cy = curr / width;

            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIdx = ny * width + nx;
                    if (!mask.get(nx, ny) && !visited.get(nIdx)) {
                        visited.set(nIdx);
                        queue[tail++] = nIdx;
                    }
                }
            }
        }
    }

    /**
     * Ajoute les pixels de fond des bordures horizontales (haut et bas) dans la file BFS.
     *
     * @param mask    Masque binaire.
     * @param visited Pixels visités.
     * @param queue   File BFS.
     * @param tail    Pointeur de fin de file.
     * @param width   Largeur.
     * @param height  Hauteur.
     * @return Nouveau pointeur de fin de file.
     */
    private int enqueueHorizontalBorders(BinaryMask mask, BitSet visited, int[] queue,
                                         int tail, int width, int height) {
        int bottomOffset = (height - 1) * width;
        for (int x = 0; x < width; x++) {
            if (!mask.get(x, 0) && !visited.get(x)) {
                visited.set(x);
                queue[tail++] = x;
            }
            int bottomIdx = bottomOffset + x;
            if (!mask.get(x, height - 1) && !visited.get(bottomIdx)) {
                visited.set(bottomIdx);
                queue[tail++] = bottomIdx;
            }
        }
        return tail;
    }

    /**
     * Ajoute les pixels de fond des bordures verticales (gauche et droite) dans la file BFS.
     *
     * @param mask    Masque binaire.
     * @param visited Pixels visités.
     * @param queue   File BFS.
     * @param tail    Pointeur de fin de file.
     * @param width   Largeur.
     * @param height  Hauteur.
     * @return Nouveau pointeur de fin de file.
     */
    private int enqueueVerticalBorders(BinaryMask mask, BitSet visited, int[] queue,
                                       int tail, int width, int height) {
        for (int y = 0; y < height; y++) {
            int leftIdx = y * width;
            if (!mask.get(0, y) && !visited.get(leftIdx)) {
                visited.set(leftIdx);
                queue[tail++] = leftIdx;
            }
            int rightIdx = y * width + width - 1;
            if (!mask.get(width - 1, y) && !visited.get(rightIdx)) {
                visited.set(rightIdx);
                queue[tail++] = rightIdx;
            }
        }
        return tail;
    }

    /**
     * Explore chaque cavité fermée et comble sélectivement celles dont l'aire est <= maxHoleArea.
     *
     * @param mask        Masque d'origine.
     * @param result      Masque de sortie à enrichir.
     * @param visited     Pixels explorés.
     * @param queue       Buffer temporaire réutilisé pour chaque composante.
     * @param maxHoleArea Seuil d'aire maximal.
     * @param width       Largeur.
     * @param height      Hauteur.
     */
    private void processHoleComponents(BinaryMask mask, BinaryMask result, BitSet visited,
                                       int[] queue, long maxHoleArea, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int startIdx = y * width + x;
                if (!mask.get(x, y) && !visited.get(startIdx)) {
                    int compSize = exploreHoleComponent(mask, visited, queue, startIdx, width, height);
                    if (compSize <= maxHoleArea) {
                        fillHolePixels(result, queue, compSize, width);
                    }
                }
            }
        }
    }

    /**
     * Parcourt une cavité fermée en 4-connexité et extrait l'ensemble de ses pixels.
     *
     * @param mask     Masque binaire.
     * @param visited  Pixels visités.
     * @param queue    Buffer de stockage de la composante.
     * @param startIdx Pixel de départ.
     * @param width    Largeur.
     * @param height   Hauteur.
     * @return Nombre total de pixels dans cette cavité.
     */
    private int exploreHoleComponent(BinaryMask mask, BitSet visited, int[] queue,
                                     int startIdx, int width, int height) {
        int head = 0;
        int tail = 0;
        visited.set(startIdx);
        queue[tail++] = startIdx;

        while (head < tail) {
            int curr = queue[head++];
            int cx = curr % width;
            int cy = curr / width;

            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIdx = ny * width + nx;
                    if (!mask.get(nx, ny) && !visited.get(nIdx)) {
                        visited.set(nIdx);
                        queue[tail++] = nIdx;
                    }
                }
            }
        }
        return tail;
    }

    /**
     * Active l'ensemble des pixels d'une cavité compacte dans le masque cible.
     *
     * @param result   Masque récepteur.
     * @param queue    Buffer contenant les indices 1D des pixels.
     * @param compSize Nombre de pixels à combler.
     * @param width    Largeur de la grille.
     */
    private void fillHolePixels(BinaryMask result, int[] queue, int compSize, int width) {
        for (int i = 0; i < compSize; i++) {
            int idx = queue[i];
            result.set(idx % width, idx / width, true);
        }
    }
}
