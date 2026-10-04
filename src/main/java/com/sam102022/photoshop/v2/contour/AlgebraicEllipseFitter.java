package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Solveur d'ajustement direct d'ellipse algébrique aux moindres carrés en 100% Java standard.
 * Implémente l'algorithme direct de Halir &amp; Flusser (1998) sous contrainte quadratique 4AC - B^2 = 1.
 */
public class AlgebraicEllipseFitter {

    private static final double EPSILON = 1e-12;

    /**
     * Ajuste une ellipse euclidienne sur un ensemble de points 2D.
     *
     * @param points Liste des points 2D (minimum 5 points non colinéaires).
     * @return Optional contenant le modèle d'ellipse ajusté, ou Optional.empty() si dégénéré.
     */
    public Optional<EllipseModel> fit(List<PixelPoint> points) {
        if (points == null || points.size() < 5) {
            return Optional.empty();
        }

        double meanX = computeMeanX(points);
        double meanY = computeMeanY(points);
        double scale = computeScale(points, meanX, meanY);
        if (scale < EPSILON) {
            return Optional.empty();
        }

        double[][] s1 = new double[3][3];
        double[][] s2 = new double[3][3];
        double[][] s3 = new double[3][3];
        accumulateScatterMatrices(points, meanX, meanY, scale, s1, s2, s3);

        double[][] s3Inv = invert3x3(s3);
        if (s3Inv == null) {
            return Optional.empty();
        }

        double[][] s2T = transpose3x3(s2);
        double[][] s3InvS2T = multiply3x3(s3Inv, s2T);
        double[][] s2S3InvS2T = multiply3x3(s2, s3InvS2T);

        double[][] q = subtract3x3(s1, s2S3InvS2T);
        double[][] m = computeReducedMatrixM(q);

        double[] eigenvector = findConstrainedEigenvector(m);
        if (eigenvector == null) {
            return Optional.empty();
        }

        double[] a2 = computeLinearCoefficients(s3InvS2T, eigenvector);
        return extractEllipseParameters(eigenvector, a2, meanX, meanY, scale);
    }

    /**
     * Calcule la distance euclidienne minimale exacte entre un point et une ellipse par raffinement de Newton.
     *
     * @param point   Point à tester.
     * @param ellipse Modèle d'ellipse.
     * @return Distance euclidienne minimale en pixels.
     */
    public double distanceToEllipse(PixelPoint point, EllipseModel ellipse) {
        double dx = point.x() - ellipse.xc();
        double dy = point.y() - ellipse.yc();
        double cosTh = Math.cos(ellipse.theta());
        double sinTh = Math.sin(ellipse.theta());
        double xLocal = dx * cosTh + dy * sinTh;
        double yLocal = -dx * sinTh + dy * cosTh;

        double a = ellipse.a();
        double b = ellipse.b();
        double t = Math.atan2(yLocal / b, xLocal / a);

        for (int iter = 0; iter < 5; iter++) {
            double ct = Math.cos(t);
            double st = Math.sin(t);
            double f = -a * st * (xLocal - a * ct) + b * ct * (yLocal - b * st);
            double fp = -a * xLocal * ct + a * a * (ct * ct - st * st) - b * yLocal * st - b * b * (ct * ct - st * st);
            if (Math.abs(fp) < EPSILON) {
                break;
            }
            t -= f / fp;
        }

        double xt = a * Math.cos(t);
        double yt = b * Math.sin(t);
        return Math.hypot(xLocal - xt, yLocal - yt);
    }

    private double computeMeanX(List<PixelPoint> points) {
        double sum = 0.0;
        for (PixelPoint p : points) {
            sum += p.x();
        }
        return sum / points.size();
    }

    private double computeMeanY(List<PixelPoint> points) {
        double sum = 0.0;
        for (PixelPoint p : points) {
            sum += p.y();
        }
        return sum / points.size();
    }

    private double computeScale(List<PixelPoint> points, double mx, double my) {
        double sumSq = 0.0;
        for (PixelPoint p : points) {
            double dx = p.x() - mx;
            double dy = p.y() - my;
            sumSq += dx * dx + dy * dy;
        }
        return Math.sqrt(sumSq / (2.0 * points.size()));
    }

