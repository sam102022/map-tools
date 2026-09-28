# Découpage de Zone Interne et Exclusion de la Chaussée Routière — Plan d'Implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre le découpage ciblé d'une zone interne de territoire sur Google Maps à partir d'un sélecteur rouge grossier, en garantissant l'exclusion mathématiquement absolue de la chaussée routière (taux de route = 0.0%) et un bord anti-aliasé sub-pixel.

**Architecture:** Détection robuste du cadre rouge et de son intérieur fermé avec réparation bornée de brèches (`RedFrameExtractor`), détection des démarcations sombres cartographiques (`DarkDemarcationDetector`), consolidation étanche des barrières d'exclusion (`ZoneBarrierConsolidator`), inondation géodésique BFS haute performance sans allocation d'objets et masquage strict post-rastérisation vectorielle (`ZoneSegmentationEngine`), avec aiguillage `--mode auto|territory|zone` dans le CLI (`CliRunner`).

**Tech Stack:** Java 21 LTS, Java2D, JUnit 5, Maven, Swing (100% Java standard, zéro dépendance native).

---

## Structure des Fichiers et Responsabilités

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   ├── OperationMode.java            # NOUVEAU : Énumération AUTO, TERRITORY, ZONE
│   │   └── SnappingConfig.java           # MODIFIÉ : Ajout du champ OperationMode
│   ├── detection/
│   │   ├── RedFrameExtractor.java        # NOUVEAU : Détection du sélecteur rouge, fermeture contrôlée et intérieur
│   │   └── DarkDemarcationDetector.java  # NOUVEAU : Détection mesurable des lignes sombres de démarcation
│   └── segmentation/
│       ├── ZoneBarrierConsolidator.java  # NOUVEAU : Consolidation étanche des barrières infranchissables
│       └── ZoneSegmentationEngine.java   # NOUVEAU : Sélection déterministe de graine, BFS haute performance et exclusion stricte
└── cli/
    └── CliRunner.java                    # MODIFIÉ : Arguments --mode, --territory-mask et aiguillage auto/zone/territory

src/test/java/com/sam102022/photoshop/
├── core/
│   ├── model/
│   │   └── SnappingConfigTest.java       # MODIFIÉ : Tests du mode OperationMode
│   ├── detection/
│   │   ├── RedFrameExtractorTest.java    # NOUVEAU : Tests unitaires de détection et fermeture du cadre
│   │   └── DarkDemarcationDetectorTest.java # NOUVEAU : Tests unitaires des lignes sombres
│   └── segmentation/
│       ├── ZoneBarrierConsolidatorTest.java # NOUVEAU : Tests unitaires de consolidation des barrières
│       └── ZoneSegmentationEngineTest.java # NOUVEAU : Tests unitaires inondation et garantie d'exclusion routière
└── IntegrationCliTest.java               # MODIFIÉ : Test grandeur nature avec sample 02 (IoU >= 0.90, road = 0.0%)
```

---

### Task 1: Modèle `OperationMode` et Évolution de `SnappingConfig`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/model/OperationMode.java`
- Modify: `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java`

- [ ] **Step 1: Écrire le test unitaire pour `OperationMode` dans `SnappingConfigTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java` :
```java
    @Test
    @DisplayName("Vérifier la configuration du mode d'opération avec repli AUTO par défaut")
    void testOperationModeDefaultsAndCustom() {
        SnappingConfig defaultConfig = SnappingConfig.defaults();
        assertEquals(OperationMode.AUTO, defaultConfig.mode(), "Le mode par défaut doit être AUTO");

        SnappingConfig zoneConfig = defaultConfig.withMode(OperationMode.ZONE);
        assertEquals(OperationMode.ZONE, zoneConfig.mode());

        SnappingConfig territoryConfig = defaultConfig.withMode(OperationMode.TERRITORY);
        assertEquals(OperationMode.TERRITORY, territoryConfig.mode());
    }
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=SnappingConfigTest#testOperationModeDefaultsAndCustom`
Expected: FAIL avec "cannot find symbol: class OperationMode"

- [ ] **Step 3: Créer l'énumération `OperationMode`**

Créer `src/main/java/com/sam102022/photoshop/core/model/OperationMode.java` :
```java
package com.sam102022.photoshop.core.model;

/**
 * Modes d'opération supportés par le moteur de découpage cartographique.
 */
public enum OperationMode {
    /**
     * Détecte automatiquement la présence d'un cadre rouge fermé de sélection de zone.
     * Si détecté, bascule en mode ZONE ; sinon, applique le mode TERRITORY global.
     */
    AUTO,

    /**
     * Force le détourage du territoire global (ignore les éventuels tracés rouges).
     */
    TERRITORY,

    /**
     * Force le découpage d'une zone interne (exige un cadre rouge fermé valide).
     */
    ZONE
}
```

