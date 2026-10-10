package com.sam102022.photoshop.core.geometry;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.awt.Point;
import java.awt.image.BufferedImage;
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
        return snap(contour, roadCandidates, imageWidth, imageHeight, config, targetEdge, null);
    }

    /**
     * Recale le contour vers la chaussée, puis affine sa position sur les bords visibles de la carte.
     *
     * @param contour Contour polygonal à recaler.
     * @param roadCandidates Masque des chaussées candidates, idéalement rasterisé depuis les axes OSM.
     * @param imageWidth Largeur de la carte et du masque.
     * @param imageHeight Hauteur de la carte et du masque.
     * @param config Configuration contenant le rayon maximal de recherche.
     * @param targetEdge Bord de chaussée ciblé.
     * @param mapImage Carte affichée, utilisée pour mesurer le contraste au bord de la chaussée.
     * @return Contour recalé sur les bords visibles des chaussées.
     * @throws IllegalArgumentException si les arguments ou dimensions sont incompatibles.
     */
    public List<Point> snap(List<Point> contour, BinaryMask roadCandidates, int imageWidth,
                            int imageHeight, SnappingConfig config, SnapTargetEdge targetEdge,
                            BufferedImage mapImage) {
        validateArguments(contour, roadCandidates, imageWidth, imageHeight, config);
        validateGuideImage(mapImage, imageWidth, imageHeight);
        return snapContour(contour, roadCandidates, imageWidth, imageHeight,
                config.snapDistance(), targetEdge, mapImage);
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
        return snapContour(points, candidates, imageWidth, imageHeight, radius, targetEdge, null);
    }

    /**
     * Recale les points du contour avec un guide visuel optionnel.
     *
     * @param points Points ordonnés du contour.
     * @param candidates Masque des routes candidates.
     * @param imageWidth Largeur de l'image.
     * @param imageHeight Hauteur de l'image.
     * @param radius Rayon maximal de recherche.
     * @param targetEdge Bord routier recherché.
     * @param mapImage Carte dont le contraste peut préciser le bord de la chaussée.
     * @return Copie recalée des points.
     */
    private List<Point> snapContour(List<Point> points, BinaryMask candidates, int imageWidth,
                                    int imageHeight, int radius, SnapTargetEdge targetEdge,
                                    BufferedImage mapImage) {
        validateContourArguments(points, candidates, imageWidth, imageHeight, targetEdge);
        validateGuideImage(mapImage, imageWidth, imageHeight);
        if (points.size() < 3) {
            return List.copyOf(points);
        }

        double area = signedArea(points);
        List<Point> snapped = new ArrayList<>(points.size());

        for (int i = 0; i < points.size(); i++) {
            Point current = points.get(i);
            VertexNormal normal = computeVertexNormal(points, i, area);
            snapped.add(snapPoint(current, normal, candidates, imageWidth, imageHeight,
                    radius, targetEdge, mapImage));
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
     * Vérifie que l'image-guide optionnelle correspond au repère du masque.
     *
     * @param mapImage Image de carte facultative.
     * @param width Largeur attendue.
     * @param height Hauteur attendue.
     */
    private static void validateGuideImage(BufferedImage mapImage, int width, int height) {
        if (mapImage != null && (mapImage.getWidth() != width || mapImage.getHeight() != height)) {
            throw new IllegalArgumentException("Dimensions incompatibles entre la carte et le masque routier.");
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
                            int imageWidth, int imageHeight, int radius, SnapTargetEdge targetEdge,
                            BufferedImage mapImage) {
        if (normal == null) {
            return new Point(current);
        }

        // Si le point initial se trouve déjà sur la chaussée
        if (candidates.get(current.x, current.y)) {
            if (targetEdge == SnapTargetEdge.INNER) {
                return retractFromRoad(current, normal, candidates, imageWidth, imageHeight, radius);
            } else {
                Point edge = advanceToRoadExit(current, normal, candidates, imageWidth, imageHeight, radius);
                return refineVisibleOuterEdge(edge, normal, candidates, mapImage);
            }
        }

        Point outwardTarget = targetFromRoadBand(current, normal, candidates, imageWidth, imageHeight,
                radius, targetEdge, 1);
        Point inwardTarget = targetFromRoadBand(current, normal, candidates, imageWidth, imageHeight,
                radius, targetEdge, -1);
        Point target = nearestTarget(current, outwardTarget, inwardTarget);
        if (target == null) return new Point(current);
        return targetEdge == SnapTargetEdge.OUTER
                ? refineVisibleOuterEdge(target, normal, candidates, mapImage)
                : target;
    }

    /**
     * Retourne le bord de chaussée détecté dans le sens indiqué par le vecteur normal.
     *
     * @param current Point courant du contour.
     * @param normal Normale locale du contour.
     * @param candidates Masque des routes.
     * @param width Largeur de l'image.
     * @param height Hauteur de l'image.
     * @param radius Rayon maximal.
     * @param targetEdge Bord routier ciblé.
     * @param direction Sens de balayage (+1 extérieur, -1 intérieur).
     * @return Point cible, ou null si aucune chaussée n'est trouvée.
     */
    private Point targetFromRoadBand(Point current, VertexNormal normal, BinaryMask candidates,
                                    int width, int height, int radius, SnapTargetEdge targetEdge,
                                    int direction) {
        int[] interval = findRoadBandInterval(candidates, current, normal, width, height, radius, direction);
        if (interval[0] < 0) return null;
        int step = targetEdge == SnapTargetEdge.OUTER
                ? (direction > 0 ? interval[1] + 1 : -(interval[0] - 1))
                : (direction > 0 ? interval[0] - 1 : -(interval[1] + 1));
        int x = (int) Math.round(current.x + normal.nx() * step);
        int y = (int) Math.round(current.y + normal.ny() * step);
        return isOutOfBounds(x, y, width, height) ? null : new Point(x, y);
    }

    /**
     * Sélectionne le point de chaussée le plus proche parmi les deux normales.
     *
     * @param origin Point initial.
     * @param first Première cible éventuelle.
     * @param second Seconde cible éventuelle.
     * @return La cible la plus proche, ou null si les deux côtés sont vides.
     */
    private Point nearestTarget(Point origin, Point first, Point second) {
        if (first == null) return second;
        if (second == null) return first;
        return origin.distanceSq(first) <= origin.distanceSq(second) ? first : second;
    }

    /**
     * Ajuste de quelques pixels la limite OSM sur le contraste réellement visible dans la carte.
     *
     * @param target Bord approximatif issu du masque OSM.
     * @param normal Normale extérieure du territoire.
     * @param candidates Masque routier servant de garde-fou spatial.
     * @param mapImage Carte source, éventuellement absente.
     * @return Position du bord visible ou bord OSM initial si le contraste est insuffisant.
     */
    private Point refineVisibleOuterEdge(Point target, VertexNormal normal, BinaryMask candidates,
                                         BufferedImage mapImage) {
        if (mapImage == null) return target;
        Point best = target;
        double bestScore = 5.0;
        for (int offset = -6; offset <= 6; offset++) {
            int x = (int) Math.round(target.x + normal.nx() * offset);
            int y = (int) Math.round(target.y + normal.ny() * offset);
            if (!isRoadNearby(candidates, x, y, 8)) continue;
            double contrast = roadnessDifference(mapImage, x, y, normal);
            double score = contrast - Math.abs(offset) * 0.9;
            if (score > bestScore) {
                bestScore = score;
                best = new Point(x, y);
            }
        }
        return best;
    }

    /**
     * Mesure la différence de caractère routier entre les côtés intérieur et extérieur du bord.
     *
     * @param image Carte à analyser.
     * @param x Abscisse du point de bord candidat.
     * @param y Ordonnée du point de bord candidat.
     * @param normal Normale extérieure.
     * @return Contraste orienté vers l'extérieur.
     */
    private double roadnessDifference(BufferedImage image, int x, int y, VertexNormal normal) {
        double inner = 0;
        double outer = 0;
        int samples = 0;
        for (int offset = 1; offset <= 3; offset++) {
            int ix = (int) Math.round(x - normal.nx() * offset);
            int iy = (int) Math.round(y - normal.ny() * offset);
            int ox = (int) Math.round(x + normal.nx() * offset);
            int oy = (int) Math.round(y + normal.ny() * offset);
            if (!isOutOfBounds(ix, iy, image.getWidth(), image.getHeight())
                    && !isOutOfBounds(ox, oy, image.getWidth(), image.getHeight())) {
                inner += roadness(image.getRGB(ix, iy));
                outer += roadness(image.getRGB(ox, oy));
                samples++;
            }
        }
        return samples == 0 ? 0 : (inner - outer) / samples;
    }

    /**
     * Calcule un score favorisant la dominante bleu-gris des chaussées et ignorant les textes gris.
     *
     * @param rgb Couleur source ARGB.
     * @return Score chromatique et tonal du pixel.
     */
    private double roadness(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return Math.max(0, 0.75 * (blue - red) + 0.25 * (green - red));
    }

    /**
     * Vérifie que le point raffiné reste dans le voisinage immédiat d'une route OSM.
     *
     * @param candidates Masque des routes.
     * @param x Abscisse du point.
     * @param y Ordonnée du point.
     * @param radius Rayon de garde.
     * @return Vrai si un candidat routier est proche.
     */
    private boolean isRoadNearby(BinaryMask candidates, int x, int y, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dy * dy <= radius * radius && candidates.get(x + dx, y + dy)) return true;
            }
        }
        return false;
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
                                       int imageWidth, int imageHeight, int radius, int direction) {
        int runStart = -1;
        int runEnd = -1;
        boolean inBand = false;

        for (int step = 1; step <= radius; step++) {
            int x = (int) Math.round(current.x + normal.nx() * step * direction);
            int y = (int) Math.round(current.y + normal.ny() * step * direction);
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
