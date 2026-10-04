package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.v2.cell.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classificateur qualifiant chaque cellule urbaine en INSIDE, PARTIAL ou OUTSIDE
 * selon les seuils paramétrés de la politique de sélection.
 */
public class CellClassifier {

    /**
     * Construit une nouvelle instance du classificateur de cellules.
     */
    public CellClassifier() {
        // Constructeur par défaut explicite
    }

    /**
     * Applique la politique de sélection pour chaque cellule urbaine.
     *
     * @param cells         Liste des cellules urbaines à qualifier.
     * @param intersections Surfaces d'intersection indexées par identifiant de cellule.
     * @param policy        Politique de seuillage définissant les bornes d'inclusion et d'exclusion.
     * @return Table associative associant chaque identifiant de cellule à sa décision d'inclusion.
     * @throws IllegalArgumentException si l'un des paramètres d'entrée est null.
     */
    public Map<Integer, CellDecision> classify(
            List<Cell> cells,
            Map<Integer, Long> intersections,
            CellSelectionPolicy policy
    ) {
        if (cells == null || intersections == null || policy == null) {
            throw new IllegalArgumentException("Les arguments de classification ne peuvent pas être null.");
        }

        Map<Integer, CellDecision> decisions = new HashMap<>(cells.size());

        for (Cell cell : cells) {
            long inter = intersections.getOrDefault(cell.id(), 0L);
            long safeInter = Math.min(cell.area(), Math.max(0L, inter));
            double cov = cell.area() > 0 ? (double) safeInter / cell.area() : 0.0;
            cov = Math.min(1.0, Math.max(0.0, cov));

            CellState state = determineState(cov, policy);
            decisions.put(cell.id(), new CellDecision(cell.id(), cell.area(), safeInter, cov, state));
        }

        return decisions;
    }

    /**
     * Détermine l'état de qualification d'une cellule en comparant son ratio de couverture aux seuils.
     *
     * @param coverage Ratio de couverture compris entre 0.0 et 1.0.
     * @param policy   Politique de seuillage contenant les seuils d'inclusion et d'exclusion.
     * @return État qualifié résultant pour la cellule (INSIDE, PARTIAL ou OUTSIDE).
     */
    private CellState determineState(double coverage, CellSelectionPolicy policy) {
        if (coverage >= policy.insideThreshold()) {
            return CellState.INSIDE;
        } else if (coverage > policy.partialThreshold()) {
            return CellState.PARTIAL;
        } else {
            return CellState.OUTSIDE;
        }
    }
}
