# Détection Fine des Chaussées et Calage Géométrique Bi-Mode (INNER / OUTER) — Plan d'Implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Détecter avec précision l'ensemble des axes routiers (y compris les rues locales blanches et grises) sur Google Maps modulé par `roadSensitivity`, et caler géométriquement les contours sur le bord intérieur (`INNER`) pour les zones et sur le bord extérieur (`OUTER`) pour les territoires.

**Architecture:** Détection hybride (teintes majeures + neutralité et gradients de bordure) dans `RoadCandidateDetector`, transmission de `roadSensitivity` dans `RoadDetector`, création de `SnapTargetEdge`, recalage vectoriel par balayage d'intervalle et rétraction dans `RoadSnapper`, et intégration dans `RoadSnappingEngine` et `ZoneSegmentationEngine`.

**Tech Stack:** Java 21 LTS, Java2D, JUnit 5, Maven, Swing (100% Java standard, zéro dépendance native).

---

## Structure des Fichiers et Responsabilités

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── detection/
│   │   ├── RoadCandidateDetector.java # MODIFIÉ : Détection des rues blanches/grises et roadSensitivity
│   │   └── RoadDetector.java          # MODIFIÉ : Transmission de roadSensitivity
│   ├── geometry/
│   │   ├── SnapTargetEdge.java        # NOUVEAU : Énumération INNER (bord intérieur) et OUTER (bord extérieur)
│   │   └── RoadSnapper.java           # MODIFIÉ : Balayage bidirectionnel et ciblage INNER / OUTER
│   └── segmentation/
│       ├── RoadSnappingEngine.java    # MODIFIÉ : Utilisation explicite de SnapTargetEdge.OUTER
│       └── ZoneSegmentationEngine.java # MODIFIÉ : Utilisation de RoadSnapper avec SnapTargetEdge.INNER

src/test/java/com/sam102022/photoshop/
├── core/
│   ├── detection/
│   │   ├── RoadCandidateDetectorTest.java # MODIFIÉ : Tests des rues blanches/grises et roadSensitivity
│   │   └── RoadDetectorTest.java          # MODIFIÉ : Tests de transmission de roadSensitivity
│   ├── geometry/
│   │   ├── SnapTargetEdgeTest.java        # NOUVEAU : Tests unitaires de SnapTargetEdge
│   │   └── RoadSnapperTest.java           # MODIFIÉ : Tests de calage INNER vs OUTER et rétraction
│   └── segmentation/
│       └── ZoneSegmentationEngineTest.java # MODIFIÉ : Test du calage géométrique précis au bord des rues
└── IntegrationCliTest.java                # MODIFIÉ : Test d'intégration grandeur nature sur sample 02
```

---

### Task 1: Détection des Rues Blanches et Grises (`RoadCandidateDetector`) et Intégration de `roadSensitivity`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/core/detection/RoadCandidateDetector.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/detection/RoadCandidateDetectorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour les rues blanches/grises et la sensibilité dans `RoadCandidateDetectorTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/core/detection/RoadCandidateDetectorTest.java` :
```java
    @Test
    @DisplayName("Détecter une rue locale blanche avec bordures grises neutres")
    void testDetectWhiteAndGreyLocalStreets() {
        int w = 20, h = 20;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        // Fond résidentiel beige / marron clair
        g.setColor(new Color(238, 230, 220));
        g.fillRect(0, 0, w, h);

        // Rue locale verticale à x=10 : chaussée blanche x=10, bordures grises à x=9 et x=11
        g.setColor(new Color(200, 200, 200)); // Bordure grise
        g.drawLine(9, 0, 9, h - 1);
        g.drawLine(11, 0, 11, h - 1);
        g.setColor(new Color(245, 245, 245)); // Chaussée blanche neutre
        g.drawLine(10, 0, 10, h - 1);
        g.dispose();

        BinaryMask roads = detector.detect(map, 1.0f);
        assertTrue(roads.get(10, 5), "La chaussée blanche doit être détectée");
        assertTrue(roads.get(9, 5) || roads.get(11, 5), "Les bordures grises de chaussée doivent être détectées");
        assertFalse(roads.get(2, 2), "Le fond beige résidentiel ne doit pas être une route");
    }

    @Test
    @DisplayName("Rejeter les surfaces blanches larges et uniformes sans structure de corridor")
    void testRejectUniformWhiteAreas() {
        int w = 30, h = 30;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(245, 245, 245)); // Grand aplat blanc uniforme (toiture)
        g.fillRect(0, 0, w, h);
        g.dispose();

        BinaryMask roads = detector.detect(map, 1.0f);
        // Au centre d'un grand aplat uniforme sans bordures à proximité, aucun pixel de route ne doit être détecté
        assertFalse(roads.get(15, 15), "Un grand aplat blanc uniforme sans bordure ne doit pas être une route");
    }

    @Test
    @DisplayName("Modulation de la détection selon roadSensitivity")
    void testRoadSensitivityModulation() {
        int w = 20, h = 20;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(238, 230, 220));
        g.fillRect(0, 0, w, h);

        // Rue à plus faible contraste (chaussée à 233, bordure à 210)
        g.setColor(new Color(210, 210, 210));
        g.drawLine(9, 0, 9, h - 1);
        g.drawLine(11, 0, 11, h - 1);
        g.setColor(new Color(233, 233, 233));
        g.drawLine(10, 0, 10, h - 1);
        g.dispose();

        BinaryMask roadsLowSens = detector.detect(map, 0.7f);
        BinaryMask roadsHighSens = detector.detect(map, 1.5f);

        // Avec une sensibilité élevée, la rue peu contrastée doit être détectée
        assertTrue(roadsHighSens.get(10, 5), "Avec haute sensibilité, la rue peu contrastée doit être détectée");
        // Avec une faible sensibilité, elle doit être filtrée
        assertFalse(roadsLowSens.get(10, 5), "Avec basse sensibilité, la rue peu contrastée doit être filtrée");
    }
