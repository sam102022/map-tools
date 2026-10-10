package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SelectorColor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Détecteur des lignes de démarcation cartographiques sombres dans le calque de limites.
 * <p>
 * Identifie les tracés internes sombres (séparations de zones géographiques Google Maps)
 * en mesurant le contraste de luminance avec la carte d'origine, tout en éliminant les bruits
 * de petite taille et en évitant toute confusion avec les tracés d'annotations colorés
 * (rouge, bleu, magenta, cyan).
 * </p>
 */
public final class DarkDemarcationDetector {

    /**
     * Seuil maximal de luminance dans le calque pour être considéré comme un tracé sombre.
     */
    private static final int MAX_DARK_LUMINANCE = 125;

    /**
     * Seuil minimal de contraste (perte de luminance par rapport à la carte brute).
     */
    private static final int MIN_CONTRAST_DIFFERENCE = 45;

    /**
     * Taille minimale en pixels d'une composante connexe pour ne pas être considérée comme bruit.
     */
    private static final int MIN_COMPONENT_SIZE = 15;

    /**
     * Détecte les lignes de démarcation sombres situées à l'intérieur du territoire.
     *
     * @param carte     Image brute de la carte Google Maps.
     * @param limites   Image du calque contenant les limites et tracés.
     * @param territory Masque binaire délimitant le territoire d'analyse.
     * @return Masque binaire filtré contenant uniquement les démarcations significatives.
     * @throws IllegalArgumentException si l'un des paramètres est {@code null} ou si les dimensions diffèrent.
     */
    public BinaryMask detect(BufferedImage carte, BufferedImage limites, BinaryMask territory) {
        validateInputs(carte, limites, territory);

        int width = carte.getWidth();
        int height = carte.getHeight();

        BinaryMask rawCandidates = extractRawCandidates(carte, limites, territory, width, height);
        return filterSmallComponents(rawCandidates, width, height, MIN_COMPONENT_SIZE);
    }

    /**
     * Valide les dimensions et la non-nullité des entrées.
     *
     * @param carte     Image de carte.
     * @param limites   Image de calque.
     * @param territory Masque de territoire.
     * @throws IllegalArgumentException si les arguments sont invalides.
     */
    private static void validateInputs(BufferedImage carte, BufferedImage limites, BinaryMask territory) {
        if (carte == null || limites == null || territory == null) {
            throw new IllegalArgumentException("Les paramètres carte, limites et territory ne peuvent pas être null.");
        }

        int width = carte.getWidth();
        int height = carte.getHeight();

        if (limites.getWidth() != width || limites.getHeight() != height) {
            throw new IllegalArgumentException("Les dimensions de carte (" + width + "x" + height
                    + ") et limites (" + limites.getWidth() + "x" + limites.getHeight() + ") ne concordent pas.");
        }

        if (territory.getWidth() != width || territory.getHeight() != height) {
            throw new IllegalArgumentException("Les dimensions de carte (" + width + "x" + height
                    + ") et territory (" + territory.getWidth() + "x" + territory.getHeight() + ") ne concordent pas.");
        }
    }

    /**
     * Extrait les pixels candidats répondant aux critères de luminance et d'exclusion chromatique.
     *
     * @param carte     Image de carte brute.
     * @param limites   Image de limites.
     * @param territory Masque de territoire.
     * @param width     Largeur de l'image.
     * @param height    Hauteur de l'image.
     * @return Masque binaire brut des candidats.
     */
    private static BinaryMask extractRawCandidates(BufferedImage carte, BufferedImage limites,
                                                   BinaryMask territory, int width, int height) {
        BinaryMask candidates = new BinaryMask(width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (territory.get(x, y) && isDarkDemarcationPixel(carte.getRGB(x, y), limites.getRGB(x, y))) {
                    candidates.set(x, y, true);
                }
            }
        }

