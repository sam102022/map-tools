package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Moteur de recalage : effectue une érosion de graine sur le masque vert puis une propagation
 * géodésique BFS bloquée par les barrières routières et bornée par snapDistance.
 */
public class RoadSnappingEngine {

    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadBarrier, SnappingConfig config) {
        if (roughGreenMask == null || roadBarrier == null || config == null) {
            throw new IllegalArgumentException("Les arguments du snapping engine ne peuvent pas être null.");
        }
        if (roughGreenMask.getWidth() != roadBarrier.getWidth() || roughGreenMask.getHeight() != roadBarrier.getHeight()) {
            throw new IllegalArgumentException("Dimensions incompatibles entre roughGreenMask et roadBarrier.");
        }

        int w = roughGreenMask.getWidth();
        int h = roughGreenMask.getHeight();

        // 1. Extraction du noyau certain (seed) par érosion adaptative
        BinaryMask seedCandidate = extractReliableSeed(roughGreenMask, config.seedErosionRadius());
        if (seedCandidate.countActivePixels() == 0) {
            seedCandidate = roughGreenMask.copy();
        }

        // Exclure les barrières routières de la graine
        BinaryMask cleanSeed = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (seedCandidate.get(x, y) && !roadBarrier.get(x, y)) {
                    cleanSeed.set(x, y, true);
                }
            }
        }

        // Ne conserver que la composante connexe principale (la plus grande) pour éliminer les faux germes
        BinaryMask seed = keepLargestComponent(cleanSeed);

        // 2. Détermination de la zone maximale autorisée (bounding zone)
        BinaryMask allowedZone = MorphologyOps.dilate(roughGreenMask, config.snapDistance());

        // 3. Propagation BFS contrainte
        BinaryMask result = new BinaryMask(w, h);
        boolean[] visited = new boolean[w * h];
        Queue<Integer> queue = new ArrayDeque<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (seed.get(x, y)) {
                    visited[idx] = true;
                    result.set(x, y, true);
                    queue.offer(idx);
                }
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int cx = current % w;
            int cy = current / w;

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    continue;
                }

                int nIdx = ny * w + nx;
                if (visited[nIdx]) {
                    continue;
                }
                visited[nIdx] = true;

                // Si c'est une barrière routière, on stoppe la propagation dans cette direction
                if (roadBarrier.get(nx, ny)) {
                    continue;
                }

                // Si le pixel est hors de la distance maximale permise, on stoppe
                if (!allowedZone.get(nx, ny)) {
                    continue;
                }

                result.set(nx, ny, true);
                queue.offer(nIdx);
            }
        }

        return result;
    }

    private BinaryMask extractReliableSeed(BinaryMask mask, int radius) {
        int r = radius;
        while (r > 0) {
            BinaryMask eroded = MorphologyOps.erode(mask, r);
            if (eroded.countActivePixels() > 0) {
                return eroded;
            }
            r /= 2;
        }
        return mask.copy();
    }

    private BinaryMask keepLargestComponent(BinaryMask mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        boolean[] visited = new boolean[w * h];
        int maxComponentSize = 0;
        int maxStartIdx = -1;

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
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
            return mask.copy();
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
