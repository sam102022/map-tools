package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Recale géodésiquement les sommets d'un contour polygonal vers le bord extérieur des axes routiers candidats.
 */
public class RoadSnapper {

    /**
     * Vecteurs tangentiel et normal unitaire à un sommet du contour.
     *
     * @param tx Composante X de la tangente unitaire.
     * @param ty Composante Y de la tangente unitaire.
     * @param nx Composante X de la normale extérieure unitaire.
     * @param ny Composante Y de la normale extérieure unitaire.
     */
    private record VertexNormal(double tx, double ty, double nx, double ny) {}

    /**
     * Recale les sommets d'un contour polygonal vers le bord extérieur des axes routiers les plus proches.
     *
     * @param contour        Liste ordonnée des sommets du polygone initial.
     * @param roadCandidates Masque binaire des pixels candidats routiers.
     * @param imageWidth     Largeur de l'image.
     * @param imageHeight    Hauteur de l'image.
     * @param config         Configuration définissant la distance maximale de recherche (snapDistance).
     * @return Nouvelle liste immuable des sommets recalés.
     * @throws IllegalArgumentException si les arguments sont invalides ou si les dimensions diffèrent.
     */
    public List<Point> snap(List<Point> contour, BinaryMask roadCandidates, int imageWidth,
                            int imageHeight, SnappingConfig config) {
        validateArguments(contour, roadCandidates, imageWidth, imageHeight, config);
        if (contour.size() < 3) {
            return List.copyOf(contour);
        }

        double area = signedArea(contour);
        List<Point> snapped = new ArrayList<>(contour.size());
        int radius = config.snapDistance();

        for (int i = 0; i < contour.size(); i++) {
            Point current = contour.get(i);
            VertexNormal normal = computeVertexNormal(contour, i, area);
            snapped.add(snapPoint(current, normal, roadCandidates, imageWidth, imageHeight, radius));
        }

        return List.copyOf(snapped);
    }

    /**
     * Valide la cohérence des arguments d'entrée pour l'opération de recalage.
     *
     * @param contour        Liste des sommets.
     * @param roadCandidates Masque des routes.
     * @param imageWidth     Largeur attendue.
     * @param imageHeight    Hauteur attendue.
     * @param config         Configuration.
     * @throws IllegalArgumentException si un argument est null ou si les dimensions divergent.
     */
    private void validateArguments(List<Point> contour, BinaryMask roadCandidates,
                                   int imageWidth, int imageHeight, SnappingConfig config) {
        if (contour == null || roadCandidates == null || config == null) {
            throw new IllegalArgumentException("Les arguments du recalage ne peuvent pas être null.");
        }
        if (roadCandidates.getWidth() != imageWidth || roadCandidates.getHeight() != imageHeight) {
            throw new IllegalArgumentException("Dimensions incompatibles entre contour et routes.");
        }
    }

    /**
     * Calcule la tangente et la normale extérieure unitaire pour un sommet donné du contour.
     *
     * @param contour Liste des sommets du polygone.
     * @param index   Indice du sommet courant.
     * @param area    Aire signée du contour.
     * @return Les vecteurs unitaires calculés, ou null si la longueur ou l'aire est nulle.
     */
    private VertexNormal computeVertexNormal(List<Point> contour, int index, double area) {
        if (area == 0) {
            return null;
        }
        int n = contour.size();
        Point previous = contour.get((index + n - 1) % n);
        Point next = contour.get((index + 1) % n);
        double tx = (next.x - previous.x);
        double ty = (next.y - previous.y);
        double length = Math.hypot(tx, ty);
        if (length == 0) {
            return null;
        }
        double unitTx = tx / length;
        double unitTy = ty / length;
        double nx = (area > 0 ? unitTy : -unitTy);
        double ny = (area > 0 ? -unitTx : unitTx);
        return new VertexNormal(unitTx, unitTy, nx, ny);
    }

    /**
     * Recale un sommet individuel du contour vers le bord extérieur du ruban routier s'il existe.
     *
     * @param current     Sommet d'origine à recaler.
     * @param normal      Vecteurs tangent et normal au sommet.
     * @param candidates  Masque binaire des candidats routiers.
     * @param imageWidth  Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius      Distance maximale de recalage (en pixels).
     * @return Nouveau point recalé, ou le point d'origine si aucune route n'est accrochée.
     */
    private Point snapPoint(Point current, VertexNormal normal, BinaryMask candidates,
                            int imageWidth, int imageHeight, int radius) {
        if (normal == null) {
            return new Point(current);
        }

        int roadEnd = findRoadBandEnd(candidates, current, normal, imageWidth, imageHeight, radius);
        if (roadEnd < 0) {
            return new Point(current);
        }

        int targetStep = Math.min(roadEnd + 1, radius);
        int targetX = (int) Math.round(current.x + normal.nx() * targetStep);
        int targetY = (int) Math.round(current.y + normal.ny() * targetStep);
        return new Point(targetX, targetY);
    }

