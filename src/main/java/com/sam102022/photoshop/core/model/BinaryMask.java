package com.sam102022.photoshop.core.model;

import java.util.Arrays;

/**
 * Matrice binaire 2D optimisée représentant un calque de pixels actifs (true) ou inactifs (false).
 */
public class BinaryMask {
    private final int width;
    private final int height;
    private final boolean[] data;

    public BinaryMask(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new boolean[width * height];
    }

    public BinaryMask(int width, int height, boolean[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (data == null || data.length != width * height) {
            throw new IllegalArgumentException("Les données du masque ne correspondent pas aux dimensions spécifiées.");
        }
        this.width = width;
        this.height = height;
        this.data = Arrays.copyOf(data, data.length);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean get(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return data[y * width + x];
    }

    public void set(int x, int y, boolean value) {
        if (isInBounds(x, y)) {
            data[y * width + x] = value;
        }
    }

    public int countActivePixels() {
        int count = 0;
        for (boolean b : data) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public BinaryMask copy() {
        return new BinaryMask(width, height, this.data);
    }

    public BinaryMask or(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] || other.data[i];
        }
        return result;
    }

    public BinaryMask and(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] && other.data[i];
        }
        return result;
    }

    public BinaryMask not() {
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = !this.data[i];
        }
        return result;
    }

    private void validateSameDimensions(BinaryMask other) {
        if (other == null || other.width != this.width || other.height != this.height) {
            throw new IllegalArgumentException("Dimensions incompatibles pour l'opération matricielle.");
        }
    }
}