- [ ] **Step 4: Mettre à jour `SnappingConfig` avec le champ `mode`**

Mettre à jour `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java` :
Ajouter le composant `OperationMode mode` dans le record, le constructeur de compatibilité à 6 arguments qui injecte `OperationMode.AUTO`, mettre à jour `defaults()`, et ajouter la méthode `withMode(OperationMode mode)`.

- [ ] **Step 5: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=SnappingConfigTest`
Expected: PASS

- [ ] **Step 6: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/model/OperationMode.java src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java
git commit -m "feat: ajout OperationMode et intégration dans SnappingConfig"
```

---

### Task 2: Détecteur et Extracteur du Sélecteur Rouge (`RedFrameExtractor`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/RedFrameExtractor.java`
- Create: `src/test/java/com/sam102022/photoshop/core/detection/RedFrameExtractorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `RedFrameExtractor`**

Créer `src/test/java/com/sam102022/photoshop/core/detection/RedFrameExtractorTest.java` :
```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests unitaires pour RedFrameExtractor")
class RedFrameExtractorTest {

    private final RedFrameExtractor extractor = new RedFrameExtractor();

    @Test
    @DisplayName("Détecter un cadre rouge fermé rectangulaire et extraire son intérieur")
    void testDetectClosedRedRectangle() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(new Color(220, 20, 20)); // Rouge vif
        g.drawRect(20, 20, 50, 50);
        g.dispose();

        assertTrue(extractor.hasRedFrame(image), "Le cadre rouge doit être détecté");
        BinaryMask interior = extractor.extractInterior(image);
        assertNotNull(interior);
        assertTrue(interior.get(45, 45), "Le centre du rectangle doit appartenir à l'intérieur");
        assertFalse(interior.get(10, 10), "L'extérieur ne doit pas appartenir à l'intérieur");
        assertFalse(interior.get(20, 20), "Le trait rouge lui-même ne doit pas appartenir à l'intérieur");
    }

    @Test
    @DisplayName("Réparer une petite brèche de 3 pixels dans le tracé rouge")
    void testRepairSmallGap() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(new Color(230, 10, 10));
        g.drawRect(20, 20, 50, 50);
        // Créer une brèche de 3 pixels sur le bord haut
        g.setColor(Color.WHITE);
        g.fillRect(40, 20, 3, 1);
        g.dispose();

        assertTrue(extractor.hasRedFrame(image));
        BinaryMask interior = extractor.extractInterior(image);
        assertTrue(interior.get(45, 45), "La brèche fine doit être colmatée et l'intérieur préservé");
        assertFalse(interior.get(5, 5), "L'extérieur ne doit pas être inondé");
    }

    @Test
    @DisplayName("Échouer avec exception explicite si la brèche est trop large (> 10 pixels)")
    void testFailOnLargeGap() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 100, 100);
        g.setColor(new Color(230, 10, 10));
        g.drawRect(20, 20, 50, 50);
        // Brèche large de 20 pixels
        g.setColor(Color.WHITE);
        g.fillRect(35, 20, 20, 1);
        g.dispose();

        assertThrows(IllegalStateException.class, () -> extractor.extractInterior(image),
                "Une brèche de 20 pixels ne doit pas être colmatée et doit lever une exception");
    }

    @Test
    @DisplayName("Ignorer les images sans cadre rouge significatif")
    void testIgnoreImagesWithoutRedFrame() {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);
        g.setColor(new Color(255, 160, 50)); // Teinte orange/jaune routière Google Maps
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        assertFalse(extractor.hasRedFrame(image));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=RedFrameExtractorTest`
Expected: FAIL avec "cannot find symbol: class RedFrameExtractor"

- [ ] **Step 3: Implémenter `RedFrameExtractor`**

Créer `src/main/java/com/sam102022/photoshop/core/detection/RedFrameExtractor.java` :
- `public boolean hasRedFrame(BufferedImage image)` : compte les pixels rouges avec $R \ge 150 \land R > G + 40 \land R > B + 40 \land S \ge 0.35 \land B \ge 0.20$. Retourne vrai si $\ge 50$ pixels.
- `public BinaryMask extractInterior(BufferedImage image)` :
  1. Construit le masque binaire du trait rouge brut.
  2. Applique une fermeture morphologique `MorphologyOps.close(rawRed, 5)` pour colmater les brèches $\le 10$ px.
  3. Effectue une inondation (BFS sur tableau plat `int[] queue`) depuis les bords de l'image sur les pixels non-rouges.
  4. L'intérieur est le complémentaire des pixels visités et non-rouges.
  5. Si l'aire intérieure est $< 500$ pixels, lève `IllegalStateException`.
  6. Retourne le masque intérieur binaire.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=RedFrameExtractorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/RedFrameExtractor.java src/test/java/com/sam102022/photoshop/core/detection/RedFrameExtractorTest.java
git commit -m "feat: ajout RedFrameExtractor avec signature chromatique et fermeture contrôlée"
```

