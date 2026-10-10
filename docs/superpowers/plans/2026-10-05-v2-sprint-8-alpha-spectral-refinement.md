# V2 Sprint 8 — Affinage Spectral de la Couverture Alpha & Protection des Ronds-Points Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter le module d'affinage spectral du Sprint 8 (`com.sam102022.photoshop.v2.refine`) pour éliminer les ~28 893 pixels blancs résiduels en lisière de route tout en sanctuarisant les carrefours giratoires par modulation smoothstep cubique, conformément à l'étape 9 de `snap_cells_prototype_v7.py`.

**Architecture:** Découpage modulaire strict conforme à l'ADR-008 (SRP) et aux ADR-001, ADR-002, ADR-004, ADR-011, ADR-014. Extension de la traçabilité des ronds-points substitués dans `Roundabout`, `HermiteSplineConnector` et `SmoothVectorContour`. Création des records immuables `AlphaRefinementConfig` et `AlphaRefinementMap`. Implémentation des sous-composants `AllowedRegionBuilder`, `SoftThresholdFilter`, `RoundaboutExemptionModulator` et de la façade d'orchestration `AlphaRefiner`. Validation unitaire TDD systématique et test d'intégration pivot étalon sur CA01.

**Tech Stack:** Java 21 standard, JUnit 5, AssertJ, Java2D (100% Java sans dépendances natives, ADR-001).

---

## Structure des Fichiers

| Rôle | Fichier | Action |
| :--- | :--- | :--- |
| **Traçabilité Giratoire** | `src/main/java/com/sam102022/photoshop/v2/contour/Roundabout.java` | Modifier (ajout `cellId` avec rétrocompatibilité) |
| **Détecteur Giratoire** | `src/main/java/com/sam102022/photoshop/v2/contour/RoundaboutDetector.java` | Modifier (fourniture du `cell.id()`) |
| **Connecteur Tangentiel** | `src/main/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnector.java` | Modifier (mémorisation des ronds-points substitués) |
| **Modèle Contour** | `src/main/java/com/sam102022/photoshop/v2/contour/SmoothVectorContour.java` | Modifier (ajout `substitutedRoundabouts` avec rétrocompatibilité) |
| **Moteur Lissage** | `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java` | Modifier (propagation des ronds-points substitués) |
| **Test Traçabilité** | `src/test/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnectorTest.java` | Modifier (vérification des ronds-points substitués) |
| **Records Domaine** | `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementConfig.java` | Créer |
| **Records Domaine** | `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementMap.java` | Créer |
| **Test Records** | `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinementModelTest.java` | Créer |
| **Composant Région** | `src/main/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilder.java` | Créer |
| **Test Région** | `src/test/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilderTest.java` | Créer |
| **Composant Filtre** | `src/main/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilter.java` | Créer |
| **Test Filtre** | `src/test/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilterTest.java` | Créer |
| **Composant Modulateur** | `src/main/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulator.java` | Créer |
| **Test Modulateur** | `src/test/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulatorTest.java` | Créer |
| **Façade Orchestration** | `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefiner.java` | Créer |
| **Test Façade** | `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinerTest.java` | Créer |
| **Intégration Pivot CA01** | `src/test/java/com/sam102022/photoshop/v2/refine/Sprint8IntegrationTest.java` | Créer |
| **Documentation & Suivi** | `docs/sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md`, `JOURNAL.md`, `CHANGELOG.md` | Modifier |

---

