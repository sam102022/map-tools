# Plan d'Implémentation V2 — Sprint 4 : Moteur de Vote Topologique & Résolution des Parcelles

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Classifier chaque cellule issue de la segmentation en 4-connexité en fonction de son taux de couverture par le polygone d'intention (INSIDE, PARTIAL, OUTSIDE) et construire le masque d'amorçage matriciel $T$ pour l'expansion géodésique du Sprint 5.

**Architecture:** Moteur modulaire respectant le principe de responsabilité unique (ADR-008). Calcul matriciel en un seul passage des surfaces d'intersection (`CellCoverageCalculator`), classification selon des seuils immuables configurables (`CellClassifier`), isolation des cellules ouvertes et synthèse du masque retenu $T$ (`PartialCellResolver`), le tout coordonné par une façade (`TopologicalVoteEngine`).

**Tech Stack:** Java 21, Java Records immuables (ADR-004), JUnit 5, AssertJ, Maven.

---

## Architecture des Fichiers à Créer

```text
src/main/java/com/sam102022/photoshop/v2/vote/
├── CellState.java                    # Enum (INSIDE, OUTSIDE, PARTIAL)
├── CellSelectionPolicy.java          # Record de politique de vote (seuils inside/partial)
├── CellDecision.java                 # Record de diagnostic par cellule (id, area, intersection, coverage, state)
├── CellSelection.java                # Record de résultat global (inside, outside, partial, retainedMask, partialMasks)
├── CellCoverageCalculator.java       # Calculateur O(W*H) des intersections et ratios de couverture
├── CellClassifier.java               # Classificateur appliquant la politique sur les ratios
├── PartialCellResolver.java          # Extracteur des découpes partielles et constructeur du masque T
└── TopologicalVoteEngine.java        # Façade unifiée orchestrant le vote

src/test/java/com/sam102022/photoshop/v2/vote/
├── CellDomainModelTest.java          # Tests unitaires des records et invariants
├── CellCoverageCalculatorTest.java   # Tests de comptage d'intersection sur grilles synthétiques
├── CellClassifierTest.java           # Tests de classification et cas limites aux seuils
├── PartialCellResolverTest.java      # Tests de construction du masque retenu et découpes partielles
├── TopologicalVoteEngineTest.java    # Tests d'orchestration globale
└── Sprint4IntegrationTest.java       # Test pivot sur CA01 (21 INSIDE, 6 PARTIAL, 103 OUTSIDE)
```

---

## Tâches Séquentielles d'Implémentation

### Task 1: Modèle de Domaine Immuable (`CellState`, `CellSelectionPolicy`, `CellDecision`, `CellSelection`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellState.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellSelectionPolicy.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellDecision.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellSelection.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/vote/CellDomainModelTest.java`

- [ ] **Step 1: Écrire le test unitaire pour les records du domaine**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/CellDomainModelTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du modèle de domaine v2.vote")
class CellDomainModelTest {