---

### Task 3: Détecteur des Démarcations Sombres Internes (`DarkDemarcationDetector`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java`
- Create: `src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `DarkDemarcationDetector`**

Créer `src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java` :
```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests unitaires pour DarkDemarcationDetector")
class DarkDemarcationDetectorTest {

    private final DarkDemarcationDetector detector = new DarkDemarcationDetector();

    @Test
    @DisplayName("Détecter une ligne sombre surimposée sur le calque de limites")
    void testDetectDarkDemarcationLine() {
        int w = 50, h = 50;
        BufferedImage carte = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage limites = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D gc = carte.createGraphics();
        gc.setColor(new Color(240, 240, 240)); // Fond clair
        gc.fillRect(0, 0, w, h);
        gc.dispose();

        Graphics2D gl = limites.createGraphics();
        gl.setColor(new Color(180, 235, 175)); // Fond vert territoire
        gl.fillRect(0, 0, w, h);
        gl.setColor(new Color(60, 60, 60)); // Ligne sombre de démarcation
        gl.drawLine(25, 5, 25, 45); // Ligne de 40 pixels (> seuil 15)
        gl.dispose();

        BinaryMask territory = new BinaryMask(w, h);
        territory.fill(true);

        BinaryMask demarcations = detector.detect(carte, limites, territory);
        assertTrue(demarcations.get(25, 20), "La ligne sombre doit être détectée");
        assertFalse(demarcations.get(10, 10), "Le fond vert ordinaire ne doit pas être une démarcation");
    }

