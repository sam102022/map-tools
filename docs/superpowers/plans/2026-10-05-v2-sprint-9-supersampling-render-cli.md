# V2 Sprint 9 — Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Pipeline CLI V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter le module de rendu sub-pixel supersampling ($SS=4$), l'assemblage et découpe d'images RGBA/niveaux de gris/overlay, le pipeline d'orchestration global V2 unifié et l'interface CLI (`com.sam102022.photoshop.v2.render`, `com.sam102022.photoshop.v2.pipeline`, `com.sam102022.photoshop.cli`), complétant ainsi les 9 sprints de l'architecture V2.

**Architecture:** Conception modulaire conforme aux ADR-001 (100% Java standard), ADR-002 (modèle de masques continus CoverageMask), ADR-003 (exécution CLI/GUI), ADR-004 (immutabilité des records), ADR-008 (SRP), ADR-009 (logs français hiérarchisés), ADR-011 (complexité cognitive < 15), ADR-012 (imports explicites), ADR-013 (date système 2026-10-05), et ADR-014 (Javadoc exhaustive en français). Le module comprend :
1. Le record immuable des livrables finaux `RenderResult` ;
2. Le record immuable de configuration unifiée `V2Config` ;
3. Le rastériseur sub-pixel `SupersampleRenderer` avec Box Filter et modulation par `AlphaRefinementMap` ;
4. Le découpeur et assembleur d'images `ImageClipper` (`clipped.png`, `mask.png`, `overlay.png`) ;
5. L'orchestrateur de bout en bout `V2Pipeline` reliant les Sprints 1 à 9 ;
6. Le point d'entrée de ligne de commande `V2CliRunner` (avec intégration `--v2` dans `CliRunner`).

**Tech Stack:** Java 21 standard, Java2D, JUnit 5, AssertJ.

---

## Structure des Fichiers

| Rôle | Fichier | Action |
| :--- | :--- | :--- |
| **Record Livrables** | `src/main/java/com/sam102022/photoshop/v2/render/RenderResult.java` | Créer |
| **Test Record Livrables** | `src/test/java/com/sam102022/photoshop/v2/render/RenderResultTest.java` | Créer |
| **Record Configuration** | `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Config.java` | Créer |
| **Test Configuration** | `src/test/java/com/sam102022/photoshop/v2/pipeline/V2ConfigTest.java` | Créer |
| **Rastériseur Sub-Pixel** | `src/main/java/com/sam102022/photoshop/v2/render/SupersampleRenderer.java` | Créer |
| **Test Rastériseur** | `src/test/java/com/sam102022/photoshop/v2/render/SupersampleRendererTest.java` | Créer |
| **Découpeur & Assembleur** | `src/main/java/com/sam102022/photoshop/v2/render/ImageClipper.java` | Créer |
| **Test Découpeur** | `src/test/java/com/sam102022/photoshop/v2/render/ImageClipperTest.java` | Créer |
| **Orchestrateur Pipeline** | `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Pipeline.java` | Créer |
| **Test Orchestrateur** | `src/test/java/com/sam102022/photoshop/v2/pipeline/V2PipelineTest.java` | Créer |
| **Runner CLI V2** | `src/main/java/com/sam102022/photoshop/cli/V2CliRunner.java` | Créer |
| **Intégration CLI** | `src/main/java/com/sam102022/photoshop/cli/CliRunner.java` | Modifier (aiguillage `--v2`) |
| **Test Runner CLI V2** | `src/test/java/com/sam102022/photoshop/cli/V2CliRunnerTest.java` | Créer |
| **Test Pivot CA01** | `src/test/java/com/sam102022/photoshop/v2/render/Sprint9IntegrationTest.java` | Créer |
| **Documentation & Suivi** | `docs/sprints_v2/sprint-9-rendu-supersampling-cli.md`, `docs/SPRINTS_V2.md`, `JOURNAL.md`, `CHANGELOG.md` | Modifier |

---

### Task 1: Contrat Immuable `RenderResult`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/render/RenderResult.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/render/RenderResultTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `RenderResult`**

Dans `src/test/java/com/sam102022/photoshop/v2/render/RenderResultTest.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests unitaires du contrat immuable RenderResult")
class RenderResultTest {

    @Test
    @DisplayName("Création valide d'un RenderResult et accès aux accesseurs")
    void testValidCreation() {
        BufferedImage clipped = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage overlay = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask coverageMask = new CoverageMask(10, 10);

        RenderResult result = new RenderResult(clipped, mask, overlay, coverageMask);

        assertNotNull(result);
        assertEquals(clipped, result.clipped());
        assertEquals(mask, result.mask());
        assertEquals(overlay, result.overlay());
        assertEquals(coverageMask, result.coverageMask());
    }

    @Test
    @DisplayName("Rejet des paramètres null dans RenderResult")
    void testRejectsNullParameters() {
        BufferedImage clipped = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        BufferedImage mask = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        BufferedImage overlay = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask coverageMask = new CoverageMask(10, 10);

        assertThrows(IllegalArgumentException.class, () -> new RenderResult(null, mask, overlay, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, null, overlay, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, mask, null, coverageMask));
        assertThrows(IllegalArgumentException.class, () -> new RenderResult(clipped, mask, overlay, null));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car la classe `RenderResult` n'existe pas encore.

- [ ] **Step 3: Implémenter `RenderResult`**

Dans `src/main/java/com/sam102022/photoshop/v2/render/RenderResult.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;

import java.awt.image.BufferedImage;

/**
 * Contrat immuable regroupant l'ensemble des livrables graphiques finaux générés par le moteur V2.
 *
 * @param clipped      Image cartographique détourée avec couche alpha continue 32-bit (ARGB).
 * @param mask         Masque en niveaux de gris 8-bit (TYPE_BYTE_GRAY) reflétant la couverture.
 * @param overlay      Image de contrôle superposant le tracé du contour frontière rouge vif sur la carte.
 * @param coverageMask Masque matriciel continu de couverture sub-pixel [0..255].
 */
public record RenderResult(
        BufferedImage clipped,
        BufferedImage mask,
        BufferedImage overlay,
        CoverageMask coverageMask
) {

    /**
     * Valide l'absence de composant nul.
     *
     * @param clipped      Image détourée ARGB non nulle.
     * @param mask         Masque monochrome 8-bit non nul.
     * @param overlay      Image de contrôle rouge vif non nulle.
     * @param coverageMask Masque de couverture géométrique sub-pixel non nul.
     * @throws IllegalArgumentException si l'un des paramètres est nul.
     */
    public RenderResult {
        if (clipped == null || mask == null || overlay == null || coverageMask == null) {
            throw new IllegalArgumentException("Aucun composant de RenderResult ne peut être null.");
        }
    }
}
```

- [ ] **Step 4: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=RenderResultTest`  
Attendu : PASS.

---

### Task 2: Record Immuable de Configuration `V2Config`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Config.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/pipeline/V2ConfigTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `V2Config`**

Dans `src/test/java/com/sam102022/photoshop/v2/pipeline/V2ConfigTest.java` :