```

- [ ] **Step 2: Exécuter les tests pour vérifier qu'ils échouent**

Run: `mvn test -Dtest=RoadCandidateDetectorTest#testDetectWhiteAndGreyLocalStreets`
Expected: FAIL avec "cannot find symbol: method detect(BufferedImage,float)"

- [ ] **Step 3: Mettre à jour `RoadCandidateDetector`**

Dans `src/main/java/com/sam102022/photoshop/core/detection/RoadCandidateDetector.java` :
- Conserver la signature existante `public BinaryMask detect(BufferedImage image)` en déléguant à `detect(image, 1.0f)`.
- Ajouter la méthode `public BinaryMask detect(BufferedImage image, float roadSensitivity)` :
  - Validation non null et validation de `roadSensitivity > 0`.
  - Pour chaque pixel :
    - Si `roadScore(r, g, b) > 0` (orange ou jaune) -> candidat actif.
    - Sinon tester `isWhiteOrGreyStreet(image, x, y, roadSensitivity)` :
      - Neutralité : $\max(|R - G|, |R - B|, |G - B|) \le 10$.
      - Luminance $Y = 0.299R + 0.587G + 0.114B$.
      - $Y \ge (240 - 15 \times (\text{roadSensitivity} - 1.0f))$ : chaussée claire.
      - Vérifier dans une fenêtre transversale (rayon 2 à 8 pixels) qu'il existe un pixel de transition sombre ($Y \le 215$).
      - Si $Y \in [180..220]$ (bordure grise neutre) : actif si adjacent à un pixel de chaussée claire.
  - Retourner `BinaryMask`.

- [ ] **Step 4: Exécuter les tests pour vérifier qu'ils passent**

Run: `mvn test -Dtest=RoadCandidateDetectorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/RoadCandidateDetector.java src/test/java/com/sam102022/photoshop/core/detection/RoadCandidateDetectorTest.java
git commit -m "feat: detection des rues blanches/grises et prise en compte de roadSensitivity"
```

---

### Task 2: Transmission de `roadSensitivity` dans `RoadDetector`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java`

