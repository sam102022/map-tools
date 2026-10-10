# Sprint 6 (V2) Implementation Plan — Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter l'extraction sub-pixel par Marching Squares 2D, le lissage par régression quadratique locale robuste (LQR) avec M-estimateur de Cauchy/Tukey, la préservation des coins par fondu Hermite smoothstep, la modélisation géométrique des ronds-points par ajustement direct d'ellipses (Halir & Flusser 100% Java pur) et leur raccordement $C^1$ sans boucles par splines d'Hermite pour transformer le masque consolidé matriciel `ConsolidatedMask` (Sprint 5) en un contour vectoriel continu lissé de haute fidélité `SmoothVectorContour`.

**Architecture:** Conception en 8 composants hautement spécialisés à responsabilité unique (ADR-008), basés sur des contrats de domaine immuables (`SmoothVectorContour`, `EllipseModel`, `Roundabout`, `ContourSmoothingConfig`), un solveur d'ellipses algébrique direct aux moindres carrés 100% Java standard (`AlgebraicEllipseFitter`), un extracteur sub-pixel Marching Squares 2D (`SubpixelContourExtractor`), un détecteur multi-échelles d'angles vifs (`CornerDetector`), un lisseur LQR polynomial robuste avec résolution locale par formule explicite de Cramer 3x3 (`RobustLqrSmoother`), un modulateur continu de coins par spline cubique Hermite smoothstep (`CornerPreservationBlender`), un détecteur et ajusteur radial d'anneaux giratoires (`RoundaboutDetector`), un connecteur tangentiel $C^1$ d'arcs d'ellipses avec garde-fou anti-boucle (`HermiteSplineConnector`), et une façade d'orchestration (`ContourSmoothingEngine`).

**Tech Stack:** Java 21 standard, JUnit 5, AssertJ, sans bibliothèque native ni framework externe (ADR-001, ADR-002, ADR-004, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014).

---

## Architecture des Fichiers à Créer

```text
src/main/java/com/sam102022/photoshop/v2/contour/
├── AlgebraicEllipseFitter.java          # Solveur direct d'ellipse Halir-Flusser 100% Java standard
├── ContourSmoothingConfig.java          # Record immuable d'hyperparamètres de lissage
├── ContourSmoothingEngine.java          # Façade d'orchestration globale du Sprint 6
├── CornerDetector.java                  # Détecteur multi-échelles d'angles vifs (L=80, seuil=38°)
├── CornerPreservationBlender.java       # Fondu Hermite Smoothstep (R0=35, R1=95 px)
├── EllipseModel.java                    # Modèle géométrique d'ellipse (xc, yc, a, b, theta)
├── HermiteSplineConnector.java          # Raccordement tangentiel C1 avec garde-fou anti-boucle
├── RobustLqrSmoother.java               # Régression quadratique locale LQR robuste (Cauchy 6 iters)
├── Roundabout.java                      # Record immuable de carrefour giratoire détecté
├── RoundaboutDetector.java              # Détection d'îlots et sondage radial 240 rayons sur Rc
├── SmoothVectorContour.java             # Contrat de domaine de sortie officiel du Sprint 6
└── SubpixelContourExtractor.java        # Marching Squares 2D (isovaleur 0.5) & rééchantillonnage 1 px

src/test/java/com/sam102022/photoshop/v2/contour/
├── AlgebraicEllipseFitterTest.java      # Validation sur ellipses synthétiques bruitées
├── ContourDomainModelTest.java          # Tests d'immutabilité et conversions unit circle
├── ContourSmoothingEngineTest.java      # Tests unitaires de la façade de lissage
├── CornerDetectorTest.java              # Détection d'angles vifs sur contours synthétiques
├── CornerPreservationBlenderTest.java   # Vérification du fondu cubique Smoothstep
├── HermiteSplineConnectorTest.java      # Vérification de la continuité C1 et du repli anti-boucle
├── RobustLqrSmootherTest.java           # Rejet d'encoche transversale et respect de courbure
├── RoundaboutDetectorTest.java          # Tests de détection de giratoires sur cellules étalons
├── Sprint6IntegrationTest.java          # Test d'intégration pivot sur CA01 (10-16 coins, 1 rond-point)
└── SubpixelContourExtractorTest.java    # Extraction exacte à +/- 0.01 px et rééchantillonnage 1 px
```

---

## Tâches Séquentielles d'Implémentation

### Task 1: Modèles de Domaine Immuables (`ContourSmoothingConfig`, `EllipseModel`, `Roundabout`, `SmoothVectorContour`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingConfig.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/EllipseModel.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/Roundabout.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/SmoothVectorContour.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/ContourDomainModelTest.java`

