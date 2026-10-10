package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests des modèles de domaine du Sprint 6")
class ContourDomainModelTest {

    @Test
    @DisplayName("ContourSmoothingConfig expose les hyperparamètres par défaut conformes à la spécification")
    void testContourSmoothingConfigDefaults() {
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        assertEquals(1.0, config.resampleStep());
        assertEquals(80, config.cornerWindowL());
        assertEquals(38.0, config.cornerThreshold());
        assertEquals(4.0, config.preSmoothSigma());
        assertEquals(22.0, config.lqrSigma());
        assertEquals(3.0, config.lqrScale());
        assertEquals(6, config.lqrIterations());
        assertEquals(35.0, config.blendR0());
        assertEquals(95.0, config.blendR1());
        assertEquals(2.0, config.roundaboutZr());
    }

    /**
     * Vérifie que les hyperparamètres de rectitude du Sprint 7 sont correctement initialisés avec les valeurs étalons.
     */
    @Test
    @DisplayName("ContourSmoothingConfig expose les hyperparamètres de rectitude conformes au Sprint 7")
    void testContourSmoothingConfigStraightParameters() {
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        assertEquals(2.7, config.straightFactor());
        assertEquals(3.0, config.straightScale());
        assertEquals(3.0, config.straightThreshold());
        assertEquals(10, config.straightTransitionK());
    }

    /**
     * Vérifie la validation stricte des bornes admissibles des hyperparamètres de rectitude.
     */
    @Test
    @DisplayName("ContourSmoothingConfig rejette les hyperparamètres de rectitude hors bornes")
    void testContourSmoothingConfigStraightValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0,
                0.5, 3.0, 3.0, 10
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0,
                2.7, 0.0, 3.0, 10
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0,
                2.7, 3.0, -1.0, 10
        ));
        assertThrows(IllegalArgumentException.class, () -> new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0,
                2.7, 3.0, 3.0, 0
        ));
    }

    @Test
    @DisplayName("EllipseModel convertit rigoureusement entre repère cartésien et cercle unité")
    void testEllipseModelConversions() {
        // Ellipse centrée en (100, 200), a=40, b=20, theta=0
        EllipseModel ellipse = new EllipseModel(100.0, 200.0, 40.0, 20.0, 0.0);
        PixelPoint ptLocal = new PixelPoint(140.0, 200.0);
        PixelPoint ptUnit = ellipse.toUnitCircle(ptLocal);
        assertEquals(1.0, ptUnit.x(), 1e-6);
        assertEquals(0.0, ptUnit.y(), 1e-6);

        PixelPoint reconstructed = ellipse.fromUnitCircle(ptUnit.x(), ptUnit.y());
        assertEquals(ptLocal.x(), reconstructed.x(), 1e-6);
        assertEquals(ptLocal.y(), reconstructed.y(), 1e-6);

        // Invariant : a >= b
        assertThrows(IllegalArgumentException.class, () -> new EllipseModel(0, 0, 10.0, 20.0, 0.0));
    }

    @Test
    @DisplayName("Roundabout encapsule les métriques d'îlot et d'inliers")
    void testRoundaboutRecord() {
        EllipseModel ell = new EllipseModel(50.0, 50.0, 15.0, 12.0, 0.5);
        Roundabout rb = new Roundabout(new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertEquals(250.0, rb.islandArea());
        assertEquals(0.85, rb.inlierRatio());
        assertNotNull(rb.exteriorEllipse());
    }

    /**
     * Vérifie la présence de cellId dans Roundabout et la rétrocompatibilité du constructeur à 4 arguments.
     */
    @Test
    @DisplayName("Roundabout stocke cellId et garantit la rétrocompatibilité")
    void testRoundaboutWithCellIdAndBackwardCompatibility() {
        EllipseModel ell = new EllipseModel(50.0, 50.0, 20.0, 15.0, 0.0);
        Roundabout rb1 = new Roundabout(42, new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertEquals(42, rb1.cellId());

        // Constructeur rétrocompatible sans cellId (cellId vaut -1 par défaut)
        Roundabout rb2 = new Roundabout(new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertEquals(-1, rb2.cellId());
    }

    @Test
    @DisplayName("SmoothVectorContour garantit l'immuabilité défensive de la liste des points et coins")
    void testSmoothVectorContourImmutability() {
        CropWindow crop = new CropWindow(10, 20, 100, 80);
        List<PixelPoint> pts = List.of(new PixelPoint(10, 10), new PixelPoint(20, 10));
        List<Integer> corners = List.of(0);
        SmoothVectorContour contour = new SmoothVectorContour(pts, corners, 100, 80, crop);

        assertEquals(2, contour.points().size());
        assertEquals(1, contour.cornerIndices().size());
        assertThrows(UnsupportedOperationException.class, () -> contour.points().add(new PixelPoint(30, 10)));
    }

    /**
     * Vérifie que SmoothVectorContour transporte la liste des ronds-points substitués avec constructeur rétrocompatible.
     */
    @Test
    @DisplayName("SmoothVectorContour transporte la liste des ronds-points substitués")
    void testSmoothVectorContourCarriesSubstitutedRoundabouts() {
        CropWindow crop = new CropWindow(0, 0, 100, 100);
        EllipseModel ell = new EllipseModel(50.0, 50.0, 20.0, 15.0, 0.0);
        Roundabout rb = new Roundabout(7, new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);

        SmoothVectorContour contourWithRb = new SmoothVectorContour(
                List.of(new PixelPoint(0, 0), new PixelPoint(10, 0)),
                List.of(0),
                100, 100, crop, List.of(rb)
        );
        assertEquals(List.of(rb), contourWithRb.substitutedRoundabouts());

        // Constructeur rétrocompatible
        SmoothVectorContour contourDefault = new SmoothVectorContour(
                List.of(new PixelPoint(0, 0)), List.of(0), 100, 100, crop
        );
        assertTrue(contourDefault.substitutedRoundabouts().isEmpty());
    }
}
