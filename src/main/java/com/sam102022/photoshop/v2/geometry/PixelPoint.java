package com.sam102022.photoshop.v2.geometry;

/**
 * Point bidimensionnel dans le repère pixel local de l'image cartographique.
 *
 * @param x Abscisse pixel (0.0 au bord gauche de l'image).
 * @param y Ordonnée pixel (0.0 au bord supérieur de l'image).
 */
public record PixelPoint(double x, double y) {}
