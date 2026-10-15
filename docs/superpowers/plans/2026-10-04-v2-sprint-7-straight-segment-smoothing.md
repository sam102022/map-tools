# V2 Sprint 7 — Lissage Adaptatif Multi-Échelle des Tronçons Droits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Implémenter le lissage LQR multi-échelle adaptatif du Sprint 7 pour redresser les longues avenues et supprimer les encoches d'icônes cartographiques tout en préservant les virages réels, conformément à l'étape 7 de `snap_cells_prototype_v7.py`.

**Architecture:** Découpage en sous-modules respectant le principe de responsabilité unique (ADR-008). Extension de `ContourSmoothingConfig` avec les hyperparamètres de rectitude, exposition de la résolution de résidus dans `RobustLqrSmoother`, création du composant dédié `StraightSegmentSmoother` calculant le double LQR, la pondération de rectitude et la transition $C^1$ par convolution boîte, puis intégration dans la façade `ContourSmoothingEngine`.

**Tech Stack:** Java 21 standard, JUnit 5, AssertJ, Java2D (100% Java sans dépendances natives, ADR-001).

---

## Structure des Fichiers

| Rôle | Fichier | Action |
| :--- | :--- | :--- |
| **Config** | `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingConfig.java` | Modifier (ajout hyperparamètres) |
| **Test Config** | `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java` | Modifier (validation des champs) |
| **LQR Residuals** | `src/main/java/com/sam102022/photoshop/v2/contour/RobustLqrSmoother.java` | Modifier (exposition `smoothResiduals`) |
| **Test LQR** | `src/test/java/com/sam102022/photoshop/v2/contour/RobustLqrSmootherTest.java` | Modifier (test unitaire de `smoothResiduals`) |
| **Composant SRP** | `src/main/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmoother.java` | Créer |
| **Test SRP** | `src/test/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmootherTest.java` | Créer |
| **Façade** | `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java` | Modifier (intégration du lisseur) |
| **Test Façade** | `src/test/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngineTest.java` | Modifier (vérification d'orchestration) |
| **Intégration Pivot** | `src/test/java/com/sam102022/photoshop/v2/contour/Sprint7IntegrationTest.java` | Créer (test CA01 vs Python V7) |

---

### Task 1: Enrichissement de `ContourSmoothingConfig` avec les Paramètres de Rectitude

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingConfig.java`
- Modify: `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java`

- [x] **Step 1: Écrire le test unitaire pour les nouveaux hyperparamètres**

Dans `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java`, ajouter la vérification des champs `straightFactor`, `straightScale`, `straightThreshold`, `straightTransitionK` et de leur validation :

```java
    @Test
    void testContourSmoothingConfigStraightParameters() {
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        assertThat(config.straightFactor()).isEqualTo(2.7);
        assertThat(config.straightScale()).isEqualTo(3.0);
        assertThat(config.straightThreshold()).isEqualTo(3.0);
        assertThat(config.straightTransitionK()).isEqualTo(10);
    }

    @Test
    void testContourSmoothingConfigStraightValidation() {
        assertThatThrownBy(() -> new ContourSmoothingConfig(
                1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0,
                0.5, 3.0, 3.0, 10
        )).isInstanceOf(IllegalArgumentException.class);
    }
```

- [x] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Erreur de compilation car le constructeur de `ContourSmoothingConfig` n'a pas encore ces paramètres.

- [x] **Step 3: Implémenter l'enrichissement dans `ContourSmoothingConfig`**

Mettre à jour `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingConfig.java` :
- Ajouter les 4 paramètres au record :
  - `double straightFactor` (doit être $\ge 1.0$)
  - `double straightScale` (doit être $> 0.0$)
  - `double straightThreshold` (doit être $> 0.0$)
  - `int straightTransitionK` (doit être $\ge 1$)
- Mettre à jour `defaultConfig()` avec `(1.0, 80, 38.0, 4.0, 22.0, 3.0, 6, 35.0, 95.0, 2.0, 2.7, 3.0, 3.0, 10)`.
- Ajouter la Javadoc exhaustive (ADR-014).

- [x] **Step 4: Mettre à jour les instanciations existantes et vérifier le succès des tests**

Mettre à jour les constructeurs appelés dans les tests existants et exécuter :  
`mvn test -Dtest=ContourDomainModelTest`  
Attendu : BUILD SUCCESS.

---

### Task 2: Exposition de la Résolution de Résidus dans `RobustLqrSmoother`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/RobustLqrSmoother.java`
- Modify: `src/test/java/com/sam102022/photoshop/v2/contour/RobustLqrSmootherTest.java`

- [x] **Step 1: Écrire le test unitaire pour `smoothResiduals`**

Dans `src/test/java/com/sam102022/photoshop/v2/contour/RobustLqrSmootherTest.java`, tester la méthode `smoothResiduals(double[] yX, double[] yY, double sigma, double scale, int iterations)` :

```java
    @Test
    void testSmoothResidualsPreservesZeroResiduals() {
        RobustLqrSmoother smoother = new RobustLqrSmoother();
        double[] zeros = new double[50];
        RobustLqrSmoother.LqrResidualResult result = smoother.smoothResiduals(zeros, zeros, 10.0, 3.0, 4);
        assertThat(result.fitX()).hasSize(50);
        assertThat(result.fitY()).hasSize(50);
        for (int i = 0; i < 50; i++) {
            assertThat(result.fitX()[i]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
            assertThat(result.fitY()[i]).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        }
    }
```

- [x] **Step 2: Vérifier l'échec du test**

Exécuter : `mvn test-compile`  
Attendu : Erreur de compilation car `LqrResidualResult` et `smoothResiduals` n'existent pas encore.

- [x] **Step 3: Implémenter `LqrResidualResult` et `smoothResiduals` dans `RobustLqrSmoother`**

Dans `RobustLqrSmoother.java` :
- Déclarer le record public :
  ```java
  public record LqrResidualResult(double[] fitX, double[] fitY) {}
  ```
- Implémenter la méthode publique :
  ```java
  public LqrResidualResult smoothResiduals(double[] yX, double[] yY, double sigma, double scale, int iterations) {
      int n = yX.length;
      int k = Math.max(1, (int) Math.floor(3.5 * sigma));
      double[] g = new double[2 * k + 1];
      for (int u = -k; u <= k; u++) {
          g[u + k] = Math.exp(-0.5 * (u / sigma) * (u / sigma));
      }
      double[] fitX = solveLqrIterations(yX, yY, g, k, n, scale, iterations, 0);
      double[] fitY = solveLqrIterations(yX, yY, g, k, n, scale, iterations, 1);
      return new LqrResidualResult(fitX, fitY);
  }
  ```
- Refactorer `smoothSegment` pour réutiliser `smoothResiduals`.
- Documenter exhaustivement en Javadoc (ADR-014).

- [x] **Step 4: Exécuter les tests du lisseur LQR**

Exécuter : `mvn test -Dtest=RobustLqrSmootherTest`  
Attendu : BUILD SUCCESS.

---

### Task 3: Création et Validation Unitaire de `StraightSegmentSmoother`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmoother.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmootherTest.java`

- [x] **Step 1: Écrire les tests unitaires pour `StraightSegmentSmoother`**

Créer `src/test/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmootherTest.java` :
1. `testStraightLineWithNotchIsStraightened()` :
   Créer un segment rectiligne horizontal de 120 points avec une encoche locale de 4 pixels de haut sur 6 points au milieu (simulant une icône).
   Vérifier qu'avec `StraightSegmentSmoother`, la déviation résiduelle maximale est $< 0.5\text{ px}$ (l'encoche est absorbée).