        return candidates;
    }

    /**
     * Vérifie si un pixel remplit les critères chromatiques et photométriques d'une démarcation sombre.
     *
     * @param rgbCarte   Couleur RVB du pixel sur la carte.
     * @param rgbLimites Couleur RVB du pixel sur le calque.
     * @return {@code true} si le pixel est une démarcation sombre valide, {@code false} sinon.
     */
    private static boolean isDarkDemarcationPixel(int rgbCarte, int rgbLimites) {
        int rL = (rgbLimites >> 16) & 0xFF;
        int gL = (rgbLimites >> 8) & 0xFF;
        int bL = rgbLimites & 0xFF;

        if (isAnnotationOrTerritoryColor(rL, gL, bL)) {
            return false;
        }

        int lumL = computeLuminance(rL, gL, bL);
        if (lumL > MAX_DARK_LUMINANCE) {
            return false;
        }

        int rC = (rgbCarte >> 16) & 0xFF;
        int gC = (rgbCarte >> 8) & 0xFF;
        int bC = rgbCarte & 0xFF;

        int lumC = computeLuminance(rC, gC, bC);
        return (lumC - lumL) >= MIN_CONTRAST_DIFFERENCE;
    }

    /**
     * Calcule la luminance standard d'un triplet RVB selon la norme UIT-R BT.601.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return Valeur de luminance [0..255].
     */
    private static int computeLuminance(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * Vérifie si le pixel correspond à une couleur d'annotation ou au vert de remplissage de territoire.
     *
     * @param r Composante rouge.
     * @param g Composante verte.
     * @param b Composante bleue.
     * @return {@code true} si le pixel est protégé contre la classification en démarcation sombre.
     */
    private static boolean isAnnotationOrTerritoryColor(int r, int g, int b) {
        if (g > r + 20 && g > b + 20) {
            return true;
        }

        return SelectorColor.RED.matches(r, g, b)
                || SelectorColor.BLUE.matches(r, g, b)
                || SelectorColor.MAGENTA.matches(r, g, b)
                || SelectorColor.CYAN.matches(r, g, b);
    }

    /**
     * Filtre les composantes connexes dont la taille est strictement inférieure au seuil minimal.
     *
     * @param rawCandidates Masque binaire brut des candidats.
     * @param width         Largeur de l'image.
     * @param height        Hauteur de l'image.
     * @param minSize       Taille minimale requise en nombre de pixels.
     * @return Nouveau masque binaire expurgé des bruits isolés.
     */
    private static BinaryMask filterSmallComponents(BinaryMask rawCandidates, int width, int height, int minSize) {
        BinaryMask filtered = new BinaryMask(width, height);
        boolean[] visited = new boolean[width * height];
        int[] queue = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (rawCandidates.get(x, y) && !visited[index]) {
                    int componentSize = exploreComponent(rawCandidates, width, height, index, visited, queue);
                    if (componentSize >= minSize) {
                        preserveComponent(filtered, queue, componentSize, width);
                    }
                }
            }
        }

        return filtered;
    }

    /**
     * Explore en largeur (BFS 4-connectivité) l'ensemble d'une composante connexe candidate.
     *
     * @param mask      Masque brut des candidats.
     * @param width     Largeur de l'image.
     * @param height    Hauteur de l'image.
     * @param startIdx  Index 1D du point de départ.
     * @param visited   Tableau des pixels déjà visités.
     * @param queue     File plate recevant les indices des pixels de la composante.
     * @return Nombre de pixels appartenant à la composante.
     */
    private static int exploreComponent(BinaryMask mask, int width, int height, int startIdx,
                                         boolean[] visited, int[] queue) {
        int head = 0;
        int tail = 0;

        visited[startIdx] = true;
        queue[tail++] = startIdx;

        while (head < tail) {
            int current = queue[head++];
            int cx = current % width;
            int cy = current / width;

            // Parcourir les 4 voisins
            tail = tryEnqueueNeighbor(mask, width, height, cx + 1, cy, visited, queue, tail);
            tail = tryEnqueueNeighbor(mask, width, height, cx - 1, cy, visited, queue, tail);
            tail = tryEnqueueNeighbor(mask, width, height, cx, cy + 1, visited, queue, tail);
            tail = tryEnqueueNeighbor(mask, width, height, cx, cy - 1, visited, queue, tail);
        }

        return tail;
    }

    /**
     * Tente d'ajouter un voisin dans la file BFS s'il est candidat et non encore visité.
     *
     * @param mask    Masque source.
     * @param width   Largeur de l'image.
     * @param height  Hauteur de l'image.
     * @param nx      Coordonnée X du voisin.
     * @param ny      Coordonnée Y du voisin.
     * @param visited Tableau des pixels visités.
     * @param queue   File plate BFS.
     * @param tail    Position d'écriture actuelle dans la file.
     * @return Nouvelle position d'écriture après ajout potentiel.
     */
    private static int tryEnqueueNeighbor(BinaryMask mask, int width, int height, int nx, int ny,
                                          boolean[] visited, int[] queue, int tail) {
        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
            int nIndex = ny * width + nx;
            if (mask.get(nx, ny) && !visited[nIndex]) {
                visited[nIndex] = true;
                queue[tail] = nIndex;
                return tail + 1;
            }
        }
        return tail;
    }

    /**
     * Active l'ensemble des pixels d'une composante validée dans le masque cible.
     *
     * @param target        Masque binaire de destination.
     * @param queue         Indices linéaires des pixels de la composante.
     * @param componentSize Nombre de pixels dans la composante.
     * @param width         Largeur de l'image.
     */
    private static void preserveComponent(BinaryMask target, int[] queue, int componentSize, int width) {
        for (int i = 0; i < componentSize; i++) {
            int idx = queue[i];
            int px = idx % width;
            int py = idx / width;
            target.set(px, py, true);
        }
    }
}