    /**
     * Balaye le rayon normal pour détecter la fin du premier ruban routier continu.
     *
     * @param candidates  Masque des candidats routiers.
     * @param current     Sommet initial du contour.
     * @param normal      Vecteurs tangent et normal au sommet.
     * @param imageWidth  Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius      Rayon maximal de recherche.
     * @return La distance en pas vers le bord extérieur de la route, ou -1 si aucune route fiable n'est trouvée.
     */
    private int findRoadBandEnd(BinaryMask candidates, Point current, VertexNormal normal,
                                int imageWidth, int imageHeight, int radius) {
        int runEnd = -1;
        boolean inBand = false;

        for (int step = 1; step <= radius; step++) {
            int x = (int) Math.round(current.x + normal.nx() * step);
            int y = (int) Math.round(current.y + normal.ny() * step);
            if (isOutOfBounds(x, y, imageWidth, imageHeight)) {
                break;
            }

            boolean isRoad = candidates.get(x, y);
            if (!inBand && isRoad && hasTangentSupport(candidates, x, y, normal)) {
                inBand = true;
                runEnd = step;
            } else if (inBand && isRoad) {
                runEnd = step;
            } else if (inBand) {
                break;
            }
        }
        return runEnd;
    }

    /**
     * Évalue si un pixel routier présente une cohérence directionnelle tangentielle suffisante (support >= 2).
     *
     * @param candidates Masque des candidats routiers.
     * @param x          Coordonnée X.
     * @param y          Coordonnée Y.
     * @param normal     Vecteurs tangent et normal au sommet.
     * @return Vrai si le support tangentiel est supérieur ou égal à 2.
     */
    private boolean hasTangentSupport(BinaryMask candidates, int x, int y, VertexNormal normal) {
        return tangentSupport(candidates, x, y, normal.tx(), normal.ty()) >= 2;
    }

    /**
     * Vérifie si des coordonnées sont situées hors des limites de l'image.
     *
     * @param x      Coordonnée X.
     * @param y      Coordonnée Y.
     * @param width  Largeur de l'image.
     * @param height Hauteur de l'image.
     * @return Vrai si les coordonnées sont hors limites.
     */
    private boolean isOutOfBounds(int x, int y, int width, int height) {
        return x < 0 || x >= width || y < 0 || y >= height;
    }

    /**
     * Évalue le support tangentiel d'un pixel le long de l'axe de la route pour filtrer les artefacts isolés.
     *
     * @param candidates Masque des candidats routiers.
     * @param x          Coordonnée X du pixel évalué.
     * @param y          Coordonnée Y du pixel évalué.
     * @param tx         Composante tangentielle unitaire en X.
     * @param ty         Composante tangentielle unitaire en Y.
     * @return Nombre d'échantillons tangentiels validant la présence continue d'une route (sur 4).
     */
    private int tangentSupport(BinaryMask candidates, int x, int y, double tx, double ty) {
        int support = 0;
        for (int offset : new int[]{-4, -2, 2, 4}) {
            if (hasNearbyCandidate(candidates, x, y, tx, ty, offset)) {
                support++;
            }
        }
        return support;
    }

    /**
     * Recherche la présence d'au moins un candidat routier dans le voisinage transversal d'un décalage tangentiel.
     *
     * @param candidates Masque des candidats routiers.
     * @param x          Coordonnée X de base.
     * @param y          Coordonnée Y de base.
     * @param tx         Composante tangentielle en X.
     * @param ty         Composante tangentielle en Y.
     * @param offset     Décalage le long de la tangente.
     * @return Vrai si un candidat est présent à proximité transversale (-1, 0 ou +1).
     */
    private boolean hasNearbyCandidate(BinaryMask candidates, int x, int y, double tx, double ty, int offset) {
        int sx = (int) Math.round(x + tx * offset);
        int sy = (int) Math.round(y + ty * offset);
        for (int across = -1; across <= 1; across++) {
            int px = (int) Math.round(sx - ty * across);
            int py = (int) Math.round(sy + tx * across);
            if (candidates.get(px, py)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calcule l'aire signée (shoelace formula) du polygone pour déterminer son orientation (sens horaire ou trigonométrique).
     *
     * @param points Liste des sommets du polygone.
     * @return Aire géométrique signée (positive si sens trigonométrique, négative si sens horaire).
     */
    private double signedArea(List<Point> points) {
        double area = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            area += (double) a.x * b.y - (double) b.x * a.y;
        }
        return area / 2.0;
    }
}
