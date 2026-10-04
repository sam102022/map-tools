package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Connecteur tangentiel C1 par splines cubiques d'Hermite et substitution d'arcs de carrefours giratoires.
 * Raccorde de façon continue et dérivable les tronçons routiers réguliers à l'anneau extérieur
 * des ronds-points détectés, avec garde-fou anti-boucle (fallback linéaire si déviation &gt; 0.7L + 2).
 */
public class HermiteSplineConnector {

    /**
     * Génère une spline cubique d'Hermite discrétisée entre deux points et vecteurs tangents.
     * En cas de déviation excessive (boucle), bascule sur un raccordement linéaire direct.
     *
     * @param p0 Point de départ.
     * @param t0 Vecteur tangent unitaire de départ.
     * @param p1 Point d'arrivée.
     * @param t1 Vecteur tangent unitaire d'arrivée.
     * @return Liste ordonnée immuable des points de la spline.
     */
    public List<PixelPoint> evaluateHermite(PixelPoint p0, PixelPoint t0, PixelPoint p1, PixelPoint t1) {
        double dx = p1.x() - p0.x();
        double dy = p1.y() - p0.y();
        double len = Math.hypot(dx, dy);

        if (len < 2.0) {
            return List.of(p0);
        }

        int steps = Math.max(6, (int) Math.floor(len * 1.2));
        List<PixelPoint> splinePoints = new ArrayList<>(steps);
        double maxDeviation = 0.0;

        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            double t2 = t * t;
            double t3 = t2 * t;

            double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
            double h10 = t3 - 2.0 * t2 + t;
            double h01 = -2.0 * t3 + 3.0 * t2;
            double h11 = t3 - t2;

            double x = h00 * p0.x() + h10 * t0.x() * len + h01 * p1.x() + h11 * t1.x() * len;
            double y = h00 * p0.y() + h10 * t0.y() * len + h01 * p1.y() + h11 * t1.y() * len;

            double chordX = p0.x() + t * dx;
            double chordY = p0.y() + t * dy;
            double dev = Math.hypot(x - chordX, y - chordY);
            if (dev > maxDeviation) {
                maxDeviation = dev;
            }
            splinePoints.add(new PixelPoint(x, y));
        }

        // Garde-fou anti-boucle : repli linéaire direct si déviation trop forte
        if (maxDeviation > 0.7 * len + 2.0) {
            return generateLinearFallback(p0, dx, dy, steps);
        }