```java
package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.contour.ContourSmoothingConfig;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.refine.AlphaRefinementConfig;
import com.sam102022.photoshop.v2.vote.CellSelectionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests unitaires du record immuable V2Config")
class V2ConfigTest {

    @Test
    @DisplayName("Configuration par défaut nominale conforme aux étalons Python V7")
    void testDefaultConfig() {
        V2Config config = V2Config.defaultConfig();

        assertNotNull(config);
        assertEquals(0.60, config.hi(), 1e-6);
        assertEquals(0.05, config.lo(), 1e-6);
        assertEquals(90, config.cropMargin());

        assertEquals(4.0, config.eps(), 1e-6);
        assertEquals(5, config.rho());
        assertEquals(15000L, config.residualHoleMaxArea());
        assertEquals(OperationMode.TERRITORY, config.operationMode());

        assertEquals(22.0, config.sig(), 1e-6);
        assertEquals(38.0, config.cornerAngle(), 1e-6);
        assertEquals(80, config.cornerWindowL());
        assertEquals(35.0, config.r0(), 1e-6);
        assertEquals(95.0, config.r1(), 1e-6);
        assertEquals(2.0, config.roundaboutRadiusZr(), 1e-6);

        assertEquals(2.7, config.straightFactor(), 1e-6);
        assertEquals(3.0, config.straightScale(), 1e-6);
        assertEquals(3.0, config.straightThreshold(), 1e-6);
        assertEquals(10, config.straightTransitionK());

        assertEquals(1.4, config.gaussianBlurSigma(), 1e-6);
        assertEquals(2.0, config.contrastStiffness(), 1e-6);
        assertEquals(2.3, config.roundaboutBufferInner(), 1e-6);
        assertEquals(3.0, config.roundaboutBufferOuter(), 1e-6);
        assertEquals(2000L, config.roadHoleMaxArea());

        assertEquals(4, config.supersamplingFactor());
    }

    @Test
    @DisplayName("Génération des sous-configurations modulaires")
    void testSubConfigConversions() {
        V2Config config = V2Config.defaultConfig();

        CellSelectionPolicy policy = config.toCellSelectionPolicy();
        assertEquals(0.60, policy.insideThreshold(), 1e-6);
        assertEquals(0.05, policy.partialThreshold(), 1e-6);

        ExpansionConfig expConfig = config.toExpansionConfig();
        assertEquals(4.0, expConfig.epsilon(), 1e-6);
        assertEquals(5, expConfig.openingDiskRadius());
        assertEquals(15000L, expConfig.maxHoleArea());

        ContourSmoothingConfig smoothConfig = config.toContourSmoothingConfig();
        assertEquals(22.0, smoothConfig.lqrSigma(), 1e-6);
        assertEquals(2.7, smoothConfig.straightFactor(), 1e-6);

        AlphaRefinementConfig refConfig = config.toAlphaRefinementConfig();
        assertEquals(1.4, refConfig.gaussianBlurSigma(), 1e-6);
        assertEquals(2000L, refConfig.roadHoleMaxArea());
    }

    @Test
    @DisplayName("Validation défensive des bornes")
    void testValidationBounds() {
        assertThrows(IllegalArgumentException.class, () -> new V2Config(
                -0.1, 0.05, 90, 4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0, 2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L, 4
        ));

        assertThrows(IllegalArgumentException.class, () -> new V2Config(
                0.60, 0.05, 90, 4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0, 2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L, 0 // SS <= 0
        ));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car la classe `V2Config` n'existe pas.

- [ ] **Step 3: Implémenter `V2Config`**

Dans `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Config.java` :

```java
package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.v2.contour.ContourSmoothingConfig;
import com.sam102022.photoshop.v2.expansion.ExpansionConfig;
import com.sam102022.photoshop.v2.refine.AlphaRefinementConfig;
import com.sam102022.photoshop.v2.vote.CellSelectionPolicy;

import java.util.Objects;

/**
 * Record immuable encapsulant la totalité des hyperparamètres du pipeline V2 (Sprints 1 à 9).
 *
 * @param hi                     Seuil minimal de couverture pour classifier une cellule en INSIDE (ex: 0.60).
 * @param lo                     Seuil de rejet en deçà duquel une cellule est OUTSIDE (ex: 0.05).
 * @param cropMargin             Marge de sécurité en pixels autour de la boîte englobante (ex: 90 px).
 * @param eps                    Marge géométrique d'expansion en pixels (ex: 4.0 px).
 * @param rho                    Rayon de l'élément structurant d'ouverture morphologique (ex: 5 px).
 * @param residualHoleMaxArea    Surface maximale des îlots intérieurs comblés (ex: 15 000 px).
 * @param operationMode          Mode d'opération cartographique (TERRITORY ou ZONE).
 * @param sig                    Écart-type du lissage LQR standard (ex: 22.0 px).
 * @param cornerAngle            Angle de détection des coins vifs en degrés (ex: 38.0°).
 * @param cornerWindowL          Demi-fenêtre d'échantillonnage des coins (ex: 80 px).
 * @param r0                     Rayon intérieur de préservation des coins (ex: 35.0 px).
 * @param r1                     Rayon extérieur marquant le lissage plein effet (ex: 95.0 px).
 * @param roundaboutRadiusZr     Rayon relatif d'influence des carrefours giratoires (ex: 2.0).
 * @param straightFactor         Facteur multiplicateur LQR pour tronçons droits (ex: 2.7).
 * @param straightScale          Tolérance Cauchy de l'estimateur robuste (ex: 3.0 px).
 * @param straightThreshold      Écart maximal pour qualifier un tronçon droit (ex: 3.0 px).
 * @param straightTransitionK    Demi-largeur de transition C1 des tronçons droits (ex: 10 points).
 * @param gaussianBlurSigma      Écart-type du flou gaussien séparable d'affinage (ex: 1.4 px).
 * @param contrastStiffness      Facteur de raideur de la coupure d'affinage (ex: 2.0).
 * @param roundaboutBufferInner  Rayon normé interne d'exemption des giratoires (ex: 2.3).
 * @param roundaboutBufferOuter  Rayon normé externe d'exemption des giratoires (ex: 3.0).
 * @param roadHoleMaxArea        Surface maximale des micro-trous routiers comblés (ex: 2 000 px).
 * @param supersamplingFactor    Facteur de sur-échantillonnage vectoriel sub-pixel SS (ex: 4).
 */
public record V2Config(
        double hi,
        double lo,
        int cropMargin,
        double eps,
        int rho,
        long residualHoleMaxArea,
        OperationMode operationMode,
        double sig,
        double cornerAngle,
        int cornerWindowL,
        double r0,
        double r1,
        double roundaboutRadiusZr,
        double straightFactor,
        double straightScale,
        double straightThreshold,
        int straightTransitionK,
        double gaussianBlurSigma,
        double contrastStiffness,
        double roundaboutBufferInner,
        double roundaboutBufferOuter,
        long roadHoleMaxArea,
        int supersamplingFactor
) {

    /**
     * Valide défensivement l'ensemble des invariants de la configuration V2.
     */
    public V2Config {
        if (lo < 0.0 || lo >= hi || hi > 1.0) {
            throw new IllegalArgumentException("Les seuils doivent vérifier 0.0 <= lo < hi <= 1.0.");
        }
        if (cropMargin < 0) {
            throw new IllegalArgumentException("cropMargin doit être positif ou nul.");
        }
        if (eps < 0.0 || rho < 0 || residualHoleMaxArea < 0) {
            throw new IllegalArgumentException("eps, rho et residualHoleMaxArea doivent être positifs ou nuls.");
        }
        Objects.requireNonNull(operationMode, "operationMode ne doit pas être nul.");
        if (sig <= 0.0 || cornerAngle < 0.0 || cornerWindowL <= 0 || r0 < 0.0 || r1 <= r0) {
            throw new IllegalArgumentException("Paramètres de lissage de contour invalides.");
        }
        if (roundaboutRadiusZr <= 1.0 || straightFactor < 1.0 || straightScale <= 0.0 || straightThreshold <= 0.0 || straightTransitionK < 1) {
            throw new IllegalArgumentException("Paramètres de giratoire ou de tronçon droit invalides.");
        }
        if (gaussianBlurSigma <= 0.0 || contrastStiffness < 1.0 || roundaboutBufferInner <= 0.0 || roundaboutBufferOuter <= roundaboutBufferInner || roadHoleMaxArea < 0) {
            throw new IllegalArgumentException("Paramètres d'affinage spectral invalides.");
        }
        if (supersamplingFactor < 1) {
            throw new IllegalArgumentException("supersamplingFactor doit être supérieur ou égal à 1.");
        }
    }

    /**
     * Fournit la configuration par défaut étalonnée sur le cas de référence Python V7.
     *
     * @return Configuration V2 nominale par défaut.
     */
    public static V2Config defaultConfig() {
        return new V2Config(
                0.60, 0.05, 90,
                4.0, 5, 15000L, OperationMode.TERRITORY,
                22.0, 38.0, 80, 35.0, 95.0, 2.0,
                2.7, 3.0, 3.0, 10,
                1.4, 2.0, 2.3, 3.0, 2000L,
                4
        );
    }

    /**
     * Convertit vers la politique de sélection de cellules (Sprint 4).
     *
     * @return Nouvelle instance de CellSelectionPolicy.
     */
    public CellSelectionPolicy toCellSelectionPolicy() {
        return new CellSelectionPolicy(hi, lo);
    }

    /**
     * Convertit vers la configuration d'expansion géodésique (Sprint 5).
     *
     * @return Nouvelle instance de ExpansionConfig.
     */
    public ExpansionConfig toExpansionConfig() {
        return new ExpansionConfig(eps, 41, rho, residualHoleMaxArea, 2, operationMode);
    }

    /**
     * Convertit vers la configuration de lissage de contour (Sprints 6 & 7).
     *
     * @return Nouvelle instance de ContourSmoothingConfig.
     */
    public ContourSmoothingConfig toContourSmoothingConfig() {
        return new ContourSmoothingConfig(
                1.0, cornerWindowL, cornerAngle, 4.0, sig, 3.0, 6,
                r0, r1, roundaboutRadiusZr, straightFactor, straightScale,
                straightThreshold, straightTransitionK
        );
    }

    /**
     * Convertit vers la configuration d'affinage spectral (Sprint 8).
     *
     * @return Nouvelle instance de AlphaRefinementConfig.
     */
    public AlphaRefinementConfig toAlphaRefinementConfig() {
        return new AlphaRefinementConfig(
                gaussianBlurSigma, contrastStiffness,
                roundaboutBufferInner, roundaboutBufferOuter,
                roadHoleMaxArea
        );
    }
}
```

- [ ] **Step 4: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=V2ConfigTest`  
Attendu : PASS.