- [ ] **Step 1: Écrire le test unitaire pour la transmission de `roadSensitivity` dans `RoadDetectorTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java` :
```java
    @Test
    @DisplayName("Vérifier la prise en compte effective de roadSensitivity par RoadDetector")
    void testRoadDetectorAppliesRoadSensitivity() {
        int w = 20, h = 20;
        BufferedImage map = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(238, 230, 220));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(210, 210, 210));
        g.drawLine(9, 0, 9, h - 1);
        g.drawLine(11, 0, 11, h - 1);
        g.setColor(new Color(233, 233, 233));
        g.drawLine(10, 0, 10, h - 1);
        g.dispose();

        SnappingConfig lowSensConfig = SnappingConfig.builder().roadSensitivity(0.7f).build();
        SnappingConfig highSensConfig = SnappingConfig.builder().roadSensitivity(1.5f).build();

        BinaryMask lowResult = detector.detectRoads(map, lowSensConfig);
        BinaryMask highResult = detector.detectRoads(map, highSensConfig);

        assertFalse(lowResult.get(10, 5), "Basse sensibilité doit filtrer la rue peu contrastée");
        assertTrue(highResult.get(10, 5), "Haute sensibilité doit détecter la rue peu contrastée");
    }
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=RoadDetectorTest#testRoadDetectorAppliesRoadSensitivity`
Expected: FAIL car `RoadDetector` n'utilise pas encore `config.roadSensitivity()`

- [ ] **Step 3: Mettre à jour `RoadDetector`**

Dans `src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java` :
Remplacer :
```java
BinaryMask candidates = new RoadCandidateDetector().detect(mapImage);
```
Par :
```java
BinaryMask candidates = new RoadCandidateDetector().detect(mapImage, config.roadSensitivity());
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=RoadDetectorTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java
git commit -m "feat: transmission de roadSensitivity a RoadCandidateDetector dans RoadDetector"
```

---

### Task 3: Énumération `SnapTargetEdge` (`INNER` / `OUTER`)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/geometry/SnapTargetEdge.java`
- Create: `src/test/java/com/sam102022/photoshop/core/geometry/SnapTargetEdgeTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour `SnapTargetEdge`**

Créer `src/test/java/com/sam102022/photoshop/core/geometry/SnapTargetEdgeTest.java` :
```java
package com.sam102022.photoshop.core.geometry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Tests unitaires pour SnapTargetEdge")
class SnapTargetEdgeTest {

