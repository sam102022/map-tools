package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SelectorColor;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Détecteur polychrome et extracteur de l'intérieur d'un sélecteur de zone annoté.
 * <p>
 * Cette classe permet d'analyser une image de calque pour détecter les annotations colorées
 * (rouge, bleu, magenta, cyan), de résoudre les ambiguïtés éventuelles en mode automatique,
 * et d'extraire la région intérieure close délimitée par le tracé après colmatage morphologique
 * des micro-brèches.
 * </p>
 */
public class ColorRegionSelectorExtractor {

    /**
     * Nombre minimal de pixels requis pour considérer une couleur comme présente et significative.
     */
    private static final int MIN_COLOR_PIXELS = 50;

    /**
     * Rayon de fermeture morphologique utilisé pour réparer les brèches du tracé (jusqu'à 10 pixels).
     */
    private static final int CLOSING_RADIUS = 5;

    /**
     * Superficie minimale en pixels requise pour qu'un intérieur fermé soit considéré comme valide.
     */
    private static final int MIN_INTERIOR_PIXELS = 50;

    /**
     * Liste ordonnée des couleurs concrètes pouvant faire l'objet d'une détection dans l'image.
     */
    private static final List<SelectorColor> CONCRETE_COLORS = List.of(
            SelectorColor.RED,
            SelectorColor.BLUE,
            SelectorColor.MAGENTA,
            SelectorColor.CYAN
    );

    /**
     * Déplacements relatifs pour le parcours 4-connexe (droite, gauche, bas, haut).
     */
    private static final int[] DX = {1, -1, 0, 0};

    /**
     * Déplacements relatifs verticaux pour le parcours 4-connexe.
     */
    private static final int[] DY = {0, 0, 1, -1};

    /**
     * Constructeur par défaut.
     */
    public ColorRegionSelectorExtractor() {
    }

    /**
     * Scanne l'image et retourne la liste des couleurs d'annotation reconnues ayant au moins 50 pixels.
     *
     * @param image Image source à analyser.
     * @return Liste immuable des couleurs détectées, dans l'ordre de priorité défini.
     * @throws IllegalArgumentException si l'image est {@code null}.
     */
    public List<SelectorColor> detectPresentColors(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image ne peut pas être null.");
        }