---

### Task 3: Rastériseur Sub-Pixel `SupersampleRenderer`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/render/SupersampleRenderer.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/render/SupersampleRendererTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `SupersampleRenderer`**

Dans `src/test/java/com/sam102022/photoshop/v2/render/SupersampleRendererTest.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du rastériseur sub-pixel SupersampleRenderer")
class SupersampleRendererTest {

    @Test
    @DisplayName("Rastérisation sub-pixel d'un carré simple et vérification du box filter")
    void testRenderSimpleSquare() {
        // CropWindow 10x10 avec origine (5, 5) sur canevas global 20x20
        CropWindow cropWindow = new CropWindow(5, 5, 10, 10);

        // Polygone carré local centré de (2, 2) à (8, 8)
        List<PixelPoint> points = List.of(
                new PixelPoint(2.0, 2.0),
                new PixelPoint(8.0, 2.0),
                new PixelPoint(8.0, 8.0),
                new PixelPoint(2.0, 8.0)
        );
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), 10, 10, cropWindow);

        // Facteur d'affinage 1.0f partout
        float[][] factor = new float[10][10];
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                factor[y][x] = 1.0f;
            }
        }
        AlphaRefinementMap refinementMap = new AlphaRefinementMap(10, 10, factor, cropWindow);

        SupersampleRenderer renderer = new SupersampleRenderer();
        CoverageMask coverage = renderer.render(contour, refinementMap, 4, 20, 20);

        assertNotNull(coverage);
        assertEquals(20, coverage.getWidth());
        assertEquals(20, coverage.getHeight());

        // Pixel intérieur plein (global 10, 10 -> local 5, 5) doit avoir couverture 255
        assertEquals(255, coverage.get(10, 10));

        // Pixel extérieur hors cropWindow (global 0, 0) doit avoir couverture 0
        assertEquals(0, coverage.get(0, 0));

        // Pixel extérieur dans cropWindow (global 6, 6 -> local 1, 1) doit avoir couverture 0
        assertEquals(0, coverage.get(6, 6));

        // Pixel sur la frontière doit avoir une valeur anti-aliasée intermédiaire
        int edgeCov = coverage.get(7, 7); // local (2, 2)
        assertTrue(edgeCov > 0 && edgeCov <= 255, "La couverture sur le bord doit être comprise entre 0 et 255");
    }

    @Test
    @DisplayName("Modulation effective par le facteur d'affinage spectral")
    void testModulationWithRefinementMap() {
        CropWindow cropWindow = new CropWindow(0, 0, 4, 4);
        List<PixelPoint> points = List.of(
                new PixelPoint(0.0, 0.0),
                new PixelPoint(4.0, 0.0),
                new PixelPoint(4.0, 4.0),
                new PixelPoint(0.0, 4.0)
        );
        SmoothVectorContour contour = new SmoothVectorContour(points, List.of(), 4, 4, cropWindow);

        // Facteur 0.5f au centre
        float[][] factor = new float[4][4];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                factor[y][x] = 0.5f;
            }
        }
        AlphaRefinementMap refinementMap = new AlphaRefinementMap(4, 4, factor, cropWindow);

        SupersampleRenderer renderer = new SupersampleRenderer();
        CoverageMask coverage = renderer.render(contour, refinementMap, 4, 4, 4);

        // Pixel au centre pleinement couvert (alpha_ss = 1.0) modulé à 0.5 -> ~128
        int covCenter = coverage.get(2, 2);
        assertTrue(Math.abs(covCenter - 128) <= 2, "La couverture modulée doit être d'environ 128 (actuel : " + covCenter + ")");
    }

    @Test
    @DisplayName("Rejet des arguments invalides ou nuls")
    void testInvalidInputs() {
        SupersampleRenderer renderer = new SupersampleRenderer();
        CropWindow cropWindow = new CropWindow(0, 0, 4, 4);
        SmoothVectorContour contour = new SmoothVectorContour(List.of(new PixelPoint(0, 0)), List.of(), 4, 4, cropWindow);
        AlphaRefinementMap map = new AlphaRefinementMap(4, 4, new float[4][4], cropWindow);

        assertThrows(NullPointerException.class, () -> renderer.render(null, map, 4, 10, 10));
        assertThrows(NullPointerException.class, () -> renderer.render(contour, null, 4, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(contour, map, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(contour, map, 4, 0, 10));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car `SupersampleRenderer` n'existe pas.

- [ ] **Step 3: Implémenter `SupersampleRenderer`**

Dans `src/main/java/com/sam102022/photoshop/v2/render/SupersampleRenderer.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Rastériseur sub-pixel haute-fidélité par supersampling ($SS=4$), filtrage boîte (Box Filter)
 * et modulation spectrale directe (Sprint 9).
 */
public class SupersampleRenderer {

    private static final Logger LOGGER = Logger.getLogger(SupersampleRenderer.class.getName());

    /**
     * Constructeur par défaut.
     */
    public SupersampleRenderer() {
    }

    /**
     * Rastérise le contour vectoriel lissé avec sur-échantillonnage, applique la réduction boîte
     * et module la couverture continue avec la matrice d'affinage spectral.
     *
     * @param contour             Contour vectoriel lissé sub-pixel (non nul).
     * @param refinementMap       Matrice de modulation d'affinage spectral locale (non nulle).
     * @param supersamplingFactor Facteur d'échantillonnage sub-pixel (>= 1, nominalement 4).
     * @param fullWidth           Largeur totale du canevas cartographique global en pixels (> 0).
     * @param fullHeight          Hauteur totale du canevas cartographique global en pixels (> 0).
     * @return Masque matriciel de couverture continue [0..255] conforme à l'ADR-002.
     * @throws NullPointerException     si contour ou refinementMap est nul.
     * @throws IllegalArgumentException si les dimensions ou le facteur SS sont invalides.
     */
    public CoverageMask render(
            SmoothVectorContour contour,
            AlphaRefinementMap refinementMap,
            int supersamplingFactor,
            int fullWidth,
            int fullHeight
    ) {
        validateInputs(contour, refinementMap, supersamplingFactor, fullWidth, fullHeight);

        CropWindow crop = contour.cropWindow();
        int localW = crop.width();
        int localH = crop.height();
        int ssW = localW * supersamplingFactor;
        int ssH = localH * supersamplingFactor;

        LOGGER.info(() -> String.format(
                "[Sprint 9] Rastérisation sub-pixel SS=%d sur ROI (%dx%d px -> %dx%d sous-pixels)...",
                supersamplingFactor, localW, localH, ssW, ssH
        ));

        byte[] highResData = rasterizeHighResPolygon(contour.points(), ssW, ssH, supersamplingFactor);
        CoverageMask globalCoverage = new CoverageMask(fullWidth, fullHeight);

        float totalSubPixels = (float) (supersamplingFactor * supersamplingFactor);

        for (int ly = 0; ly < localH; ly++) {
            int gy = crop.y0() + ly;
            if (gy < 0 || gy >= fullHeight) {
                continue;
            }
            for (int lx = 0; lx < localW; lx++) {
                int gx = crop.x0() + lx;
                if (gx < 0 || gx >= fullWidth) {
                    continue;
                }

                int activeCount = countActiveSubPixels(highResData, lx, ly, ssW, supersamplingFactor);
                if (activeCount == 0) {
                    continue;
                }

                float alphaSs = activeCount / totalSubPixels;
                float finalAlpha = alphaSs * refinementMap.factorAt(lx, ly);
                int cov = Math.clamp(Math.round(finalAlpha * 255.0f), 0, 255);
                if (cov > 0) {
                    globalCoverage.set(gx, gy, cov);
                }
            }
        }

        return globalCoverage;
    }

