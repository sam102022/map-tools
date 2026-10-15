package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Façade orchestratrice du vote topologique et de la sélection de cellules urbaines.
 * <p>
 * Coordonne les étapes successives de calcul d'intersection spatiale, de qualification
 * selon la politique de seuillage, de partitionnement et de résolution du masque d'amorçage.
 * </p>
 */
public class TopologicalVoteEngine {

    private final CellCoverageCalculator coverageCalculator;
    private final CellClassifier classifier;
    private final PartialCellResolver partialResolver;

    /**
     * Enregistrement interne représentant la partition des identifiants de cellules.
     *
     * @param insideIds  Ensemble des identifiants des cellules INSIDE.
     * @param outsideIds Ensemble des identifiants des cellules OUTSIDE.
     * @param partialIds Ensemble des identifiants des cellules PARTIAL.
     */
    private record CellPartition(
            Set<Integer> insideIds,
            Set<Integer> outsideIds,
            Set<Integer> partialIds
    ) {}

    /**
     * Construit une nouvelle instance du moteur de vote avec les composants par défaut.
     */
    public TopologicalVoteEngine() {
        this(new CellCoverageCalculator(), new CellClassifier(), new PartialCellResolver());
    }

    /**
     * Construit une nouvelle instance du moteur de vote avec injection des dépendances.
     *
     * @param coverageCalculator Calculateur d'intersection et de couverture spatiale.
     * @param classifier         Classificateur qualifiant les cellules selon les seuils.
     * @param partialResolver    Résolveur de parcelles partielles et assembleur du masque T.
     * @throws IllegalArgumentException si l'une des dépendances fournies est null.
     */
    public TopologicalVoteEngine(
            CellCoverageCalculator coverageCalculator,
            CellClassifier classifier,
            PartialCellResolver partialResolver
    ) {
        if (coverageCalculator == null || classifier == null || partialResolver == null) {
            throw new IllegalArgumentException("Les dépendances injectées ne peuvent pas être null.");
        }
        this.coverageCalculator = coverageCalculator;
        this.classifier = classifier;
        this.partialResolver = partialResolver;
    }

    /**
     * Exécute l'orchestration complète du vote topologique avec la politique par défaut (60% / 5%).
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param cells              Liste des cellules urbaines segmentées.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @return Résultat immuable de la sélection des cellules et masque d'amorçage.
     * @throws IllegalArgumentException si l'un des paramètres est null ou si les masques ont des dimensions incohérentes.
     */
    public CellSelection execute(CellLabelMap labelMap, List<Cell> cells, BinaryMask croppedPolygonMask) {
        return execute(labelMap, cells, croppedPolygonMask, CellSelectionPolicy.defaultPolicy());
    }

    /**
     * Exécute l'orchestration complète du vote topologique selon une politique de sélection paramétrée.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param cells              Liste des cellules urbaines segmentées.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @param policy             Politique de sélection définissant les seuils d'inclusion et d'exclusion.
     * @return Résultat immuable de la sélection des cellules et masque d'amorçage.
     * @throws IllegalArgumentException si l'un des paramètres est null ou si les masques ont des dimensions incohérentes.
     */
    public CellSelection execute(
            CellLabelMap labelMap,
            List<Cell> cells,
            BinaryMask croppedPolygonMask,
            CellSelectionPolicy policy
    ) {
        validateInputs(labelMap, cells, croppedPolygonMask, policy);

        Map<Integer, Long> intersections = coverageCalculator.computeIntersections(labelMap, croppedPolygonMask);
        Map<Integer, CellDecision> decisions = classifier.classify(cells, intersections, policy);
        CellPartition partition = partitionDecisions(decisions);

        PartialCellResolver.ResolutionResult resolution = partialResolver.resolve(
                labelMap,
                croppedPolygonMask,
                partition.insideIds(),
                partition.partialIds(),
                policy.minPartialThickness()
        );

        return new CellSelection(
                partition.insideIds(),
                partition.outsideIds(),
                partition.partialIds(),
                decisions,
                resolution.retainedMask(),
                resolution.partialMasks()
        );
    }

    /**
     * Valide défensivement les paramètres d'entrée de l'orchestration du vote topologique.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param cells              Liste des cellules urbaines.
     * @param croppedPolygonMask Masque binaire du polygone d'intention.
     * @param policy             Politique de sélection de cellules.
     * @throws IllegalArgumentException si l'un des paramètres d'entrée est null.
     */
    private void validateInputs(
            CellLabelMap labelMap,
            List<Cell> cells,
            BinaryMask croppedPolygonMask,
            CellSelectionPolicy policy
    ) {
        if (labelMap == null || cells == null || croppedPolygonMask == null || policy == null) {
            throw new IllegalArgumentException("Aucun argument d'exécution du vote ne peut être null.");
        }
    }

    /**
     * Partitionne l'ensemble des décisions par état qualifié (INSIDE, OUTSIDE, PARTIAL).
     *
     * @param decisions Table associative des décisions par identifiant de cellule.
     * @return Partition immuable des identifiants regroupés par catégorie.
     */
    private CellPartition partitionDecisions(Map<Integer, CellDecision> decisions) {
        Set<Integer> insideIds = new HashSet<>();
        Set<Integer> outsideIds = new HashSet<>();
        Set<Integer> partialIds = new HashSet<>();

        for (CellDecision decision : decisions.values()) {
            switch (decision.state()) {
                case INSIDE -> insideIds.add(decision.cellId());
                case OUTSIDE -> outsideIds.add(decision.cellId());
                case PARTIAL -> partialIds.add(decision.cellId());
            }
        }

        return new CellPartition(insideIds, outsideIds, partialIds);
    }
}
