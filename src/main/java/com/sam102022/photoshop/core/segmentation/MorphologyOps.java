package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Opérations de morphologie mathématique 2D sur BinaryMask (dilatation, érosion, ouverture, fermeture, composantes connexes).
 */
public final class MorphologyOps {

    private MorphologyOps() {
    }

    public static BinaryMask dilate(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(x, y)) {
                    int minX = Math.max(0, x - radius);
                    int maxX = Math.min(w - 1, x + radius);
                    int minY = Math.max(0, y - radius);
                    int maxY = Math.min(h - 1, y + radius);

                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            result.set(nx, ny, true);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static BinaryMask erode(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) {
                    continue;
                }
                boolean allNeighborsActive = true;
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(w - 1, x + radius);
                int minY = Math.max(0, y - radius);
                int maxY = Math.min(h - 1, y + radius);

                if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) {
                    allNeighborsActive = false;
                } else {
                    checkLoop:
                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            if (!mask.get(nx, ny)) {
                                allNeighborsActive = false;
                                break checkLoop;
                            }
                        }
                    }
                }

                if (allNeighborsActive) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    public static BinaryMask close(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        return erode(dilate(mask, radius), radius);
    }

    public static BinaryMask open(BinaryMask mask, int radius) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        if (radius <= 0) {
            return mask.copy();
        }
        return dilate(erode(mask, radius), radius);
    }

    public static BinaryMask keepLargestComponent(BinaryMask mask) {
        if (mask == null) {
            throw new IllegalArgumentException("Le masque ne peut pas être null.");
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        boolean[] visited = new boolean[w * h];
        int maxComponentSize = 0;
        int maxStartIdx = -1;

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                int idx = rowOffset + x;
                if (mask.get(x, y) && !visited[idx]) {
                    int size = 0;
                    Queue<Integer> q = new ArrayDeque<>();
                    q.offer(idx);
                    visited[idx] = true;

                    while (!q.isEmpty()) {
                        int cur = q.poll();
                        size++;
                        int cx = cur % w;
                        int cy = cur / w;

                        for (int i = 0; i < 4; i++) {
                            int nx = cx + dx[i];
                            int ny = cy + dy[i];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                                int nIdx = ny * w + nx;
                                if (mask.get(nx, ny) && !visited[nIdx]) {
                                    visited[nIdx] = true;
                                    q.offer(nIdx);
                                }
                            }
                        }
                    }

                    if (size > maxComponentSize) {
                        maxComponentSize = size;
                        maxStartIdx = idx;
                    }
                }
            }
        }

        if (maxStartIdx == -1) {
            return new BinaryMask(w, h);
        }

        BinaryMask largest = new BinaryMask(w, h);
        boolean[] inLargest = new boolean[w * h];
        Queue<Integer> q = new ArrayDeque<>();
        q.offer(maxStartIdx);
        inLargest[maxStartIdx] = true;
        largest.set(maxStartIdx % w, maxStartIdx / w, true);

        while (!q.isEmpty()) {
            int cur = q.poll();
            int cx = cur % w;
            int cy = cur / w;

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int nIdx = ny * w + nx;
                    if (mask.get(nx, ny) && !inLargest[nIdx]) {
                        inLargest[nIdx] = true;
                        largest.set(nx, ny, true);
                        q.offer(nIdx);
                    }
                }
            }
        }

        return largest;
    }
}