    /**
     * Rastérise le polygone continu avec décalage de demi-pixel sur une image haute résolution monochrome.
     *
     * @param points Sommets du contour vectoriel dans le repère local de la ROI.
     * @param ssW    Largeur haute résolution.
     * @param ssH    Hauteur haute résolution.
     * @param ss     Facteur d'échelle de supersampling.
     * @return Tableau linéaire des octets de l'image (0 ou 255).
     */
    private byte[] rasterizeHighResPolygon(List<PixelPoint> points, int ssW, int ssH, int ss) {
        BufferedImage highRes = new BufferedImage(ssW, ssH, BufferedImage.TYPE_BYTE_GRAY);
        if (points.size() < 3) {
            return ((DataBufferByte) highRes.getRaster().getDataBuffer()).getData();
        }

        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD, points.size());
        PixelPoint first = points.get(0);
        path.moveTo((first.x() + 0.5) * ss, (first.y() + 0.5) * ss);

        for (int i = 1; i < points.size(); i++) {
            PixelPoint p = points.get(i);
            path.lineTo((p.x() + 0.5) * ss, (p.y() + 0.5) * ss);
        }
        path.closePath();

        Graphics2D g2 = highRes.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(Color.WHITE);
            g2.fill(path);
        } finally {
            g2.dispose();
        }

        return ((DataBufferByte) highRes.getRaster().getDataBuffer()).getData();
    }

    /**
     * Dénombre le nombre de sous-pixels actifs dans le bloc carré SS x SS.
     *
     * @param data Tableau d'octets de l'image haute résolution.
     * @param lx   Abscisse locale du pixel cible.
     * @param ly   Ordonnée locale du pixel cible.
     * @param ssW  Largeur haute résolution.
     * @param ss   Facteur d'échelle.
     * @return Nombre de sous-pixels non nuls compris entre 0 et ss*ss.
     */
    private int countActiveSubPixels(byte[] data, int lx, int ly, int ssW, int ss) {
        int count = 0;
        int startY = ly * ss;
        int startX = lx * ss;

        for (int dy = 0; dy < ss; dy++) {
            int rowOffset = (startY + dy) * ssW;
            for (int dx = 0; dx < ss; dx++) {
                if (data[rowOffset + startX + dx] != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Valide les préconditions et arguments d'entrée.
     */
    private void validateInputs(
            SmoothVectorContour contour,
            AlphaRefinementMap refinementMap,
            int supersamplingFactor,
            int fullWidth,
            int fullHeight
    ) {
        Objects.requireNonNull(contour, "contour ne doit pas être nul.");
        Objects.requireNonNull(refinementMap, "refinementMap ne doit pas être nulle.");
        if (supersamplingFactor < 1) {
            throw new IllegalArgumentException("supersamplingFactor doit être >= 1 : " + supersamplingFactor);
        }
        if (fullWidth <= 0 || fullHeight <= 0) {
            throw new IllegalArgumentException("Dimensions du canevas invalides : " + fullWidth + "x" + fullHeight);
        }
    }
}
```

- [ ] **Step 4: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=SupersampleRendererTest`  
Attendu : PASS.

---

### Task 4: Découpeur & Assembleur `ImageClipper`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/render/ImageClipper.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/render/ImageClipperTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `ImageClipper`**

Dans `src/test/java/com/sam102022/photoshop/v2/render/ImageClipperTest.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests unitaires du découpeur et assembleur ImageClipper")
class ImageClipperTest {

    @Test
    @DisplayName("Assemblage conforme de clipped (ARGB), mask (BYTE_GRAY) et overlay (RGB)")
    void testClipComposition() {
        int w = 10;
        int h = 10;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        // Peindre la carte en bleu
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                map.setRGB(x, y, 0x0000FF);
            }
        }

        CoverageMask coverage = new CoverageMask(w, h);
        // Définir un bloc 4x4 au centre avec couverture 255
        for (int y = 3; y <= 6; y++) {
            for (int x = 3; x <= 6; x++) {
                coverage.set(x, y, 255);
            }
        }
        // Pixel avec couverture partielle à (2, 2)
        coverage.set(2, 2, 128);

        ImageClipper clipper = new ImageClipper();
        RenderResult result = clipper.clip(map, coverage);

        assertNotNull(result);

        // 1. Clipped (ARGB)
        BufferedImage clipped = result.clipped();
        assertEquals(BufferedImage.TYPE_INT_ARGB, clipped.getType());
        // Pixel intérieur : alpha 255, bleu conservé
        int argbInside = clipped.getRGB(4, 4);
        assertEquals(255, (argbInside >>> 24) & 0xFF);
        assertEquals(0x0000FF, argbInside & 0x00FFFFFF);

        // Pixel extérieur : alpha 0 (transparent)
        int argbOutside = clipped.getRGB(0, 0);
        assertEquals(0, (argbOutside >>> 24) & 0xFF);

        // Pixel partiel à (2, 2) : alpha = (255 * 128 + 127) / 255 = 128
        int argbPartial = clipped.getRGB(2, 2);
        assertEquals(128, (argbPartial >>> 24) & 0xFF);

        // 2. Mask (BYTE_GRAY)
        BufferedImage mask = result.mask();
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, mask.getType());
        assertEquals(255, mask.getRaster().getSample(4, 4, 0));
        assertEquals(0, mask.getRaster().getSample(0, 0, 0));
        assertEquals(128, mask.getRaster().getSample(2, 2, 0));

        // 3. Overlay (RGB) : bordure rouge vif #FF0000 sur le pourtour du bloc
        BufferedImage overlay = result.overlay();
        assertEquals(BufferedImage.TYPE_INT_RGB, overlay.getType());
        // Le pixel (3, 3) est sur le bord du bloc 4x4 -> doit être rouge
        assertEquals(0xFF0000, overlay.getRGB(3, 3) & 0xFFFFFF);
        // Le pixel intérieur (4, 4) n'est pas sur le bord (tous voisins actifs) -> couleur d'origine bleue
        assertEquals(0x0000FF, overlay.getRGB(4, 4) & 0xFFFFFF);
    }

    @Test
    @DisplayName("Rejet des arguments invalides ou de dimensions incohérentes")
    void testInvalidInputs() {
        ImageClipper clipper = new ImageClipper();
        BufferedImage img10 = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        CoverageMask cov10 = new CoverageMask(10, 10);
        CoverageMask cov20 = new CoverageMask(20, 20);

        assertThrows(NullPointerException.class, () -> clipper.clip(null, cov10));
        assertThrows(NullPointerException.class, () -> clipper.clip(img10, null));
        assertThrows(IllegalArgumentException.class, () -> clipper.clip(img10, cov20));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car `ImageClipper` n'existe pas.

- [ ] **Step 3: Implémenter `ImageClipper`**

Dans `src/main/java/com/sam102022/photoshop/v2/render/ImageClipper.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.CoverageMask;

import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Assembleur et découpeur d'images produisant le trio de livrables graphiques de production :
 * image détourée ARGB, masque monochrome 8-bit et image de contrôle avec contour rouge vif (Sprint 9).
 */
public class ImageClipper {

    private static final Logger LOGGER = Logger.getLogger(ImageClipper.class.getName());

    /**
     * Constructeur par défaut.
     */
    public ImageClipper() {
    }

    /**
     * Découpe l'image source selon le masque de couverture et assemble les trois images de sortie.
     *
     * @param mapImage     Image source cartographique (non nulle).
     * @param coverageMask Masque de couverture sub-pixel continue [0..255] (non nul).
     * @return Record immuable RenderResult contenant les trois images et le masque de couverture.
     * @throws NullPointerException     si mapImage ou coverageMask est nul.
     * @throws IllegalArgumentException si les dimensions sont divergentes.
     */
    public RenderResult clip(BufferedImage mapImage, CoverageMask coverageMask) {
        validateInputs(mapImage, coverageMask);

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();

        LOGGER.info(() -> String.format(
                "[Sprint 9] Assemblage des livrables finaux (%dx%d px)...", w, h
        ));

        BufferedImage clipped = createClippedImage(mapImage, coverageMask, w, h);
        BufferedImage mask = createMaskImage(coverageMask, w, h);
        BufferedImage overlay = createOverlayImage(mapImage, coverageMask, w, h);

        return new RenderResult(clipped, mask, overlay, coverageMask);
    }

    /**
     * Crée l'image cartographique détourée ARGB avec canal alpha calculé.
     */
    private BufferedImage createClippedImage(BufferedImage mapImage, CoverageMask coverageMask, int w, int h) {
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        boolean hasAlpha = mapImage.getColorModel().hasAlpha();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cov = coverageMask.get(x, y);
                if (cov == 0) {
                    continue;
                }
                int rgb = mapImage.getRGB(x, y);
                int srcAlpha = hasAlpha ? ((rgb >>> 24) & 0xFF) : 255;
                int finalAlpha = (srcAlpha * cov + 127) / 255;
                if (finalAlpha > 0) {
                    clipped.setRGB(x, y, (finalAlpha << 24) | (rgb & 0x00FFFFFF));
                }
            }
        }
        return clipped;
    }

    /**
     * Crée le masque monochrome 8-bit TYPE_BYTE_GRAY reflétant les niveaux de couverture.
     */
    private BufferedImage createMaskImage(CoverageMask coverageMask, int w, int h) {
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask.getRaster().setSample(x, y, 0, coverageMask.get(x, y));
            }
        }
        return mask;
    }

    /**
     * Crée l'image de contrôle overlay superposant la bordure rouge vif (#FF0000) sur la carte.
     */
    private BufferedImage createOverlayImage(BufferedImage mapImage, CoverageMask coverageMask, int w, int h) {
        BufferedImage overlay = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        // 1. Recopie de l'image source
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                overlay.setRGB(x, y, mapImage.getRGB(x, y) & 0x00FFFFFF);
            }
        }

        // 2. Détection de bordure morphologique : Edge = Mfull & ~erode(Mfull)
        boolean[] mFull = buildThresholdMask(coverageMask, w, h, 128);

        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                if (mFull[rowOffset + x] && isEdgePixel(mFull, x, y, w, h)) {
                    overlay.setRGB(x, y, 0xFF0000);
                }
            }
        }

        return overlay;
    }

    /**
     * Construit un tableau booléen aplati à partir du seuillage de couverture.
     */
    private boolean[] buildThresholdMask(CoverageMask coverageMask, int w, int h, int threshold) {
        boolean[] mask = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            int rowOffset = y * w;
            for (int x = 0; x < w; x++) {
                mask[rowOffset + x] = coverageMask.get(x, y) >= threshold;
            }
        }
        return mask;
    }

    /**
     * Détermine si un pixel actif possède au moins un voisin orthogonal inactif (bordure).
     */
    private boolean isEdgePixel(boolean[] mask, int x, int y, int w, int h) {
        if (x == 0 || x == w - 1 || y == 0 || y == h - 1) {
            return true;
        }
        return !mask[y * w + (x - 1)]
                || !mask[y * w + (x + 1)]
                || !mask[(y - 1) * w + x]
                || !mask[(y + 1) * w + x];
    }

    /**
     * Valide la cohérence des arguments d'entrée.
     */
    private void validateInputs(BufferedImage mapImage, CoverageMask coverageMask) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(coverageMask, "coverageMask ne doit pas être nul.");
        if (mapImage.getWidth() != coverageMask.getWidth() || mapImage.getHeight() != coverageMask.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions incompatibles entre mapImage (%dx%d) et coverageMask (%dx%d).",
                    mapImage.getWidth(), mapImage.getHeight(),
                    coverageMask.getWidth(), coverageMask.getHeight()
            ));
        }
    }
}
```

- [ ] **Step 4: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=ImageClipperTest`  
Attendu : PASS.