### Task 1: Traçabilité des Ronds-Points Substitués dans le Contour Vectoriel

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/Roundabout.java`
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/RoundaboutDetector.java`
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnector.java`
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/SmoothVectorContour.java`
- Modify: `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java`
- Modify: `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java`
- Modify: `src/test/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnectorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `cellId` dans `Roundabout` et `substitutedRoundabouts` dans `SmoothVectorContour`**

Dans `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java`, ajouter les tests de validation de `cellId` sur `Roundabout` et de la liste des ronds-points substitués sur `SmoothVectorContour` :

```java
    @Test
    void testRoundaboutWithCellIdAndBackwardCompatibility() {
        EllipseModel ell = new EllipseModel(50.0, 50.0, 20.0, 15.0, 0.0);
        Roundabout rb1 = new Roundabout(42, new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertThat(rb1.cellId()).isEqualTo(42);

        // Constructeur rétrocompatible sans cellId (cellId vaut -1 par défaut)
        Roundabout rb2 = new Roundabout(new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertThat(rb2.cellId()).isEqualTo(-1);
    }

    @Test
    void testSmoothVectorContourCarriesSubstitutedRoundabouts() {
        CropWindow crop = new CropWindow(0, 0, 100, 100);
        EllipseModel ell = new EllipseModel(50.0, 50.0, 20.0, 15.0, 0.0);
        Roundabout rb = new Roundabout(7, new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);

        SmoothVectorContour contourWithRb = new SmoothVectorContour(
                List.of(new PixelPoint(0, 0), new PixelPoint(10, 0)),
                List.of(0),
                100, 100, crop, List.of(rb)
        );
        assertThat(contourWithRb.substitutedRoundabouts()).containsExactly(rb);

        // Constructeur rétrocompatible
        SmoothVectorContour contourDefault = new SmoothVectorContour(
                List.of(new PixelPoint(0, 0)), List.of(0), 100, 100, crop
        );
        assertThat(contourDefault.substitutedRoundabouts()).isEmpty();
    }
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`cannot find symbol method cellId()`, `wrong number of arguments for SmoothVectorContour`).

- [ ] **Step 3: Mettre à jour `Roundabout` et `SmoothVectorContour`**

Dans `src/main/java/com/sam102022/photoshop/v2/contour/Roundabout.java` :
- Passer le record canonique à :
  ```java
  public record Roundabout(
          int cellId,
          PixelPoint center,
          EllipseModel exteriorEllipse,
          double islandArea,
          double inlierRatio
  ) {
      public Roundabout(PixelPoint center, EllipseModel exteriorEllipse, double islandArea, double inlierRatio) {
          this(-1, center, exteriorEllipse, islandArea, inlierRatio);
      }
      // validations existantes...
  }
  ```
- Compléter la Javadoc en français (ADR-014).

Dans `src/main/java/com/sam102022/photoshop/v2/contour/SmoothVectorContour.java` :
- Passer le record canonique à :
  ```java
  public record SmoothVectorContour(
          List<PixelPoint> points,
          List<Integer> cornerIndices,
          int width,
          int height,
          CropWindow cropWindow,
          List<Roundabout> substitutedRoundabouts
  ) {
      public SmoothVectorContour(List<PixelPoint> points, List<Integer> cornerIndices,
                                 int width, int height, CropWindow cropWindow) {
          this(points, cornerIndices, width, height, cropWindow, List.of());
      }
      // List.copyOf défensif pour substitutedRoundabouts
  }
  ```

Dans `src/main/java/com/sam102022/photoshop/v2/contour/RoundaboutDetector.java` :
- Lors de l'instanciation de `Roundabout` à la ligne 76 :
  `new Roundabout(cell.id(), new PixelPoint(cx, cy), ring.ellipse(), cell.area(), ring.inlierRatio())`

Dans `src/main/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnector.java` :
- Créer un record de retour :
  ```java
  public record RoundaboutSubstitutionResult(
          List<PixelPoint> contour,
          List<Roundabout> substitutedRoundabouts
  ) {}
  ```
- Ajouter la méthode :
  ```java
  public RoundaboutSubstitutionResult integrateRoundaboutsWithTracking(
          List<PixelPoint> contour, List<Roundabout> roundabouts,
          BinaryMask territoryMask, double roundaboutZr)
  ```
  qui conserve la liste `List<Roundabout> substituted = new ArrayList<>()` des ronds-points pour lesquels un arc est substitué.
- Faire déléguer `integrateRoundabouts(...)` à cette méthode en renvoyant `result.contour()`.

Dans `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java` :
- Utiliser `integrateRoundaboutsWithTracking` pour récupérer à la fois les points finaux et les ronds-points substitués, et les injecter dans le `SmoothVectorContour` retourné.

- [ ] **Step 4: Exécuter les tests unitaires et de non-régression**

Exécuter : `mvn test -Dtest=ContourDomainModelTest,HermiteSplineConnectorTest,Sprint7IntegrationTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/contour/Roundabout.java src/main/java/com/sam102022/photoshop/v2/contour/RoundaboutDetector.java src/main/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnector.java src/main/java/com/sam102022/photoshop/v2/contour/SmoothVectorContour.java src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java src/test/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnectorTest.java; git commit -m "feat(v2): track substituted roundabouts in SmoothVectorContour and Roundabout"
```

---

### Task 2: Modèles de Domaine du Sprint 8 (`AlphaRefinementConfig` & `AlphaRefinementMap`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementConfig.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementMap.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinementModelTest.java`

- [ ] **Step 1: Écrire le test unitaire pour les records de domaine**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinementModelTest.java` :
- Tester les valeurs par défaut de `AlphaRefinementConfig.defaultConfig()` :
  - `gaussianBlurSigma` == 1.4
  - `contrastStiffness` == 2.0
  - `roundaboutBufferInner` == 2.3
  - `roundaboutBufferOuter` == 3.0
  - `roadHoleMaxArea` == 2000
- Tester les validations de `AlphaRefinementConfig` (sigma <= 0, stiffness < 1.0, bufferInner <= 0, bufferOuter <= bufferInner, roadHoleMaxArea < 0).
- Tester `AlphaRefinementMap` : immutabilité, dimensions, méthode `factorAt(x, y)` avec clamp/vérification de bornes.

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`AlphaRefinementConfig` et `AlphaRefinementMap` introuvables).

- [ ] **Step 3: Implémenter `AlphaRefinementConfig` et `AlphaRefinementMap`**

Créer `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementConfig.java` :
- Record immuable (ADR-004).
- Constructeur canonique avec validations explicites.
- Méthode statique d'usine `defaultConfig()`.
- Javadoc complète en français (ADR-014).

Créer `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementMap.java` :
- Record immuable :
  ```java
  public record AlphaRefinementMap(
          int width,
          int height,
          float[][] factor,
          CropWindow cropWindow
  ) {
      public AlphaRefinementMap {
          // validations dimensions > 0, factor non null, dimensions cohérentes, clone défensif des lignes
      }
      public float factorAt(int x, int y) {
          if (x < 0 || x >= width || y < 0 || y >= height) {
              return 0.0f;
          }
          return factor[y][x];
      }
  }
  ```
- Javadoc complète en français (ADR-014).

- [ ] **Step 4: Exécuter les tests du modèle**

Exécuter : `mvn test -Dtest=AlphaRefinementModelTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementConfig.java src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefinementMap.java src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinementModelTest.java; git commit -m "feat(v2): implement AlphaRefinementConfig and AlphaRefinementMap domain models"
```

---

### Task 3: Composant `AllowedRegionBuilder` (Union Binaire & Comblement des Trous)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilder.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilderTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `AllowedRegionBuilder`**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilderTest.java` :
- Tester le cas nominal :
  - `retainedMask` (ou `consolidatedMask`) contenant un trou intérieur fermé de 100 pixels.
  - `roadMask` avec des chaussées bordières.
  - Un rond-point substitué dont l'îlot central a un identifiant de cellule.
  - Vérifier que $M_{\text{allowed}}$ englobe les routes, les cellules du territoire rempli et l'îlot du giratoire.
- Tester le seuillage de comblement :
  - Un trou de 50 pixels dans la route est comblé car $50 < 2000$.
  - Un grand trou de 5000 pixels (extérieur ou cour) reste non comblé car $5000 \ge 2000$.

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`AllowedRegionBuilder` introuvable).

