# Découpage de Zone Interne Multi-Couleurs et Exclusion de la Chaussée Routière — Plan d'Implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre le découpage ciblé d'une zone interne de territoire sur Google Maps à partir d'un sélecteur polychrome grossier (rouge, bleu, magenta, cyan ou auto-détection), en garantissant l'exclusion mathématiquement absolue de la chaussée routière (taux de route = 0.0%) et un bord anti-aliasé sub-pixel.

**Architecture:** Modélisation des couleurs d'annotation (`SelectorColor`), détection robuste et fermeture contrôlée des cadres colorés (`ColorRegionSelectorExtractor`), détection des démarcations sombres cartographiques sans confusion avec les annotations (`DarkDemarcationDetector`), consolidation étanche des barrières (`ZoneBarrierConsolidator`), inondation géodésique BFS haute performance sans allocation d'objets et masquage strict post-rastérisation vectorielle (`ZoneSegmentationEngine`), avec aiguillage `--mode auto|territory|zone` et `--zone-color <couleur>` dans le CLI (`CliRunner`).

**Tech Stack:** Java 21 LTS, Java2D, JUnit 5, Maven, Swing (100% Java standard, zéro dépendance native).

---

## Structure des Fichiers et Responsabilités

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   ├── OperationMode.java                 # NOUVEAU : Énumération AUTO, TERRITORY, ZONE
│   │   ├── SelectorColor.java                 # NOUVEAU : Énumération AUTO, RED, BLUE, MAGENTA, CYAN
│   │   └── SnappingConfig.java                # MODIFIÉ : Ajout des champs mode et zoneColor
│   ├── detection/
│   │   ├── ColorRegionSelectorExtractor.java  # NOUVEAU : Détection du cadre coloré, fermeture et intérieur
│   │   └── DarkDemarcationDetector.java       # NOUVEAU : Détection mesurable des lignes sombres de démarcation
│   └── segmentation/
│       ├── ZoneBarrierConsolidator.java       # NOUVEAU : Consolidation étanche des barrières infranchissables
│       └── ZoneSegmentationEngine.java        # NOUVEAU : Sélection déterministe de graine, BFS sans allocation et exclusion stricte
└── cli/
    └── CliRunner.java                         # MODIFIÉ : Arguments --mode, --zone-color, --territory-mask et aiguillage

src/test/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   ├── SelectorColorTest.java             # NOUVEAU : Tests unitaires de validation et parsing des couleurs
│   │   └── SnappingConfigTest.java            # MODIFIÉ : Tests des configurations mode et zoneColor
│   ├── detection/
│   │   ├── ColorRegionSelectorExtractorTest.java # NOUVEAU : Tests unitaires de détection multi-couleurs et fermeture
│   │   └── DarkDemarcationDetectorTest.java   # NOUVEAU : Tests unitaires des lignes sombres
│   └── segmentation/
│       ├── ZoneBarrierConsolidatorTest.java   # NOUVEAU : Tests unitaires de consolidation des barrières
│       └── ZoneSegmentationEngineTest.java   # NOUVEAU : Tests unitaires inondation et garantie d'exclusion routière
└── IntegrationCliTest.java                    # MODIFIÉ : Test grandeur nature avec sample 02 (IoU >= 0.90, road = 0.0%)
```

---

### Task 1: Modèles `OperationMode`, `SelectorColor` et Évolution de `SnappingConfig`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/model/OperationMode.java`
- Create: `src/main/java/com/sam102022/photoshop/core/model/SelectorColor.java`
- Create: `src/test/java/com/sam102022/photoshop/core/model/SelectorColorTest.java`
- Modify: `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `SelectorColor` et `OperationMode`**

Créer `src/test/java/com/sam102022/photoshop/core/model/SelectorColorTest.java` :
```java
package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests unitaires pour SelectorColor")
class SelectorColorTest {

    @Test
    @DisplayName("Tester le parsing insensible à la casse et les alias français")
    void testParsingAndAliases() {
        assertEquals(SelectorColor.RED, SelectorColor.fromString("red"));
        assertEquals(SelectorColor.RED, SelectorColor.fromString("ROUGE"));
        assertEquals(SelectorColor.BLUE, SelectorColor.fromString("bleu"));
        assertEquals(SelectorColor.MAGENTA, SelectorColor.fromString("magenta"));
        assertEquals(SelectorColor.CYAN, SelectorColor.fromString("cyan"));
        assertEquals(SelectorColor.AUTO, SelectorColor.fromString("auto"));
        assertThrows(IllegalArgumentException.class, () -> SelectorColor.fromString("inconnue"));
    }

