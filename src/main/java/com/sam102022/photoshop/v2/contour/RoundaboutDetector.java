package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Détecteur géométrique de carrefours giratoires et modélisateur d'anneaux routiers.
 * Identifie les cellules centrales candidates (îlots), sonde radialement la chaussée
 * fermée Rc sur 240 directions et ajuste une ellipse extérieure par régression robuste.
 */
public class RoundaboutDetector {

    private final SubpixelContourExtractor contourExtractor;
    private final AlgebraicEllipseFitter ellipseFitter;

    /**
     * Initialise le détecteur avec ses sous-composants dédiés.
     */
    public RoundaboutDetector() {
        this.contourExtractor = new SubpixelContourExtractor();
        this.ellipseFitter = new AlgebraicEllipseFitter();
    }

    /**
     * Détecte les carrefours giratoires au sein du réseau routier recadré.
     *
     * @param rc        Masque binaire de la chaussée routière fermée.
     * @param labelMap  Matrice des étiquettes des cellules du non-route.
     * @param cells     Liste de l'ensemble des cellules candidates.
     * @param maxRadius Rayon maximal de sondage radial en pixels (typiquement 90 px).
     * @param numRays   Nombre de rayons angulaires (typiquement 240).
     * @return Liste ordonnée immuable des ronds-points détectés et modélisés.
     */
    public List<Roundabout> detect(BinaryMask rc, CellLabelMap labelMap, List<Cell> cells,
                                   double maxRadius, int numRays) {
        if (rc == null || labelMap == null || cells == null) {
            return List.of();
        }

        int w = rc.getWidth();
        int h = rc.getHeight();
        List<Roundabout> roundabouts = new ArrayList<>();

        for (Cell cell : cells) {
            if (!isPotentialIsland(cell, w, h)) {
                continue;
            }

            List<PixelPoint> islandContour = extractIslandContour(cell, labelMap);
            if (islandContour.size() < 5) {
                continue;
            }

            Optional<EllipseModel> islandEllipseOpt = validateIslandEllipse(cell, islandContour);
            if (islandEllipseOpt.isEmpty()) {
                continue;
            }

            double cx = cell.centroid().x();
            double cy = cell.centroid().y();
            RaySamplingResult sampling = castRadialRays(rc, cx, cy, maxRadius, numRays);
            if (sampling.points().size() < numRays * 0.5) {
                continue;
            }

            Optional<FittedRing> ringOpt = fitExteriorRing(sampling, cell.area());
            if (ringOpt.isPresent()) {
                FittedRing ring = ringOpt.get();
                roundabouts.add(new Roundabout(
                        cell.id(),
                        new PixelPoint(cx, cy),
                        ring.ellipse(),
                        cell.area(),
                        ring.inlierRatio()
                ));
            }
        }

        return Collections.unmodifiableList(roundabouts);
    }

    /**
     * Évalue si une cellule répond aux critères géométriques préalables d'un îlot central de giratoire.
     *
     * @param cell Cellule à analyser.
     * @param w    Largeur du masque.
     * @param h    Hauteur du masque.
     * @return true si la cellule est un îlot potentiel intérieur.
     */
    private boolean isPotentialIsland(Cell cell, int w, int h) {
        if (cell.area() < 40 || cell.area() > 9000) {
            return false;
        }
        return cell.minX() > 1 && cell.minY() > 1 && cell.maxX() < w - 1 && cell.maxY() < h - 1;
    }

    /**
     * Extrait le contour sub-pixel local d'une cellule îlot et le replace dans le repère global.
     *
     * @param cell     Cellule candidate.
     * @param labelMap Matrice des étiquettes.
     * @return Liste des points du contour de l'îlot.
     */
    private List<PixelPoint> extractIslandContour(Cell cell, CellLabelMap labelMap) {
        int x0 = cell.minX();
        int y0 = cell.minY();
        int bw = cell.maxX() - x0 + 1;
        int bh = cell.maxY() - y0 + 1;

        BinaryMask patch = new BinaryMask(bw, bh);
        for (int y = y0; y <= cell.maxY(); y++) {
            for (int x = x0; x <= cell.maxX(); x++) {
                if (labelMap.getLabel(x, y) == cell.id()) {
                    patch.set(x - x0, y - y0, true);
                }
            }
        }

        List<PixelPoint> patchContour = contourExtractor.extract(patch, 1.0);
        List<PixelPoint> globalContour = new ArrayList<>(patchContour.size());
        for (PixelPoint p : patchContour) {
            globalContour.add(new PixelPoint(p.x() + x0, p.y() + y0));
        }
        return globalContour;
    }

    /**
     * Valide la géométrie elliptique de l'îlot central selon son élongation et ses résidus RMS.
     *
     * @param cell    Cellule d'origine.
     * @param contour Points du contour de l'îlot.
     * @return Optional contenant l'ellipse d'îlot validée ou Optional.empty() si non elliptique.
     */
    private Optional<EllipseModel> validateIslandEllipse(Cell cell, List<PixelPoint> contour) {
        Optional<EllipseModel> opt = ellipseFitter.fit(contour);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        EllipseModel ell = opt.get();
        double a = ell.a();
        double b = ell.b();
        if (b / a < 0.55) {
            return Optional.empty();
        }

        double sumSq = 0.0;
        for (PixelPoint p : contour) {
            double res = ellipseFitter.distanceToEllipse(p, ell);
            sumSq += res * res;
        }
        double rms = Math.sqrt(sumSq / contour.size());
        double normalizedRes = rms / ((a + b) / 2.0);

        if (normalizedRes > 0.06 && rms > 1.0) {
            return Optional.empty();
        }
        return Optional.of(ell);
    }