- [ ] **Step 1: Écrire le test unitaire pour les records de domaine du contour**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests des modèles de domaine du Sprint 6")
class ContourDomainModelTest {

    @Test
    @DisplayName("ContourSmoothingConfig expose les hyperparamètres par défaut conformes à la spécification")
    void testContourSmoothingConfigDefaults() {
        ContourSmoothingConfig config = ContourSmoothingConfig.defaultConfig();
        assertEquals(1.0, config.resampleStep());
        assertEquals(80, config.cornerWindowL());
        assertEquals(38.0, config.cornerThreshold());
        assertEquals(4.0, config.preSmoothSigma());
        assertEquals(22.0, config.lqrSigma());
        assertEquals(3.0, config.lqrScale());
        assertEquals(6, config.lqrIterations());
        assertEquals(35.0, config.blendR0());
        assertEquals(95.0, config.blendR1());
        assertEquals(2.0, config.roundaboutZr());
    }

    @Test
    @DisplayName("EllipseModel convertit rigoureusement entre repère cartésien et cercle unité")
    void testEllipseModelConversions() {
        // Ellipse centrée en (100, 200), a=40, b=20, theta=0
        EllipseModel ellipse = new EllipseModel(100.0, 200.0, 40.0, 20.0, 0.0);
        PixelPoint ptLocal = new PixelPoint(140.0, 200.0);
        PixelPoint ptUnit = ellipse.toUnitCircle(ptLocal);
        assertEquals(1.0, ptUnit.x(), 1e-6);
        assertEquals(0.0, ptUnit.y(), 1e-6);

        PixelPoint reconstructed = ellipse.fromUnitCircle(ptUnit.x(), ptUnit.y());
        assertEquals(ptLocal.x(), reconstructed.x(), 1e-6);
        assertEquals(ptLocal.y(), reconstructed.y(), 1e-6);

        // Invariant : a >= b
        assertThrows(IllegalArgumentException.class, () -> new EllipseModel(0, 0, 10.0, 20.0, 0.0));
    }

    @Test
    @DisplayName("Roundabout encapsule les métriques d'îlot et d'inliers")
    void testRoundaboutRecord() {
        EllipseModel ell = new EllipseModel(50.0, 50.0, 15.0, 12.0, 0.5);
        Roundabout rb = new Roundabout(new PixelPoint(50.0, 50.0), ell, 250.0, 0.85);
        assertEquals(250.0, rb.islandArea());
        assertEquals(0.85, rb.inlierRatio());
        assertNotNull(rb.exteriorEllipse());
    }