---

### Task 5: Orchestrateur de Bout en Bout `V2Pipeline`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Pipeline.java`
- Create: `src/test/java/com/sam102022/photoshop/v2/pipeline/V2PipelineTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `V2Pipeline`**

Dans `src/test/java/com/sam102022/photoshop/v2/pipeline/V2PipelineTest.java` :

```java
package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.TerritoryGeometry;
import com.sam102022.photoshop.v2.render.RenderResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Tests unitaires de l'orchestrateur de bout en bout V2Pipeline")
class V2PipelineTest {

    @Test
    @DisplayName("Exécution fonctionnelle minimale sur canevas synthétique")
    void testMinimalPipelineExecution() {
        int w = 200;
        int h = 200;

        BufferedImage mapImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BinaryMask roadMask = new BinaryMask(w, h);

        // Dessiner un carroyage routier simple
        for (int i = 0; i < w; i++) {
            roadMask.set(i, 50, true);
            roadMask.set(i, 150, true);
            roadMask.set(50, i, true);
            roadMask.set(150, i, true);
        }

        // Polygone englobant centré
        MapContext mapContext = new MapContext(new GeoCoordinate(0.0, 0.0), 10, w, h);
        List<GeoCoordinate> ring = List.of(
                new GeoCoordinate(-0.01, -0.01),
                new GeoCoordinate(0.01, -0.01),
                new GeoCoordinate(0.01, 0.01),
                new GeoCoordinate(-0.01, 0.01)
        );
        TerritoryGeometry geometry = new TerritoryGeometry(List.of(ring), List.of());

        V2Pipeline pipeline = new V2Pipeline();
        V2Config config = V2Config.defaultConfig();

        RenderResult result = pipeline.execute(mapImage, geometry, mapContext, roadMask, config);

        assertNotNull(result);
        assertEquals(w, result.clipped().getWidth());
        assertEquals(h, result.clipped().getHeight());
        assertEquals(w, result.coverageMask().getWidth());
        assertEquals(h, result.coverageMask().getHeight());
    }