2. `testPronouncedCurvePreservesCurvature()` :
   Créer un arc de cercle (rayon 50 px, 100 points).
   Vérifier que le poids de rectitude $w_{st}$ moyen est proche de 0 et que la déviation entre le lissage simple et le lissage adaptatif reste minime ($< 0.3\text{ px}$).
3. `testShortSegmentReturnsUnchanged()` :
   Segment de longueur $< 2.5 \sigma \to$ retourné intact.

- [x] **Step 2: Vérifier l'échec des tests**

Exécuter : `mvn test-compile`  
Attendu : Échec car la classe n'existe pas.

- [x] **Step 3: Implémenter `StraightSegmentSmoother`**

Créer `src/main/java/com/sam102022/photoshop/v2/contour/StraightSegmentSmoother.java` :
- Méthode `public List<PixelPoint> smoothContour(List<PixelPoint> contour, List<Integer> cornerIndices, ContourSmoothingConfig config)` : partitionne le contour fermé entre les coins et traite chaque segment.
- Méthode `public List<PixelPoint> smoothSegment(List<PixelPoint> segment, ContourSmoothingConfig config)` :
  - Si `n < 2.5 * sigma`, retourner `segment`.
  - Calculer la corde directrice $P_0 \to P_1$ et les résidus `yX, yY`.
  - Calculer `sm_a = lqrSmoother.smoothResiduals(yX, yY, sigma, scale, iters)`.
  - Calculer `sm_b = lqrSmoother.smoothResiduals(yX, yY, straightFactor * sigma, straightScale, 8)`.
  - Calculer $\Delta(t) = \sqrt{(sm_b.x - sm_a.x)^2 + (sm_b.y - sm_a.y)^2}$.
  - Calculer $w_{\text{raw}}(t) = \text{clamp}\left(\frac{ST\_T - \Delta(t)}{ST\_T / 2.0}, 0.0, 1.0\right)$.
  - Appliquer `boxFilterWithEdgePadding(w_raw, k)` avec $k = 10$.
  - Combiner : $\mathbf{sm}(t) = \mathbf{sm}_a(t) + w_{st}(t) \cdot (\mathbf{sm}_b(t) - \mathbf{sm}_a(t))$.
  - Reconstituer les `PixelPoint(baseX + sm.x, baseY + sm.y)`.