        int[] counts = countConcreteColors(image);
        List<SelectorColor> detected = new ArrayList<>();
        for (int i = 0; i < CONCRETE_COLORS.size(); i++) {
            if (counts[i] >= MIN_COLOR_PIXELS) {
                detected.add(CONCRETE_COLORS.get(i));
            }
        }
        return Collections.unmodifiableList(detected);
    }

    /**
     * Résout la couleur cible à traiter en fonction de la couleur demandée et des tracés présents.
     *
     * @param image          Image contenant le calque d'annotation.
     * @param requestedColor Couleur demandée (ou {@link SelectorColor#AUTO} / {@code null} pour auto-détection).
     * @return La couleur de sélection retenue, ou {@code null} si aucune couleur n'est présente en mode auto.
     * @throws IllegalArgumentException si l'image est {@code null} ou si une couleur demandée explicite est absente.
     * @throws IllegalStateException    si le mode auto détecte plusieurs couleurs d'annotation distinctes simultanées.
     */
    public SelectorColor resolveTargetColor(BufferedImage image, SelectorColor requestedColor) {
        if (image == null) {
            throw new IllegalArgumentException("L'image ne peut pas être null.");
        }

        if (requestedColor != null && requestedColor != SelectorColor.AUTO) {
            return validateRequestedColor(image, requestedColor);
        }

        return resolveAutomaticColor(image);
    }

    /**
     * Extrait la grille binaire correspondant aux pixels satisfaisant la signature chromatique spécifiée.
     *
     * @param image Image source.
     * @param color Couleur recherchée.
     * @return Masque binaire brut où chaque pixel actif correspond à la couleur cible.
     * @throws IllegalArgumentException si l'image ou la couleur est {@code null}.
     */
    public BinaryMask extractRawSelectorMask(BufferedImage image, SelectorColor color) {
        if (image == null) {
            throw new IllegalArgumentException("L'image ne peut pas être null.");
        }
        if (color == null) {
            throw new IllegalArgumentException("La couleur de sélection ne peut pas être null.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BinaryMask mask = new BinaryMask(width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (color.matches(r, g, b)) {
                    mask.set(x, y, true);
                }
            }
        }

        return mask;
    }

    /**
     * Extrait le masque binaire de l'intérieur de la région close délimitée par la couleur cible.
     *
     * @param image          Image source contenant le tracé.
     * @param requestedColor Couleur cible demandée ou {@link SelectorColor#AUTO}.
     * @return Masque binaire correspondant strictement à la région intérieure close.
     * @throws IllegalArgumentException si l'image est {@code null} ou si aucune couleur valide n'est trouvée.
     * @throws IllegalStateException    si le tracé présente une brèche non colmatable (> 10 px) ou est ambigu.
     */
    public BinaryMask extractInterior(BufferedImage image, SelectorColor requestedColor) {
        if (image == null) {
            throw new IllegalArgumentException("L'image ne peut pas être null.");
        }

        SelectorColor targetColor = resolveTargetColor(image, requestedColor);
        if (targetColor == null) {
            throw new IllegalArgumentException("Aucune couleur d'annotation valide n'a été détectée dans l'image de calque.");
        }

        BinaryMask rawMask = extractRawSelectorMask(image, targetColor);
        BinaryMask closedMask = MorphologyOps.close(rawMask, CLOSING_RADIUS);

        boolean[] exteriorVisited = floodExterior(closedMask);

        return buildInteriorMask(image.getWidth(), image.getHeight(), exteriorVisited, rawMask, targetColor);
    }

    /**
     * Valide la présence effective d'une couleur explicite demandée.
     *
     * @param image          Image source.
     * @param requestedColor Couleur concrète souhaitée.
     * @return La couleur validée si le seuil de pixels est atteint.
     * @throws IllegalArgumentException si la couleur est absente ou insuffisante.
     */
    private SelectorColor validateRequestedColor(BufferedImage image, SelectorColor requestedColor) {
        int count = countPixelsOfColor(image, requestedColor);
        if (count < MIN_COLOR_PIXELS) {
            throw new IllegalArgumentException("La couleur spécifiée (" + requestedColor
                    + ") n'a pas été détectée dans l'image de calque.");
        }
        return requestedColor;
    }

    /**
     * Résout automatiquement la couleur cible unique présente dans l'image.
     *
     * @param image Image source.
     * @return La couleur détectée, ou {@code null} si aucune couleur n'est présente.
     * @throws IllegalStateException si plusieurs couleurs distinctes sont simultanément détectées.
     */
    private SelectorColor resolveAutomaticColor(BufferedImage image) {
        List<SelectorColor> present = detectPresentColors(image);
        if (present.isEmpty()) {
            return null;
        }
        if (present.size() == 1) {
            return present.get(0);
        }
        throw new IllegalStateException("Plusieurs cadres de couleurs distinctes ont été détectés dans le calque : "
                + present + ". Veuillez préciser la zone à découper via l'option --zone-color <rouge|bleu|...>.");
    }

    /**
     * Compte le nombre de pixels correspondant à une couleur donnée dans toute l'image.
     *
     * @param image Image source.
     * @param color Couleur à dénombrer.
     * @return Nombre total de pixels concordants.
     */
    private int countPixelsOfColor(BufferedImage image, SelectorColor color) {
        int width = image.getWidth();
        int height = image.getHeight();
        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (color.matches(r, g, b)) {
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Scanne l'image en une seule passe pour dénombrer les pixels de chaque couleur concrète supportée.
     *
     * @param image Image source.
     * @return Tableau des effectifs de pixels indexé selon {@link #CONCRETE_COLORS}.
     */
    private int[] countConcreteColors(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] counts = new int[CONCRETE_COLORS.size()];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                incrementMatchingColor(r, g, b, counts);
            }
        }

        return counts;
    }

    /**
     * Incrémente le compteur de la première couleur concrète concordante pour un pixel donné.
     *
     * @param r      Composante rouge [0..255].
     * @param g      Composante verte [0..255].
     * @param b      Composante bleue [0..255].
     * @param counts Tableau des compteurs de couleurs à mettre à jour.
     */
    private void incrementMatchingColor(int r, int g, int b, int[] counts) {
        for (int i = 0; i < CONCRETE_COLORS.size(); i++) {
            if (CONCRETE_COLORS.get(i).matches(r, g, b)) {
                counts[i]++;
                break;
            }
        }
    }

    /**
     * Réalise une inondation par parcours en largeur (BFS) depuis les 4 bords extérieurs de l'image.
     *
     * @param closedMask Masque fermé faisant office de barrière infranchissable.
     * @return Tableau booléen indexé par coordonnées linéaires indiquant les pixels atteints depuis l'extérieur.
     */
    private boolean[] floodExterior(BinaryMask closedMask) {
        int width = closedMask.getWidth();
        int height = closedMask.getHeight();
        int totalPixels = width * height;

        boolean[] visited = new boolean[totalPixels];
        int[] queue = new int[totalPixels];
        int head = 0;
        int tail = 0;

        tail = seedExteriorBorders(closedMask, visited, queue, tail);

        while (head < tail) {
            int current = queue[head++];
            int cx = current % width;
            int cy = current / width;

            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];

                if (isInsideImage(nx, ny, width, height)) {
                    int nIdx = ny * width + nx;
                    if (!visited[nIdx] && !closedMask.get(nx, ny)) {
                        visited[nIdx] = true;
                        queue[tail++] = nIdx;
                    }
                }
            }
        }

        return visited;
    }

    /**
     * Initialise les graines d'inondation extérieure sur les 4 bordures géométriques de l'image.
     *
     * @param closedMask Masque fermé servant d'obstacle.
     * @param visited    Tableau des pixels déjà visités.
     * @param queue      File d'attente circulaire pour le BFS.
     * @param tail       Pointeur courant de fin de file.
     * @return Nouveau pointeur de fin de file après ensemencement des bords.
     */
    private int seedExteriorBorders(BinaryMask closedMask, boolean[] visited, int[] queue, int tail) {
        int width = closedMask.getWidth();
        int height = closedMask.getHeight();

        for (int x = 0; x < width; x++) {
            tail = trySeedPixel(x, 0, width, closedMask, visited, queue, tail);
            tail = trySeedPixel(x, height - 1, width, closedMask, visited, queue, tail);
        }

        for (int y = 0; y < height; y++) {
            tail = trySeedPixel(0, y, width, closedMask, visited, queue, tail);
            tail = trySeedPixel(width - 1, y, width, closedMask, visited, queue, tail);
        }

        return tail;
    }

    /**
     * Tente d'ajouter un pixel de départ dans la file d'inondation s'il n'est pas sur la barrière.
     *
     * @param x          Coordonnée X.
     * @param y          Coordonnée Y.
     * @param width      Largeur de l'image.
     * @param closedMask Masque de barrière fermée.
     * @param visited    Tableau de visite.
     * @param queue      File d'attente BFS.
     * @param tail       Pointeur de fin de file.
     * @return Pointeur de fin de file éventuellement incrémenté.
     */
    private int trySeedPixel(int x, int y, int width, BinaryMask closedMask,
                             boolean[] visited, int[] queue, int tail) {
        int idx = y * width + x;
        if (!visited[idx] && !closedMask.get(x, y)) {
            visited[idx] = true;
            queue[tail++] = idx;
        }
        return tail;
    }

    /**
     * Vérifie si des coordonnées (x, y) appartiennent à l'emprise rectangulaire de l'image.
     *
     * @param x      Coordonnée X.
     * @param y      Coordonnée Y.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @return {@code true} si le point est dans l'image, {@code false} sinon.
     */
    private boolean isInsideImage(int x, int y, int width, int height) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Construit le masque intérieur à partir des pixels non atteints par l'inondation extérieure.
     *
     * @param width           Largeur de l'image.
     * @param height          Hauteur de l'image.
     * @param exteriorVisited Tableau des pixels visités depuis l'extérieur.
     * @param rawMask         Masque brut du tracé d'annotation.
     * @param targetColor     Couleur cible sélectionnée (pour contextualisation des erreurs).
     * @return Le masque binaire correspondant à la région intérieure close.
     * @throws IllegalStateException si la superficie intérieure est inférieure au seuil minimal (brèche).
     */
    private BinaryMask buildInteriorMask(int width, int height, boolean[] exteriorVisited,
                                         BinaryMask rawMask, SelectorColor targetColor) {
        BinaryMask interior = new BinaryMask(width, height);
        int interiorCount = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (!exteriorVisited[idx] && !rawMask.get(x, y)) {
                    interior.set(x, y, true);
                    interiorCount++;
                }
            }
        }

        if (interiorCount < MIN_INTERIOR_PIXELS) {
            throw new IllegalStateException("Le tracé d'annotation de couleur " + targetColor
                    + " présente une brèche non colmatable (> 10 pixels). Veuillez fermer le contour.");
        }

        return interior;
    }
}
