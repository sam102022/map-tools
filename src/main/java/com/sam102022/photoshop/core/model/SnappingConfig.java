package com.sam102022.photoshop.core.model;

/**
 * Configuration paramétrable pour le calage routier et le détourage.
 */
public record SnappingConfig(
        int snapDistance,
        float roadSensitivity,
        int smoothRadius,
        int seedErosionRadius,
        int closingRadius
) {
    public SnappingConfig {
        if (snapDistance <= 0) {
            throw new IllegalArgumentException("snapDistance doit être > 0 : " + snapDistance);
        }
        if (roadSensitivity <= 0.0f) {
            throw new IllegalArgumentException("roadSensitivity doit être > 0 : " + roadSensitivity);
        }
        if (smoothRadius < 0) {
            throw new IllegalArgumentException("smoothRadius doit être >= 0 : " + smoothRadius);
        }
        if (seedErosionRadius < 0) {
            throw new IllegalArgumentException("seedErosionRadius doit être >= 0 : " + seedErosionRadius);
        }
        if (closingRadius < 0) {
            throw new IllegalArgumentException("closingRadius doit être >= 0 : " + closingRadius);
        }
    }

    public static SnappingConfig defaults() {
        return new SnappingConfig(40, 1.0f, 1, 8, 2);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int snapDistance = 40;
        private float roadSensitivity = 1.0f;
        private int smoothRadius = 1;
        private int seedErosionRadius = 8;
        private int closingRadius = 2;

        public Builder snapDistance(int snapDistance) {
            this.snapDistance = snapDistance;
            return this;
        }

        public Builder roadSensitivity(float roadSensitivity) {
            this.roadSensitivity = roadSensitivity;
            return this;
        }

        public Builder smoothRadius(int smoothRadius) {
            this.smoothRadius = smoothRadius;
            return this;
        }

        public Builder seedErosionRadius(int seedErosionRadius) {
            this.seedErosionRadius = seedErosionRadius;
            return this;
        }

        public Builder closingRadius(int closingRadius) {
            this.closingRadius = closingRadius;
            return this;
        }

        public SnappingConfig build() {
            return new SnappingConfig(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius, closingRadius);
        }
    }
}