    @Test
    @DisplayName("Rejet des paramètres obligatoires nuls")
    void testRejectsNullParameters() {
        V2Pipeline pipeline = new V2Pipeline();
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        MapContext ctx = new MapContext(new GeoCoordinate(0, 0), 10, 10, 10);
        TerritoryGeometry geom = new TerritoryGeometry(List.of(List.of(new GeoCoordinate(0, 0))), List.of());
        BinaryMask road = new BinaryMask(10, 10);
        V2Config cfg = V2Config.defaultConfig();

        assertThrows(NullPointerException.class, () -> pipeline.execute(null, geom, ctx, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, null, ctx, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, null, road, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, ctx, (BinaryMask) null, cfg));
        assertThrows(NullPointerException.class, () -> pipeline.execute(map, geom, ctx, road, null));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car `V2Pipeline` n'existe pas.

- [ ] **Step 3: Implémenter `V2Pipeline`**

Dans `src/main/java/com/sam102022/photoshop/v2/pipeline/V2Pipeline.java` :

```java
package com.sam102022.photoshop.v2.pipeline;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.cell.BoundingBoxCropper;
import com.sam102022.photoshop.v2.cell.CellLabeler;
import com.sam102022.photoshop.v2.cell.CellLabelingResult;
import com.sam102022.photoshop.v2.cell.CropWindow;
import com.sam102022.photoshop.v2.contour.ContourSmoothingEngine;
import com.sam102022.photoshop.v2.contour.SmoothVectorContour;
import com.sam102022.photoshop.v2.expansion.ConsolidatedMask;
import com.sam102022.photoshop.v2.expansion.RoadBoundaryConsolidator;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.PolygonMask;
import com.sam102022.photoshop.v2.geometry.PolygonRasterizer;
import com.sam102022.photoshop.v2.geometry.TerritoryGeometry;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;
import com.sam102022.photoshop.v2.refine.AlphaRefiner;
import com.sam102022.photoshop.v2.refine.AlphaRefinementMap;
import com.sam102022.photoshop.v2.render.ImageClipper;
import com.sam102022.photoshop.v2.render.RenderResult;
import com.sam102022.photoshop.v2.render.SupersampleRenderer;
import com.sam102022.photoshop.v2.vote.CellSelection;
import com.sam102022.photoshop.v2.vote.TopologicalVoteEngine;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Orchestrateur fonctionnel de bout en bout du pipeline V2 reliant les 9 étapes algorithmiques (Sprints 1 à 9).
 */
public class V2Pipeline {

    private static final Logger LOGGER = Logger.getLogger(V2Pipeline.class.getName());

    private final PolygonRasterizer polygonRasterizer;
    private final BoundingBoxCropper cropper;
    private final CellLabeler cellLabeler;
    private final TopologicalVoteEngine voteEngine;
    private final RoadBoundaryConsolidator consolidator;
    private final ContourSmoothingEngine smoothingEngine;
    private final AlphaRefiner alphaRefiner;
    private final SupersampleRenderer supersampleRenderer;
    private final ImageClipper imageClipper;

    /**
     * Initialise le pipeline complet avec ses sous-composants par défaut.
     */
    public V2Pipeline() {
        this(
                new PolygonRasterizer(),
                new BoundingBoxCropper(),
                new CellLabeler(),
                new TopologicalVoteEngine(),
                new RoadBoundaryConsolidator(),
                new ContourSmoothingEngine(),
                new AlphaRefiner(),
                new SupersampleRenderer(),
                new ImageClipper()
        );
    }

    /**
     * Initialise le pipeline avec injection explicite des sous-composants.
     */
    public V2Pipeline(
            PolygonRasterizer polygonRasterizer,
            BoundingBoxCropper cropper,
            CellLabeler cellLabeler,
            TopologicalVoteEngine voteEngine,
            RoadBoundaryConsolidator consolidator,
            ContourSmoothingEngine smoothingEngine,
            AlphaRefiner alphaRefiner,
            SupersampleRenderer supersampleRenderer,
            ImageClipper imageClipper
    ) {
        this.polygonRasterizer = Objects.requireNonNull(polygonRasterizer, "polygonRasterizer ne doit pas être nul.");
        this.cropper = Objects.requireNonNull(cropper, "cropper ne doit pas être nul.");
        this.cellLabeler = Objects.requireNonNull(cellLabeler, "cellLabeler ne doit pas être nul.");
        this.voteEngine = Objects.requireNonNull(voteEngine, "voteEngine ne doit pas être nul.");
        this.consolidator = Objects.requireNonNull(consolidator, "consolidator ne doit pas être nul.");
        this.smoothingEngine = Objects.requireNonNull(smoothingEngine, "smoothingEngine ne doit pas être nul.");
        this.alphaRefiner = Objects.requireNonNull(alphaRefiner, "alphaRefiner ne doit pas être nul.");
        this.supersampleRenderer = Objects.requireNonNull(supersampleRenderer, "supersampleRenderer ne doit pas être nul.");
        this.imageClipper = Objects.requireNonNull(imageClipper, "imageClipper ne doit pas être nul.");
    }

    /**
     * Exécute le pipeline complet avec configuration par défaut.
     *
     * @param mapImage   Image source cartographique (non nulle).
     * @param geometry   Géométrie vectorielle du territoire (non nulle).
     * @param mapContext Contexte de projection géographique (non nul).
     * @param roadMask   Masque binaire des axes routiers (non nul).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask
    ) {
        return execute(mapImage, geometry, mapContext, roadMask, V2Config.defaultConfig());
    }

    /**
     * Exécute le pipeline complet selon la configuration fournie.
     *
     * @param mapImage   Image source cartographique (non nulle).
     * @param geometry   Géométrie vectorielle du territoire (non nulle).
     * @param mapContext Contexte de projection géographique (non nul).
     * @param roadMask   Masque binaire des axes routiers (non nul).
     * @param config     Configuration unifiée du pipeline V2 (non nulle).
     * @return Record immuable RenderResult.
     */
    public RenderResult execute(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask,
            V2Config config
    ) {
        validateInputs(mapImage, geometry, mapContext, roadMask, config);
        long startTime = System.currentTimeMillis();
        int width = mapImage.getWidth();
        int height = mapImage.getHeight();

        LOGGER.info(() -> String.format("[Pipeline V2] Démarrage du traitement (%dx%d px)...", width, height));

        // 1. Projection et rastérisation du polygone d'intention P (Sprint 1)
        List<PixelPoint> polygonPoints = projectPolygon(geometry, mapContext);
        PolygonMask polygonMask = polygonRasterizer.rasterize(width, height, List.of(polygonPoints), List.of());

        // 2. Recadrage et étiquetage des cellules (Sprint 3)
        CropWindow cropWindow = cropper.computeCropWindow(polygonPoints, width, height, config.cropMargin());
        BinaryMask croppedRoad = cropper.crop(roadMask, cropWindow);
        BinaryMask croppedPoly = cropper.crop(polygonMask.mask(), cropWindow);
        CellLabelingResult labelingResult = cellLabeler.label(croppedRoad);

        // 3. Vote topologique (Sprint 4)
        CellSelection selection = voteEngine.execute(
                labelingResult.labelMap(),
                labelingResult.cells(),
                croppedPoly,
                config.toCellSelectionPolicy()
        );

        // 4. Consolidation géodésique et barrières (Sprint 5)
        ConsolidatedMask consolidated = consolidator.consolidate(
                selection.retainedMask(),
                croppedRoad,
                cropWindow,
                config.toExpansionConfig()
        );

        // 5. Lissage vectoriel multi-échelle et substitution giratoires (Sprints 6 & 7)
        SmoothVectorContour smoothContour = smoothingEngine.process(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                labelingResult.cells(),
                selection.retainedMask(),
                config.toContourSmoothingConfig()
        );

        // 6. Affinage spectral (Sprint 8)
        AlphaRefinementMap refinementMap = alphaRefiner.refine(
                consolidated,
                croppedRoad,
                labelingResult.labelMap(),
                smoothContour.substitutedRoundabouts(),
                config.toAlphaRefinementConfig()
        );

        // 7. Rendu Sub-Pixel Supersampling SS=4 (Sprint 9)
        CoverageMask coverageMask = supersampleRenderer.render(
                smoothContour,
                refinementMap,
                config.supersamplingFactor(),
                width,
                height
        );

        // 8. Assemblage et découpe finale (Sprint 9)
        RenderResult renderResult = imageClipper.clip(mapImage, coverageMask);

        long totalElapsed = System.currentTimeMillis() - startTime;
        LOGGER.info(() -> String.format("[Pipeline V2] Pipeline achevé avec succès en %d ms.", totalElapsed));

        return renderResult;
    }

    /**
     * Projette les sommets du premier anneau extérieur géographique en coordonnées pixels.
     */
    private List<PixelPoint> projectPolygon(TerritoryGeometry geometry, MapContext mapContext) {
        if (geometry.outerRings().isEmpty()) {
            throw new IllegalArgumentException("La géométrie du territoire ne contient aucun anneau extérieur.");
        }
        WebMercatorProjection projection = new WebMercatorProjection(mapContext);
        return geometry.outerRings().get(0).stream()
                .map(projection::toPixel)
                .toList();
    }

    /**
     * Valide la présence de tous les arguments obligatoires.
     */
    private void validateInputs(
            BufferedImage mapImage,
            TerritoryGeometry geometry,
            MapContext mapContext,
            BinaryMask roadMask,
            V2Config config
    ) {
        Objects.requireNonNull(mapImage, "mapImage ne doit pas être nulle.");
        Objects.requireNonNull(geometry, "geometry ne doit pas être nulle.");
        Objects.requireNonNull(mapContext, "mapContext ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(config, "config ne doit pas être nulle.");
    }
}
```

- [ ] **Step 4: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=V2PipelineTest`  
Attendu : PASS.

---

### Task 6: CLI V2 & Intégration `V2CliRunner`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/cli/V2CliRunner.java`
- Modify: `src/main/java/com/sam102022/photoshop/cli/CliRunner.java`
- Create: `src/test/java/com/sam102022/photoshop/cli/V2CliRunnerTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `V2CliRunner`**

Dans `src/test/java/com/sam102022/photoshop/cli/V2CliRunnerTest.java` :

```java
package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du runner CLI V2")
class V2CliRunnerTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @BeforeEach
    void setUpStreams() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Affichage de l'aide avec --help ou -h")
    void testHelpOption() {
        int exitCode = V2CliRunner.run(new String[]{"--help"});
        assertEquals(0, exitCode);
        assertTrue(outContent.toString().contains("Pipeline V2"));
        assertTrue(outContent.toString().contains("--map"));
        assertTrue(outContent.toString().contains("--json"));
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants")
    void testMissingArguments() {
        int exitCode = V2CliRunner.run(new String[]{});
        assertEquals(1, exitCode);

        int exitCode2 = V2CliRunner.run(new String[]{"--map", "carte.png"});
        assertEquals(1, exitCode2);
        assertTrue(errContent.toString().contains("Erreur"));
    }

    @Test
    @DisplayName("Exécution complète en ligne de commande avec fichiers de test CA01")
    void testExecutionWithCA01Files(@TempDir Path tempDir) {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        Path mapPath = Paths.get("src/test/resources/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        Path roadPath = Paths.get("maps/road.png");

        if (!Files.exists(jsonPath) || !Files.exists(mapPath) || !Files.exists(roadPath)) {
            return; // Saut propre si hors environnement fixture
        }

        String[] args = new String[]{
                "--v2",
                "--map", mapPath.toString(),
                "--json", jsonPath.toString(),
                "--road", roadPath.toString(),
                "--out-dir", tempDir.toString()
        };

        int exitCode = V2CliRunner.run(args);
        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve("clipped.png")));
        assertTrue(Files.exists(tempDir.resolve("mask.png")));
        assertTrue(Files.exists(tempDir.resolve("overlay.png")));
    }
}
```

- [ ] **Step 2: Vérifier l'échec de compilation**

Exécuter : `mvn test-compile`  
Attendu : Échec car `V2CliRunner` n'existe pas.

- [ ] **Step 3: Implémenter `V2CliRunner`**

Dans `src/main/java/com/sam102022/photoshop/cli/V2CliRunner.java` :

```java
package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.OperationMode;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.pipeline.V2Config;
import com.sam102022.photoshop.v2.pipeline.V2Pipeline;
import com.sam102022.photoshop.v2.render.RenderResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Point d'entrée d'exécution en ligne de commande pour le pipeline V2 haute-fidélité (Sprint 9).
 */
public final class V2CliRunner {