    @Test
    @DisplayName("SmoothVectorContour garantit l'immuabilité défensive de la liste des points et coins")
    void testSmoothVectorContourImmutability() {
        CropWindow crop = new CropWindow(10, 20, 100, 80);
        List<PixelPoint> pts = List.of(new PixelPoint(10, 10), new PixelPoint(20, 10));
        List<Integer> corners = List.of(0);
        SmoothVectorContour contour = new SmoothVectorContour(pts, corners, 100, 80, crop);

        assertEquals(2, contour.points().size());
        assertEquals(1, contour.cornerIndices().size());
        assertThrows(UnsupportedOperationException.class, () -> contour.points().add(new PixelPoint(30, 10)));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=ContourDomainModelTest`  
Expected: Compilation failure (classes non existantes).

- [ ] **Step 3: Implémenter `ContourSmoothingConfig`, `EllipseModel`, `Roundabout` et `SmoothVectorContour`**

Créer les 4 records dans `com.sam102022.photoshop.v2.contour` avec Javadoc intégrale en français, validation défensive d'arguments et méthodes géométriques `toUnitCircle` / `fromUnitCircle` conformes à ADR-004, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=ContourDomainModelTest`  
Expected: PASS (100%).

---

### Task 2: Solveur d'Ellipse Algébrique Pur Java (`AlgebraicEllipseFitter`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/AlgebraicEllipseFitter.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/AlgebraicEllipseFitterTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `AlgebraicEllipseFitter`**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du solveur direct d'ellipse algébrique (Halir & Flusser 1998)")
class AlgebraicEllipseFitterTest {

    @Test
    @DisplayName("Ajuste avec une précision < 1% une ellipse connue bruitée")
    void testFitKnownEllipse() {
        double expectedXc = 120.0;
        double expectedYc = 250.0;
        double expectedA = 45.0;
        double expectedB = 25.0;
        double expectedTheta = 0.6; // ~34.4 degrés

        List<PixelPoint> points = new ArrayList<>();
        int n = 40;
        for (int i = 0; i < n; i++) {
            double phi = 2.0 * Math.PI * i / n;
            double u = expectedA * Math.cos(phi);
            double v = expectedB * Math.sin(phi);
            double x = expectedXc + u * Math.cos(expectedTheta) - v * Math.sin(expectedTheta);
            double y = expectedYc + u * Math.sin(expectedTheta) + v * Math.cos(expectedTheta);
            // Bruit déterministe léger +/- 0.2 px
            double noise = ((i % 3) - 1) * 0.2;
            points.add(new PixelPoint(x + noise, y - noise));
        }

        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();
        Optional<EllipseModel> fittedOpt = fitter.fit(points);
        assertTrue(fittedOpt.isPresent(), "L'ellipse doit être ajustée avec succès.");

        EllipseModel fitted = fittedOpt.get();
        assertEquals(expectedXc, fitted.xc(), 1.0, "Centre X proche");
        assertEquals(expectedYc, fitted.yc(), 1.0, "Centre Y proche");
        assertEquals(expectedA, fitted.a(), 1.0, "Demi-grand axe proche");
        assertEquals(expectedB, fitted.b(), 1.0, "Demi-petit axe proche");
        assertTrue(fitted.a() >= fitted.b(), "L'invariant a >= b doit être respecté");
    }

    @Test
    @DisplayName("Retourne Optional.empty() si le nuage de points est insuffisant ou colinéaire")
    void testDegeneratePoints() {
        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();
        assertFalse(fitter.fit(List.of()).isPresent());
        assertFalse(fitter.fit(List.of(new PixelPoint(0, 0), new PixelPoint(1, 1))).isPresent());

        // Points alignés sur une ligne droite
        List<PixelPoint> colinear = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            colinear.add(new PixelPoint(i * 10, i * 10));
        }
        assertFalse(fitter.fit(colinear).isPresent());
    }

    @Test
    @DisplayName("Calcule les résidus de distance euclidienne exacte à l'ellipse par méthode itérative")
    void testComputeResiduals() {
        EllipseModel ellipse = new EllipseModel(0, 0, 20.0, 10.0, 0.0);
        AlgebraicEllipseFitter fitter = new AlgebraicEllipseFitter();

        // Point à l'extérieur : (25, 5)
        double res = fitter.distanceToEllipse(new PixelPoint(25.0, 5.0), ellipse);
        assertEquals(6.159263, res, 1e-4);

        // Point sur l'axe : (0, 15)
        double resY = fitter.distanceToEllipse(new PixelPoint(0.0, 15.0), ellipse);
        assertEquals(5.0, resY, 1e-4);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=AlgebraicEllipseFitterTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `AlgebraicEllipseFitter`**

Implémenter l'ajustement direct de Halir & Flusser sous contrainte $4AC - B^2 = 1$ en 100% Java standard :
1. Centrage et normalisation d'échelle des points ;
2. Construction des matrices de dispersion $S_1, S_2, S_3$ ($3 \times 3$) ;
3. Inversion de $S_3$ par comatrices/Cramer ;
4. Calcul de $M = C_1^{-1} (S_1 - S_2 S_3^{-1} S_2^T)$ ;
5. Résolution analytique des valeurs propres 3x3 par méthode trigonométrique de Cardano et extraction de l'unique vecteur propre $u_1$ satisfaisant $4 v_0 v_2 - v_1^2 > 0$ ;
6. Extraction des 6 coefficients coniques $(A, B, C, D, E, F)$ ;
7. Dénormalisation et conversion géométrique $(x_c, y_c, a, b, \theta)$ avec $a \ge b$ et $\theta \in [0, \pi[$ ;
8. Calcul de distance point-ellipse par 5 itérations de Newton sur l'angle paramétrique.
Conformité stricte à ADR-001, ADR-008, ADR-011 (découpage en méthodes privées <= 15 complexité), ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=AlgebraicEllipseFitterTest`  
Expected: PASS (100%).

---

### Task 3: Extracteur de Contour Sub-Pixel (`SubpixelContourExtractor`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/SubpixelContourExtractor.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/SubpixelContourExtractorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `SubpixelContourExtractor`**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests de l'extracteur de contour sub-pixel Marching Squares 2D")
class SubpixelContourExtractorTest {

    @Test
    @DisplayName("Extrait un contour sub-pixel fermé sur un rectangle binaire avec précision de bord")
    void testExtractOnRectangle() {
        int w = 30;
        int h = 30;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 5; y < 25; y++) {
            for (int x = 5; x < 25; x++) {
                mask.set(x, y, true);
            }
        }

        SubpixelContourExtractor extractor = new SubpixelContourExtractor();
        List<PixelPoint> contour = extractor.extract(mask, 1.0);

        assertFalse(contour.isEmpty(), "Le contour extrait ne doit pas être vide.");
        // Le contour doit avoir un pas approximatif uniforme de 1.0 px
        for (int i = 0; i < contour.size(); i++) {
            PixelPoint p1 = contour.get(i);
            PixelPoint p2 = contour.get((i + 1) % contour.size());
            double dist = Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
            assertEquals(1.0, dist, 0.05, "Le pas entre points consécutifs doit être proche de 1.0 px");
        }
    }

    @Test
    @DisplayName("Comble automatiquement les cavités intérieures avant l'extraction")
    void testComblementTrousInterieurs() {
        int w = 40;
        int h = 40;
        BinaryMask mask = new BinaryMask(w, h);
        for (int y = 5; y < 35; y++) {
            for (int x = 5; x < 35; x++) {
                mask.set(x, y, true);
            }
        }
        // Trou intérieur de 10x10 px
        for (int y = 15; y < 25; y++) {
            for (int x = 15; x < 25; x++) {
                mask.set(x, y, false);
            }
        }

        SubpixelContourExtractor extractor = new SubpixelContourExtractor();
        List<PixelPoint> contour = extractor.extract(mask, 1.0);

        // Une seule boucle extérieure doit être produite
        assertFalse(contour.isEmpty());
        // Vérification qu'aucun point n'est dans la zone du trou intérieur
        for (PixelPoint p : contour) {
            boolean inHole = (p.x() >= 15.0 && p.x() <= 24.0 && p.y() >= 15.0 && p.y() <= 24.0);
            assertFalse(inHole, "Le contour ne doit pas traverser le trou comblé.");
        }
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=SubpixelContourExtractorTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `SubpixelContourExtractor`**

Implémenter l'algorithme Marching Squares 2D à isovaleur 0.5 :
1. Comblement préalable des trous intérieurs via inondation inverse (flood fill depuis la bordure sur masque inversé) ;
2. Matérialisation d'une grille de cellules $2 \times 2$ avec bordure de 1 px (`pad = 1`) ;
3. Génération des segments orientés reliant les milieux d'arêtes selon les 16 cas standards (isovaleur 0.5) ;
4. Chaînage déterministe des segments par table de hachage de coordonnées discrètes d'arêtes pour former des polygones fermés ;
5. Sélection de la plus grande boucle extérieure continue et soustraction de l'offset de bordure de 1.0 px ;
6. Rééchantillonnage curviligne équidistant le long de l'arc cumulé à pas `step = 1.0 px` avec interpolation linéaire unitaire.
Conformité stricte à ADR-001, ADR-008, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=SubpixelContourExtractorTest`  
Expected: PASS (100%).

---

### Task 4: Détecteur d'Angles Vifs & Coins (`CornerDetector`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/CornerDetector.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/CornerDetectorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `CornerDetector`**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du détecteur d'angles vifs et coins")
class CornerDetectorTest {

    @Test
    @DisplayName("Détecte exactement les 4 sommets d'un grand rectangle à pas unitaire")
    void testDetectRectangleCorners() {
        // Rectangle de 200 x 200 px échantillonné à pas 1 px (800 points)
        List<PixelPoint> contour = new ArrayList<>();
        // Côté haut (gauche à droite)
        for (int x = 0; x < 200; x++) contour.add(new PixelPoint(x, 0));
        // Côté droit (haut en bas)
        for (int y = 0; y < 200; y++) contour.add(new PixelPoint(200, y));
        // Côté bas (droite à gauche)
        for (int x = 200; x > 0; x--) contour.add(new PixelPoint(x, 200));
        // Côté gauche (bas en haut)
        for (int y = 200; y > 0; y--) contour.add(new PixelPoint(0, y));

        CornerDetector detector = new CornerDetector();
        // L=40, threshold=38 deg, preSmoothSigma=2.0
        List<Integer> corners = detector.detectCorners(contour, 40, 38.0, 2.0);

        assertEquals(4, corners.size(), "Un rectangle doit comporter exactement 4 coins détectés.");
    }

    @Test
    @DisplayName("Ne détecte aucun coin sur un cercle continu")
    void testNoCornersOnCircle() {
        List<PixelPoint> contour = new ArrayList<>();
        int n = 500;
        double radius = 100.0;
        for (int i = 0; i < n; i++) {
            double phi = 2.0 * Math.PI * i / n;
            contour.add(new PixelPoint(150 + radius * Math.cos(phi), 150 + radius * Math.sin(phi)));
        }

        CornerDetector detector = new CornerDetector();
        List<Integer> corners = detector.detectCorners(contour, 80, 38.0, 4.0);
        assertTrue(corners.isEmpty(), "Un cercle lisse ne doit comporter aucun angle vif.");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=CornerDetectorTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `CornerDetector`**

Implémenter la détection d'angles vifs :
1. Pré-filtrage gaussien périodique léger ($\sigma_{\text{pre}} = 4.0\text{ px}$, fenêtre $K = \lfloor 4 \sigma_{\text{pre}} \rfloor$) ;
2. Évaluation des vecteurs sécants amont et aval espacés de $L = 80\text{ px}$ :
   $v_1 = c(i) - c((i - L + N) \pmod N)$ et $v_2 = c((i + L) \pmod N) - c(i)$ ;
3. Déviation angulaire en degrés $\theta(i) = |\text{atan2}(v_{1,x} v_{2,y} - v_{1,y} v_{2,x}, \; v_{1,x} v_{2,x} + v_{1,y} v_{2,y})| \times \frac{180}{\pi}$ ;
4. Extraction des maxima locaux stricts vérifiant $\theta(i) \ge \theta_{\text{corner}}$ ($38^\circ$) et $\theta(i) \ge \max_{k \in [-L, L]} \theta((i+k) \pmod N)$ ;
5. Retour d'une `List<Integer>` triée des indices immuables.
Conformité stricte à ADR-001, ADR-008, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=CornerDetectorTest`  
Expected: PASS (100%).

---

### Task 5: Lisseur LQR Robuste & Fondu Progressif Hermite (`RobustLqrSmoother` & `CornerPreservationBlender`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/RobustLqrSmoother.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/CornerPreservationBlender.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/RobustLqrSmootherTest.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/CornerPreservationBlenderTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour le lisseur LQR et le fondu Hermite**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du lisseur quadratique robuste LQR")
class RobustLqrSmootherTest {

    @Test
    @DisplayName("Ignore une encoche étroite de carrefour (5 px) sans affaisser la ligne principale")
    void testRejetEncocheCarrefour() {
        int n = 200;
        List<PixelPoint> segment = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double y = 50.0;
            // Encoche transversale étroite de 5 px de large et 20 px de profondeur au centre (i=100)
            if (i >= 98 && i <= 102) {
                y += 20.0;
            }
            segment.add(new PixelPoint(i, y));
        }

        RobustLqrSmoother smoother = new RobustLqrSmoother();
        List<PixelPoint> smoothed = smoother.smoothSegment(segment, 22.0, 3.0, 6);

        assertEquals(n, smoothed.size());
        // Au centre (i=100), le lissage robuste doit rejeter l'encoche et rester très proche de y=50
        PixelPoint center = smoothed.get(100);
        assertEquals(50.0, center.y(), 2.0, "L'encoche doit être effacée comme valeur aberrante.");
    }
}
```

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Tests du fondu de préservation des coins CornerPreservationBlender")
class CornerPreservationBlenderTest {

    @Test
    @DisplayName("Conserve le contour brut à 100% à distance <= R0 et applique le lissage à 100% à distance >= R1")
    void testBlendBetweenR0AndR1() {
        int n = 200;
        List<PixelPoint> raw = new ArrayList<>();
        List<PixelPoint> smoothed = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            raw.add(new PixelPoint(i, 0.0));
            smoothed.add(new PixelPoint(i, 10.0));
        }

        // Coin à l'indice 0
        List<Integer> corners = List.of(0);
        CornerPreservationBlender blender = new CornerPreservationBlender();
        List<PixelPoint> blended = blender.blend(raw, smoothed, corners, 35.0, 95.0);

        // Au coin (i=0) et jusqu'à 35 px : brut à 100% (y = 0.0)
        assertEquals(0.0, blended.get(0).y(), 1e-6);
        assertEquals(0.0, blended.get(20).y(), 1e-6);
        assertEquals(0.0, blended.get(35).y(), 1e-6);

        // À distance >= 95 px (i=100) : lissé à 100% (y = 10.0)
        assertEquals(10.0, blended.get(100).y(), 1e-6);

        // À mi-chemin (d = 65 px), smoothstep = 0.5 (y = 5.0)
        assertEquals(5.0, blended.get(65).y(), 1e-4);
    }
}
```

- [ ] **Step 2: Exécuter les tests pour vérifier l'échec initial**

Run: `mvn test -Dtest="RobustLqrSmootherTest,CornerPreservationBlenderTest"`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `RobustLqrSmoother` et `CornerPreservationBlender`**

1. **`RobustLqrSmoother` :**
   - Partitionnement du contour en segments délimités par les coins ;
   - Garde-fou segment court : si $n < 2.5 \sigma_{\text{lqr}}$ ($55\text{ px}$), conserver le tracé brut ;
   - Soustraction de la corde directrice $base(t) = P_0 + \frac{t}{n-1}(P_{n-1} - P_0)$ ;
   - Convolution polynomiale locale d'ordre 2 avec poids gaussiens $g(u) = \exp(-0.5(u/\sigma)^2)$ ($k = \lfloor 3.5\sigma \rfloor = 77$) ;
   - Régularisation $\lambda = 10^{-3} M_0(t) + 10^{-9}$ et résolution locale $3 \times 3$ par déterminants de Cramer ;
   - Repondération itérative de Cauchy/Tukey $w(t) = 1 / (1 + (dev(t)/sc)^2)^2$ (6 itérations) ;
   - Réintégration de la corde directrice.
2. **`CornerPreservationBlender` :**
   - Distance curviligne minimale périodique $d(i) = \min_{c} \min(|i-c|, N - |i-c|)$ ;
   - Fondu cubique d'Hermite (*Smoothstep*) $3\alpha^2 - 2\alpha^3$ avec $\alpha = \text{clamp}((d - R_0)/(R_1 - R_0), 0, 1)$ ;
   - Combinaison affine $P_{\text{blend}}(i) = P_{\text{raw}}(i) + S(\alpha) \cdot (P_{\text{smooth}}(i) - P_{\text{raw}}(i))$.
Conformité stricte à ADR-001, ADR-008, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter les tests pour vérifier le succès**

Run: `mvn test -Dtest="RobustLqrSmootherTest,CornerPreservationBlenderTest"`  
Expected: PASS (100%).

---

### Task 6: Détecteur Géométrique des Ronds-points (`RoundaboutDetector`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/RoundaboutDetector.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/RoundaboutDetectorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `RoundaboutDetector`**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du détecteur de ronds-points")
class RoundaboutDetectorTest {

    @Test
    @DisplayName("Détecte un rond-point synthétique constitué d'un îlot circulaire entouré d'un anneau routier")
    void testDetectSyntheticRoundabout() {
        int w = 120;
        int h = 120;
        int cx = 60;
        int cy = 60;
        int islandR = 10;
        int roadR = 20;

        // Chaussée Rc (anneau entre islandR et roadR)
        BinaryMask rc = new BinaryMask(w, h);
        int[] labels = new int[w * h];
        long islandArea = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dist = Math.hypot(x - cx, y - cy);
                if (dist <= islandR) {
                    labels[y * w + x] = 1; // Îlot central
                    islandArea++;
                } else if (dist <= roadR) {
                    rc.set(x, y, true); // Anneau de route
                    labels[y * w + x] = 0;
                } else {
                    labels[y * w + x] = 2; // Extérieur
                }
            }
        }

        CellLabelMap labelMap = new CellLabelMap(w, h, labels, 2);
        Cell island = new Cell(1, islandArea, cx - islandR, cy - islandR, cx + islandR, cy + islandR, new PixelPoint(cx, cy));

        RoundaboutDetector detector = new RoundaboutDetector();
        List<Roundabout> detected = detector.detect(rc, labelMap, List.of(island), 90, 240);

        assertEquals(1, detected.size(), "Le rond-point synthétique doit être détecté.");
        Roundabout rb = detected.get(0);
        assertEquals(cx, rb.center().x(), 1.0);
        assertEquals(cy, rb.center().y(), 1.0);
        assertEquals(roadR, rb.exteriorEllipse().a(), 2.0);
        assertTrue(rb.inlierRatio() >= 0.5);
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=RoundaboutDetectorTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `RoundaboutDetector`**

Implémenter la détection de ronds-points :
1. Filtrage préalable des cellules : $40 \le \text{area} \le 9000$, non adjacente au bord de l'image, compacité/solidité $\ge 0.90$ ;
2. Extraction de la frontière de l'îlot par Marching Squares local et ajustement d'ellipse via `AlgebraicEllipseFitter` ;
3. Rejet si $b/a < 0.55$ ou résidu $\text{RMS} > 1.0$ ;
4. Émission de $n_{\text{ang}} = 240$ rayons radiaux depuis le centre de l'îlot sur la chaussée fermée $R_c$ (pas $0.5\text{ px}$, portée max $90\text{ px}$) pour détecter la distance de sortie de l'anneau ;
5. Filtrage sur le mode bas (percentile 35 + 1.0 px) pour éliminer les embranchements perpendiculaires ;
6. Ajustement robuste en 8 itérations avec rejet des résidus $> \max(1.5, \text{percentile}_{60})$ ;
7. Validation géométrique par rapport au rayon théorique $iR = \sqrt{\text{area}/\pi}$ ($1.3 iR \le a, b \le 4.5 iR + 8$ et $\max(a, b) \le 70\text{ px}$).
Conformité stricte à ADR-001, ADR-008, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=RoundaboutDetectorTest`  
Expected: PASS (100%).

---

### Task 7: Raccordement Tangentiel $C^1$ par Splines d'Hermite (`HermiteSplineConnector`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnector.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/HermiteSplineConnectorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `HermiteSplineConnector`**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests du connecteur Hermite C1 et de substitution d'arcs de ronds-points")
class HermiteSplineConnectorTest {

    @Test
    @DisplayName("Génère une spline d'Hermite continue entre deux points avec tangentes alignées")
    void testHermiteSplineDirect() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        PixelPoint p0 = new PixelPoint(0, 0);
        PixelPoint t0 = new PixelPoint(1, 0);
        PixelPoint p1 = new PixelPoint(50, 0);
        PixelPoint t1 = new PixelPoint(1, 0);

        List<PixelPoint> spline = connector.evaluateHermite(p0, t0, p1, t1);
        assertFalse(spline.isEmpty());
        // Doit être parfaitement horizontal y=0
        for (PixelPoint p : spline) {
            assertEquals(0.0, p.y(), 1e-4);
        }
    }

    @Test
    @DisplayName("Active le repli linéaire sécurisé en cas de boucle excessive sur la spline d'Hermite")
    void testHermiteLoopFallback() {
        HermiteSplineConnector connector = new HermiteSplineConnector();
        PixelPoint p0 = new PixelPoint(0, 0);
        PixelPoint t0 = new PixelPoint(-1, 0); // Tangente orientée à l'envers provoquant une boucle
        PixelPoint p1 = new PixelPoint(20, 0);
        PixelPoint t1 = new PixelPoint(-1, 0);

        List<PixelPoint> spline = connector.evaluateHermite(p0, t0, p1, t1);
        assertFalse(spline.isEmpty());
        // Repli linéaire : pas de boucle aberrante
        for (PixelPoint p : spline) {
            assertTrue(p.x() >= -1.0 && p.x() <= 21.0);
        }
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=HermiteSplineConnectorTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `HermiteSplineConnector`**

Implémenter l'interpolation et la substitution vectorielle d'arcs de giratoires :
1. Détection de proximité topologique : projection dans le repère normalisé unitaire de l'ellipse ($\rho \in [0.75, 1.3]$ sur $\ge 12$ points) et recouvrement du disque central sur le territoire $\ge 20\%$ ;
2. Identification des bornes de contact $i_A$ et $i_B$ via le plus grand saut d'indices parmi les points à $\rho \le 2.0$ ;
3. Sélection de l'arc extérieur de l'ellipse (orientation $sgn \in \{+1, -1\}$ minimisant le passage sur le territoire via sonde à $1.12 \times arc$) ;
4. Évaluation des tangentes unitaires de voie ($d_A, d_B$ à $\pm 6\text{ px}$) et d'ellipse ($t_A, t_B$) ;
5. Interpolation cubique d'Hermite $C^1$ avec garde-fou anti-boucle (déviation max $\le 0.7 L + 2$) et repli linéaire ;
6. Épissure continue dans le contour vectoriel fermé ordonné.
Conformité stricte à ADR-001, ADR-008, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test pour vérifier le succès**

Run: `mvn test -Dtest=HermiteSplineConnectorTest`  
Expected: PASS (100%).

---

### Task 8: Façade d'Orchestration Globale & Test d'Intégration Pivot CA01 (`ContourSmoothingEngine` & `Sprint6IntegrationTest`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngine.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/ContourSmoothingEngineTest.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/contour/Sprint6IntegrationTest.java`

- [ ] **Step 1: Écrire les tests unitaires et le test d'intégration pivot CA01**

```java
package com.sam102022.photoshop.v2.contour;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.expansion.RoadBoundaryConsolidator;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.TopologicalVoteEngine;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test d'intégration pivot Sprint 6 : Lissage Sub-Pixel & Ronds-points CA01")
class Sprint6IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprint 6 sur CA01 : 10 à 16 coins, 1 rond-point substitué, budget < 1000 ms")
    void testEndToEndSprint6OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image maps/road.png doit exister.");

        long startTime = System.currentTimeMillis();

        // 1. Polygone P (Sprint 1)
        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        WebMercatorProjection projection = new WebMercatorProjection(loaded.mapContext());
        List<PixelPoint> polygonPoints = loaded.geometry().outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();

        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        int width = roadImg.getWidth();
        int height = roadImg.getHeight();

        PolygonRasterizer rasterizer = new PolygonRasterizer();
        PolygonMask polygonMask = rasterizer.rasterize(width, height, List.of(polygonPoints), List.of());
        BinaryMask fullRoad = BinaryMask.fromImage(roadImg, 128);

        // 2. Cellules & Vote (Sprints 3 & 4)
        BoundingBoxCropper cropper = new BoundingBoxCropper();
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, 90);
        BinaryMask croppedRoad = cropper.crop(fullRoad, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);

        CellLabeler labeler = new CellLabeler();
        CellLabelingResult labelingResult = labeler.label(croppedRoad);
        TopologicalVoteEngine votingEngine = new TopologicalVoteEngine();
        CellSelection selection = votingEngine.execute(labelingResult.labelMap(), labelingResult.cells(), croppedPoly);

        // 3. Masque consolidé (Sprint 5)
        RoadBoundaryConsolidator consolidator = new RoadBoundaryConsolidator();
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                ExpansionConfig.defaultTerritory()
        );

        // 4. Exécution du Sprint 6
        long startSprint6 = System.currentTimeMillis();
        ContourSmoothingEngine engine = new ContourSmoothingEngine();
        SmoothVectorContour result = engine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                ContourSmoothingConfig.defaultConfig()
        );
        long elapsedSprint6 = System.currentTimeMillis() - startSprint6;
        long elapsedTotal = System.currentTimeMillis() - startTime;

        assertNotNull(result);
        assertFalse(result.points().isEmpty());

        System.out.printf("Sprint 6 exécuté en %d ms (total pipeline : %d ms). Points de contour : %d, Coins détectés : %d%n",
                elapsedSprint6, elapsedTotal, result.points().size(), result.cornerIndices().size());

        // Critères d'acceptation de la spécification Sprint 6 :
        // 1. Coins authentiques : 10 à 16 coins
        int cornerCount = result.cornerIndices().size();
        assertTrue(cornerCount >= 10 && cornerCount <= 16,
                "Le nombre de coins détectés doit être compris entre 10 et 16 (actuel : " + cornerCount + ")");

        // 2. Pas moyen de rééchantillonnage proche de 1.0 px
        double avgStep = 0.0;
        int n = result.points().size();
        for (int i = 0; i < n; i++) {
            PixelPoint p1 = result.points().get(i);
            PixelPoint p2 = result.points().get((i + 1) % n);
            avgStep += Math.hypot(p2.x() - p1.x(), p2.y() - p1.y());
        }
        avgStep /= n;
        assertEquals(1.0, avgStep, 0.1, "Le pas moyen entre points du contour doit être proche de 1.0 px");

        // 3. Budget de performance <= 600 ms en Java standard
        assertTrue(elapsedSprint6 <= 1000, "Le temps de calcul du Sprint 6 doit être inférieur à 1000 ms");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec initial**

Run: `mvn test -Dtest=Sprint6IntegrationTest`  
Expected: Compilation failure.

- [ ] **Step 3: Implémenter `ContourSmoothingEngine`**

Assembler la chaîne séquentielle complète :
1. Extraction du contour sub-pixel brut et rééchantillonnage 1 px (`SubpixelContourExtractor`) ;
2. Détection des sommets d'angles vifs (`CornerDetector`) ;
3. Lissage LQR robuste sur les résidus polynomiaux (`RobustLqrSmoother`) ;
4. Modulation continue par fondu Hermite smoothstep (`CornerPreservationBlender`) ;
5. Détection des îlots et ronds-points (`RoundaboutDetector`) ;
6. Raccordement tangentiel $C^1$ et substitution d'arcs d'ellipses (`HermiteSplineConnector`) ;
7. Production du contrat immuable `SmoothVectorContour`.
Conformité stricte à ADR-001, ADR-004, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014.

- [ ] **Step 4: Exécuter le test d'intégration pour vérifier le succès**

Run: `mvn test -Dtest=Sprint6IntegrationTest`  
Expected: PASS (100%).

---

### Task 9: Documentation, Traçabilité Journalière & Clôture du Sprint 6

**Files:**
- Modify: `docs/sprints_v2/sprint-6-lissage-subpixel-ronds-points.md`
- Modify: `docs/SPRINTS_V2.md`
- Modify: `JOURNAL.md`

- [ ] **Step 1: Mettre à jour `sprint-6-lissage-subpixel-ronds-points.md` avec le statut Validé**
- [ ] **Step 2: Mettre à jour `docs/SPRINTS_V2.md` avec l'avancement du Sprint 6**
- [ ] **Step 3: Rédiger l'entrée journalière détaillée dans `JOURNAL.md` selon ADR-010 et ADR-013**
- [ ] **Step 4: Exécuter la suite complète des tests de l'architecture V2**

Run: `mvn test -Dtest="com.sam102022.photoshop.v2.**.*Test"`  
Expected: PASS (100% de réussite sur l'ensemble des Sprints 1 à 6).
