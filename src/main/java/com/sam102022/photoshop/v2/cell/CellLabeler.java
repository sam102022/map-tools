package com.sam102022.photoshop.v2.cell;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Étiqueteur de composantes connexes (Connected-Component Labeling) en 4-connexité stricte.
 * Segmente l'espace complémentaire du réseau routier (pixels où roadMask est à false) en cellules disjointes.
 * L'algorithme utilise un balayage Two-Pass optimisé avec structure d'équivalences Union-Find.
 */
public class CellLabeler {

    /**
     * Analyse le masque des routes fermées et extrait l'ensemble des cellules en 4-connexité.
     *
     * @param roadMask Masque binaire où les pixels à true représentent les routes infranchissables.
     * @return Résultat complet de la segmentation contenant la matrice d'étiquettes et la liste des cellules.
     * @throws IllegalArgumentException si roadMask est null.
     */
    public CellLabelingResult label(BinaryMask roadMask) {
        if (roadMask == null) {
            throw new IllegalArgumentException("Le masque routier ne peut pas être null.");
        }

        int width = roadMask.getWidth();
        int height = roadMask.getHeight();
        int[] labels = new int[width * height];

        DisjointSet disjointSet = new DisjointSet();

        firstPass(roadMask, width, height, labels, disjointSet);
        return secondPass(width, height, labels, disjointSet);
    }