    @Test
    @DisplayName("Tester le filtrage chromatique exact")
    void testColorMatching() {
        // Rouge vif
        assertTrue(SelectorColor.RED.matches(220, 20, 20));
        assertFalse(SelectorColor.RED.matches(255, 180, 50), "Ne doit pas matcher une route orange");
        assertFalse(SelectorColor.RED.matches(160, 240, 160), "Ne doit pas matcher le vert territoire");

        // Bleu vif
        assertTrue(SelectorColor.BLUE.matches(20, 30, 220));
        assertFalse(SelectorColor.BLUE.matches(200, 200, 200), "Ne doit pas matcher du gris");

        // Magenta
        assertTrue(SelectorColor.MAGENTA.matches(220, 20, 220));

        // Cyan
        assertTrue(SelectorColor.CYAN.matches(20, 220, 220));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=SelectorColorTest`
Expected: FAIL avec "cannot find symbol: class SelectorColor"

- [ ] **Step 3: Créer l'énumération `OperationMode`**

Créer `src/main/java/com/sam102022/photoshop/core/model/OperationMode.java` :
```java
package com.sam102022.photoshop.core.model;

/**
 * Modes d'opération supportés par le moteur de découpage cartographique.
 */
public enum OperationMode {
    /**
     * Détecte automatiquement la présence d'un cadre de sélection de zone fermé.
     */
    AUTO,

    /**
     * Force le détourage du territoire global.
     */
    TERRITORY,

    /**
     * Force le découpage d'une zone interne ciblée.
     */
    ZONE
}
```

- [ ] **Step 4: Créer l'énumération `SelectorColor`**

Créer `src/main/java/com/sam102022/photoshop/core/model/SelectorColor.java` :
- Énumération avec `AUTO`, `RED`, `BLUE`, `MAGENTA`, `CYAN`.
- Méthode statique `public static SelectorColor fromString(String name)`.
- Méthode `public boolean matches(int r, int g, int b)`.

- [ ] **Step 5: Mettre à jour `SnappingConfig` avec `mode` et `zoneColor`**

Mettre à jour `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java` :
- Ajouter `OperationMode mode` et `SelectorColor zoneColor`.
- Constructeurs de compatibilité conservant les valeurs par défaut `AUTO`.
- Méthodes `withMode(OperationMode mode)` et `withZoneColor(SelectorColor color)`.

- [ ] **Step 6: Exécuter les tests pour vérifier qu'ils passent**

Run: `mvn test -Dtest=SelectorColorTest,SnappingConfigTest`
Expected: PASS

- [ ] **Step 7: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/model/OperationMode.java src/main/java/com/sam102022/photoshop/core/model/SelectorColor.java src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java src/test/java/com/sam102022/photoshop/core/model/SelectorColorTest.java src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java
git commit -m "feat: ajout OperationMode et SelectorColor avec prédicats chromatiques stricts"
```

---

### Task 2: Détecteur Polychrome du Sélecteur de Zone (`ColorRegionSelectorExtractor`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractor.java`
- Create: `src/test/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `ColorRegionSelectorExtractor`**

Créer `src/test/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractorTest.java` :
- Test `testDetectClosedBlueRectangle()` : détection et extraction de l'intérieur d'un rectangle bleu.
- Test `testDetectMultipleColorsAndDisambiguation()` : image avec un cadre rouge ET un cadre bleu. Si `SelectorColor.AUTO`, lève `IllegalStateException` mentionnant les deux couleurs. Si `SelectorColor.RED`, extrait avec succès l'intérieur rouge.
- Test `testRepairSmallGap()` : fermeture réussie d'une brèche $\le 5$ pixels.
- Test `testFailOnLargeGap()` : levée de `IllegalStateException` si brèche $> 10$ pixels.
- Test `testIgnoreMapBackgroundWithoutAnnotation()` : rejet des fonds sans tracé d'annotation.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=ColorRegionSelectorExtractorTest`
Expected: FAIL avec "cannot find symbol: class ColorRegionSelectorExtractor"

- [ ] **Step 3: Implémenter `ColorRegionSelectorExtractor`**

Créer `src/main/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractor.java` :
- `public List<SelectorColor> detectPresentColors(BufferedImage image)` : retourne les couleurs ayant $\ge 50$ pixels.
- `public SelectorColor resolveTargetColor(BufferedImage image, SelectorColor requestedColor)` : résout la couleur ou lève une exception descriptive si ambiguïté.
- `public BinaryMask extractInterior(BufferedImage image, SelectorColor targetColor)` : extrait le masque intérieur de la couleur cible après fermeture contrôlée de rayon 5. Lève `IllegalStateException` si le cadre n'est pas étanche.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=ColorRegionSelectorExtractorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractor.java src/test/java/com/sam102022/photoshop/core/detection/ColorRegionSelectorExtractorTest.java
git commit -m "feat: ajout ColorRegionSelectorExtractor avec support multi-couleurs et fermeture de brèches"
```

---

### Task 3: Détecteur des Démarcations Sombres Internes (`DarkDemarcationDetector`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java`
- Create: `src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `DarkDemarcationDetector`**

Créer `src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java` :
- Test `testDetectDarkDemarcationLine()` : détection d'une ligne sombre ($Y_C - Y_L \ge 45, Y_L \le 125$).
- Test `testDoNotConfuseWithColorAnnotations()` : vérifie qu'un tracé d'annotation rouge ou bleu vif n'est PAS classé comme démarcation sombre.
- Test `testFilterSmallNoise()` : suppression des composantes $< 15$ pixels.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=DarkDemarcationDetectorTest`
Expected: FAIL avec "cannot find symbol: class DarkDemarcationDetector"

- [ ] **Step 3: Implémenter `DarkDemarcationDetector`**

Créer `src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java` :
- Détecte les pixels où $Y_L \le 125 \land Y_C - Y_L \ge 45 \land territory.get(x, y) \land \neg isAnnotationColor(x, y)$.
- Filtre les composantes isolées $< 15$ pixels.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=DarkDemarcationDetectorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java
git commit -m "feat: ajout DarkDemarcationDetector isolé des annotations colorées"
```

---

### Task 4: Consolidation Étanche des Barrières (`ZoneBarrierConsolidator`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java`
- Create: `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `ZoneBarrierConsolidator`**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java` :
- Test `testConsolidateBarriers()` : union des routes, démarcations sombres et de l'extérieur du territoire.
- Test `testCloseDiagonalBarriers()` : colmatage morphologique pour interdire le passage diagonal 8-connexe.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=ZoneBarrierConsolidatorTest`
Expected: FAIL avec "cannot find symbol: class ZoneBarrierConsolidator"

- [ ] **Step 3: Implémenter `ZoneBarrierConsolidator`**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java` :
- Méthode `public BinaryMask consolidate(BinaryMask roadCandidates, BinaryMask darkDemarcations, BinaryMask territoryMask)` :
  - Fusionne `roadCandidates | darkDemarcations | ~territoryMask`.
  - Applique `MorphologyOps.close(rawBarriers, 1)`.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=ZoneBarrierConsolidatorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java
git commit -m "feat: ajout ZoneBarrierConsolidator avec colmatage des diagonales 8-connexes"
```

---

### Task 5: Moteur de Découpage de Zone (`ZoneSegmentationEngine`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java`
- Create: `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `ZoneSegmentationEngine`**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java` :
- Test `testSegmentZoneStrictRoadExclusion()` : inondation arrêtée net sur la route et vérification que tous les pixels de chaussée sont à zéro absolu dans le masque final.
- Test `testDeterministicSeedSelectionOnSymmetry()` : vérification du départage déterministe de la graine par centroïde et ordre lexicographique.
- Test `testFailWhenNoFreePixelsInTerritory()` : exception explicite si $S = \emptyset$.
- Test `testSubPixelAntiAliasingPreserved()` : présence de fractions [1..254] sur les bordures non-routières.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest`
Expected: FAIL avec "cannot find symbol: class ZoneSegmentationEngine"

- [ ] **Step 3: Implémenter `ZoneSegmentationEngine`**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java` :
- Sélection déterministe de la graine dans $S$.
- Inondation BFS haute performance avec file primitive `int[] queue = new int[W * H]` indexée par $y \times W + x$.
- Confinement strict à l'intérieur de `interiorMask`.
- Extraction de contour via `ContourExtractor`.
- Simplification RDP avec $\epsilon = 0.8$ via `ContourSimplifier`.
- Rastérisation sub-pixel continue via `PolygonBuilder`.
- Préservation du noyau intérieur assaini (`safeCore`).
- **Garantie absolue d'exclusion :** Pour tout pixel où $\text{roadCandidates}(x, y) == \text{true}$ ou $\neg \text{territoryMask}(x, y)$, forcer `finalCoverage.set(x, y, 0)`.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java
git commit -m "feat: ajout ZoneSegmentationEngine avec exclusion absolue de la chaussée et BFS primitif"
```

---

### Task 6: Intégration CLI (`CliRunner`) avec `--mode` et `--zone-color`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/cli/CliRunner.java`
- Modify: `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `--mode`, `--zone-color` et `--territory-mask` dans `CliRunnerTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java` :
- Test de parsing `--mode zone --zone-color blue`.
- Test d'échec si `--mode zone` sans cadre valide.
- Test d'échec si calque avec plusieurs couleurs d'annotation sans précision de `--zone-color`.
- Test de parsing `--territory-mask <path>`.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=CliRunnerTest`
Expected: FAIL

- [ ] **Step 3: Mettre à jour `CliRunner`**

Mettre à jour `src/main/java/com/sam102022/photoshop/cli/CliRunner.java` :
- Parser `--mode <auto|territory|zone>`.
- Parser `--zone-color <auto|red|blue|magenta|cyan>` (alias `-zc`).
- Parser `--territory-mask <path>` (alias `-tm`).
- Orchestrer l'extraction selon le mode et la couleur de zone choisie, avec logs en français.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=CliRunnerTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/cli/CliRunner.java src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java
git commit -m "feat: ajout options --mode, --zone-color et --territory-mask dans CliRunner"
```

---

### Task 7: Intégration dans la GUI Swing (`MainWindow` & `FileSelectionPanel`)

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/gui/FileSelectionPanel.java`
- Modify: `src/main/java/com/sam102022/photoshop/gui/MainWindow.java`
- Modify: `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java`

- [ ] **Step 1: Écrire les tests unitaires de l'IHM**

Ajouter un test dans `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java` validant la présence du sélecteur optionnel de masque de territoire et la sélection de couleur de zone.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=MainWindowTest`
Expected: FAIL

- [ ] **Step 3: Implémenter les modifications GUI**

- Dans `FileSelectionPanel` : ajouter un champ de fichier optionnel "Masque Territoire (optionnel)".
- Dans `MainWindow` : ajouter un sélecteur de couleur de zone (`AUTO`, `ROUGE`, `BLEU`, `MAGENTA`, `CYAN`) et actualiser le message d'état du mode actif.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=MainWindowTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/gui/FileSelectionPanel.java src/main/java/com/sam102022/photoshop/gui/MainWindow.java src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java
git commit -m "feat: ajout sélection de couleur et masque de territoire dans l'IHM Swing"
```

---

### Task 8: Test d'Intégration Réel (`Sample 02`) et Validation Complète

**Files:**
- Modify: `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java`

- [ ] **Step 1: Écrire le test d'intégration sur `sample 02` dans `IntegrationCliTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java` :
- Charger `src/main/resources/sample 02/carte à découper.jpg`, `limites.jpg` avec un cadre rouge tracé autour de la zone 1 (ou annoter l'image en mémoire avec un contour rouge fermant la zone 1).
- Charger `src/main/resources/sample 02/mask territoire.jpg`.
- Charger la référence attendue `src/main/resources/sample 02/mask zone 1.jpg`.
- Exécuter le découpage de zone avec `--mode zone --zone-color red --territory-mask ...`.
- **Assertion 1 :** $\text{IoU} \ge 0.90$ par rapport à `mask zone 1.jpg`.
- **Assertion 2 :** Pour chaque pixel du masque final, si `roadCandidates.get(x, y)` est vrai, alors `mask.get(x, y) == 0` (taux d'inclusion routière = 0.000%).
- **Assertion 3 :** Présence de valeurs sub-pixel partielles `0 < v < 255` sur le contour extérieur.

- [ ] **Step 2: Exécuter le test d'intégration pour vérifier qu'il passe**

Run: `mvn test -Dtest=IntegrationCliTest`
Expected: PASS

- [ ] **Step 3: Exécuter la suite complète de validation pour garantir 100% de non-régression**

Run: `mvn test`
Expected: BUILD SUCCESS (100% des tests passants)

- [ ] **Step 4: Commiter le test d'intégration et la validation finale**

Run:
```bash
git add src/test/java/com/sam102022/photoshop/IntegrationCliTest.java
git commit -m "test: test d'intégration multi-couleurs sur sample 02 avec IoU >= 0.90 et exclusion stricte des routes"
```
