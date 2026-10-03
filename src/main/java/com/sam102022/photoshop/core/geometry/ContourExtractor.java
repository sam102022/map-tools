package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrait les contours ordonnés d'un masque binaire (voisinage 8-connexe, sens horaire).
 */
public class ContourExtractor {
    // 8-voisinage dans le sens horaire : 0:N, 1:NE, 2:E, 3:SE, 4:S, 5:SW, 6:W, 7:NW
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DY = {-1, -1, 0, 1, 1, 1, 0, -1};

    /**
     * Trace et extrait le plus grand contour extérieur ordonné d'un masque binaire
     * en utilisant l'algorithme de suivi de contour de Moore (8-voisinage).
     *
     * @param mask Masque binaire d'entrée.
     * @return Liste ordonnée des points formant le contour polygonal fermé.
     * @throws IllegalArgumentException si mask est null.
     */
    public List<Point> extractLargestContour(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }

        BinaryMask target = MorphologyOps.keepLargestComponent(mask);
        Point start = findStartingBoundaryPoint(target);
        if (start == null) {
            return List.of();
        }

        return traceMooreContour(target, start);
    }

    /**
     * Recherche le premier pixel de frontière dans le masque en balayant de haut en bas et de gauche à droite.
     *
     * @param target Masque binaire à explorer.
     * @return Premier point de frontière trouvé, ou null si le masque est vide.
     */
    private Point findStartingBoundaryPoint(BinaryMask target) {
        int height = target.getHeight();
        int width = target.getWidth();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (target.get(x, y) && isBoundary(target, x, y)) {
                    return new Point(x, y);
                }
            }
        }
        return null;
    }

    /**
     * Recherche la direction du premier voisin actif dans le 8-voisinage horaire.
     *
     * @param mask         Masque binaire.
     * @param current      Point actuel sur le contour.
     * @param startScanDir Direction initiale d'analyse (0 à 7).
     * @return Indice de direction [0..7] du voisin actif, ou -1 si aucun voisin n'est actif.
     */
    private int findNextNeighborDirection(BinaryMask mask, Point current, int startScanDir) {
        for (int offset = 0; offset < 8; offset++) {
            int d = (startScanDir + offset) % 8;
            int nx = current.x + DX[d];
            int ny = current.y + DY[d];
            if (mask.get(nx, ny)) {
                return d;
            }
        }
        return -1;
    }

    /**
     * Parcourt et trace le contour fermé à partir du point de départ via l'algorithme de Moore.
     *
     * @param target Masque binaire cible.
     * @param start  Point de départ sur la frontière.
     * @return Liste ordonnée des points formant la boucle de contour.
     */
    private List<Point> traceMooreContour(BinaryMask target, Point start) {
        List<Point> contour = new ArrayList<>();
        Point current = new Point(start);
        contour.add(new Point(current));

        Point firstNext = null;
        int startScanDir = 7;
        int maxLimit = Math.max(1000, (target.getWidth() + target.getHeight()) * 8);

        for (int iteration = 0; iteration < maxLimit; iteration++) {
            int foundDir = findNextNeighborDirection(target, current, startScanDir);
            Point next = (foundDir != -1) ? new Point(current.x + DX[foundDir], current.y + DY[foundDir]) : null;

            if (shouldStopTracing(current, start, next, firstNext)) {
                break;
            }

            if (firstNext == null) {
                firstNext = next;
            }

            contour.add(next);
            current = next;
            startScanDir = (foundDir + 5) % 8;
        }

        return List.copyOf(contour);
    }

    /**
     * Détermine si le traçage du contour de Moore doit s'interrompre.
     * La boucle s'arrête lorsqu'aucun voisin n'est actif ou lorsque le critère d'arrêt de Jacob
     * est vérifié (retour au point de départ avec le même pas initial).
     *
     * @param current   Point actuellement exploré sur le contour.
     * @param start     Point d'origine initial du contour.
     * @param next      Prochain point candidat, ou null si aucun voisin n'est actif.
     * @param firstNext Deuxième point validé lors de la première itération, ou null.
     * @return Vrai si l'exploration du contour doit prendre fin.
     */
    private boolean shouldStopTracing(Point current, Point start, Point next, Point firstNext) {
        if (next == null) {
            return true;
        }
        return current.equals(start) && next.equals(firstNext);
    }

    /**
     * Vérifie si un pixel actif possède au moins un voisin 8-connexe inactif (frontière extérieure).
     *
     * @param mask Masque binaire.
     * @param x    Coordonnée X.
     * @param y    Coordonnée Y.
     * @return Vrai si le pixel est sur le bord du masque.
     */
    private boolean isBoundary(BinaryMask mask, int x, int y) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dy != 0) && !mask.get(x + dx, y + dy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extrait l'ensemble des points de frontière non ordonnés d'un masque binaire.
     *
     * @param mask Masque binaire d'entrée.
     * @return Liste des points de frontière.
     * @throws IllegalArgumentException si mask est null.
     */
    public List<Point> extract(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        List<Point> boundary = new ArrayList<>();
        int height = mask.getHeight();
        int width = mask.getWidth();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y) && isBoundary(mask, x, y)) {
                    boundary.add(new Point(x, y));
                }
            }
        }
        return List.copyOf(boundary);
    }
}