    @Test
    @DisplayName("Vérifier les valeurs de l'énumération SnapTargetEdge")
    void testEnumValues() {
        assertEquals(2, SnapTargetEdge.values().length);
        assertNotNull(SnapTargetEdge.valueOf("INNER"));
        assertNotNull(SnapTargetEdge.valueOf("OUTER"));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier qu'il échoue**

Run: `mvn test -Dtest=SnapTargetEdgeTest`
Expected: FAIL avec "cannot find symbol: class SnapTargetEdge"

- [ ] **Step 3: Créer `SnapTargetEdge.java`**

Créer `src/main/java/com/sam102022/photoshop/core/geometry/SnapTargetEdge.java` :
```java
package com.sam102022.photoshop.core.geometry;

/**
 * Cible géométrique du recalage d'un contour le long d'un ruban routier.
 */
public enum SnapTargetEdge {
    /**
     * Calage au bord extérieur du ruban routier (englobe l'emprise de la route).
     * Mode appliqué pour le détourage de territoire global.
     */
    OUTER,

    /**
     * Calage au bord intérieur du ruban routier (s'arrête au pied de la chaussée).
     * Mode appliqué pour le découpage de zone afin de ne pas englober la chaussée.
     */
    INNER
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Run: `mvn test -Dtest=SnapTargetEdgeTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/geometry/SnapTargetEdge.java src/test/java/com/sam102022/photoshop/core/geometry/SnapTargetEdgeTest.java
git commit -m "feat: ajout SnapTargetEdge pour distinguer calage bord interieur et exterieur"
```

---

### Task 4: Recalage Géométrique Bi-Mode dans `RoadSnapper`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/core/geometry/RoadSnapper.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/geometry/RoadSnapperTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour le calage `INNER` vs `OUTER` dans `RoadSnapperTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/core/geometry/RoadSnapperTest.java` :
```java
    @Test
    @DisplayName("Caler au bord intérieur (INNER) vs bord extérieur (OUTER) face à une route")
    void testSnapInnerVsOuterEdge() {
        int w = 50, h = 50;
        BinaryMask roads = new BinaryMask(w, h);
        // Route verticale de largeur 4 px à x=[25..28]
        for (int y = 0; y < h; y++) {
            for (int x = 25; x <= 28; x++) {
                roads.set(x, y, true);
            }
        }

        // Contour carré à gauche de la route : x=[10..20], y=[10..30]
        List<Point> contour = List.of(
                new Point(10, 10),
                new Point(20, 10),
                new Point(20, 30),
                new Point(10, 30)
        );

        // En mode OUTER : le bord droit à x=20 doit avancer jusqu'au bord extérieur x=29
        List<Point> snappedOuter = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.OUTER);
        assertEquals(29, snappedOuter.get(1).x, "Mode OUTER doit englober la route jusqu'à son bord extérieur (x=29)");

        // En mode INNER : le bord droit à x=20 doit s'arrêter juste avant la route à x=24
        List<Point> snappedInner = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.INNER);
        assertEquals(24, snappedInner.get(1).x, "Mode INNER doit s'arrêter juste avant la route (x=24)");
    }

    @Test
    @DisplayName("Rétraction en mode INNER si le sommet initial est déjà situé sur la chaussée")
    void testSnapInnerEdgeRetractWhenStartingOnRoad() {
        int w = 50, h = 50;
        BinaryMask roads = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 20; x <= 25; x++) {
                roads.set(x, y, true);
            }
        }

        // Contour dont le sommet droit est déjà sur la route à x=22
        List<Point> contour = List.of(
                new Point(10, 10),
                new Point(22, 10),
                new Point(22, 30),
                new Point(10, 30)
        );

        List<Point> snappedInner = snapper.snapContour(contour, roads, w, h, 15, SnapTargetEdge.INNER);
        // Doit être rétracté en arrière vers x=19 (hors de la route)
        assertEquals(19, snappedInner.get(1).x, "Mode INNER doit rétracter le sommet hors de la route à x=19");
    }
```

- [ ] **Step 2: Exécuter les tests pour vérifier qu'ils échouent**

Run: `mvn test -Dtest=RoadSnapperTest#testSnapInnerVsOuterEdge`
Expected: FAIL avec "cannot find symbol: method snapContour(..., SnapTargetEdge)"

- [ ] **Step 3: Mettre à jour `RoadSnapper`**

Dans `src/main/java/com/sam102022/photoshop/core/geometry/RoadSnapper.java` :
- Conserver la signature historique `snapContour(points, candidates, imageWidth, imageHeight, radius)` en déléguant avec `SnapTargetEdge.OUTER`.
- Ajouter la méthode `public List<Point> snapContour(List<Point> points, BinaryMask candidates, int imageWidth, int imageHeight, int radius, SnapTargetEdge targetEdge)`.
- Remplacer `findRoadBandEnd` par une analyse d'intervalle `findRoadBandInterval(candidates, current, normal, imageWidth, imageHeight, radius)` retournant `[k_start, k_end]`.
- Implémenter le positionnement :
  - Si `!isRoad(current)` :
    - Si `targetEdge == SnapTargetEdge.INNER` : `targetStep = Math.max(0, k_start - 1)`.
    - Si `targetEdge == SnapTargetEdge.OUTER` : `targetStep = Math.min(k_end + 1, radius)`.
  - Si `isRoad(current)` :
    - Si `targetEdge == SnapTargetEdge.OUTER` : avancer jusqu'à la fin de la route `k_end + 1`.
    - Si `targetEdge == SnapTargetEdge.INNER` : balayage inverse $-\vec{n}$ pour sortir de la route à l'intérieur.
- Conserver complexité cognitive $\le 15$ par méthode.

- [ ] **Step 4: Exécuter les tests pour vérifier qu'ils passent**