    @Test
    @DisplayName("Ignorer les petits artefacts ou chiffres isolés (< 15 pixels)")
    void testFilterSmallNoise() {
        int w = 50, h = 50;
        BufferedImage carte = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        BufferedImage limites = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        Graphics2D gl = limites.createGraphics();
        gl.setColor(new Color(180, 235, 175));
        gl.fillRect(0, 0, w, h);
        gl.setColor(new Color(50, 50, 50));
        gl.fillRect(20, 20, 2, 2); // 4 pixels isolés
        gl.dispose();

        BinaryMask territory = new BinaryMask(w, h);
        territory.fill(true);

        BinaryMask demarcations = detector.detect(carte, limites, territory);
        assertFalse(demarcations.get(20, 20), "Les artefacts isolés < 15 pixels doivent être filtrés");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=DarkDemarcationDetectorTest`
Expected: FAIL avec "cannot find symbol: class DarkDemarcationDetector"

- [ ] **Step 3: Implémenter `DarkDemarcationDetector`**

Créer `src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java` :
- Méthode `public BinaryMask detect(BufferedImage carte, BufferedImage limites, BinaryMask territory)` :
  - Calcule la luminance $Y = 0.299R + 0.587G + 0.114B$.
  - Sélectionne les pixels où $Y_L \le 125 \land Y_C - Y_L \ge 45 \land territory.get(x, y) \land \neg isRed(x, y)$.
  - Filtre les composantes connexes $< 15$ pixels (nettoyage morphologique ou BFS de composantes).
  - Retourne le `BinaryMask` des démarcations.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=DarkDemarcationDetectorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetector.java src/test/java/com/sam102022/photoshop/core/detection/DarkDemarcationDetectorTest.java
git commit -m "feat: ajout DarkDemarcationDetector pour repérer les délimitations cartographiques sombres"
```

---

### Task 4: Consolidation Étanche des Barrières (`ZoneBarrierConsolidator`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java`
- Create: `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `ZoneBarrierConsolidator`**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java` :
```java
package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests unitaires pour ZoneBarrierConsolidator")
class ZoneBarrierConsolidatorTest {

    private final ZoneBarrierConsolidator consolidator = new ZoneBarrierConsolidator();

    @Test
    @DisplayName("Consolider les routes, démarcations et l'extérieur du territoire")
    void testConsolidateBarriers() {
        int w = 20, h = 20;
        BinaryMask roads = new BinaryMask(w, h);
        roads.set(10, 5, true);

        BinaryMask demarcations = new BinaryMask(w, h);
        demarcations.set(5, 10, true);

        BinaryMask territory = new BinaryMask(w, h);
        // Territoire actif sur [2..18, 2..18]
        for (int y = 2; y <= 18; y++) {
            for (int x = 2; x <= 18; x++) {
                territory.set(x, y, true);
            }
        }

        BinaryMask barriers = consolidator.consolidate(roads, demarcations, territory);
        assertTrue(barriers.get(10, 5), "La route doit être une barrière");
        assertTrue(barriers.get(5, 10), "La démarcation doit être une barrière");
        assertTrue(barriers.get(0, 0), "L'extérieur du territoire doit être une barrière");
        assertFalse(barriers.get(15, 15), "L'intérieur libre du territoire ne doit pas être une barrière");
    }

    @Test
    @DisplayName("Colmater les diagonales 8-connexes pour interdire les fuites")
    void testCloseDiagonalBarriers() {
        int w = 10, h = 10;
        BinaryMask roads = new BinaryMask(w, h);
        // Deux pixels en diagonale (5, 5) et (6, 6)
        roads.set(5, 5, true);
        roads.set(6, 6, true);

        BinaryMask territory = new BinaryMask(w, h);
        territory.fill(true);

        BinaryMask barriers = consolidator.consolidate(roads, new BinaryMask(w, h), territory);
        // Après fermeture morphologique rayon 1, (5, 6) ou (6, 5) doit être bloqué pour empêcher le passage
        assertTrue(barriers.get(5, 6) || barriers.get(6, 5), "Le passage diagonal doit être colmaté");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=ZoneBarrierConsolidatorTest`
Expected: FAIL avec "cannot find symbol: class ZoneBarrierConsolidator"

- [ ] **Step 3: Implémenter `ZoneBarrierConsolidator`**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java` :
- Méthode `public BinaryMask consolidate(BinaryMask roadCandidates, BinaryMask darkDemarcations, BinaryMask territoryMask)` :
  - Fusionne `roadCandidates | darkDemarcations | ~territoryMask`.
  - Applique `MorphologyOps.close(rawBarriers, 1)` pour colmater les diagonales 8-connexes.
  - Retourne les barrières consolidées étanches.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=ZoneBarrierConsolidatorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidator.java src/test/java/com/sam102022/photoshop/core/segmentation/ZoneBarrierConsolidatorTest.java
git commit -m "feat: ajout ZoneBarrierConsolidator pour étanchéifier les barrières bloquantes"
```

---

### Task 5: Moteur de Découpage de Zone (`ZoneSegmentationEngine`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java`
- Create: `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `ZoneSegmentationEngine`**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java` :
- Test `testSegmentZoneStrictRoadExclusion()` : vérifie que l'inondation s'arrête net sur la route et qu'aucun pixel de route n'est présent dans le `CoverageMask` final (`coverage.get(roadX, roadY) == 0`).
- Test `testDeterministicSeedSelectionOnSymmetry()` : vérifie qu'une zone symétrique donne toujours exactement la même graine via le centroïde et le tri lexicographique.
- Test `testFailWhenNoFreePixelsInTerritory()` : vérifie la levée de `IllegalStateException` quand l'intérieur du cadre ne contient aucun pixel libre.
- Test `testSubPixelAntiAliasingPreserved()` : vérifie la présence de valeurs partielles `0 < v < 255` sur les bordures non routières.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest`
Expected: FAIL avec "cannot find symbol: class ZoneSegmentationEngine"

- [ ] **Step 3: Implémenter `ZoneSegmentationEngine`**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java` :
- Recherche déterministe de la graine dans $S = \text{interiorMask} \cap \text{territoryMask} \setminus \text{consolidatedBarriers}$ (distance euclidienne, départage centroïde et lexicographique).
- Inondation BFS haute performance avec file primitive `int[] queue = new int[W * H]` (zéro allocation `Point`).
- Extraction de contour via `ContourExtractor`.
- Simplification RDP avec $\epsilon = 0.8$ (`ContourSimplifier`).
- Rastérisation sub-pixel (`PolygonBuilder.rasterizePixelCenterContour`).
- Préservation sécurisée du noyau intérieur érodé (`MorphologyOps.erode(zoneBinary, 1).andNot(barriers)`).
- **Garantie absolue d'exclusion :** Pour tout pixel où $\text{roadCandidates}(x, y) == \text{true}$ ou $\neg \text{territoryMask}(x, y)$, forcer explicitement `finalCoverage.set(x, y, 0)`.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java
git commit -m "feat: ajout ZoneSegmentationEngine avec inondation BFS primitive et exclusion absolue de la chaussée"
```

---

### Task 6: Intégration CLI et Aiguillage Automatique (`CliRunner`)

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/cli/CliRunner.java`
- Modify: `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `--mode` et `--territory-mask` dans `CliRunnerTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java` :
- Test de parsing `--mode zone`, `--mode territory`, `--mode auto`.
- Test d'erreur quand `--mode zone` est spécifié mais qu'aucun cadre rouge n'est détecté.
- Test de parsing `--territory-mask <path>`.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=CliRunnerTest`
Expected: FAIL

- [ ] **Step 3: Mettre à jour `CliRunner`**

Mettre à jour `src/main/java/com/sam102022/photoshop/cli/CliRunner.java` :
- Parser l'option `--mode <auto|territory|zone>`.
- Parser l'option `--territory-mask <path>` (ou `-tm`).
- Charger le masque de territoire si fourni (avec support BYTE_GRAY et ARGB seuillés à 128).
- Dans l'orchestration du traitement :
  - Détecter `hasRedFrame` via `RedFrameExtractor`.
  - Aiguiller vers `ZoneSegmentationEngine` ou `RoadSnappingEngine` selon le mode actif.
  - Afficher les logs descriptifs en français.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=CliRunnerTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/cli/CliRunner.java src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java
git commit -m "feat: intégration du mode zone et de l'option territory-mask dans CliRunner"
```

---

### Task 7: Intégration dans la GUI Swing (`MainWindow` & `FileSelectionPanel`)

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/gui/FileSelectionPanel.java`
- Modify: `src/main/java/com/sam102022/photoshop/gui/MainWindow.java`
- Modify: `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java`

- [ ] **Step 1: Écrire les tests unitaires de l'IHM pour le champ de masque de territoire**

Ajouter un test dans `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java` validant la présence du sélecteur optionnel de masque de territoire et la mise à jour du statut lors du chargement d'un cadre rouge.

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=MainWindowTest`
Expected: FAIL

- [ ] **Step 3: Implémenter les modifications GUI**

- Dans `FileSelectionPanel` : ajouter un champ de fichier optionnel "Masque Territoire (optionnel)".
- Dans `MainWindow` : lors du traitement ou du chargement du masque, vérifier si un cadre rouge est présent et afficher le badge ou texte d'état `"Mode : Découpage de Zone (bord intérieur)"`.

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=MainWindowTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/gui/FileSelectionPanel.java src/main/java/com/sam102022/photoshop/gui/MainWindow.java src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java
git commit -m "feat: support du mode zone et du masque de territoire dans la GUI Swing"
```

---

### Task 8: Test d'Intégration Grandeur Nature (`Sample 02`) et Validation Complète

**Files:**
- Modify: `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java`

- [ ] **Step 1: Écrire le test d'intégration sur `sample 02` dans `IntegrationCliTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java` :
- Charger `src/main/resources/sample 02/carte à découper.jpg`, `limites.jpg`, et tracer un cadre rouge entourant la zone 1 (ou charger une image annotée).
- Charger `src/main/resources/sample 02/mask territoire.jpg`.
- Charger la référence attendue `src/main/resources/sample 02/mask zone 1.jpg`.
- Exécuter le traitement en mode `--mode zone`.
- **Assertion 1 :** Calculer l'IoU binaire au seuil 128 et vérifier que $\text{IoU} \ge 0.90$.
- **Assertion 2 :** Vérifier que pour chaque pixel du masque final, si `roadCandidates.get(x, y)` est vrai, alors `mask.get(x, y) == 0` (taux d'inclusion routière = 0.000%).
- **Assertion 3 :** Vérifier la présence de valeurs sub-pixel partielles `0 < v < 255` sur le contour.

- [ ] **Step 2: Exécuter le test d'intégration pour vérifier qu'il passe**

Run: `mvn test -Dtest=IntegrationCliTest`
Expected: PASS

- [ ] **Step 3: Exécuter la suite complète de validation pour garantir 100% de non-régression**

Run: `mvn test`
Expected: BUILD SUCCESS (100% des tests passants)

- [ ] **Step 4: Commiter le test d'intégration et les ajustements finaux**

Run:
```bash
git add src/test/java/com/sam102022/photoshop/IntegrationCliTest.java
git commit -m "test: test d'intégration grandeur nature sur sample 02 avec IoU >= 0.90 et exclusion stricte des routes"
```