- [ ] **Step 3: Implémenter `AllowedRegionBuilder`**

Créer `src/main/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilder.java` :
- Injection du résolveur de cavités `ResidualHoleResolver` (ADR-008).
- Signature principale :
  ```java
  public BinaryMask build(BinaryMask consolidatedMask,
                          BinaryMask croppedRoad,
                          CellLabelMap labelMap,
                          List<Roundabout> substitutedRoundabouts,
                          long roadHoleMaxArea)
  ```
- Algorithme :
  1. $M_{\text{fill}} = \text{holeResolver.fillCompactHoles}(consolidatedMask, \text{Long.MAX\_VALUE})$ (remplit toutes les cavités intérieures du territoire).
  2. Création de $M_{\text{allowed}}$ initialisé avec $M_{\text{fill}} \lor croppedRoad$.
  3. Inclusion des pixels des cellules dont l'id correspond à un rond-point substitué :
     Pour chaque `Roundabout rb` avec `rb.cellId() > 0` : marquer dans $M_{\text{allowed}}$ les pixels où `labelMap.getLabel(x, y) == rb.cellId()`.
  4. Comblement sélectif des trous de chaussée :
     $\text{allowedFilled} = \text{holeResolver.fillCompactHoles}(M_{\text{allowed}}, \text{roadHoleMaxArea} - 1)$.
