package com.sam102022.photoshop.core.model;

/** Couverture géométrique par pixel, de 0 (transparent) à 255 (opaque). */
public final class CoverageMask {
    private final int width;
    private final int height;
    private final byte[] data;

    public CoverageMask(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        long size = (long) width * height;
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Le masque est trop grand : " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new byte[(int) size];
    }

    private CoverageMask(int width, int height, byte[] data) {
        this.width = width;
        this.height = height;
        this.data = data;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public int get(int x, int y) {
        checkBounds(x, y);
        return data[y * width + x] & 0xFF;
    }

    public void set(int x, int y, int value) {
        checkBounds(x, y);
        data[y * width + x] = (byte) Math.max(0, Math.min(255, value));
    }

    public BinaryMask toBinaryMask(int threshold) {
        if (threshold < 0 || threshold > 255) {
            throw new IllegalArgumentException("Le seuil doit être compris entre 0 et 255 : " + threshold);
        }
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            if ((data[i] & 0xFF) >= threshold) result.set(i % width, i / width, true);
        }
        return result;
    }

    public static CoverageMask fromBinaryMask(BinaryMask mask) {
        if (mask == null) throw new IllegalArgumentException("Le masque binaire ne peut pas être null.");
        CoverageMask result = new CoverageMask(mask.getWidth(), mask.getHeight());
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (mask.get(x, y)) result.set(x, y, 255);
            }
        }
        return result;
    }

    /** Combine les couvertures en conservant pixel par pixel la valeur maximale. */
    public CoverageMask max(CoverageMask other) {
        if (other == null || width != other.width || height != other.height) {
            throw new IllegalArgumentException("Dimensions incompatibles pour la combinaison des masques de couverture.");
        }
        byte[] combined = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            combined[i] = (byte) Math.max(data[i] & 0xFF, other.data[i] & 0xFF);
        }
        return new CoverageMask(width, height, combined);
    }

    private void checkBounds(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("Coordonnées hors limites : (" + x + ", " + y + ") pour " + width + "x" + height);
        }
    }
}