        return Collections.unmodifiableList(splinePoints);
    }

    private List<PixelPoint> generateLinearFallback(PixelPoint p0, double dx, double dy, int steps) {
        List<PixelPoint> fallback = new ArrayList<>(steps);
        for (int i = 0; i < steps; i++) {
            double t = (double) i / steps;
            fallback.add(new PixelPoint(p0.x() + t * dx, p0.y() + t * dy));
        }
        return Collections.unmodifiableList(fallback);
    }

    /**
     * Intègre les ronds-points détectés dans le contour en substituant l'arc extérieur de l'ellipse.
     *
     * @param contour       Contour fermé en cours de traitement.
     * @param roundabouts   Liste des ronds-points détectés.
     * @param territoryMask Masque du territoire retenu (pour tester l'extérieur de l'arc).
     * @param roundaboutZr  Rayon d'influence relatif (typiquement 2.0).
     * @return Contour vectoriel enrichi des arcs d'ellipses.
     */
    public List<PixelPoint> integrateRoundabouts(List<PixelPoint> contour, List<Roundabout> roundabouts,
                                                 BinaryMask territoryMask, double roundaboutZr) {
        if (contour == null || roundabouts == null || roundabouts.isEmpty()) {
            return contour;
        }

        List<PixelPoint> currentContour = new ArrayList<>(contour);

        for (Roundabout rb : roundabouts) {
            EllipseModel ell = rb.exteriorEllipse();
            if (!bordersRoundabout(currentContour, ell) || !intersectsTerritoryDisk(ell, territoryMask)) {
                continue;
            }

            ContactBounds contact = findContactBounds(currentContour, ell, roundaboutZr);
            if (contact == null) {
                continue;
            }

            SelectedArc selectedArc = selectBestExteriorArc(currentContour.get(contact.iA()),
                    currentContour.get(contact.iB()), ell, territoryMask);
            if (selectedArc == null) {
                continue;
            }

            currentContour = spliceRoundaboutArc(currentContour, contact, selectedArc, ell);
        }

        return Collections.unmodifiableList(currentContour);
    }

    private boolean bordersRoundabout(List<PixelPoint> contour, EllipseModel ell) {
        int nearCount = 0;
        for (PixelPoint p : contour) {
            PixelPoint u = ell.toUnitCircle(p);
            double rho = Math.hypot(u.x(), u.y());
            if (rho >= 0.75 && rho <= 1.3) {
                nearCount++;
            }
        }
        return nearCount >= 12;
    }

    private boolean intersectsTerritoryDisk(EllipseModel ell, BinaryMask territoryMask) {
        int w = territoryMask.getWidth();
        int h = territoryMask.getHeight();
        int insideCount = 0;
        int totalProbes = 0;

        for (int rIdx = 1; rIdx <= 5; rIdx++) {
            double r = (double) rIdx / 5.0;
            for (int tIdx = 0; tIdx < 90; tIdx++) {
                double th = 2.0 * Math.PI * tIdx / 90.0;
                PixelPoint pt = ell.fromUnitCircle(r * Math.cos(th), r * Math.sin(th));
                int px = Math.clamp((int) Math.round(pt.x()), 0, w - 1);
                int py = Math.clamp((int) Math.round(pt.y()), 0, h - 1);
                if (territoryMask.get(px, py)) {
                    insideCount++;
                }
                totalProbes++;
            }
        }

        return (double) insideCount / totalProbes >= 0.20;
    }

    private ContactBounds findContactBounds(List<PixelPoint> contour, EllipseModel ell, double zr) {
        int n = contour.size();
        List<Integer> contactIndices = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            PixelPoint u = ell.toUnitCircle(contour.get(i));
            if (Math.hypot(u.x(), u.y()) <= zr) {
                contactIndices.add(i);
            }
        }

        if (contactIndices.isEmpty()) {
            return null;
        }

        int maxGap = -1;
        int maxGapIdx = 0;
        int numContacts = contactIndices.size();

        for (int k = 0; k < numContacts; k++) {
            int curr = contactIndices.get(k);
            int next = (k == numContacts - 1) ? contactIndices.get(0) + n : contactIndices.get(k + 1);
            int gap = next - curr;
            if (gap > maxGap) {
                maxGap = gap;
                maxGapIdx = k;
            }
        }

        int iA = contactIndices.get((maxGapIdx + 1) % numContacts);
        int iB = contactIndices.get(maxGapIdx);
        int runlen = Math.floorMod(iB - iA, n) + 1;

        if (runlen > 10.0 * ell.a()) {
            return null;
        }

        return new ContactBounds(iA, iB, runlen);
    }

    private SelectedArc selectBestExteriorArc(PixelPoint ptA, PixelPoint ptB, EllipseModel ell, BinaryMask territoryMask) {
        PixelPoint ua = ell.toUnitCircle(ptA);
        PixelPoint ub = ell.toUnitCircle(ptB);
        double ra = Math.max(Math.hypot(ua.x(), ua.y()), 1.05);
        double rb = Math.max(Math.hypot(ub.x(), ub.y()), 1.05);

        double pa = Math.atan2(ua.y(), ua.x());
        double pb = Math.atan2(ub.y(), ub.x());
        double da = Math.acos(1.0 / ra);
        double db = Math.acos(1.0 / rb);

        SelectedArc best = null;
        double bestScore = Double.MAX_VALUE;

        for (int sgn : new int[]{1, -1}) {
            double fa = pa + sgn * da;
            double fb = pb - sgn * db;
            double sw = Math.floorMod((long) Math.toDegrees((fb - fa) * sgn * 1000.0), 360000L) / 1000.0;
            double sweepRad = Math.toRadians(sw);

            int arcLen = Math.max(8, (int) Math.floor(sweepRad * Math.max(ell.a(), ell.b())));
            List<PixelPoint> arcPoints = new ArrayList<>(arcLen);
            int insideCount = 0;

            for (int k = 0; k < arcLen; k++) {
                double ph = fa + sgn * (k * sweepRad / (arcLen - 1));
                double u = Math.cos(ph);
                double v = Math.sin(ph);
                arcPoints.add(ell.fromUnitCircle(u, v));

                PixelPoint probe = ell.fromUnitCircle(u * 1.12, v * 1.12);
                int px = Math.clamp((int) Math.round(probe.x()), 0, territoryMask.getWidth() - 1);
                int py = Math.clamp((int) Math.round(probe.y()), 0, territoryMask.getHeight() - 1);
                if (territoryMask.get(px, py)) {
                    insideCount++;
                }
            }

            double insideRatio = (double) insideCount / arcLen;
            double score = insideRatio + (sweepRad > Math.toRadians(320.0) ? 0.5 : 0.0);

            if (score < bestScore) {
                bestScore = score;
                best = new SelectedArc(arcPoints);
            }
        }

        return best;
    }

    private List<PixelPoint> spliceRoundaboutArc(List<PixelPoint> contour, ContactBounds contact,
                                                 SelectedArc arc, EllipseModel ell) {
        int n = contour.size();
        PixelPoint ptA = contour.get(contact.iA());
        PixelPoint ptB = contour.get(contact.iB());

        List<PixelPoint> arcPts = arc.points();
        PixelPoint ap = arcPts.get(0);
        PixelPoint bp = arcPts.get(arcPts.size() - 1);

        PixelPoint dA = computeUnitTangent(contour, contact.iA(), 6, n);
        PixelPoint dB = computeUnitTangent(contour, contact.iB(), 6, n);

        PixelPoint tA = computeTangentFromPoints(ap, arcPts.get(Math.min(3, arcPts.size() - 1)));
        PixelPoint tB = computeTangentFromPoints(arcPts.get(Math.max(0, arcPts.size() - 4)), bp);

        List<PixelPoint> splineA = evaluateHermite(ptA, dA, ap, tA);
        List<PixelPoint> splineB = evaluateHermite(bp, tB, ptB, dB);

        List<PixelPoint> newContour = new ArrayList<>();
        newContour.addAll(splineA);
        newContour.addAll(arcPts);
        newContour.addAll(splineB);

        // Ajout du reste du contour
        for (int i = contact.runlen(); i < n; i++) {
            int idx = Math.floorMod(contact.iA() + i, n);
            newContour.add(contour.get(idx));
        }

        return newContour;
    }

    private PixelPoint computeUnitTangent(List<PixelPoint> contour, int centerIdx, int offset, int n) {
        PixelPoint prev = contour.get(Math.floorMod(centerIdx - offset, n));
        PixelPoint next = contour.get(Math.floorMod(centerIdx + offset, n));
        return computeTangentFromPoints(prev, next);
    }

    private PixelPoint computeTangentFromPoints(PixelPoint from, PixelPoint to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double len = Math.hypot(dx, dy) + 1e-9;
        return new PixelPoint(dx / len, dy / len);
    }

    private record ContactBounds(int iA, int iB, int runlen) {}
    private record SelectedArc(List<PixelPoint> points) {}
}