    private V2CliRunner() {
    }

    /**
     * Point d'entrée principal pour la commande CLI V2.
     *
     * @param args Arguments de la ligne de commande.
     * @return Code de sortie (0 en cas de succès, 1 en cas d'erreur ou d'aide).
     */
    public static int run(String[] args) {
        if (args == null || args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp(System.out);
            return (args == null || args.length == 0) ? 1 : 0;
        }

        try {
            return executePipeline(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur de syntaxe ou de configuration V2 : " + e.getMessage());
            printHelp(System.err);
            return 1;
        } catch (Exception e) {
            System.err.println("Erreur lors de l'exécution du pipeline V2 : " + e.getMessage());
            return 1;
        }
    }

    /**
     * Exécute le pipeline V2 complet à partir des arguments CLI.
     */
    private static int executePipeline(String[] args) throws IOException {
        String mapPathStr = getRequiredOptionValue(args, "--map");
        String jsonPathStr = getRequiredOptionValue(args, "--json");
        String roadPathStr = getOptionValue(args, "--road", null);
        String outDirStr = getOptionValue(args, "--out-dir", null);
        String clippedOutStr = getOptionValue(args, "--output", null);
        String maskOutStr = getOptionValue(args, "--mask-out", null);
        String overlayOutStr = getOptionValue(args, "--overlay-out", null);

        Path mapPath = Paths.get(mapPathStr);
        Path jsonPath = Paths.get(jsonPathStr);
        if (!Files.exists(mapPath)) {
            throw new IllegalArgumentException("Le fichier image cartographique n'existe pas : " + mapPath);
        }
        if (!Files.exists(jsonPath)) {
            throw new IllegalArgumentException("Le fichier JSON de géométrie n'existe pas : " + jsonPath);
        }

        System.out.println("-> [V2] Chargement des données sources...");
        BufferedImage mapImg = ImageLoader.load(mapPath);

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);

        BinaryMask roadMask;
        if (roadPathStr != null && Files.exists(Paths.get(roadPathStr))) {
            System.out.println("-> [V2] Chargement du masque routier : " + roadPathStr);
            BufferedImage roadImg = ImageIO.read(Paths.get(roadPathStr).toFile());
            roadMask = BinaryMask.fromImage(roadImg, 128);
        } else {
            Path defaultRoad = Paths.get("maps/road.png");
            if (Files.exists(defaultRoad)) {
                System.out.println("-> [V2] Utilisation du masque routier par défaut : " + defaultRoad);
                BufferedImage roadImg = ImageIO.read(defaultRoad.toFile());
                roadMask = BinaryMask.fromImage(roadImg, 128);
            } else {
                throw new IllegalArgumentException("Aucun masque routier fourni (--road) et maps/road.png introuvable.");
            }
        }

        V2Config config = parseConfig(args);
        V2Pipeline pipeline = new V2Pipeline();
        RenderResult result = pipeline.execute(mapImg, loaded.geometry(), loaded.mapContext(), roadMask, config);

        saveOutputs(result, outDirStr, clippedOutStr, maskOutStr, overlayOutStr, mapPath);
        System.out.println("Succès ! Pipeline V2 achevé avec succès.");
        return 0;
    }

    /**
     * Parse et assemble la configuration V2 à partir des options fournies.
     */
    private static V2Config parseConfig(String[] args) {
        V2Config def = V2Config.defaultConfig();
        int ss = getIntOption(args, "--ss", def.supersamplingFactor());
        int cropMargin = getIntOption(args, "--crop-margin", def.cropMargin());
        double eps = getDoubleOption(args, "--eps", def.eps());
        int rho = getIntOption(args, "--rho", def.rho());
        double sig = getDoubleOption(args, "--sig", def.sig());
        double cornerAngle = getDoubleOption(args, "--corner-angle", def.cornerAngle());
        String modeStr = getOptionValue(args, "--mode", "territory");
        OperationMode mode = OperationMode.fromString(modeStr);

        return new V2Config(
                def.hi(), def.lo(), cropMargin, eps, rho, def.residualHoleMaxArea(),
                mode, sig, cornerAngle, def.cornerWindowL(), def.r0(), def.r1(),
                def.roundaboutRadiusZr(), def.straightFactor(), def.straightScale(),
                def.straightThreshold(), def.straightTransitionK(), def.gaussianBlurSigma(),
                def.contrastStiffness(), def.roundaboutBufferInner(), def.roundaboutBufferOuter(),
                def.roadHoleMaxArea(), ss
        );
    }

    /**
     * Sauvegarde les images produites selon les chemins configurés.
     */
    private static void saveOutputs(
            RenderResult result,
            String outDirStr,
            String clippedOutStr,
            String maskOutStr,
            String overlayOutStr,
            Path sourceMapPath
    ) throws IOException {
        Path outDir = outDirStr != null ? Paths.get(outDirStr) : sourceMapPath.getParent();
        if (outDir == null) {
            outDir = Paths.get(".");
        }

        Path clippedPath = clippedOutStr != null ? Paths.get(clippedOutStr) : outDir.resolve("clipped.png");
        Path maskPath = maskOutStr != null ? Paths.get(maskOutStr) : outDir.resolve("mask.png");
        Path overlayPath = overlayOutStr != null ? Paths.get(overlayOutStr) : outDir.resolve("overlay.png");

        System.out.println("-> [V2] Enregistrement des livrables graphiques...");
        ImageExporter.savePng(result.clipped(), clippedPath);
        System.out.println("   [Livrable] Image détourée ARGB : " + clippedPath.toAbsolutePath());
        ImageExporter.savePng(result.mask(), maskPath);
        System.out.println("   [Livrable] Masque monochrome 8-bit : " + maskPath.toAbsolutePath());
        ImageExporter.savePng(result.overlay(), overlayPath);
        System.out.println("   [Livrable] Image de contrôle overlay : " + overlayPath.toAbsolutePath());
    }