    /**
     * Lance des sondes radiales réparties uniformément depuis le centre pour détecter la sortie de voie.
     *
     * @param rc        Masque binaire de la chaussée fermée.
     * @param cx        Abscisse du centre de l'îlot.
     * @param cy        Ordonnée du centre de l'îlot.
     * @param maxRadius Rayon maximal exploré en pixels.
     * @param numRays   Nombre de rayons émis.
     * @return Résultat du sondage radial (points de contact et distances).
     */
    private RaySamplingResult castRadialRays(BinaryMask rc, double cx, double cy, double maxRadius, int numRays) {
        int w = rc.getWidth();
        int h = rc.getHeight();
        List<PixelPoint> points = new ArrayList<>();
        List<Double> distances = new ArrayList<>();

        for (int i = 0; i < numRays; i++) {
            double angle = 2.0 * Math.PI * i / numRays;
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);

            int state = 0;
            double dist = 0.0;
            double exitDist = -1.0;

            while (dist < maxRadius) {
                int px = (int) Math.round(cx + dx * dist);
                int py = (int) Math.round(cy + dy * dist);
                if (px < 0 || px >= w || py < 0 || py >= h) {
                    break;
                }

                boolean inRoad = rc.get(px, py);
                if (state == 0 && inRoad) {
                    state = 1;
                } else if (state == 1 && !inRoad) {
                    exitDist = dist;
                    break;
                }
                dist += 0.5;
            }

            if (exitDist > 0.0) {
                distances.add(exitDist);
                points.add(new PixelPoint(cx + dx * exitDist, cy + dy * exitDist));
            }
        }

        double[] distArr = distances.stream().mapToDouble(Double::doubleValue).toArray();
        return new RaySamplingResult(points, distArr);
    }

    /**
     * Ajuste itérativement une ellipse sur les points du bord extérieur de l'anneau routier.
     *
     * @param sampling   Points et distances issus du sondage radial.
     * @param islandArea Surface en pixels de l'îlot central.
     * @return Optional contenant l'anneau extérieur modélisé ou Optional.empty().
     */
    private Optional<FittedRing> fitExteriorRing(RaySamplingResult sampling, double islandArea) {
        double[] dists = sampling.distances();
        List<PixelPoint> points = sampling.points();
        double threshold = computePercentile(dists, 35.0) + 1.0;

        List<PixelPoint> keptPoints = new ArrayList<>();
        for (int i = 0; i < dists.length; i++) {
            if (dists[i] <= threshold) {
                keptPoints.add(points.get(i));
            }
        }

        if (keptPoints.size() < 8) {
            return Optional.empty();
        }

        EllipseModel bestModel = null;
        double bestInlierRatio = 0.0;

        for (int iter = 0; iter < 8; iter++) {
            if (keptPoints.size() < 8) {
                break;
            }

            Optional<EllipseModel> fitOpt = ellipseFitter.fit(keptPoints);
            if (fitOpt.isEmpty()) {
                break;
            }

            EllipseModel current = fitOpt.get();
            double[] residuals = new double[points.size()];
            int inlierCount = 0;

            for (int i = 0; i < points.size(); i++) {
                double r = ellipseFitter.distanceToEllipse(points.get(i), current);
                residuals[i] = r;
                if (r < 2.0) {
                    inlierCount++;
                }
            }

            bestModel = current;
            bestInlierRatio = (double) inlierCount / points.size();

            double p60 = computePercentile(residuals, 60.0);
            double cutoff = Math.max(1.5, p60);

            keptPoints = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                if (residuals[i] < cutoff) {
                    keptPoints.add(points.get(i));
                }
            }
        }

        if (bestModel == null) {
            return Optional.empty();
        }

        double ir = Math.sqrt(islandArea / Math.PI);
        double minAxis = Math.min(bestModel.a(), bestModel.b());
        double maxAxis = Math.max(bestModel.a(), bestModel.b());

        if (bestInlierRatio < 0.30 || minAxis < 1.3 * ir || maxAxis > 4.5 * ir + 8.0 || maxAxis > 70.0) {
            return Optional.empty();
        }

        return Optional.of(new FittedRing(bestModel, bestInlierRatio));
    }

    /**
     * Calcule un percentile linéaire sur un tableau de doubles non trié.
     *
     * @param values     Tableau de valeurs numériques.
     * @param percentile Rang de percentile compris entre 0.0 et 100.0.
     * @return Valeur interpolée au percentile spécifié.
     */
    private double computePercentile(double[] values, double percentile) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        double rank = (percentile / 100.0) * (sorted.length - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        double weight = rank - low;
        return sorted[low] * (1.0 - weight) + sorted[high] * weight;
    }

    /**
     * Record interne encapsulant les résultats du sondage radial.
     *
     * @param points    Points de sortie de la chaussée.
     * @param distances Distances mesurées depuis le centre.
     */
    private record RaySamplingResult(List<PixelPoint> points, double[] distances) {}

    /**
     * Record interne encapsulant une ellipse d'anneau ajustée avec son taux d'inliers.
     *
     * @param ellipse     Modèle d'ellipse ajusté.
     * @param inlierRatio Taux de concordance des rayons (inliers).
     */
    private record FittedRing(EllipseModel ellipse, double inlierRatio) {}
}
