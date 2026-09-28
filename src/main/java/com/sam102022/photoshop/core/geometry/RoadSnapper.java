package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Recaleur géométrique projetant les sommets d'un contour vectoriel vers les axes routiers environnants.
 * <p>
 * Calcule pour chaque sommet sa normale extérieure et balaye le rayon pour accrocher :
 * <ul>
 *     <li>Soit le bord extérieur de la route ({@link SnapTargetEdge#OUTER}) pour le mode Territoire ;</li>
 *     <li>Soit le bord intérieur de la chaussée ({@link SnapTargetEdge#INNER}) pour le mode Zone.</li>
 * </ul>
 * </p>
 */
public class RoadSnapper {

    /**
     * Vecteurs tangent et normal unitaires associés à un sommet du polygone.
     *
     * @param tx Composante X de la tangente unitaire.
     * @param ty Composante Y de la tangente unitaire.
     * @param nx Composante X de la normale extérieure unitaire.
     * @param ny Composante Y de la normale extérieure unitaire.
     */
    private record VertexNormal(double tx, double ty, double nx, double ny) {}

    /**
     * Recale les sommets d'un contour polygonal vers le bord extérieur des axes routiers (mode historique).
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
        return snap(contour, roadCandidates, imageWidth, imageHeight, config, SnapTargetEdge.OUTER);
    }

    /**
     * Recale les sommets d'un contour polygonal vers la cible spécifiée (bord intérieur ou extérieur).
     *
     * @param contour        Liste ordonnée des sommets du polygone initial.
     * @param roadCandidates Masque binaire des pixels candidats routiers.
     * @param imageWidth     Largeur de l'image.
     * @param imageHeight    Hauteur de l'image.
     * @param config         Configuration définissant la distance de recalage.
     * @param targetEdge     Cible de bordure routière (INNER ou OUTER).
     * @return Nouvelle liste immuable des sommets recalés.
     * @throws IllegalArgumentException si les arguments sont invalides.
     */
    public List<Point> snap(List<Point> contour, BinaryMask roadCandidates, int imageWidth,
                            int imageHeight, SnappingConfig config, SnapTargetEdge targetEdge) {
        validateArguments(contour, roadCandidates, imageWidth, imageHeight, config);
        return snapContour(contour, roadCandidates, imageWidth, imageHeight, config.snapDistance(), targetEdge);
    }

    /**
     * Recale directement une liste de points le long d'un masque de routes vers le bord intérieur ou extérieur.
     *
     * @param points      Liste des sommets.
     * @param candidates  Masque binaire des routes candidates.
     * @param imageWidth  Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius      Rayon maximal de balayage (en pixels).
     * @param targetEdge  Cible de bordure routière (INNER ou OUTER).
     * @return Nouvelle liste des sommets recalés.
     * @throws IllegalArgumentException si les arguments sont invalides ou si les dimensions divergent.
     */
    public List<Point> snapContour(List<Point> points, BinaryMask candidates, int imageWidth,
                                   int imageHeight, int radius, SnapTargetEdge targetEdge) {
        validateContourArguments(points, candidates, imageWidth, imageHeight, targetEdge);
        if (points.size() < 3) {
            return List.copyOf(points);
        }

        double area = signedArea(points);
        List<Point> snapped = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            Point current = points.get(i);
            VertexNormal normal = computeVertexNormal(points, i, area);
            snapped.add(snapPoint(current, normal, candidates, imageWidth, imageHeight, radius, targetEdge));
        }

        return List.copyOf(snapped);
    }

    /**
     * Valide la cohérence des arguments d'entrée pour l'opération de recalage avec configuration.
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
     * Valide les arguments pour l'opération de recalage géométrique direct.
     */
    private static void validateContourArguments(List<Point> points, BinaryMask candidates,
                                                 int imageWidth, int imageHeight, SnapTargetEdge targetEdge) {
        if (points == null || candidates == null || targetEdge == null) {
            throw new IllegalArgumentException("Les arguments ne peuvent pas être null.");
        }
        if (candidates.getWidth() != imageWidth || candidates.getHeight() != imageHeight) {
            throw new IllegalArgumentException("Dimensions incompatibles entre contour et masque routier.");
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
     * Recale un sommet individuel le long de sa normale vers le bord ciblé (INNER ou OUTER).
     *
     * @param current     Sommet d'origine à recaler.
     * @param normal      Vecteurs tangent et normal au sommet.
     * @param candidates  Masque binaire des candidats routiers.
     * @param imageWidth  Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius      Distance maximale de recalage (en pixels).
     * @param targetEdge  Cible de bordure routière.
     * @return Nouveau point recalé.
     */
    private Point snapPoint(Point current, VertexNormal normal, BinaryMask candidates,
                            int imageWidth, int imageHeight, int radius, SnapTargetEdge targetEdge) {
        if (normal == null) {
            return new Point(current);
        }

        // Si le point initial se trouve déjà sur la chaussée
        if (candidates.get(current.x, current.y)) {
            if (targetEdge == SnapTargetEdge.INNER) {
                return retractFromRoad(current, normal, candidates, imageWidth, imageHeight, radius);
            } else {
                return advanceToRoadExit(current, normal, candidates, imageWidth, imageHeight, radius);
            }
        }

        int[] interval = findRoadBandInterval(candidates, current, normal, imageWidth, imageHeight, radius);
        int roadStart = interval[0];
        int roadEnd = interval[1];

        if (roadStart < 0) {
            return new Point(current);
        }

        int targetStep;
        if (targetEdge == SnapTargetEdge.INNER) {
            targetStep = Math.max(0, roadStart - 1);
        } else {
            targetStep = Math.min(roadEnd + 1, radius);
        }

        int targetX = (int) Math.round(current.x + normal.nx() * targetStep);
        int targetY = (int) Math.round(current.y + normal.ny() * targetStep);
        return new Point(targetX, targetY);
    }

    /**
     * Rétracte un sommet situé sur la chaussée en reculant le long de la normale inverse (-n)
     * jusqu'à sortir de la route.
     */
    private Point retractFromRoad(Point current, VertexNormal normal, BinaryMask candidates,
                                  int imageWidth, int imageHeight, int radius) {
        for (int step = 1; step <= radius; step++) {
            int x = (int) Math.round(current.x - normal.nx() * step);
            int y = (int) Math.round(current.y - normal.ny() * step);
            if (isOutOfBounds(x, y, imageWidth, imageHeight)) {
                break;
            }
            if (!candidates.get(x, y)) {
                return new Point(x, y);
            }
        }
        return new Point(current);
    }

    /**
     * Avance un sommet situé sur la chaussée le long de la normale (+n) jusqu'à sortir
     * au bord extérieur de la route.
     */
    private Point advanceToRoadExit(Point current, VertexNormal normal, BinaryMask candidates,
                                    int imageWidth, int imageHeight, int radius) {
        for (int step = 1; step <= radius; step++) {
            int x = (int) Math.round(current.x + normal.nx() * step);
            int y = (int) Math.round(current.y + normal.ny() * step);
            if (isOutOfBounds(x, y, imageWidth, imageHeight)) {
                break;
            }
            if (!candidates.get(x, y)) {
                return new Point(x, y);
            }
        }
        return new Point(current);
    }

    /**
     * Balaye le rayon normal pour détecter l'intervalle [start, end] du premier ruban routier continu.
     *
     * @param candidates  Masque des candidats routiers.
     * @param current     Sommet initial du contour.
     * @param normal      Vecteurs tangent et normal au sommet.
     * @param imageWidth  Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius      Rayon maximal de recherche.
     * @return Tableau à 2 éléments [k_start, k_end] ou [-1, -1] si aucune route n'est accrochée.
     */
    private int[] findRoadBandInterval(BinaryMask candidates, Point current, VertexNormal normal,
                                       int imageWidth, int imageHeight, int radius) {
        int runStart = -1;
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
                runStart = step;
                runEnd = step;
            } else if (inBand && isRoad) {
                runEnd = step;
            } else if (inBand) {
                break;
            }
        }
        return new int[]{runStart, runEnd};
    }

    /**
     * Évalue si un pixel routier présente une cohérence directionnelle tangentielle suffisante (support >= 2).
     */
    private boolean hasTangentSupport(BinaryMask candidates, int x, int y, VertexNormal normal) {
        return tangentSupport(candidates, x, y, normal.tx(), normal.ty()) >= 2;
    }

    /**
     * Vérifie si des coordonnées sont situées hors des limites de l'image.
     */
    private boolean isOutOfBounds(int x, int y, int width, int height) {
        return x < 0 || x >= width || y < 0 || y >= height;
    }

    /**
     * Évalue le support tangentiel d'un pixel le long de l'axe de la route pour filtrer les artefacts isolés.
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
     */
    private boolean hasNearbyCandidate(BinaryMask candidates, int x, int y, double tx, double ty, int offset) {
        int sampleX = (int) Math.round(x + tx * offset);
        int sampleY = (int) Math.round(y + ty * offset);

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int cx = sampleX + dx;
                int cy = sampleY + dy;
                if (!isOutOfBounds(cx, cy, candidates.getWidth(), candidates.getHeight())
                        && candidates.get(cx, cy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Calcule l'aire signée du polygone fermé pour déterminer son orientation (horaire vs anti-horaire).
     */
    private double signedArea(List<Point> points) {
        double area = 0.0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            area += (double) a.x * b.y - (double) b.x * a.y;
        }
        return area / 2.0;
    }
}
