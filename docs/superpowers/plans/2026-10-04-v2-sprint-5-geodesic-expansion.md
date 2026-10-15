# Sprint 5 (V2) Implementation Plan — Reconstruction des Frontières Routières & Expansion Géodésique Matricielle

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter l'expansion géodésique matricielle bornée et la consolidation morphologique des frontières routières pour transformer le masque des cellules sélectionnées $T$ (Sprint 4) et le masque des routes $R_c$ (Sprint 2) en un masque binaire consolidé étanche `ConsolidatedMask` ($M_c$) comblant les îlots compacts, en 100% Java standard sans dépendances natives.

**Architecture:** Modélisation par contrats immuables (`ConsolidatedMask`, `DistanceMap`, `ExpansionConfig`), calcul linéaire de demi-largeur locale par EDT Meijster $O(N)$ (`EuclideanDistanceTransform`) et filtre max séparable de Lemire $O(N)$ (`LocalMaxFilter`), propagation géodésique 8-connexe Dijkstra bornée (`BoundedGeodesicExpander`), consolidation morphologique sanctuarisant $T$ (`MorphologicalConsolidator`), comblement des îlots compacts par inondation inverse (`ResidualHoleResolver`), partitionnement médian en mode ZONE (`BoundaryRoadPartitioner`), et orchestration globale (`RoadBoundaryConsolidator`).

**Tech Stack:** Java 21 standard, JUnit 5, AssertJ, sans bibliothèque native ni framework externe (ADR-001, ADR-002, ADR-004, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014).

---

## Architecture des Fichiers à Créer

```text
src/main/java/com/sam102022/photoshop/v2/expansion/
├── BoundedGeodesicExpander.java         # Moteur de propagation Dijkstra bornée (MCP)
├── BoundaryRoadPartitioner.java         # Partage médian équitable en mode ZONE
├── ConsolidatedMask.java                # Contrat de domaine immuable de sortie
├── DistanceMap.java                     # Matrice float[] de distances euclidiennes
├── EuclideanDistanceTransform.java      # Algorithme linéaire Meijster O(N)
├── ExpansionConfig.java                 # Record de configuration et hyperparamètres
├── LocalMaxFilter.java                  # Filtre max 1D séparable de Lemire O(N)
├── MorphologicalConsolidator.java       # Union, padding et ouverture par disque (sanctuarisation de T)
├── ResidualHoleResolver.java            # Inondation inverse et comblement <= 15 000 px
└── RoadBoundaryConsolidator.java        # Orchestrateur de haut niveau du Sprint 5

src/test/java/com/sam102022/photoshop/v2/expansion/
├── BoundedGeodesicExpanderTest.java     # Tests d'arrêt de propagation et étanchéité carrefour
├── BoundaryRoadPartitionerTest.java     # Tests d'équidistance et non-chevauchement en mode ZONE
├── EuclideanDistanceTransformTest.java  # Tests de conformité mathématique EDT Meijster O(N)
├── LocalMaxFilterTest.java              # Tests du filtre max séparable glissant O(N)
├── MorphologicalConsolidatorTest.java   # Tests d'ouverture par disque et sanctuarisation de T
├── ResidualHoleResolverTest.java        # Tests de comblement sélectif (<= 15 000 px)
├── RoadBoundaryConsolidatorTest.java    # Tests du pipeline combiné
└── Sprint5IntegrationTest.java          # Test d'intégration pivot sur CA01 (1 215 383 px)
```

---

## Tâches Séquentielles d'Implémentation

### Task 1: Contrats de Domaine Immuables (`ExpansionConfig`, `DistanceMap`, `ConsolidatedMask`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/ExpansionConfig.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/DistanceMap.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/ConsolidatedMask.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/ExpansionDomainModelTest.java`

- [ ] **Step 1: Écrire le test unitaire pour les records de domaine**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests des modèles de domaine du Sprint 5")
class ExpansionDomainModelTest {