    private void accumulateScatterMatrices(List<PixelPoint> points, double mx, double my, double scale,
                                           double[][] s1, double[][] s2, double[][] s3) {
        for (PixelPoint p : points) {
            double x = (p.x() - mx) / scale;
            double y = (p.y() - my) / scale;
            double x2 = x * x;
            double xy = x * y;
            double y2 = y * y;

            s1[0][0] += x2 * x2;  s1[0][1] += x2 * xy;  s1[0][2] += x2 * y2;
            s1[1][0] += xy * x2;  s1[1][1] += xy * xy;  s1[1][2] += xy * y2;
            s1[2][0] += y2 * x2;  s1[2][1] += y2 * xy;  s1[2][2] += y2 * y2;

            s2[0][0] += x2 * x;   s2[0][1] += x2 * y;   s2[0][2] += x2;
            s2[1][0] += xy * x;   s2[1][1] += xy * y;   s2[1][2] += xy;
            s2[2][0] += y2 * x;   s2[2][1] += y2 * y;   s2[2][2] += y2;

            s3[0][0] += x * x;    s3[0][1] += x * y;    s3[0][2] += x;
            s3[1][0] += y * x;    s3[1][1] += y * y;    s3[1][2] += y;
            s3[2][0] += x;        s3[2][1] += y;        s3[2][2] += 1.0;
        }
    }

    private double[][] computeReducedMatrixM(double[][] q) {
        double[][] m = new double[3][3];
        for (int col = 0; col < 3; col++) {
            m[0][col] = 0.5 * q[2][col];
            m[1][col] = -q[1][col];
            m[2][col] = 0.5 * q[0][col];
        }
        return m;
    }