    /**
     * Affiche le manuel d'aide utilisateur.
     */
    private static void printHelp(PrintStream out) {
        out.println("Usage: java -jar photoshop-1.0.jar --v2 [options]");
        out.println("Pipeline V2 de détourage cartographique sub-pixel haute-fidélité.");
        out.println();
        out.println("Options obligatoires :");
        out.println("  --map <chemin>         Image cartographique source (ex: 05_style_contraste_sans_rien.png)");
        out.println("  --json <chemin>        Fichier JSON contenant les polygones et coordonnées cartographiques");
        out.println();
        out.println("Options optionnelles :");
        out.println("  --road <chemin>        Image du masque routier (défaut : maps/road.png)");
        out.println("  --out-dir <dossier>    Dossier de destination pour clipped.png, mask.png, overlay.png");
        out.println("  --output <chemin>      Chemin explicite pour l'image détourée clipped.png");
        out.println("  --mask-out <chemin>    Chemin explicite pour le masque monochrome mask.png");
        out.println("  --overlay-out <chemin> Chemin explicite pour l'image de contrôle overlay.png");
        out.println("  --ss <int>             Facteur de supersampling vectoriel (défaut : 4)");
        out.println("  --mode <nom>           Mode d'opération : territory, zone (défaut : territory)");
        out.println("  --crop-margin <int>    Marge de sécurité de la boîte englobante en pixels (défaut : 90)");
        out.println("  --eps <double>         Marge de sécurité d'expansion géodésique (défaut : 4.0)");
        out.println("  --help, -h             Affiche ce message d'aide.");
    }

    private static boolean hasOption(String[] args, String... options) {
        for (String arg : args) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(arg)) return true;
            }
        }
        return false;
    }

    private static String getRequiredOptionValue(String[] args, String option) {
        String val = getOptionValue(args, option, null);
        if (val == null) {
            throw new IllegalArgumentException("Option obligatoire manquante : " + option);
        }
        return val;
    }

    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    return args[i + 1];
                }
                throw new IllegalArgumentException("Valeur manquante pour l'option : " + option);
            }
        }
        return defaultValue;
    }

    private static int getIntOption(String[] args, String option, int defaultValue) {
        String val = getOptionValue(args, option, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur entière invalide pour " + option + " : " + val);
        }
    }

    private static double getDoubleOption(String[] args, String option, double defaultValue) {
        String val = getOptionValue(args, option, null);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur décimale invalide pour " + option + " : " + val);
        }
    }
}
```

- [ ] **Step 4: Mettre à jour `CliRunner.java` pour déléguer vers `V2CliRunner` lors de la présence de `--v2`**

Dans `src/main/java/com/sam102022/photoshop/cli/CliRunner.java` :
Au début de `run(String[] args)` :
```java
        if (hasOption(args, "--v2")) {
            return V2CliRunner.run(args);
        }
```

- [ ] **Step 5: Exécuter le test unitaire pour vérifier son passage**

Exécuter : `mvn test -Dtest=V2CliRunnerTest`  
Attendu : PASS.

---

### Task 7: Test d'Intégration Pivot CA01 (`Sprint9IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/render/Sprint9IntegrationTest.java`

- [ ] **Step 1: Écrire le test d'intégration pivot étalon pour le Sprint 9**

Dans `src/test/java/com/sam102022/photoshop/v2/render/Sprint9IntegrationTest.java` :

```java
package com.sam102022.photoshop.v2.render;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.CoverageMask;
import com.sam102022.photoshop.v2.geometry.JsonTerritoryLoader;
import com.sam102022.photoshop.v2.pipeline.V2Config;
import com.sam102022.photoshop.v2.pipeline.V2Pipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test d'intégration pivot Sprint 9 : Rendu Sub-Pixel SS=4 et Export CA01")
class Sprint9IntegrationTest {

    @Test
    @DisplayName("Pipeline complet Sprints 1 à 9 sur CA01 : concordance IoU >= 0.99 et budget temps <= 2500 ms")
    void testEndToEndSprint9OnCA01() throws IOException {
        Path jsonPath = Paths.get("src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json");
        if (!Files.exists(jsonPath)) {
            jsonPath = Paths.get("maps/captures_maps/Territoire CA01/01_plan_avec_territoires.json");
        }
        assertTrue(Files.exists(jsonPath), "Le fichier JSON CA01 doit exister.");

        Path mapPath = Paths.get("src/test/resources/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        if (!Files.exists(mapPath)) {
            mapPath = Paths.get("maps/captures_maps/Territoire CA01/05_style_contraste_sans_rien.png");
        }
        assertTrue(Files.exists(mapPath), "L'image map CA01 doit exister.");

        Path roadPath = Paths.get("maps/road.png");
        assertTrue(Files.exists(roadPath), "L'image maps/road.png doit exister.");

        JsonTerritoryLoader loader = new JsonTerritoryLoader();
        JsonTerritoryLoader.LoadedTerritory loaded = loader.load(jsonPath);
        BufferedImage mapImg = ImageIO.read(mapPath.toFile());
        BufferedImage roadImg = ImageIO.read(roadPath.toFile());
        BinaryMask roadMask = BinaryMask.fromImage(roadImg, 128);

        long startPipeline = System.currentTimeMillis();
        V2Pipeline pipeline = new V2Pipeline();
        V2Config config = V2Config.defaultConfig();

        RenderResult result = pipeline.execute(mapImg, loaded.geometry(), loaded.mapContext(), roadMask, config);
        long elapsedTotal = System.currentTimeMillis() - startPipeline;

        assertNotNull(result);
        assertNotNull(result.clipped());
        assertNotNull(result.mask());
        assertNotNull(result.overlay());
        assertNotNull(result.coverageMask());

        System.out.printf("Pipeline complet V2 exécuté en %d ms sur CA01 (%dx%d px).%n",
                elapsedTotal, mapImg.getWidth(), mapImg.getHeight());

        // A. Vérification des dimensions et types d'images
        assertEquals(mapImg.getWidth(), result.clipped().getWidth());
        assertEquals(mapImg.getHeight(), result.clipped().getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, result.clipped().getType());
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, result.mask().getType());
        assertEquals(BufferedImage.TYPE_INT_RGB, result.overlay().getType());

        // B. Concordance avec l'étalon Python V7 si disponible
        Path pyMaskPath = Paths.get("maps/python/CA01_mask_v7.png");
        if (Files.exists(pyMaskPath)) {
            BufferedImage pyMaskImg = ImageIO.read(pyMaskPath.toFile());
            double iou = computeMaskIoU(result.coverageMask(), pyMaskImg);
            System.out.printf("IoU vs CA01_mask_v7.png : %.4f%%%n", iou * 100.0);
            assertTrue(iou >= 0.99, "L'indice IoU par rapport à CA01_mask_v7.png doit être >= 0.99 (actuel : " + iou + ")");
        }

        // C. Budget de performance : <= 2500 ms de bout en bout
        assertTrue(elapsedTotal <= 2500, "Le temps total du pipeline V2 doit être <= 2500 ms (actuel : " + elapsedTotal + " ms)");
    }

    private double computeMaskIoU(CoverageMask coverageMask, BufferedImage referenceMask) {
        int w = coverageMask.getWidth();
        int h = coverageMask.getHeight();
        long intersection = 0;
        long union = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean a = coverageMask.get(x, y) >= 128;
                boolean b = (referenceMask.getRaster().getSample(x, y, 0)) >= 128;
                if (a && b) intersection++;
                if (a || b) union++;
            }
        }
        return union == 0 ? 1.0 : (double) intersection / (double) union;
    }
}
```

- [ ] **Step 2: Exécuter le test d'intégration pour vérifier son passage**

Exécuter : `mvn test -Dtest=Sprint9IntegrationTest`  
Attendu : PASS.

---

### Task 8: Documentation & Clôture de l'Architecture V2

**Files:**
- Modify: `docs/sprints_v2/sprint-9-rendu-supersampling-cli.md`
- Modify: `docs/SPRINTS_V2.md`
- Modify: `JOURNAL.md`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Mettre à jour `sprint-9-rendu-supersampling-cli.md` avec statut Terminé et métriques**
- [ ] **Step 2: Mettre à jour `docs/SPRINTS_V2.md` pour marquer le Sprint 9 et l'achèvement de la V2**
- [ ] **Step 3: Mettre à jour `JOURNAL.md` pour le 2026-10-05 (ADR-010, ADR-013)**
- [ ] **Step 4: Mettre à jour `CHANGELOG.md`**
- [ ] **Step 5: Exécuter la suite complète de tests V2**

Exécuter : `mvn test -Dtest="com.sam102022.photoshop.v2.**.*Test,com.sam102022.photoshop.cli.V2CliRunnerTest"`  
Attendu : 100% PASS.