- Javadoc complète en français et complexité cognitive $\le 15$ (ADR-011, ADR-014).

- [ ] **Step 4: Exécuter les tests du composant**

Exécuter : `mvn test -Dtest=AllowedRegionBuilderTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilder.java src/test/java/com/sam102022/photoshop/v2/refine/AllowedRegionBuilderTest.java; git commit -m "feat(v2): implement AllowedRegionBuilder with road hole filling"
```

---

### Task 4: Composant `SoftThresholdFilter` (Flou Gaussien Séparable 2D & Seuillage Raide)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilter.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilterTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `SoftThresholdFilter`**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilterTest.java` :
- Tester sur un masque unitaire constant plein (tous pixels à 1) : le facteur doit être uniforme à 1.0f.
- Tester sur un masque vide (tous pixels à 0) : le facteur doit être uniforme à 0.0f.
- Tester sur une marche d'escalier (demi-plan vertical $x < 20 \implies 1, x \ge 20 \implies 0$) :
  - Vérifier la décroissance douce et monotone centrée sur la frontière.
  - Vérifier que la valeur en $x = 19.5$ est symétrique à 0.5f.
  - Vérifier que les valeurs restent strictement bridées dans $[0.0, 1.0]$.

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`SoftThresholdFilter` introuvable).

- [ ] **Step 3: Implémenter `SoftThresholdFilter`**

Créer `src/main/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilter.java` :
- Méthode principale :
  ```java
  public float[][] filter(BinaryMask allowedMask, double sigma, double stiffness)
  ```
- Construction du noyau gaussien 1D normalisé :
  $R = \max(1, (int) Math.floor(4.0 \times \sigma + 0.5))$.
  $K[i] = \exp\left(-\frac{i^2}{2 \sigma^2}\right)$, normalisé par la somme totale.
- Passe horizontale séparable avec gestion miroir aux bords (`reflectIndex(x, width)`).
- Passe verticale séparable avec gestion miroir aux bords (`reflectIndex(y, height)`).
- Application de la fonction de transfert point par point :
  $\text{factor}[y][x] = \text{clamp}\big((\text{blurred}[y][x] - 0.5f) \times (float) stiffness + 0.5f,\; 0.0f,\; 1.0f\big)$.
- Javadoc complète en français (ADR-014), méthodes privées $\le 15$ en complexité cognitive (ADR-011).

- [ ] **Step 4: Exécuter les tests du filtre**