    @Test
    @DisplayName("CellSelectionPolicy : seuils par défaut et validation des invariants")
    void testPolicyDefaultsAndValidation() {
        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy();
        assertEquals(0.60, policy.insideThreshold(), 1e-6);
        assertEquals(0.05, policy.partialThreshold(), 1e-6);

        // Seuils inversés ou invalides
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(0.3, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(-0.1, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new CellSelectionPolicy(0.8, 1.2));
    }

    @Test
    @DisplayName("CellDecision : validation des invariants")
    void testCellDecisionValidation() {
        CellDecision decision = new CellDecision(1, 100, 75, 0.75, CellState.INSIDE);
        assertEquals(1, decision.cellId());
        assertEquals(100, decision.cellArea());
        assertEquals(75, decision.intersectionArea());
        assertEquals(0.75, decision.coverage(), 1e-6);
        assertEquals(CellState.INSIDE, decision.state());

        assertThrows(IllegalArgumentException.class, () -> new CellDecision(0, 100, 50, 0.5, CellState.PARTIAL));
        assertThrows(IllegalArgumentException.class, () -> new CellDecision(1, 0, 0, 0.0, CellState.OUTSIDE));
        assertThrows(IllegalArgumentException.class, () -> new CellDecision(1, 100, 150, 1.5, CellState.INSIDE));
    }

    @Test
    @DisplayName("CellSelection : navigation et immutabilité")
    void testCellSelectionNavigation() {
        BinaryMask mask = new BinaryMask(10, 10);
        CellDecision d1 = new CellDecision(1, 50, 40, 0.8, CellState.INSIDE);
        CellDecision d2 = new CellDecision(2, 50, 5, 0.1, CellState.PARTIAL);
        CellDecision d3 = new CellDecision(3, 50, 0, 0.0, CellState.OUTSIDE);

        CellSelection selection = new CellSelection(
                Set.of(1),
                Set.of(3),
                Set.of(2),
                Map.of(1, d1, 2, d2, 3, d3),
                mask,
                Map.of(2, mask)
        );

        assertTrue(selection.isRetained(1));
        assertTrue(selection.isRetained(2));
        assertFalse(selection.isRetained(3));
        assertEquals(Optional.of(d1), selection.findDecision(1));
        assertEquals(Optional.empty(), selection.findDecision(99));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Lancer : `mvn test -Dtest=CellDomainModelTest`
Attendu : Échec de compilation car les types `CellState`, `CellSelectionPolicy`, `CellDecision` et `CellSelection` n'existent pas encore.

- [ ] **Step 3: Implémenter les records du domaine**

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellState.java` :
```java
package com.sam102022.photoshop.v2.vote;

/**
 * Qualification de l'état d'inclusion d'une cellule par rapport au territoire d'intention.
 */
public enum CellState {
    /** Cellule entièrement absorbée dans le territoire (couverture >= insideThreshold). */
    INSIDE,
    /** Cellule entièrement rejetée en dehors du territoire (couverture <= partialThreshold). */
    OUTSIDE,
    /** Cellule frontière mixte conservée uniquement sur son intersection avec le polygone. */
    PARTIAL
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellSelectionPolicy.java` :
```java
package com.sam102022.photoshop.v2.vote;

/**
 * Politique de seuillage configurable pour la classification des cellules urbaines.
 *
 * @param insideThreshold  Seuil minimal d'inclusion pour qualifier une cellule de INSIDE (ex: 0.60).
 * @param partialThreshold Seuil minimal en deçà duquel une cellule est rejetée en OUTSIDE (ex: 0.05).
 */
public record CellSelectionPolicy(
        double insideThreshold,
        double partialThreshold
) {
    public static final double DEFAULT_INSIDE_THRESHOLD = 0.60;
    public static final double DEFAULT_PARTIAL_THRESHOLD = 0.05;

    public CellSelectionPolicy {
        if (partialThreshold < 0.0 || partialThreshold >= insideThreshold || insideThreshold > 1.0) {
            throw new IllegalArgumentException("Les seuils doivent vérifier 0.0 <= partialThreshold (" + partialThreshold
                    + ") < insideThreshold (" + insideThreshold + ") <= 1.0");
        }
    }

    public static CellSelectionPolicy defaultPolicy() {
        return new CellSelectionPolicy(DEFAULT_INSIDE_THRESHOLD, DEFAULT_PARTIAL_THRESHOLD);
    }
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellDecision.java` :
```java
package com.sam102022.photoshop.v2.vote;

/**
 * Diagnostic immuable du résultat du vote pour une cellule donnée.
 *
 * @param cellId           Identifiant unique de la cellule (1..N).
 * @param cellArea         Surface totale de la cellule en pixels.
 * @param intersectionArea Surface d'intersection entre la cellule et le polygone d'intention en pixels.
 * @param coverage         Ratio de couverture (intersectionArea / cellArea compris entre 0.0 et 1.0).
 * @param state            État qualifié résultant de la classification.
 */
public record CellDecision(
        int cellId,
        long cellArea,
        long intersectionArea,
        double coverage,
        CellState state
) {
    public CellDecision {
        if (cellId <= 0) {
            throw new IllegalArgumentException("L'identifiant de cellule doit être strictement positif : " + cellId);
        }
        if (cellArea <= 0) {
            throw new IllegalArgumentException("La surface de cellule doit être strictement positive : " + cellArea);
        }
        if (intersectionArea < 0 || intersectionArea > cellArea) {
            throw new IllegalArgumentException("Surface d'intersection invalide : " + intersectionArea
                    + " pour une cellule d'aire " + cellArea);
        }
        if (coverage < 0.0 || coverage > 1.000001) {
            throw new IllegalArgumentException("Ratio de couverture hors bornes [0.0, 1.0] : " + coverage);
        }
        if (state == null) {
            throw new IllegalArgumentException("L'état de la cellule ne peut pas être null.");
        }
    }
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellSelection.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Résultat immuable de la sélection des cellules et masque d'amorçage pour le Sprint 5.
 *
 * @param insideCellIds    Identifiants des cellules retenues à 100%.
 * @param outsideCellIds   Identifiants des cellules rejetées à 100%.
 * @param partialCellIds   Identifiants des cellules à découpage partiel.
 * @param decisions        Table des diagnostics indexée par identifiant de cellule.
 * @param retainedMask     Masque binaire complet T (cellules INSIDE + portions retenues des PARTIAL).
 * @param partialCellMasks Sous-masques binaires individuels de chaque cellule partielle.
 */
public record CellSelection(
        Set<Integer> insideCellIds,
        Set<Integer> outsideCellIds,
        Set<Integer> partialCellIds,
        Map<Integer, CellDecision> decisions,
        BinaryMask retainedMask,
        Map<Integer, BinaryMask> partialCellMasks
) {
    public CellSelection {
        if (insideCellIds == null || outsideCellIds == null || partialCellIds == null
                || decisions == null || retainedMask == null || partialCellMasks == null) {
            throw new IllegalArgumentException("Les composants de CellSelection ne peuvent pas être null.");
        }
        insideCellIds = Set.copyOf(insideCellIds);
        outsideCellIds = Set.copyOf(outsideCellIds);
        partialCellIds = Set.copyOf(partialCellIds);
        decisions = Map.copyOf(decisions);
        partialCellMasks = Collections.unmodifiableMap(Map.copyOf(partialCellMasks));
    }

    public boolean isRetained(int cellId) {
        return insideCellIds.contains(cellId) || partialCellIds.contains(cellId);
    }

    public Optional<CellDecision> findDecision(int cellId) {
        return Optional.ofNullable(decisions.get(cellId));
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier la réussite**

Lancer : `mvn test -Dtest=CellDomainModelTest`
Attendu : PASS (Tous les tests réussissent).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/vote/CellState.java \
        src/main/java/com/sam102022/photoshop/v2/vote/CellSelectionPolicy.java \
        src/main/java/com/sam102022/photoshop/v2/vote/CellDecision.java \
        src/main/java/com/sam102022/photoshop/v2/vote/CellSelection.java \
        src/test/java/com/sam102022/photoshop/v2/vote/CellDomainModelTest.java
git commit -m "feat(v2/vote): ajout des modèles de domaine immuables pour le vote topologique"
```

---

### Task 2: Calculateur de Couverture Géométrique (`CellCoverageCalculator`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculator.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculatorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `CellCoverageCalculator`**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculatorTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests unitaires du calculateur de couverture CellCoverageCalculator")
class CellCoverageCalculatorTest {

    @Test
    @DisplayName("Calcul exact des surfaces d'intersection et ratios de couverture")
    void testComputeCoverages() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // Moitié gauche (x < 5) = Cellule 1 (50 px), Moitié droite (x >= 5) = Cellule 2 (50 px)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                labels[y * 10 + x] = (x < 5) ? 1 : 2;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 2);

        List<Cell> cells = List.of(
                new Cell(1, 50, 0, 0, 4, 9, new PixelPoint(2.0, 4.5)),
                new Cell(2, 50, 5, 0, 9, 9, new PixelPoint(7.0, 4.5))
        );

        // Polygone recouvrant entièrement la cellule 1 (50 px) et 10 px de la cellule 2 (x=5)
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 6; x++) {
                polygonMask.set(x, y, true);
            }
        }

        CellCoverageCalculator calculator = new CellCoverageCalculator();
        Map<Integer, Long> intersections = calculator.computeIntersections(labelMap, polygonMask);

        assertEquals(50L, intersections.get(1));
        assertEquals(10L, intersections.get(2));

        Map<Integer, Double> coverages = calculator.computeCoverages(labelMap, cells, polygonMask);
        assertEquals(1.0, coverages.get(1), 1e-6);
        assertEquals(0.20, coverages.get(2), 1e-6);
    }

    @Test
    @DisplayName("Rejet des masques de dimensions divergentes")
    void testDimensionMismatch() {
        CellLabelMap labelMap = new CellLabelMap(10, 10, new int[100], 0);
        BinaryMask polygonMask = new BinaryMask(12, 10);
        CellCoverageCalculator calculator = new CellCoverageCalculator();

        assertThrows(IllegalArgumentException.class, () -> calculator.computeIntersections(labelMap, polygonMask));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Lancer : `mvn test -Dtest=CellCoverageCalculatorTest`
Attendu : FAIL (La classe `CellCoverageCalculator` n'existe pas).

- [ ] **Step 3: Implémenter `CellCoverageCalculator`**

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculator.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculateur de surface d'intersection spatiale et de ratio de couverture
 * entre chaque cellule urbaine et le masque du polygone d'intention.
 */
public class CellCoverageCalculator {

    /**
     * Calcule le nombre de pixels d'intersection entre chaque cellule et le polygone d'intention.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @return Table associative (cellId -> nombre de pixels d'intersection).
     */
    public Map<Integer, Long> computeIntersections(CellLabelMap labelMap, BinaryMask croppedPolygonMask) {
        validateInputs(labelMap, croppedPolygonMask);

        int cellCount = labelMap.cellCount();
        long[] counts = new long[cellCount + 1];

        int width = labelMap.width();
        int height = labelMap.height();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = labelMap.getLabel(x, y);
                if (label > 0 && label <= cellCount && croppedPolygonMask.get(x, y)) {
                    counts[label]++;
                }
            }
        }

        Map<Integer, Long> result = new HashMap<>(cellCount);
        for (int i = 1; i <= cellCount; i++) {
            result.put(i, counts[i]);
        }
        return result;
    }

    /**
     * Calcule le ratio de couverture (intersection / surface totale) pour chaque cellule.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param cells              Liste des cellules urbaines.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @return Table associative (cellId -> ratio de couverture entre 0.0 et 1.0).
     */
    public Map<Integer, Double> computeCoverages(CellLabelMap labelMap, List<Cell> cells, BinaryMask croppedPolygonMask) {
        Map<Integer, Long> intersections = computeIntersections(labelMap, croppedPolygonMask);
        Map<Integer, Double> coverages = new HashMap<>(cells.size());

        for (Cell cell : cells) {
            long inter = intersections.getOrDefault(cell.id(), 0L);
            double cov = cell.area() > 0 ? (double) inter / cell.area() : 0.0;
            coverages.put(cell.id(), Math.min(1.0, Math.max(0.0, cov)));
        }
        return coverages;
    }

    private void validateInputs(CellLabelMap labelMap, BinaryMask polygonMask) {
        if (labelMap == null || polygonMask == null) {
            throw new IllegalArgumentException("Les masques labelMap et polygonMask ne peuvent pas être null.");
        }
        if (labelMap.width() != polygonMask.getWidth() || labelMap.height() != polygonMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence de dimensions : labelMap=" + labelMap.width() + "x"
                    + labelMap.height() + ", polygonMask=" + polygonMask.getWidth() + "x" + polygonMask.getHeight());
        }
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier la réussite**

Lancer : `mvn test -Dtest=CellCoverageCalculatorTest`
Attendu : PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculator.java \
        src/test/java/com/sam102022/photoshop/v2/vote/CellCoverageCalculatorTest.java
git commit -m "feat(v2/vote): calcul O(W*H) des intersections et ratios de couverture"
```

---

### Task 3: Classificateur de Cellules (`CellClassifier`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/CellClassifier.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/vote/CellClassifierTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `CellClassifier`**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/CellClassifierTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests unitaires du classificateur CellClassifier")
class CellClassifierTest {

    @Test
    @DisplayName("Classification selon les seuils : INSIDE, PARTIAL et OUTSIDE")
    void testClassification() {
        List<Cell> cells = List.of(
                new Cell(1, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(2, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(3, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(4, 100, 0, 0, 9, 9, new PixelPoint(5, 5)),
                new Cell(5, 100, 0, 0, 9, 9, new PixelPoint(5, 5))
        );

        Map<Integer, Long> intersections = Map.of(
                1, 75L, // cov = 0.75 -> INSIDE
                2, 60L, // cov = 0.60 -> INSIDE (borne exacte)
                3, 30L, // cov = 0.30 -> PARTIAL
                4, 5L,  // cov = 0.05 -> OUTSIDE (borne exacte)
                5, 2L   // cov = 0.02 -> OUTSIDE
        );

        CellSelectionPolicy policy = CellSelectionPolicy.defaultPolicy(); // 0.60 / 0.05
        CellClassifier classifier = new CellClassifier();

        Map<Integer, CellDecision> decisions = classifier.classify(cells, intersections, policy);

        assertEquals(CellState.INSIDE, decisions.get(1).state());
        assertEquals(CellState.INSIDE, decisions.get(2).state());
        assertEquals(CellState.PARTIAL, decisions.get(3).state());
        assertEquals(CellState.OUTSIDE, decisions.get(4).state());
        assertEquals(CellState.OUTSIDE, decisions.get(5).state());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Lancer : `mvn test -Dtest=CellClassifierTest`
Attendu : FAIL (La classe `CellClassifier` n'existe pas).

- [ ] **Step 3: Implémenter `CellClassifier`**

Créer `src/main/java/com/sam102022/photoshop/v2/vote/CellClassifier.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.v2.cell.Cell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classificateur qualifiant chaque cellule en INSIDE, PARTIAL ou OUTSIDE
 * selon les seuils paramétrés de la politique de sélection.
 */
public class CellClassifier {

    /**
     * Applique la politique de sélection pour chaque cellule urbaine.
     *
     * @param cells         Liste des cellules urbaines.
     * @param intersections Surfaces d'intersection indexées par identifiant de cellule.
     * @param policy        Politique de seuillage.
     * @return Table associative (cellId -> CellDecision).
     */
    public Map<Integer, CellDecision> classify(
            List<Cell> cells,
            Map<Integer, Long> intersections,
            CellSelectionPolicy policy
    ) {
        if (cells == null || intersections == null || policy == null) {
            throw new IllegalArgumentException("Les arguments ne peuvent pas être null.");
        }

        Map<Integer, CellDecision> decisions = new HashMap<>(cells.size());

        for (Cell cell : cells) {
            long inter = intersections.getOrDefault(cell.id(), 0L);
            double cov = cell.area() > 0 ? (double) inter / cell.area() : 0.0;
            cov = Math.min(1.0, Math.max(0.0, cov));

            CellState state = determineState(cov, policy);
            decisions.put(cell.id(), new CellDecision(cell.id(), cell.area(), inter, cov, state));
        }

        return decisions;
    }

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
```

- [ ] **Step 4: Exécuter le test pour vérifier la réussite**

Lancer : `mvn test -Dtest=CellClassifierTest`
Attendu : PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/vote/CellClassifier.java \
        src/test/java/com/sam102022/photoshop/v2/vote/CellClassifierTest.java
git commit -m "feat(v2/vote): classification des cellules selon la politique de seuils"
```

---

### Task 4: Résolveur de Parcelles Partielles & Masque d'Amorçage $T$ (`PartialCellResolver`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/PartialCellResolver.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/vote/PartialCellResolverTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `PartialCellResolver`**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/PartialCellResolverTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du résolveur de parcelles partielles PartialCellResolver")
class PartialCellResolverTest {

    @Test
    @DisplayName("Génération conforme du masque d'amorçage T : INSIDE complet + PARTIAL restreint")
    void testResolveRetainedMask() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // y < 5 : Cellule 1 (INSIDE), y >= 5 : Cellule 2 (PARTIAL)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                labels[y * 10 + x] = (y < 5) ? 1 : 2;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 2);

        // Polygone actif uniquement sur x < 5
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 5; x++) {
                polygonMask.set(x, y, true);
            }
        }

        Set<Integer> insideIds = Set.of(1);
        Set<Integer> partialIds = Set.of(2);

        PartialCellResolver resolver = new PartialCellResolver();
        PartialCellResolver.ResolutionResult result = resolver.resolve(labelMap, polygonMask, insideIds, partialIds);

        BinaryMask retainedMask = result.retainedMask();

        // La cellule 1 doit être conservée à 100% (50 px), même pour x >= 5
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 10; x++) {
                assertTrue(retainedMask.get(x, y), "Pixel cellule 1 doit être conservé à 100%");
            }
        }

        // La cellule 2 ne doit être conservée que sur l'intersection avec le polygone (x < 5)
        for (int y = 5; y < 10; y++) {
            for (int x = 0; x < 5; x++) {
                assertTrue(retainedMask.get(x, y), "Intersection cellule 2 doit être conservée");
            }
            for (int x = 5; x < 10; x++) {
                assertFalse(retainedMask.get(x, y), "Hors-intersection cellule 2 doit être rejeté");
            }
        }

        assertEquals(75, retainedMask.countActivePixels());
        assertTrue(result.partialMasks().containsKey(2));
        assertEquals(25, result.partialMasks().get(2).countActivePixels());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Lancer : `mvn test -Dtest=PartialCellResolverTest`
Attendu : FAIL (La classe `PartialCellResolver` n'existe pas).

- [ ] **Step 3: Implémenter `PartialCellResolver`**

Créer `src/main/java/com/sam102022/photoshop/v2/vote/PartialCellResolver.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Résolveur d'intersection des cellules partielles et générateur du masque d'amorçage T.
 */
public class PartialCellResolver {

    /**
     * Résultat de la résolution contenant le masque d'amorçage global et les masques partiels individuels.
     */
    public record ResolutionResult(
            BinaryMask retainedMask,
            Map<Integer, BinaryMask> partialMasks
    ) {}

    /**
     * Construit le masque d'amorçage complet T et les masques individuels des cellules partielles.
     *
     * @param labelMap           Matrice d'étiquettes de cellules.
     * @param croppedPolygonMask Masque binaire du polygone d'intention dans le repère local.
     * @param insideCellIds      Identifiants des cellules conservées à 100%.
     * @param partialCellIds     Identifiants des cellules partielles.
     * @return Résultat de résolution immuable.
     */
    public ResolutionResult resolve(
            CellLabelMap labelMap,
            BinaryMask croppedPolygonMask,
            Set<Integer> insideCellIds,
            Set<Integer> partialCellIds
    ) {
        validateInputs(labelMap, croppedPolygonMask, insideCellIds, partialCellIds);

        int width = labelMap.width();
        int height = labelMap.height();

        BinaryMask retainedMask = new BinaryMask(width, height);
        Map<Integer, BinaryMask> partialMasks = new HashMap<>();
        for (int partialId : partialCellIds) {
            partialMasks.put(partialId, new BinaryMask(width, height));
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = labelMap.getLabel(x, y);
                if (label <= 0) {
                    continue;
                }
                if (insideCellIds.contains(label)) {
                    retainedMask.set(x, y, true);
                } else if (partialCellIds.contains(label) && croppedPolygonMask.get(x, y)) {
                    retainedMask.set(x, y, true);
                    BinaryMask pMask = partialMasks.get(label);
                    if (pMask != null) {
                        pMask.set(x, y, true);
                    }
                }
            }
        }

        return new ResolutionResult(retainedMask, partialMasks);
    }

    private void validateInputs(
            CellLabelMap labelMap,
            BinaryMask polygonMask,
            Set<Integer> insideIds,
            Set<Integer> partialIds
    ) {
        if (labelMap == null || polygonMask == null || insideIds == null || partialIds == null) {
            throw new IllegalArgumentException("Aucun argument de résolution ne peut être null.");
        }
        if (labelMap.width() != polygonMask.getWidth() || labelMap.height() != polygonMask.getHeight()) {
            throw new IllegalArgumentException("Incohérence de dimensions entre labelMap et polygonMask.");
        }
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier la réussite**

Lancer : `mvn test -Dtest=PartialCellResolverTest`
Attendu : PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/vote/PartialCellResolver.java \
        src/test/java/com/sam102022/photoshop/v2/vote/PartialCellResolverTest.java
git commit -m "feat(v2/vote): résolution des parcelles partielles et génération du masque T"
```

---

### Task 5: Façade Coordinatrice (`TopologicalVoteEngine`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngine.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngineTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `TopologicalVoteEngine`**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngineTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du moteur TopologicalVoteEngine")
class TopologicalVoteEngineTest {

    @Test
    @DisplayName("Orchestration complète du vote topologique avec politique par défaut")
    void testExecuteDefaultPolicy() {
        int width = 10;
        int height = 10;
        int[] labels = new int[100];
        // 3 bandes horizontales :
        // y 0..2 : Cell 1 (30 px)
        // y 3..6 : Cell 2 (40 px)
        // y 7..9 : Cell 3 (30 px)
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                if (y < 3) labels[y * 10 + x] = 1;
                else if (y < 7) labels[y * 10 + x] = 2;
                else labels[y * 10 + x] = 3;
            }
        }
        CellLabelMap labelMap = new CellLabelMap(width, height, labels, 3);

        List<Cell> cells = List.of(
                new Cell(1, 30, 0, 0, 9, 2, new PixelPoint(4.5, 1.0)),
                new Cell(2, 40, 0, 3, 9, 6, new PixelPoint(4.5, 4.5)),
                new Cell(3, 30, 0, 7, 9, 9, new PixelPoint(4.5, 8.0))
        );

        // Polygone recouvrant entièrement Cell 1 (30 px), 10 px de Cell 2 (cov = 0.25), 0 px de Cell 3
        BinaryMask polygonMask = new BinaryMask(width, height);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 10; x++) polygonMask.set(x, y, true);
        }
        for (int x = 0; x < 10; x++) {
            polygonMask.set(x, 3, true); // 10 px dans Cell 2
        }

        TopologicalVoteEngine engine = new TopologicalVoteEngine();
        CellSelection selection = engine.execute(labelMap, cells, polygonMask);

        assertNotNull(selection);
        assertEquals(1, selection.insideCellIds().size());
        assertTrue(selection.insideCellIds().contains(1));

        assertEquals(1, selection.partialCellIds().size());
        assertTrue(selection.partialCellIds().contains(2));

        assertEquals(1, selection.outsideCellIds().size());
        assertTrue(selection.outsideCellIds().contains(3));

        // Le masque d'amorçage doit contenir 30 (Cell 1) + 10 (Cell 2) = 40 px
        assertEquals(40, selection.retainedMask().countActivePixels());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Lancer : `mvn test -Dtest=TopologicalVoteEngineTest`
Attendu : FAIL (La classe `TopologicalVoteEngine` n'existe pas).

- [ ] **Step 3: Implémenter `TopologicalVoteEngine`**

Créer `src/main/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngine.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Façade orchestrant le vote topologique sur l'ensemble des cellules urbaines.
 */
public class TopologicalVoteEngine {

    private final CellCoverageCalculator coverageCalculator;
    private final CellClassifier classifier;
    private final PartialCellResolver partialResolver;

    public TopologicalVoteEngine() {
        this(new CellCoverageCalculator(), new CellClassifier(), new PartialCellResolver());
    }

    public TopologicalVoteEngine(
            CellCoverageCalculator coverageCalculator,
            CellClassifier classifier,
            PartialCellResolver partialResolver
    ) {
        this.coverageCalculator = coverageCalculator;
        this.classifier = classifier;
        this.partialResolver = partialResolver;
    }

    /**
     * Exécute le vote avec la politique par défaut (0.60 / 0.05).
     */
    public CellSelection execute(CellLabelMap labelMap, List<Cell> cells, BinaryMask croppedPolygonMask) {
        return execute(labelMap, cells, croppedPolygonMask, CellSelectionPolicy.defaultPolicy());
    }

    /**
     * Exécute le vote topologique selon une politique paramétrée.
     */
    public CellSelection execute(
            CellLabelMap labelMap,
            List<Cell> cells,
            BinaryMask croppedPolygonMask,
            CellSelectionPolicy policy
    ) {
        Map<Integer, Long> intersections = coverageCalculator.computeIntersections(labelMap, croppedPolygonMask);
        Map<Integer, CellDecision> decisions = classifier.classify(cells, intersections, policy);

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

        PartialCellResolver.ResolutionResult resolution = partialResolver.resolve(
                labelMap,
                croppedPolygonMask,
                insideIds,
                partialIds
        );

        return new CellSelection(
                insideIds,
                outsideIds,
                partialIds,
                decisions,
                resolution.retainedMask(),
                resolution.partialMasks()
        );
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier la réussite**

Lancer : `mvn test -Dtest=TopologicalVoteEngineTest`
Attendu : PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngine.java \
        src/test/java/com/sam102022/photoshop/v2/vote/TopologicalVoteEngineTest.java
git commit -m "feat(v2/vote): façade d'orchestration TopologicalVoteEngine"
```

---

### Task 6: Test d'Intégration Pivot CA01 (`Sprint4IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/vote/Sprint4IntegrationTest.java`

- [ ] **Step 1: Écrire le test d'intégration sur le territoire pivot CA01**

Créer `src/test/java/com/sam102022/photoshop/v2/vote/Sprint4IntegrationTest.java` :
```java
package com.sam102022.photoshop.v2.vote;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test d'intégration Sprint 4 : Vote Topologique et Classification sur CA01")
class Sprint4IntegrationTest {

    @Test
    @DisplayName("Vote topologique déterministe CA01 : 21 INSIDE, 6 PARTIAL, 103 OUTSIDE")
    void testTopologicalVoteOnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit être disponible.");

        Path roadPngPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPngPath), "L'image témoin maps/road.png doit exister.");

        long startTime = System.currentTimeMillis();

        // 1. Chargement et projection géométrique (Sprint 1)
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());
        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();

        // 2. Rastérisation du polygone global (Sprint 1)
        BufferedImage roadImage = ImageIO.read(roadPngPath.toFile());
        int width = roadImage.getWidth();
        int height = roadImage.getHeight();
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(polygonPoints, width, height);

        // 3. Masque des routes (Sprint 2)
        BinaryMask fullRoadMask = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (((roadImage.getRGB(x, y) >>> 16) & 0xFF) > 127) {
                    fullRoadMask.set(x, y, true);
                }
            }
        }

        // 4. Recadrage avec marge mg = 90 px (Sprint 3)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoadMask, cropWindow);
        BinaryMask croppedPolygon = cropper.crop(polygonMask.mask(), cropWindow);

        // 5. Segmentation en cellules 4-connexes (Sprint 3)
        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        assertEquals(130, labelingResult.cells().size());

        // 6. Exécution du vote topologique (Sprint 4)
        TopologicalVoteEngine voteEngine = new TopologicalVoteEngine();
        CellSelection selection = voteEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPolygon
        );

        // 7. Vérifications de conformité stricte avec l'étalon Python snap_cells_prototype_v5.py
        assertNotNull(selection);
        assertEquals(21, selection.insideCellIds().size(),
                "Le nombre de cellules INSIDE doit être exactement de 21.");
        assertEquals(6, selection.partialCellIds().size(),
                "Le nombre de cellules PARTIAL doit être exactement de 6.");
        assertEquals(103, selection.outsideCellIds().size(),
                "Le nombre de cellules OUTSIDE doit être exactement de 103.");
        assertEquals(130, selection.insideCellIds().size() + selection.partialCellIds().size() + selection.outsideCellIds().size(),
                "La somme des décisions doit couvrir l'intégralité des 130 cellules.");

        // Vérification du masque d'amorçage T
        assertNotNull(selection.retainedMask());
        assertEquals(1505, selection.retainedMask().getWidth());
        assertEquals(1783, selection.retainedMask().getHeight());
        assertTrue(selection.retainedMask().countActivePixels() > 0);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Sprint 4 exécuté sur CA01 en %d ms (21 INSIDE, 6 PARTIAL, 103 OUTSIDE).%n", elapsed);
        assertTrue(elapsed < 1000, "Le temps de calcul total doit être inférieur à 1000 ms.");
    }
}
```

- [ ] **Step 2: Exécuter le test d'intégration pivot**

Lancer : `mvn test -Dtest=Sprint4IntegrationTest`
Attendu : PASS (21 INSIDE, 6 PARTIAL, 103 OUTSIDE, validation déterministe).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/sam102022/photoshop/v2/vote/Sprint4IntegrationTest.java
git commit -m "test(v2/vote): test d'intégration pivot Sprint 4 sur CA01"
```

---

### Task 7: Mise à Jour Documentaire (`docs/sprints_v2/sprint-4-vote-topologique.md`, `docs/SPRINTS_V2.md`, `JOURNAL.md`)

**Files:**
- Modify: `docs/sprints_v2/sprint-4-vote-topologique.md`
- Modify: `docs/SPRINTS_V2.md`
- Modify: `JOURNAL.md`

- [ ] **Step 1: Harmoniser la fiche de sprint `sprint-4-vote-topologique.md`**

Mettre à jour la fiche pour pointer vers la Design Spec et le Plan d'Implémentation, et aligner les contrats d'entrée/sortie (`CellLabelMap`, `retainedMask`).

- [ ] **Step 2: Valider l'intégralité de la suite de tests**

Lancer : `mvn test`
Attendu : 100% de succès sur l'ensemble du projet.

- [ ] **Step 3: Commit**

```bash
git add docs/sprints_v2/sprint-4-vote-topologique.md \
        docs/SPRINTS_V2.md \
        JOURNAL.md
git commit -m "docs(v2): mise à jour documentaire Sprint 4 (vote topologique)"
```