    private double[] findConstrainedEigenvector(double[][] m) {
        List<Double> roots = solveCharacteristicRoots(m);
        for (double lambda : roots) {
            double[][] a = new double[3][3];
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    a[r][c] = m[r][c] - (r == c ? lambda : 0.0);
                }
            }
            double[] v = findNullspaceVector(a);
            if (v != null) {
                double cond = 4.0 * v[0] * v[2] - v[1] * v[1];
                if (cond > EPSILON) {
                    return v[0] < 0 ? new double[]{-v[0], -v[1], -v[2]} : v;
                }
            }
        }
        return null;
    }

    private List<Double> solveCharacteristicRoots(double[][] m) {
        double tr = m[0][0] + m[1][1] + m[2][2];
        double[][] m2 = multiply3x3(m, m);
        double tr2 = m2[0][0] + m2[1][1] + m2[2][2];
        double det = determinant3x3(m);

        double c2 = -tr;
        double c1 = 0.5 * (tr * tr - tr2);
        double c0 = -det;

        return solveCubicRoots(c2, c1, c0);
    }

    private List<Double> solveCubicRoots(double a, double b, double c) {
        List<Double> roots = new ArrayList<>();
        double p = b - a * a / 3.0;
        double q = 2.0 * a * a * a / 27.0 - a * b / 3.0 + c;
        double disc = (q * q) / 4.0 + (p * p * p) / 27.0;

        if (disc <= EPSILON) {
            double r = Math.sqrt(-p * p * p / 27.0);
            if (r > EPSILON) {
                double phi = Math.acos(Math.clamp(-q / (2.0 * r), -1.0, 1.0));
                roots.add(2.0 * Math.cbrt(r) * Math.cos(phi / 3.0) - a / 3.0);
                roots.add(2.0 * Math.cbrt(r) * Math.cos((phi + 2.0 * Math.PI) / 3.0) - a / 3.0);
                roots.add(2.0 * Math.cbrt(r) * Math.cos((phi + 4.0 * Math.PI) / 3.0) - a / 3.0);
            }
        } else {
            double sqrtDisc = Math.sqrt(disc);
            double u = Math.cbrt(-q / 2.0 + sqrtDisc);
            double v = Math.cbrt(-q / 2.0 - sqrtDisc);
            roots.add(u + v - a / 3.0);
        }
        return roots;
    }

    private double[] findNullspaceVector(double[][] a) {
        double[] c01 = crossProduct(a[0], a[1]);
        double[] c02 = crossProduct(a[0], a[2]);
        double[] c12 = crossProduct(a[1], a[2]);

        double n01 = normSquared(c01);
        double n02 = normSquared(c02);
        double n12 = normSquared(c12);

        double[] best = c01;
        double maxNorm = n01;
        if (n02 > maxNorm) {
            best = c02;
            maxNorm = n02;
        }
        if (n12 > maxNorm) {
            best = c12;
            maxNorm = n12;
        }
        return maxNorm > 1e-14 ? best : null;
    }

    private double[] computeLinearCoefficients(double[][] s3InvS2T, double[] a1) {
        double[] a2 = new double[3];
        for (int r = 0; r < 3; r++) {
            double sum = 0.0;
            for (int c = 0; c < 3; c++) {
                sum += s3InvS2T[r][c] * a1[c];
            }
            a2[r] = -sum;
        }
        return a2;
    }

    private Optional<EllipseModel> extractEllipseParameters(double[] a1, double[] a2,
                                                           double mx, double my, double scale) {
        double aCoeff = a1[0];
        double bCoeff = a1[1] / 2.0;
        double cCoeff = a1[2];
        double dCoeff = a2[0] / 2.0;
        double fCoeff = a2[1] / 2.0;
        double gCoeff = a2[2];

        double den = bCoeff * bCoeff - aCoeff * cCoeff;
        if (Math.abs(den) < EPSILON) {
            return Optional.empty();
        }

        double x0 = (cCoeff * dCoeff - bCoeff * fCoeff) / den;
        double y0 = (aCoeff * fCoeff - bCoeff * dCoeff) / den;

        double num = 2.0 * (aCoeff * fCoeff * fCoeff + cCoeff * dCoeff * dCoeff + gCoeff * bCoeff * bCoeff
                - 2.0 * bCoeff * dCoeff * fCoeff - aCoeff * cCoeff * gCoeff);
        double term = Math.sqrt((aCoeff - cCoeff) * (aCoeff - cCoeff) + 4.0 * bCoeff * bCoeff);
        double den1 = den * (term - (aCoeff + cCoeff));
        double den2 = den * (-term - (aCoeff + cCoeff));

        if (num / den1 <= 0.0 || num / den2 <= 0.0) {
            return Optional.empty();
        }

        double w = Math.sqrt(num / den1);
        double h = Math.sqrt(num / den2);
        double phi = 0.5 * Math.atan2(2.0 * bCoeff, aCoeff - cCoeff);
        if (aCoeff > cCoeff) {
            phi += 0.5 * Math.PI;
        }
        if (w < h) {
            double temp = w;
            w = h;
            h = temp;
            phi += Math.PI / 2.0;
        }
        phi = phi % Math.PI;
        if (phi < 0.0) {
            phi += Math.PI;
        }

        return Optional.of(new EllipseModel(
                mx + x0 * scale,
                my + y0 * scale,
                w * scale,
                h * scale,
                phi
        ));
    }

    private double[][] invert3x3(double[][] m) {
        double det = determinant3x3(m);
        if (Math.abs(det) < EPSILON) {
            return null;
        }
        double invDet = 1.0 / det;
        double[][] inv = new double[3][3];

        inv[0][0] = (m[1][1] * m[2][2] - m[1][2] * m[2][1]) * invDet;
        inv[0][1] = (m[0][2] * m[2][1] - m[0][1] * m[2][2]) * invDet;
        inv[0][2] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) * invDet;

        inv[1][0] = (m[1][2] * m[2][0] - m[1][0] * m[2][2]) * invDet;
        inv[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) * invDet;
        inv[1][2] = (m[0][2] * m[1][0] - m[0][0] * m[1][2]) * invDet;

        inv[2][0] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) * invDet;
        inv[2][1] = (m[0][1] * m[2][0] - m[0][0] * m[2][1]) * invDet;
        inv[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) * invDet;

        return inv;
    }

    private double determinant3x3(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }

    private double[][] multiply3x3(double[][] a, double[][] b) {
        double[][] res = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                res[r][c] = a[r][0] * b[0][c] + a[r][1] * b[1][c] + a[r][2] * b[2][c];
            }
        }
        return res;
    }

    private double[][] subtract3x3(double[][] a, double[][] b) {
        double[][] res = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                res[r][c] = a[r][c] - b[r][c];
            }
        }
        return res;
    }

    private double[][] transpose3x3(double[][] m) {
        double[][] res = new double[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                res[r][c] = m[c][r];
            }
        }
        return res;
    }

    private double[] crossProduct(double[] u, double[] v) {
        return new double[]{
                u[1] * v[2] - u[2] * v[1],
                u[2] * v[0] - u[0] * v[2],
                u[0] * v[1] - u[1] * v[0]
        };
    }

    private double normSquared(double[] v) {
        return v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
    }
}
