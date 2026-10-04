package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.OperationMode;
import java.util.Objects;

/**
 * Configuration immuable pour l'expansion géodésique et la consolidation matricielle.
 *
 * @param epsilon           Marge de sécurité géométrique en pixels (défaut : 4.0).
 * @param maxFilterSize     Taille de la fenêtre carrée du filtre maximum local (défaut : 41).
 * @param openingDiskRadius Rayon de l'élément structurant circulaire d'ouverture (défaut : 5).
 * @param maxHoleArea       Surface maximale des îlots compacts comblés (défaut : 15 000 px).
 * @param dilationSeedSteps Nombre d'itérations de dilatation pour les graines d'amorce (défaut : 2).
 * @param operationMode     Mode d'opération (TERRITORY ou ZONE).
 */
public record ExpansionConfig(
        double epsilon,
        int maxFilterSize,
        int openingDiskRadius,
        long maxHoleArea,
        int dilationSeedSteps,
        OperationMode operationMode
) {
    /** Marge par défaut en pixels. */
    public static final double DEFAULT_EPSILON = 4.0;
    /** Taille par défaut de la fenêtre du filtre maximum local (41x41). */
    public static final int DEFAULT_MAX_FILTER_SIZE = 41;
    /** Rayon par défaut du disque d'ouverture morphologique (5 px). */
    public static final int DEFAULT_OPENING_RADIUS = 5;
    /** Surface maximale par défaut d'un trou compact à combler (15 000 px). */
    public static final long DEFAULT_MAX_HOLE_AREA = 15_000L;
    /** Nombre d'itérations de dilatation par défaut pour les germes d'amorce (2 étapes). */
    public static final int DEFAULT_SEED_DILATION_STEPS = 2;

    /**
     * Constructeur canonique avec validations défensives des hyperparamètres.
     *
     * @param epsilon           Marge géométrique en pixels (>= 0).
     * @param maxFilterSize     Taille impaire de la fenêtre du filtre max (>= 1).
     * @param openingDiskRadius Rayon du disque (>= 0).
     * @param maxHoleArea       Aire maximale de trou (>= 0).
     * @param dilationSeedSteps Étapes de dilatation des graines (>= 0).
     * @param operationMode     Mode d'opération non nul.
     */
    public ExpansionConfig {
        if (epsilon < 0) {
            throw new IllegalArgumentException("epsilon doit être positif ou nul");
        }
        if (maxFilterSize <= 0 || maxFilterSize % 2 == 0) {
            throw new IllegalArgumentException("maxFilterSize doit être un entier impair strictement positif");
        }
        if (openingDiskRadius < 0) {
            throw new IllegalArgumentException("openingDiskRadius doit être positif ou nul");
        }
        if (maxHoleArea < 0) {
            throw new IllegalArgumentException("maxHoleArea doit être positif ou nul");
        }
        if (dilationSeedSteps < 0) {
            throw new IllegalArgumentException("dilationSeedSteps doit être positif ou nul");
        }
        Objects.requireNonNull(operationMode, "operationMode ne doit pas être nul");
    }

    /**
     * Crée une configuration par défaut pour le mode TERRITORY.
     *
     * @return Configuration par défaut pour le mode TERRITORY.
     */
    public static ExpansionConfig defaultTerritory() {
        return new ExpansionConfig(
                DEFAULT_EPSILON,
                DEFAULT_MAX_FILTER_SIZE,
                DEFAULT_OPENING_RADIUS,
                DEFAULT_MAX_HOLE_AREA,
                DEFAULT_SEED_DILATION_STEPS,
                OperationMode.TERRITORY
        );
    }

    /**
     * Crée une configuration par défaut pour le mode ZONE.
     *
     * @return Configuration par défaut pour le mode ZONE.
     */
    public static ExpansionConfig defaultZone() {
        return new ExpansionConfig(
                DEFAULT_EPSILON,
                DEFAULT_MAX_FILTER_SIZE,
                DEFAULT_OPENING_RADIUS,
                DEFAULT_MAX_HOLE_AREA,
                DEFAULT_SEED_DILATION_STEPS,
                OperationMode.ZONE
        );
    }
}