Exécuter : `mvn test -Dtest=SoftThresholdFilterTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilter.java src/test/java/com/sam102022/photoshop/v2/refine/SoftThresholdFilterTest.java; git commit -m "feat(v2): implement 2D separable Gaussian blur and soft threshold filter"
```

---

### Task 5: Composant `RoundaboutExemptionModulator` (Smoothstep Elliptique)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulator.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulatorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `RoundaboutExemptionModulator`**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulatorTest.java` :
- Tester avec une liste vide de ronds-points : le facteur en sortie doit être identique au facteur d'entrée.
- Tester avec une ellipse centrée en $(50, 50)$ et demi-axes $a = 20, b = 20$ (cercle) :
  - Pour $\rho \le 2.3$ (centre ou intérieur proche), $z_w = 1.0 \implies \text{factorProtected} = 1.0f$ même si le facteur d'entrée valait $0.0f$.
  - Pour $\rho \ge 3.0$ (au-delà de la zone tampon), $z_w = 0.0 \implies \text{factorProtected} == \text{factorOriginal}$.
  - Pour $\rho \in ]2.3, 3.0[$, vérifier la décroissance continue $C^1$ du bouclier $z_w$.

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`RoundaboutExemptionModulator` introuvable).

- [ ] **Step 3: Implémenter `RoundaboutExemptionModulator`**

Créer `src/main/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulator.java` :
- Méthode principale :
  ```java
  public float[][] modulate(float[][] rawFactor,
                            List<Roundabout> substitutedRoundabouts,
                            double fz0,
                            double fz1)
  ```
- Initialisation de la matrice `zw[height][width]` à 0.0f.
- Pour chaque `Roundabout rb` :
  - $R = (int) Math.floor(3.0 \times rb.exteriorEllipse().a()) + 4$.
  - Délimitation de la boîte englobante $[x_a, x_b] \times [y_a, y_b]$.
  - Pour chaque point $(x, y)$ dans la boîte :
    - Calcul de $(u, v)$ via `rb.exteriorEllipse().toUnitCircle(new PixelPoint(x, y))`.
    - $\rho = \text{hypot}(u, v)$.
    - $t = \text{clamp}\big((fz1 - \rho) / (fz1 - fz0),\; 0.0,\; 1.0\big)$.
    - $s = t \times t \times (3.0 - 2.0 \times t)$.
    - $zw[y][x] = \max(zw[y][x], (float) s)$.
- Application du bouclier :
  $\text{protectedFactor}[y][x] = 1.0f - zw[y][x] \times (1.0f - rawFactor[y][x])$.
- Javadoc complète en français (ADR-014).

- [ ] **Step 4: Exécuter les tests du modulateur**

Exécuter : `mvn test -Dtest=RoundaboutExemptionModulatorTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulator.java src/test/java/com/sam102022/photoshop/v2/refine/RoundaboutExemptionModulatorTest.java; git commit -m "feat(v2): implement RoundaboutExemptionModulator with smoothstep transition"
```

---

### Task 6: Façade d'Orchestration `AlphaRefiner`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefiner.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinerTest.java`

- [ ] **Step 1: Écrire le test unitaire d'orchestration pour `AlphaRefiner`**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinerTest.java` :
- Vérifier la validation des arguments (non nullité de chaque entrée).
- Vérifier l'enchaînement correct des trois sous-modules (AllowedRegionBuilder -> SoftThresholdFilter -> RoundaboutExemptionModulator).
- Vérifier que la `CropWindow` et les dimensions sont fidèlement transmises dans `AlphaRefinementMap`.

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec de compilation (`AlphaRefiner` introuvable).

- [ ] **Step 3: Implémenter la façade `AlphaRefiner`**

Créer `src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefiner.java` :
- Constructeur avec injection de dépendances (ADR-008) et constructeur sans argument par défaut :
  ```java
  public class AlphaRefiner {
      private final AllowedRegionBuilder regionBuilder;
      private final SoftThresholdFilter thresholdFilter;
      private final RoundaboutExemptionModulator exemptionModulator;
      // ...
  }
  ```
