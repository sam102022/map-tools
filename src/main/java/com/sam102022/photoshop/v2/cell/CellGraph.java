package com.sam102022.photoshop.v2.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Graphe relationnel topologique immuable modélisant les cellules urbaines
 * et leurs connexions par les interfaces routières adjacentes.
 *
 * @param cells            Liste immuable de l'ensemble des cellules du graphe.
 * @param roadInterfaces   Liste immuable de l'ensemble des interfaces routières.
 * @param cellById         Table d'accès direct cellule par identifiant.
 * @param interfacesByCell Indexation des interfaces routières pour chaque cellule.
 */
public record CellGraph(
        List<Cell> cells,
        List<RoadInterface> roadInterfaces,
        Map<Integer, Cell> cellById,
        Map<Integer, List<RoadInterface>> interfacesByCell
) {

    /**
     * Valide et crée une instance immuable du graphe topologique.
     *
     * @param cells            Liste des cellules.
     * @param roadInterfaces   Liste des interfaces routières.
     * @param cellById         Index des cellules.
     * @param interfacesByCell Index des interfaces par cellule.
     * @throws IllegalArgumentException si l'une des collections est null.
     */
    public CellGraph {
        if (cells == null || roadInterfaces == null || cellById == null || interfacesByCell == null) {
            throw new IllegalArgumentException("Les composants du CellGraph ne peuvent pas être null.");
        }
        cells = List.copyOf(cells);
        roadInterfaces = List.copyOf(roadInterfaces);
        cellById = Map.copyOf(cellById);

        Map<Integer, List<RoadInterface>> defensiveInterfaces = new HashMap<>();
        for (Map.Entry<Integer, List<RoadInterface>> entry : interfacesByCell.entrySet()) {
            defensiveInterfaces.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        interfacesByCell = Collections.unmodifiableMap(defensiveInterfaces);
    }

    /**
     * Constructeur de commodité construisant automatiquement les tables d'indexation.
     *
     * @param cells          Liste des cellules à intégrer dans le graphe.
     * @param roadInterfaces Liste des interfaces routières à intégrer.
     * @throws IllegalArgumentException si cells ou roadInterfaces est null.
     */
    public CellGraph(List<Cell> cells, List<RoadInterface> roadInterfaces) {
        this(
                cells,
                roadInterfaces,
                buildCellMap(cells),
                buildInterfacesMap(roadInterfaces)
        );
    }

    /**
     * Construit la table d'indexation identifiant -> Cellule.
     */
    private static Map<Integer, Cell> buildCellMap(List<Cell> cells) {
        if (cells == null) {
            throw new IllegalArgumentException("La liste de cellules ne peut pas être null.");
        }
        Map<Integer, Cell> map = new HashMap<>();
        for (Cell cell : cells) {
            map.put(cell.id(), cell);
        }
        return map;
    }

    /**
     * Construit la table d'indexation cellule -> liste des interfaces routières connectées.
     */
    private static Map<Integer, List<RoadInterface>> buildInterfacesMap(List<RoadInterface> interfaces) {
        if (interfaces == null) {
            throw new IllegalArgumentException("La liste d'interfaces routières ne peut pas être null.");
        }
        Map<Integer, List<RoadInterface>> map = new HashMap<>();
        for (RoadInterface ri : interfaces) {
            map.computeIfAbsent(ri.cellA(), k -> new ArrayList<>()).add(ri);
            map.computeIfAbsent(ri.cellB(), k -> new ArrayList<>()).add(ri);
        }
        return map;
    }

    /**
     * Recherche une cellule par son identifiant unique.
     *
     * @param id Identifiant de la cellule.
     * @return Optional contenant la cellule si trouvée, vide sinon.
     */
    public Optional<Cell> findCell(int id) {
        return Optional.ofNullable(cellById.get(id));
    }

    /**
     * Retourne la liste des interfaces routières connectées à une cellule spécifique.
     *
     * @param cellId Identifiant de la cellule.
     * @return Liste immuable des interfaces (vide si aucune interface trouvée).
     */
    public List<RoadInterface> getInterfaces(int cellId) {
        return interfacesByCell.getOrDefault(cellId, List.of());
    }

    /**
     * Recherche l'interface routière séparant spécifiquement deux cellules cellA et cellB.
     *
     * @param cellA Première cellule.
     * @param cellB Seconde cellule.
     * @return Optional contenant l'interface correspondante, vide sinon.
     */
    public Optional<RoadInterface> findInterface(int cellA, int cellB) {
        int min = Math.min(cellA, cellB);
        int max = Math.max(cellA, cellB);
        for (RoadInterface ri : getInterfaces(min)) {
            if (ri.cellA() == min && ri.cellB() == max) {
                return Optional.of(ri);
            }
        }
        return Optional.empty();
    }

    /**
     * Retourne l'ensemble des identifiants des cellules voisines immédiates d'une cellule donnée.
     *
     * @param cellId Identifiant de la cellule source.
     * @return Ensemble des cellules voisines séparées par une RoadInterface.
     */
    public Set<Integer> getNeighbors(int cellId) {
        Set<Integer> neighbors = new HashSet<>();
        for (RoadInterface ri : getInterfaces(cellId)) {
            neighbors.add(ri.getOppositeCell(cellId));
        }
        return Collections.unmodifiableSet(neighbors);
    }
}
