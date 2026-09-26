package com.sam102022.photoshop.core.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContourSimplifierTest {

    @Test
    @DisplayName("Simplification d'une ligne droite avec points redondants")
    void testSimplifyCollinearPoints() {
        List<Point> points = new ArrayList<>();
        // Ligne droite avec 10 points
        for (int x = 0; x <= 10; x++) {
            points.add(new Point(x, 0));
        }
        points.add(new Point(10, 10));
        points.add(new Point(0, 10));
        points.add(new Point(0, 0)); // Fermé

        ContourSimplifier simplifier = new ContourSimplifier();
        List<Point> simplified = simplifier.simplify(points, 0.5);

        // Doit éliminer les points intermédiaires sur les segments droits
        assertTrue(simplified.size() < points.size(), "Le contour doit être réduit");
        assertEquals(new Point(0, 0), simplified.getFirst());
        assertEquals(new Point(0, 0), simplified.getLast());
    }

    @Test
    @DisplayName("Tolérance 0 ou liste trop courte conserve les points")
    void testEdgeCases() {
        ContourSimplifier simplifier = new ContourSimplifier();
        List<Point> twoPoints = List.of(new Point(0, 0), new Point(1, 1));
        assertEquals(twoPoints, simplifier.simplify(twoPoints, 1.0));

        List<Point> triangle = List.of(new Point(0, 0), new Point(5, 0), new Point(0, 5), new Point(0, 0));
        assertEquals(triangle, simplifier.simplify(triangle, 0.0));
        assertThrows(IllegalArgumentException.class, () -> simplifier.simplify(null, 1.0));
    }
}