- Respecter scrupuleusement la complexité cognitive $\le 15$ (ADR-011) en découpant en sous-méthodes privées :
  - `computeBaselineAndResiduals(...)`
  - `computeStraightnessWeights(...)`
  - `applyBoxFilterWithEdgePadding(...)`
  - `assembleSmoothedPoints(...)`
- Javadoc intégrale en français (ADR-014).

- [x] **Step 4: Exécuter les tests unitaires**

Exécuter : `mvn test -Dtest=StraightSegmentSmootherTest`  
Attendu : BUILD SUCCESS (3/3 tests passants).

---

### Task 4: Intégration de `StraightSegmentSmoother` dans `ContourSmoothingEngine`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java`
- Modify: `src/test/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngineTest.java`

- [x] **Step 1: Modifier le test de façade `ContourSmoothingEngineTest`**

Vérifier que `ContourSmoothingEngine` utilise la configuration multi-échelle sans régression.

- [x] **Step 2: Intégrer `StraightSegmentSmoother` dans `ContourSmoothingEngine`**

Remplacer l'appel direct de `lqrSmoother.smoothContour(...)` par :
```java
List<PixelPoint> lqrSmoothed = straightSmoother.smoothContour(
        rawContour,
        initialCorners,
        config
);
```
Tout en conservant les étapes 1, 2, 4, 5, 6, 7 intactes.

- [x] **Step 3: Exécuter les tests du package contour**

Exécuter : `mvn test -Dtest="com.sam102022.photoshop.v2.contour.*Test"`  
Attendu : Tous les tests passent avec succès.

---

### Task 5: Test d'Intégration Pivot CA01 (`Sprint7IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/contour/Sprint7IntegrationTest.java`

- [x] **Step 1: Écrire le test d'intégration `Sprint7IntegrationTest`**

Créer le test d'intégration pivot étalon :
- Charger `01_plan_avec_territoires.json` et `05_style_contraste_sans_rien.png` de CA01.
- Exécuter la chaîne Sprints 1 à 6 + Sprint 7.
- Vérifier :
  1. Nombre de points du contour vectoriel final $\sim 5200\text{ points}$ ;
  2. Nombre de coins majeurs préservés $\ge 5$ ;
  3. Temps d'exécution total du lissage multi-échelle $\le 500\text{ ms}$ ;
  4. Concordance géométrique avec `contour_smooth.npy` de Python V7 (distance de Hausdorff $\le 2.0\text{ px}$ et RMS $\le 0.5\text{ px}$).

- [x] **Step 2: Exécuter le test d'intégration**

Exécuter : `mvn test -Dtest=Sprint7IntegrationTest`  
Attendu : BUILD SUCCESS.

- [x] **Step 3: Validation globale de non-régression V2**

Exécuter : `mvn test -Dtest="com.sam102022.photoshop.v2.**"`  
Attendu : 100% des tests V2 réussis sans avertissement.