    @Test
    @DisplayName("ExpansionConfig expose les valeurs par défaut pour TERRITORY et ZONE")
    void testExpansionConfigDefaults() {
        ExpansionConfig territory = ExpansionConfig.defaultTerritory();
        assertEquals(4.0, territory.epsilon());
        assertEquals(41, territory.maxFilterSize());
        assertEquals(5, territory.openingDiskRadius());
        assertEquals(15000L, territory.maxHoleArea());
        assertEquals(2, territory.dilationSeedSteps());
        assertEquals(OperationMode.TERRITORY, territory.operationMode());

        ExpansionConfig zone = ExpansionConfig.defaultZone();
        assertEquals(OperationMode.ZONE, zone.operationMode());
    }

    @Test
    @DisplayName("DistanceMap alloue et manipule les valeurs sans allocation d'objets")
    void testDistanceMapOperations() {
        DistanceMap map = new DistanceMap(10, 10, new float[100]);
        map.set(2, 3, 4.5f);
        assertEquals(4.5f, map.get(2, 3));
        assertThrows(IllegalArgumentException.class, () -> new DistanceMap(10, 10, new float[50]));
    }

    @Test
    @DisplayName("ConsolidatedMask valide les dimensions et calcule les offsets de CropWindow")
    void testConsolidatedMaskProperties() {
        BinaryMask mask = new BinaryMask(100, 80);
        CropWindow crop = new CropWindow(15, 25, 100, 80);
        ConsolidatedMask consolidated = new ConsolidatedMask(100, 80, mask, crop);

        assertEquals(100, consolidated.width());
        assertEquals(80, consolidated.height());
        assertEquals(15, consolidated.offsetX());
        assertEquals(25, consolidated.offsetY());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=ExpansionDomainModelTest`  
Expected: Compilation failure (classes non existantes).

- [ ] **Step 3: Implémenter les records `ExpansionConfig`, `DistanceMap` et `ConsolidatedMask`**

Créer `ExpansionConfig.java`, `DistanceMap.java`, et `ConsolidatedMask.java` avec Javadoc intégrale en français, validation défensive d'arguments et immutabilité stricte conforme à ADR-004, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=ExpansionDomainModelTest`  
Expected: PASS (100%).

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/ExpansionConfig.java src/main/java/com/sam102022/photoshop/v2/expansion/DistanceMap.java src/main/java/com/sam102022/photoshop/v2/expansion/ConsolidatedMask.java src/test/java/com/sam102022/photoshop/v2/expansion/ExpansionDomainModelTest.java
git commit -m "feat(v2): implement immutable domain models for Sprint 5 expansion"
```

---

### Task 2: Transformée de Distance Euclidienne Exacte $O(N)$ Meijster (`EuclideanDistanceTransform`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/EuclideanDistanceTransform.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/EuclideanDistanceTransformTest.java`

- [ ] **Step 1: Écrire le test unitaire pour l'algorithme Meijster**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests de la transformée de distance euclidienne exacte O(N)")
class EuclideanDistanceTransformTest {

    @Test
    @DisplayName("Calcul de demi-largeur sur une bande routière rectiligne")
    void testStraightRoadHalfWidth() {
        int width = 20;
        int height = 10;
        BinaryMask roadMask = new BinaryMask(width, height);
        // Route horizontale entre y = 3 et y = 6 (largeur 4 pixels : y=3,4,5,6)
        for (int y = 3; y <= 6; y++) {
            for (int x = 0; x < width; x++) {
                roadMask.set(x, y, true);
            }
        }

        EuclideanDistanceTransform edt = new EuclideanDistanceTransform();
        DistanceMap distanceMap = edt.compute(roadMask);

        // Au bord extérieur (y=3 ou y=6), distance au bord est de 1.0 px
        assertEquals(1.0f, distanceMap.get(5, 3), 0.01f);
        assertEquals(1.0f, distanceMap.get(5, 6), 0.01f);
        // Au centre (y=4 ou y=5), distance au bord est de 2.0 px
        assertEquals(2.0f, distanceMap.get(5, 4), 0.01f);
        assertEquals(2.0f, distanceMap.get(5, 5), 0.01f);
        // Hors route (y=0), distance = 0
        assertEquals(0.0f, distanceMap.get(5, 0), 0.01f);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=EuclideanDistanceTransformTest`  
Expected: FAIL (classe non trouvée).

- [ ] **Step 3: Implémenter `EuclideanDistanceTransform`**

Implémenter l'algorithme exact de Meijster (passe 1D verticale $O(H)$, puis passe 1D horizontale $O(W)$ avec gestion des paraboles) en Java pur standard (ADR-001, complexité cognitive <= 15 via sous-méthodes privées par passe).

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=EuclideanDistanceTransformTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/EuclideanDistanceTransform.java src/test/java/com/sam102022/photoshop/v2/expansion/EuclideanDistanceTransformTest.java
git commit -m "feat(v2): implement linear-time Meijster Euclidean Distance Transform O(N)"
```

---

### Task 3: Filtre Maximum Séparable $O(N)$ Lemire (`LocalMaxFilter`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/LocalMaxFilter.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/LocalMaxFilterTest.java`

- [ ] **Step 1: Écrire le test unitaire pour le filtre max séparable 1D glissant**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests du filtre maximum local séparable O(N)")
class LocalMaxFilterTest {

    @Test
    @DisplayName("Propagation du maximum local dans une fenêtre glissante")
    void testSlidingWindowMaximum() {
        int width = 10;
        int height = 10;
        DistanceMap input = new DistanceMap(width, height, new float[width * height]);
        BinaryMask road = new BinaryMask(width, height);
        // Placer un pic isolé à (5, 5) avec valeur 8.0f
        input.set(5, 5, 8.0f);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                road.set(x, y, true);
            }
        }

        LocalMaxFilter filter = new LocalMaxFilter();
        // Fenêtre de taille 5 (k = 2 pixels de rayon)
        DistanceMap result = filter.filter(input, road, 5);

        // Les pixels dans le voisinage de rayon 2 doivent valoir 8.0f
        assertEquals(8.0f, result.get(5, 5), 0.001f);
        assertEquals(8.0f, result.get(3, 5), 0.001f);
        assertEquals(8.0f, result.get(7, 5), 0.001f);
        // Au-delà de rayon 2, la valeur redevient 0.0f
        assertEquals(0.0f, result.get(2, 5), 0.001f);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=LocalMaxFilterTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `LocalMaxFilter` avec file monotone 1D séparable**

Implémenter l'algorithme glissant 1D de Lemire en deux passes horizontales et verticales indépendantes garantissant $O(W \times H)$ avec décomposition en sous-méthodes privées.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=LocalMaxFilterTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/LocalMaxFilter.java src/test/java/com/sam102022/photoshop/v2/expansion/LocalMaxFilterTest.java
git commit -m "feat(v2): implement separable O(N) 2D local maximum filter with monotonic queue"
```

---

### Task 4: Propagateur Géodésique Bounded Dijkstra (`BoundedGeodesicExpander`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/BoundedGeodesicExpander.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/BoundedGeodesicExpanderTest.java`

- [ ] **Step 1: Écrire le test unitaire pour l'arrêt strict aux carrefours et sur les bords**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests de l'expansion géodésique Dijkstra bornée")
class BoundedGeodesicExpanderTest {

    @Test
    @DisplayName("L'expansion couvre la chaussée limitrophe mais s'arrête net sans fuiter dans une rue transversale")
    void testExpansionStopsAtPerpendicularStreet() {
        int width = 50;
        int height = 50;
        BinaryMask roadMask = new BinaryMask(width, height);
        // Route frontière horizontale (y = 20 à 25)
        for (int y = 20; y <= 25; y++) {
            for (int x = 0; x < width; x++) roadMask.set(x, y, true);
        }
        // Rue transversale montante vers le haut (x = 24 à 26, y = 0 à 19)
        for (int y = 0; y < 20; y++) {
            for (int x = 24; x <= 26; x++) roadMask.set(x, y, true);
        }

        // Cellule intérieure T située sous la route (y = 26 à 40)
        BinaryMask retainedMask = new BinaryMask(width, height);
        for (int y = 26; y < 40; y++) {
            for (int x = 0; x < width; x++) retainedMask.set(x, y, true);
        }

        DistanceMap maxDist = new DistanceMap(width, height, new float[width * height]);
        // Demi-largeur de 3 px partout sur la route
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (roadMask.get(x, y)) maxDist.set(x, y, 3.0f);
            }
        }

        BoundedGeodesicExpander expander = new BoundedGeodesicExpander();
        ExpansionConfig config = ExpansionConfig.defaultTerritory(); // eps = 4.0 -> borne = 2 * 3 + 4 = 10 px
        BinaryMask ext = expander.expand(retainedMask, roadMask, maxDist, config);

        // La chaussée horizontale mitoyenne (y = 20 à 25) doit être entièrement absorbée
        assertTrue(ext.get(10, 22), "La chaussée mitoyenne doit être couverte");
        assertTrue(ext.get(10, 20), "Le bord externe de la chaussée doit être atteint");

        // La rue transversale lointaine (y = 5) ne doit PAS être absorbée (arrêt géodésique à 10 px)
        assertFalse(ext.get(25, 5), "La rue transversale extérieure ne doit pas être envahie");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=BoundedGeodesicExpanderTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `BoundedGeodesicExpander`**

Implémenter l'algorithme de propagation MCP 8-connexe à double métrique ($1.0$ cardinal, $\sqrt{2}$ diagonal) initialisé par dilatation 2-steps de $T$, avec arrêt à $2 \times hw_{\max} + \varepsilon$.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=BoundedGeodesicExpanderTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/BoundedGeodesicExpander.java src/test/java/com/sam102022/photoshop/v2/expansion/BoundedGeodesicExpanderTest.java
git commit -m "feat(v2): implement 8-connected Dijkstra bounded geodesic road expander"
```

---

### Task 5: Régularisation Morphologique avec Sanctuarisation (`MorphologicalConsolidator`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/MorphologicalConsolidator.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/MorphologicalConsolidatorTest.java`

- [ ] **Step 1: Écrire le test unitaire validant l'ouverture circulaire et la sanctuarisation de $T$**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du consolidateur morphologique avec sanctuarisation de T")
class MorphologicalConsolidatorTest {

    @Test
    @DisplayName("L'ouverture lisse les excroissances de l'extension sans altérer les coins authentiques de T")
    void testInteriorSanctuaryDuringOpening() {
        int width = 40;
        int height = 40;
        BinaryMask t = new BinaryMask(width, height);
        // T a un coin vif carré à (10, 10)
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) t.set(x, y, true);
        }

        BinaryMask ext = new BinaryMask(width, height);
        // Excroissance étroite de 1 pixel sur le côté
        ext.set(30, 20, true);

        MorphologicalConsolidator consolidator = new MorphologicalConsolidator();
        BinaryMask result = consolidator.consolidate(t, ext, 5);

        // Le coin vif intérieur de T est préservé à 100%
        assertTrue(result.get(10, 10), "Le coin vif de T doit être sanctuarisé");
        assertTrue(result.get(10, 29));
        // T est préservé partout où t.get(x, y) est vrai
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) {
                assertTrue(result.get(x, y));
            }
        }
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=MorphologicalConsolidatorTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `MorphologicalConsolidator`**

Implémenter l'union $U = T \cup ext$, le padding $\rho + 2 = 7\text{ px}$, l'ouverture par disque de rayon $\rho = 5$ (érosion puis dilatation avec masque circulaire euclidien), et la réinjection finale $M_c = T \cup \text{open}(U, B)$.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=MorphologicalConsolidatorTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/MorphologicalConsolidator.java src/test/java/com/sam102022/photoshop/v2/expansion/MorphologicalConsolidatorTest.java
git commit -m "feat(v2): implement morphological opening with interior sanctuary Mc = T | open(U, disk)"
```

---

### Task 6: Détecteur & Combleur de Trous Résiduels (`ResidualHoleResolver`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/ResidualHoleResolver.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/ResidualHoleResolverTest.java`

- [ ] **Step 1: Écrire le test unitaire pour le comblement sélectif des îlots compacts**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du comblement sélectif des îlots résiduels")
class ResidualHoleResolverTest {

    @Test
    @DisplayName("Comble un petit trou compact <= 15000 px mais préserve une enclave géante > 15000 px")
    void testSelectiveHoleFilling() {
        int width = 200;
        int height = 200;
        BinaryMask mask = new BinaryMask(width, height);
        // Remplir l'ensemble sauf les bords
        for (int y = 5; y < 195; y++) {
            for (int x = 5; x < 195; x++) mask.set(x, y, true);
        }
        // Créer un petit trou fermé de 10x10 = 100 pixels (centre à 50, 50)
        for (int y = 45; y < 55; y++) {
            for (int x = 45; x < 55; x++) mask.set(x, y, false);
        }
        // Créer un grand trou fermé de 130x130 = 16 900 pixels (> 15 000 px)
        for (int y = 60; y < 190; y++) {
            for (int x = 60; x < 190; x++) mask.set(x, y, false);
        }

        ResidualHoleResolver resolver = new ResidualHoleResolver();
        BinaryMask filled = resolver.fillCompactHoles(mask, 15000L);

        // Le petit trou (100 px) doit être comblé
        assertTrue(filled.get(50, 50), "Le petit trou compact doit être comblé");
        // Le grand trou (16 900 px) doit être conservé intact
        assertFalse(filled.get(100, 100), "Le grand trou > 15 000 px ne doit pas être bouché");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=ResidualHoleResolverTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `ResidualHoleResolver`**

Implémenter l'inversion de masque, l'inondation BFS depuis les 4 bords pour marquer l'extérieur infini, le marquage en composantes connexes des trous résiduels, et le comblement sélectif des composantes dont la surface est $\le \text{maxHoleArea}$.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=ResidualHoleResolverTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/ResidualHoleResolver.java src/test/java/com/sam102022/photoshop/v2/expansion/ResidualHoleResolverTest.java
git commit -m "feat(v2): implement selective compact residual hole resolver <= 15000 px"
```

---

### Task 7: Partageur Médian de Frontières en Mode ZONE (`BoundaryRoadPartitioner`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/BoundaryRoadPartitioner.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/BoundaryRoadPartitionerTest.java`

- [ ] **Step 1: Écrire le test unitaire pour le partage équitable de chaussée mitoyenne**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du partitionneur médian de chaussée en mode ZONE")
class BoundaryRoadPartitionerTest {

    @Test
    @DisplayName("Partitionne équitablement une route entre Zone 1 et Zone 2 sans aucun chevauchement")
    void testEquitablePartitionWithoutOverlap() {
        int width = 30;
        int height = 10;
        // Route horizontale entre y = 3 et y = 6 (4 pixels de haut : 3, 4, 5, 6)
        BinaryMask road = new BinaryMask(width, height);
        for (int y = 3; y <= 6; y++) {
            for (int x = 0; x < width; x++) road.set(x, y, true);
        }

        // Zone 1 située au-dessus (y < 3)
        BinaryMask zone1 = new BinaryMask(width, height);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < width; x++) zone1.set(x, y, true);
        }

        // Zone 2 située en dessous (y > 6)
        BinaryMask zone2 = new BinaryMask(width, height);
        for (int y = 7; y < height; y++) {
            for (int x = 0; x < width; x++) zone2.set(x, y, true);
        }

        BoundaryRoadPartitioner partitioner = new BoundaryRoadPartitioner();
        BinaryMask allocatedToZone1 = partitioner.partition(road, zone1, zone2);

        // Les lignes les plus proches de Zone 1 (y = 3 et 4) sont attribuées à Zone 1
        assertTrue(allocatedToZone1.get(5, 3));
        // Les lignes les plus proches de Zone 2 (y = 5 et 6) ne sont PAS attribuées à Zone 1
        assertFalse(allocatedToZone1.get(5, 6));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=BoundaryRoadPartitionerTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `BoundaryRoadPartitioner`**

Implémenter le double front BFS / Dijkstra géodésique attribuant chaque pixel routier à la zone la plus proche et garantissant l'invariance stricte de non-recouvrement.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=BoundaryRoadPartitionerTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/BoundaryRoadPartitioner.java src/test/java/com/sam102022/photoshop/v2/expansion/BoundaryRoadPartitionerTest.java
git commit -m "feat(v2): implement geodesic Voronoi boundary road partitioner for ZONE mode"
```

---

### Task 8: Orchestrateur Sprint 5 (`RoadBoundaryConsolidator`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/expansion/RoadBoundaryConsolidator.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/expansion/RoadBoundaryConsolidatorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour l'orchestrateur de haut niveau**

```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Tests de l'orchestrateur RoadBoundaryConsolidator")
class RoadBoundaryConsolidatorTest {

    @Test
    @DisplayName("Exécute la chaîne complète et retourne un ConsolidatedMask cohérent")
    void testEndToEndConsolidation() {
        int width = 50;
        int height = 50;
        BinaryMask retained = new BinaryMask(width, height);
        for (int y = 15; y < 35; y++) {
            for (int x = 15; x < 35; x++) retained.set(x, y, true);
        }
        BinaryMask road = new BinaryMask(width, height);
        for (int y = 10; y <= 14; y++) {
            for (int x = 10; x < 40; x++) road.set(x, y, true);
        }
        CropWindow crop = new CropWindow(100, 200, width, height);

        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask result = consolidator.consolidate(retained, road, crop, ExpansionConfig.defaultTerritory());

        assertNotNull(result);
        assertEquals(width, result.width());
        assertEquals(height, result.height());
        assertEquals(100, result.offsetX());
        assertEquals(200, result.offsetY());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=RoadBoundaryConsolidatorTest`  
Expected: FAIL.

- [ ] **Step 3: Implémenter `RoadBoundaryConsolidator`**

Assembler la chaîne complète (EDT $\rightarrow$ MaxFilter $\rightarrow$ BoundedGeodesicExpander $\rightarrow$ MorphologicalConsolidator $\rightarrow$ ResidualHoleResolver) et encapsuler dans `ConsolidatedMask`.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=RoadBoundaryConsolidatorTest`  
Expected: PASS.

- [ ] **Step 5: Git commit**

```bash
git add src/main/java/com/sam102022/photoshop/v2/expansion/RoadBoundaryConsolidator.java src/test/java/com/sam102022/photoshop/v2/expansion/RoadBoundaryConsolidatorTest.java
git commit -m "feat(v2): implement RoadBoundaryConsolidator orchestrating Sprint 5 pipeline"
```

---

### Task 9: Test d'Intégration Pivot CA01 (`Sprint5IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/expansion/Sprint5IntegrationTest.java`

- [ ] **Step 1: Écrire le test d'intégration complet sur les fixtures réelles de CA01**

```java
package com.sam102022.photoshop.v2.expansion;

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
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.TopologicalVotingEngine;
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

@DisplayName("Test d'intégration Sprint 5 : Expansion Géodésique & ConsolidatedMask CA01")
class Sprint5IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprint 5 sur CA01 : surface cible de 1 215 383 px et budget < 1500 ms")
    void testEndToEndSprint5OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(jsonPath) && Files.exists(roadPath));

        long startTime = System.currentTimeMillis();

        // 1. Chargement & rasterisation du polygone P (Sprint 1)
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());
        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();
        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(polygonPoints, loaded.mapContext().width(), loaded.mapContext().height());

        // 2. Chargement du masque de routes (Sprint 2)
        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        BinaryMask fullRoad = new BinaryMask(roadImg.getWidth(), roadImg.getHeight());
        for (int y = 0; y < roadImg.getHeight(); y++) {
            for (int x = 0; x < roadImg.getWidth(); x++) {
                if (((roadImg.getRGB(x, y) >>> 16) & 0xFF) > 127) fullRoad.set(x, y, true);
            }
        }

        // 3. Recadrage & segmentation des 130 cellules (Sprint 3)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, fullRoad.getWidth(), fullRoad.getHeight(), 90);
        BinaryMask croppedRoad = cropper.crop(fullRoad, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);
        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        assertEquals(130, labelingResult.cells().size());

        // 4. Vote topologique et masque retenu T (Sprint 4)
        TopologicalVotingEngine votingEngine = new TopologicalVotingEngine();
        CellSelection selection = votingEngine.vote(labelingResult, croppedPoly);
        assertNotNull(selection.retainedMask());

        // 5. Exécution du Sprint 5 : Expansion géodésique & consolidation
        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                ExpansionConfig.defaultTerritory()
        );

        long elapsed = System.currentTimeMillis() - startTime;
        assertNotNull(consolidated);

        // Validation de la surface consolidée (cible Python : 1 215 383 px à +/- 0.5%)
        long area = consolidated.mask().countSetPixels();
        double matchRate = 1.0 - Math.abs(area - 1_215_383L) / 1_215_383.0;
        System.out.printf("Sprint 5 exécuté en %d ms. Surface Mc = %d px (concordance étalon Python : %.3f%%)%n",
                elapsed, area, matchRate * 100.0);

        assertTrue(matchRate >= 0.995, "Le taux de concordance avec l'étalon Python doit être >= 99.5%");
        assertTrue(elapsed < 1500, "Le temps de calcul global doit être inférieur à 1500 ms");
    }
}
```

- [ ] **Step 2: Exécuter le test d'intégration pour valider le pipeline complet**

Run: `mvn test -Dtest=Sprint5IntegrationTest`  
Expected: PASS (Concordance $\ge 99.5\%$, exécution $\le 1500\text{ ms}$).

- [ ] **Step 3: Git commit**

```bash
git add src/test/java/com/sam102022/photoshop/v2/expansion/Sprint5IntegrationTest.java
git commit -m "test(v2): add end-to-end integration test for Sprint 5 geodesic consolidation on CA01"
```

---

### Task 10: Validation de Non-Régression Globale & Journalisation

**Files:**
- Modify: `JOURNAL.md`
- Modify: `docs/SPRINTS_V2.md`

- [ ] **Step 1: Exécuter l'ensemble de la suite de tests unitaires et d'intégration V2**

Run: `mvn test -Dtest="com.sam102022.photoshop.v2.**.*Test"`  
Expected: 100% de succès.

- [ ] **Step 2: Mettre à jour `docs/SPRINTS_V2.md`**

Passer le statut du Sprint 5 à validé avec liens vers les spécifications et les résultats mesurés.

- [ ] **Step 3: Mettre à jour `JOURNAL.md`**

Consigner la formalisation de la spécification et du plan d'implémentation du Sprint 5.

- [ ] **Step 4: Git commit**

```bash
git add docs/SPRINTS_V2.md JOURNAL.md
git commit -m "docs(v2): finalize Sprint 5 geodesic expansion specification and implementation plan"
```
