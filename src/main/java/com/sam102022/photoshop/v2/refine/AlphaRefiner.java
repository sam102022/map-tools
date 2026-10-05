package com.sam102022.photoshop.v2.refine;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.contour.Roundabout;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Façade principale d'orchestration de l'affinage spectral et de la modulation alpha (Sprint 8).
 * Coordonne la fusion binaire de la zone autorisée, le filtrage gaussien à seuil raide
 * et la sanctuarisation continue des carrefours giratoires substitués.
 */
public class AlphaRefiner {

    private static final Logger LOGGER = Logger.getLogger(AlphaRefiner.class.getName());

    private final AllowedRegionBuilder regionBuilder;
    private final SoftThresholdFilter thresholdFilter;
    private final RoundaboutExemptionModulator exemptionModulator;

    /**
     * Initialise la façade avec les implémentations par défaut de chaque sous-composant.
     */
    public AlphaRefiner() {
        this(new AllowedRegionBuilder(), new SoftThresholdFilter(), new RoundaboutExemptionModulator());
    }

    /**
     * Initialise la façade avec des sous-composants injectés.
     *
     * @param regionBuilder      Constructeur de la région autorisée (non nul).
     * @param thresholdFilter    Filtre gaussien à seuillage doux (non nul).
     * @param exemptionModulator Modulateur d'exemption des giratoires (non nul).
     */
    public AlphaRefiner(
            AllowedRegionBuilder regionBuilder,
            SoftThresholdFilter thresholdFilter,
            RoundaboutExemptionModulator exemptionModulator
    ) {
        this.regionBuilder = Objects.requireNonNull(regionBuilder, "regionBuilder ne doit pas être nul.");
        this.thresholdFilter = Objects.requireNonNull(thresholdFilter, "thresholdFilter ne doit pas être nul.");
        this.exemptionModulator = Objects.requireNonNull(exemptionModulator, "exemptionModulator ne doit pas être nul.");
    }

    /**
     * Exécute l'affinage spectral avec la configuration par défaut.
     *
     * @param consolidatedMask       Masque consolidé du territoire issu du Sprint 5 (non nul).
     * @param croppedRoad            Masque binaire de la chaussée nettoyée recadrée (non nul).
     * @param labelMap               Matrice d'étiquetage des cellules candidates (non nulle).
     * @param substitutedRoundabouts Liste des giratoires substitués issus du Sprint 6/7 (non nulle).
     * @return Contrat immuable AlphaRefinementMap encapsulant la matrice de facteurs [0..1].
     */
    public AlphaRefinementMap refine(
            ConsolidatedMask consolidatedMask,
            BinaryMask croppedRoad,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts
    ) {
        return refine(consolidatedMask, croppedRoad, labelMap, substitutedRoundabouts, AlphaRefinementConfig.defaultConfig());
    }

    /**
     * Exécute l'enchaînement complet de l'affinage spectral selon la configuration fournie.
     *
     * @param consolidatedMask       Masque consolidé du territoire issu du Sprint 5 (non nul).
     * @param croppedRoad            Masque binaire de la chaussée nettoyée recadrée (non nul).
     * @param labelMap               Matrice d'étiquetage des cellules candidates (non nulle).
     * @param substitutedRoundabouts Liste des giratoires substitués issus du Sprint 6/7 (non nulle).
     * @param config                 Paramètres de configuration de l'affinage spectral (non nul).
     * @return Contrat immuable AlphaRefinementMap encapsulant la matrice de facteurs [0..1].
     */
    public AlphaRefinementMap refine(
            ConsolidatedMask consolidatedMask,
            BinaryMask croppedRoad,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts,
            AlphaRefinementConfig config
    ) {
        validateInputs(consolidatedMask, croppedRoad, labelMap, substitutedRoundabouts, config);

        long startTime = System.currentTimeMillis();
        LOGGER.info(() -> String.format(
                "[Sprint 8] Démarrage de l'affinage spectral (%dx%d px, %d giratoires substitués)...",
                consolidatedMask.width(),
                consolidatedMask.height(),
                substitutedRoundabouts.size()
        ));

        // 1. Construction de la zone autorisée
        BinaryMask allowed = regionBuilder.build(
                consolidatedMask.mask(),
                croppedRoad,
                labelMap,
                substitutedRoundabouts,
                config.roadHoleMaxArea()
        );

        // 2. Flou gaussien séparable et seuillage raide
        float[][] rawFactor = thresholdFilter.filter(
                allowed,
                config.gaussianBlurSigma(),
                config.contrastStiffness()
        );

        // 3. Modulation de protection continue des giratoires
        float[][] finalFactor = exemptionModulator.modulate(
                rawFactor,
                substitutedRoundabouts,
                config.roundaboutBufferInner(),
                config.roundaboutBufferOuter()
        );

        long elapsedMs = System.currentTimeMillis() - startTime;
        LOGGER.info(() -> String.format(
                "[Sprint 8] Affinage spectral achevé avec succès en %d ms.",
                elapsedMs
        ));

        return new AlphaRefinementMap(
                consolidatedMask.width(),
                consolidatedMask.height(),
                finalFactor,
                consolidatedMask.cropWindow()
        );
    }

    /**
     * Valide l'intégrité de l'ensemble des arguments de la méthode refine.
     *
     * @param consolidatedMask       Masque du territoire consolidé.
     * @param croppedRoad            Masque de la chaussée recadrée.
     * @param labelMap               Matrice des étiquettes des cellules.
     * @param substitutedRoundabouts Liste des ronds-points substitués.
     * @param config                 Configuration d'affinage spectral.
     */
    private void validateInputs(
            ConsolidatedMask consolidatedMask,
            BinaryMask croppedRoad,
            CellLabelMap labelMap,
            List<Roundabout> substitutedRoundabouts,
            AlphaRefinementConfig config
    ) {
        Objects.requireNonNull(consolidatedMask, "consolidatedMask ne doit pas être nul.");
        Objects.requireNonNull(croppedRoad, "croppedRoad ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "labelMap ne doit pas être nulle.");
        Objects.requireNonNull(substitutedRoundabouts, "substitutedRoundabouts ne doit pas être nulle.");
        Objects.requireNonNull(config, "config ne doit pas être nulle.");
    }
}