    /**
     * Première passe : attribution des étiquettes provisoires et détection des équivalences Union-Find.
     *
     * @param roadMask    Masque binaire des routes fermées.
     * @param width       Largeur de la matrice en pixels.
     * @param height      Hauteur de la matrice en pixels.
     * @param labels      Tableau linéaire des étiquettes à renseigner.
     * @param disjointSet Structure Union-Find pour enregistrer les équivalences de labels.
     */
    private void firstPass(BinaryMask roadMask, int width, int height, int[] labels, DisjointSet disjointSet) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!roadMask.get(x, y)) {
                    int wLabel = (x > 0) ? labels[y * width + (x - 1)] : 0;
                    int nLabel = (y > 0) ? labels[(y - 1) * width + x] : 0;
                    labels[y * width + x] = resolveFirstPassLabel(wLabel, nLabel, disjointSet);
                }
            }
        }
    }

    /**
     * Détermine l'étiquette à affecter au pixel courant en fonction de ses voisins Ouest et Nord.
     *
     * @param wLabel      Étiquette du voisin Ouest (0 si inexistant ou route).
     * @param nLabel      Étiquette du voisin Nord (0 si inexistant ou route).
     * @param disjointSet Structure Union-Find pour consigner les fusions d'étiquettes.
     * @return Étiquette provisoire attribuée au pixel courant.
     */
    private int resolveFirstPassLabel(int wLabel, int nLabel, DisjointSet disjointSet) {
        if (wLabel == 0 && nLabel == 0) {
            return disjointSet.add();
        }
        if (wLabel > 0 && nLabel == 0) {
            return wLabel;
        }
        if (wLabel == 0 && nLabel > 0) {
            return nLabel;
        }
        disjointSet.union(wLabel, nLabel);
        return Math.min(wLabel, nLabel);
    }

    /**
     * Seconde passe : réindexation consécutive 1..N des labels et calcul cumulatif des attributs de cellule.
     *
     * @param width       Largeur de la matrice en pixels.
     * @param height      Hauteur de la matrice en pixels.
     * @param labels      Tableau linéaire d'étiquettes issues de la première passe.
     * @param disjointSet Structure Union-Find résolue.
     * @return Résultat d'étiquetage immuable associant la matrice finale et les cellules instanciées.
     */
    private CellLabelingResult secondPass(int width, int height, int[] labels, DisjointSet disjointSet) {
        int[] rootToNewId = buildConsecutiveIdMapping(disjointSet);
        int cellCount = computeCellCount(rootToNewId);

        Accumulator acc = new Accumulator(cellCount);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int origLabel = labels[idx];
                if (origLabel > 0) {
                    int root = disjointSet.find(origLabel);
                    int newId = rootToNewId[root];
                    labels[idx] = newId;
                    acc.accumulate(newId, x, y);
                }
            }
        }

        List<Cell> cells = acc.buildCells();
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, cellCount);
        return new CellLabelingResult(labelMap, cells);
    }

    /**
     * Associe chaque racine canonique d'équivalence à un identifiant unique consécutif 1..N.
     *
     * @param disjointSet Structure Union-Find contenant les composantes connexes.
     * @return Tableau associant chaque label d'origine à son nouvel identifiant consécutif (0 si inactif).
     */
    private int[] buildConsecutiveIdMapping(DisjointSet disjointSet) {
        int maxLabel = disjointSet.size();
        int[] mapping = new int[maxLabel + 1];
        int nextId = 1;

        for (int i = 1; i <= maxLabel; i++) {
            if (disjointSet.find(i) == i) {
                mapping[i] = nextId++;
            }
        }
        return mapping;
    }

    /**
     * Détermine le nombre total de cellules créées d'après le tableau de mapping.
     *
     * @param rootToNewId Tableau de correspondance racine vers identifiant consécutif.
     * @return Nombre total de cellules (identifiant maximal généré).
     */
    private int computeCellCount(int[] rootToNewId) {
        int max = 0;
        for (int id : rootToNewId) {
            if (id > max) {
                max = id;
            }
        }
        return max;
    }

    /**
     * Accumulateur interne pour agréger surfaces, bornes rectangulaires et centroïdes.
     */
    private static class Accumulator {
        private final int cellCount;
        private final long[] areas;
        private final int[] minXs;
        private final int[] minYs;
        private final int[] maxXs;
        private final int[] maxYs;
        private final double[] sumXs;
        private final double[] sumYs;

        /**
         * Initialise un accumulateur pour le nombre de cellules spécifié.
         *
         * @param cellCount Nombre total de cellules à dimensionner.
         */
        Accumulator(int cellCount) {
            this.cellCount = cellCount;
            this.areas = new long[cellCount + 1];
            this.minXs = new int[cellCount + 1];
            this.minYs = new int[cellCount + 1];
            this.maxXs = new int[cellCount + 1];
            this.maxYs = new int[cellCount + 1];
            this.sumXs = new double[cellCount + 1];
            this.sumYs = new double[cellCount + 1];

            Arrays.fill(minXs, Integer.MAX_VALUE);
            Arrays.fill(minYs, Integer.MAX_VALUE);
            Arrays.fill(maxXs, Integer.MIN_VALUE);
            Arrays.fill(maxYs, Integer.MIN_VALUE);
        }

        /**
         * Agrège un pixel (x, y) appartenant à la cellule d'identifiant id.
         *
         * @param id Identifiant consécutif de la cellule (1..cellCount).
         * @param x  Abscisse locale du pixel.
         * @param y  Ordonnée locale du pixel.
         */
        void accumulate(int id, int x, int y) {
            areas[id]++;
            if (x < minXs[id]) minXs[id] = x;
            if (x > maxXs[id]) maxXs[id] = x;
            if (y < minYs[id]) minYs[id] = y;
            if (y > maxYs[id]) maxYs[id] = y;
            sumXs[id] += x;
            sumYs[id] += y;
        }

        /**
         * Construit la liste immuable des cellules finalisées avec leurs statistiques géométriques.
         *
         * @return Liste ordonnée de cellules.
         */
        List<Cell> buildCells() {
            List<Cell> cells = new ArrayList<>(cellCount);
            for (int id = 1; id <= cellCount; id++) {
                long area = areas[id];
                PixelPoint centroid = new PixelPoint(sumXs[id] / area, sumYs[id] / area);
                cells.add(new Cell(id, area, minXs[id], minYs[id], maxXs[id], maxYs[id], centroid));
            }
            return cells;
        }
    }

    /**
     * Structure de données Union-Find pour la gestion des équivalences d'étiquettes.
     */
    private static class DisjointSet {
        private int[] parent;
        private int count;

        /**
         * Initialise un ensemble disjoint vide.
         */
        DisjointSet() {
            this.parent = new int[256];
            this.count = 0;
        }

        /**
         * Alloue et ajoute une nouvelle composante indépendante.
         *
         * @return Identifiant entier unique attribué à cette composante.
         */
        int add() {
            count++;
            ensureCapacity(count);
            parent[count] = count;
            return count;
        }

        /**
         * Recherche le représentant canonique (racine) de l'élément avec compression de chemin.
         *
         * @param i Identifiant d'élément.
         * @return Racine canonique de l'ensemble d'équivalence.
         */
        int find(int i) {
            int root = i;
            while (parent[root] != root) {
                root = parent[root];
            }
            // Compression de chemin
            int curr = i;
            while (curr != root) {
                int next = parent[curr];
                parent[curr] = root;
                curr = next;
            }
            return root;
        }

        /**
         * Fusionne deux ensembles d'équivalence.
         *
         * @param i Premier identifiant.
         * @param j Second identifiant.
         */
        void union(int i, int j) {
            int rootI = find(i);
            int rootJ = find(j);
            if (rootI != rootJ) {
                if (rootI < rootJ) {
                    parent[rootJ] = rootI;
                } else {
                    parent[rootI] = rootJ;
                }
            }
        }

        /**
         * Retourne le nombre total d'éléments alloués.
         *
         * @return Taille actuelle du DisjointSet.
         */
        int size() {
            return count;
        }

        /**
         * Assure une capacité minimale suffisante du tableau sous-jacent.
         *
         * @param minCapacity Capacité minimale requise.
         */
        private void ensureCapacity(int minCapacity) {
            if (minCapacity >= parent.length) {
                int newCapacity = Math.max(parent.length * 2, minCapacity + 256);
                parent = Arrays.copyOf(parent, newCapacity);
            }
        }
    }
}