Run: `mvn test -Dtest=RoadSnapperTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/geometry/RoadSnapper.java src/test/java/com/sam102022/photoshop/core/geometry/RoadSnapperTest.java
git commit -m "feat: calage bi-mode INNER et OUTER dans RoadSnapper avec retraction sur chaussee"
```

---

### Task 5: Intégration dans `RoadSnappingEngine` et `ZoneSegmentationEngine`

**Files:**
- Modify: `src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java`
- Modify: `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java`
- Modify: `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java`

- [ ] **Step 1: Écrire le test unitaire pour le calage précis de zone dans `ZoneSegmentationEngineTest`**

Ajouter dans `src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java` :
```java
    @Test
    @DisplayName("Calage vectoriel géométrique précis au bord intérieur d'une rue blanche")
    void testZoneSnapsGeometricallyToInnerRoadEdge() {
        int w = 50, h = 50;
        BinaryMask territory = new BinaryMask(w, h).not();

        // Rue locale blanche à x=[25..28]
        BinaryMask roads = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 25; x <= 28; x++) {
                roads.set(x, y, true);
            }
        }

        BinaryMask barriers = roads.copy();
        BinaryMask interior = new BinaryMask(w, h);
        fillRegion(interior, 10, 10, 14, 30); // x de 10 à 23 (s'arrête à 1 pixel avant la route)

        CoverageMask result = engine.segmentZone(interior, roads, barriers, territory, defaultConfig);
        assertNotNull(result);

        // La bordure de la zone doit s'arrêter exactement à x=24 avec couverture active
        assertTrue(result.get(24, 20) > 0, "Le bord intérieur à x=24 doit être actif");
        assertEquals(0, result.get(25, 20), "La chaussée à x=25 doit être strictement à 0");
    }
```

- [ ] **Step 2: Exécuter le test pour vérifier son statut**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest#testZoneSnapsGeometricallyToInnerRoadEdge`

- [ ] **Step 3: Mettre à jour `RoadSnappingEngine` et `ZoneSegmentationEngine`**

Dans `src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java` :
- S'assurer que l'appel à `roadSnapper.snapContour` passe explicitement `SnapTargetEdge.OUTER`.

Dans `src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java` :
- Instancier `RoadSnapper roadSnapper = new RoadSnapper();`
- Dans `rasterizeZoneCoverage(...)`, après simplification du contour vectoriel, appliquer :
  `List<Point> snappedContour = roadSnapper.snapContour(simplified, roadCandidates, width, height, config.snapDistance(), SnapTargetEdge.INNER);`
- Rasteriser `snappedContour` via `polygonBuilder`.
- Garantir le masquage strict à zéro des pixels routiers (`applyStrictExclusions`).

- [ ] **Step 4: Exécuter les tests pour vérifier qu'ils passent**

Run: `mvn test -Dtest=ZoneSegmentationEngineTest,RoadSnappingEngineTest`
Expected: PASS

- [ ] **Step 5: Commiter les modifications**

Run:
```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java src/main/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngine.java src/test/java/com/sam102022/photoshop/core/segmentation/ZoneSegmentationEngineTest.java
git commit -m "feat: integration de RoadSnapper INNER dans ZoneSegmentationEngine et OUTER dans RoadSnappingEngine"
```

---

### Task 6: Test d'Intégration Réel et Validation de Non-Régression Complète

**Files:**
- Modify: `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java`

- [ ] **Step 1: Exécuter les tests d'intégration sur sample 01 et sample 02**

Run: `mvn test -Dtest=IntegrationCliTest`
Expected: PASS (IoU $\ge 0.90$ et exclusion routière à 0.0%)

- [ ] **Step 2: Exécuter l'intégralité de la suite Maven**

Run: `mvn clean test`
Expected: BUILD SUCCESS (100% de succès)

- [ ] **Step 3: Commiter les validations finales**

Run:
```bash
git add src/test/java/com/sam102022/photoshop/IntegrationCliTest.java
git commit -m "test: validation d'integration sur les rues locales avec calage INNER/OUTER"
```