- Méthode d'orchestration :
  ```java
  public AlphaRefinementMap refine(
          ConsolidatedMask consolidatedMask,
          BinaryMask croppedRoad,
          CellLabelMap labelMap,
          List<Roundabout> substitutedRoundabouts,
          AlphaRefinementConfig config
  )
  ```
- Journalisation structurée en français des étapes (ADR-009).
- Javadoc exhaustive en français (ADR-014).

- [ ] **Step 4: Exécuter les tests de la façade**

Exécuter : `mvn test -Dtest=AlphaRefinerTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 5: Commit git**

Exécuter :
```powershell
git add src/main/java/com/sam102022/photoshop/v2/refine/AlphaRefiner.java src/test/java/com/sam102022/photoshop/v2/refine/AlphaRefinerTest.java; git commit -m "feat(v2): implement AlphaRefiner facade for spectral alpha refinement"
```

---

### Task 7: Test d'Intégration Pivot CA01 (`Sprint8IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/refine/Sprint8IntegrationTest.java`

- [ ] **Step 1: Écrire le test d'intégration pivot étalon sur CA01**

Créer `src/test/java/com/sam102022/photoshop/v2/refine/Sprint8IntegrationTest.java` :
- Charger CA01 (`01_plan_avec_territoires.json` et `maps/road.png`).
- Exécuter la chaîne Sprints 1 à 7 : projection, rastérisation, crop, segmentation des cellules, vote topologique, consolidation géodésique, lissage vectoriel avec substitution de ronds-points.
- Exécuter `AlphaRefiner.refine(...)`.
- Vérifier :
  1. `AlphaRefinementMap` non nulle de dimensions $1245 \times 1285$ px.
  2. Rastérisation du contour vectoriel à échelle standard ou via test d'atténuation.
  3. Vérifier que les pixels résiduels de bordure sont bien neutralisés ($\text{factor} < 0.5$).
  4. Vérifier que la zone du rond-point substitué est sanctuarisée ($\text{factor} = 1.0$).
  5. Vérifier que le temps de calcul propre au Sprint 8 est $\le 150\text{ ms}$ (budget performance).

- [ ] **Step 2: Exécuter le test d'intégration CA01**

Exécuter : `mvn test -Dtest=Sprint8IntegrationTest`  
Attendu : BUILD SUCCESS.

- [ ] **Step 3: Exécuter la suite complète de validation du projet**

Exécuter : `mvn test`  
Attendu : 100% de tests réussis sans régression.

- [ ] **Step 4: Commit git**

Exécuter :
```powershell
git add src/test/java/com/sam102022/photoshop/v2/refine/Sprint8IntegrationTest.java; git commit -m "test(v2): add Sprint8IntegrationTest pivot test on CA01"
```

---

### Task 8: Documentation, Changelog & Journalisation

**Files:**
- Modify: `docs/sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md`
- Modify: `docs/SPRINTS_V2.md`
- Modify: `CHANGELOG.md`
- Modify: `JOURNAL.md`

- [ ] **Step 1: Mettre à jour la documentation du sprint**

Dans `docs/sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md` et `docs/SPRINTS_V2.md` :
- Marquer le Sprint 8 comme validé / terminé.
- Noter les résultats de performance et métriques obtenues sur CA01.

- [ ] **Step 2: Mettre à jour le CHANGELOG et le JOURNAL**

Dans `CHANGELOG.md` et `JOURNAL.md` :
- Rendre compte de l'implémentation du Sprint 8 conformément à l'ADR-010.

- [ ] **Step 3: Commit git final du Sprint 8**

Exécuter :
```powershell
git add docs/sprints_v2/sprint-8-affinage-spectral-couverture-alpha.md docs/SPRINTS_V2.md CHANGELOG.md JOURNAL.md; git commit -m "docs(v2): update documentation and journals for Sprint 8 completion"
```
