# Plan d'Implémentation : Détourage de Carte Google Maps avec Snapping Routier

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Développer une application Java 21 (CLI + GUI Swing) permettant de détourer automatiquement une capture Google Maps en recalant un masque vert grossier sur les axes routiers environnants (autoroutes, départementales, rues urbaines) via détection de couleur, filtre Sobel, morphologie mathématique et propagation BFS contrainte.

**Architecture:** Le système repose sur une représentation matricielle mémoire (`BinaryMask`), un extracteur de masque vert (`GreenMaskExtractor`), un détecteur multi-critères d'axes routiers (`RoadDetector`), un moteur géodésique de recalage contraint par barrières (`RoadSnappingEngine`), des modules d'entrée/sortie (`ImageLoader`, `ImageExporter` avec anticrénelage alpha), pilotés soit en CLI (`CliRunner`) soit via une interface graphique interactive (`MainWindow`).

**Tech Stack:** Java 21, Maven, Java2D (BufferedImage, ImageIO), Swing, JUnit Jupiter 5.10.2.

---

### Task 1: Configuration du Projet et Framework de Test JUnit 5

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/sam102022/photoshop/SmokeTest.java`

- [ ] **Step 1: Mettre à jour `pom.xml` avec JUnit 5 et le plugin Surefire**

Remplacer le contenu de `pom.xml` par :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.sam102022</groupId>
    <artifactId>photoshop</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.jupiter.version>5.10.2</junit.jupiter.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.jupiter.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.1</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.sam102022.photoshop.Main</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Créer le test SmokeTest**

Créer `src/test/java/com/sam102022/photoshop/SmokeTest.java` :

```java
package com.sam102022.photoshop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {

    @Test
    @DisplayName("L'environnement de test JUnit 5 est opérationnel")
    void shouldPassSmokeTest() {
        assertTrue(true, "Le framework de test fonctionne correctement.");
    }
}
```

- [ ] **Step 3: Exécuter le test pour valider l'environnement**

Exécuter la commande :
```bash
mvn test -Dtest=SmokeTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 4: Commiter**

```bash
git add pom.xml src/test/java/com/sam102022/photoshop/SmokeTest.java
git commit -m "build: configure JUnit 5 and project structure"
```

---

### Task 2: Modèle BinaryMask

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/model/BinaryMask.java`
- Test: `src/test/java/com/sam102022/photoshop/core/model/BinaryMaskTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour BinaryMask**

Créer `src/test/java/com/sam102022/photoshop/core/model/BinaryMaskTest.java` :

```java
package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BinaryMaskTest {

    @Test
    @DisplayName("Initialisation d'un masque vide avec dimensions valides")
    void testInitialization() {
        BinaryMask mask = new BinaryMask(10, 20);
        assertEquals(10, mask.getWidth());
        assertEquals(20, mask.getHeight());
        assertEquals(0, mask.countActivePixels());
        assertFalse(mask.get(0, 0));
        assertFalse(mask.get(9, 19));
    }

    @Test
    @DisplayName("Gestion des coordonnées et des limites")
    void testBoundsAndSet() {
        BinaryMask mask = new BinaryMask(5, 5);
        assertTrue(mask.isInBounds(0, 0));
        assertTrue(mask.isInBounds(4, 4));
        assertFalse(mask.isInBounds(-1, 0));
        assertFalse(mask.isInBounds(0, 5));

        mask.set(2, 3, true);
        assertTrue(mask.get(2, 3));
        assertEquals(1, mask.countActivePixels());

        mask.set(2, 3, false);
        assertFalse(mask.get(2, 3));
        assertEquals(0, mask.countActivePixels());
    }

    @Test
    @DisplayName("Opérations logiques : OR, AND, NOT et copie")
    void testBitwiseOperations() {
        BinaryMask m1 = new BinaryMask(2, 2);
        m1.set(0, 0, true);
        m1.set(1, 0, true);

        BinaryMask m2 = new BinaryMask(2, 2);
        m2.set(1, 0, true);
        m2.set(1, 1, true);

        BinaryMask orMask = m1.or(m2);
        assertEquals(3, orMask.countActivePixels());
        assertTrue(orMask.get(0, 0));
        assertTrue(orMask.get(1, 0));
        assertTrue(orMask.get(1, 1));
        assertFalse(orMask.get(0, 1));

        BinaryMask andMask = m1.and(m2);
        assertEquals(1, andMask.countActivePixels());
        assertTrue(andMask.get(1, 0));

        BinaryMask notMask = m1.not();
        assertEquals(2, notMask.countActivePixels());
        assertFalse(notMask.get(0, 0));
        assertFalse(notMask.get(1, 0));
        assertTrue(notMask.get(0, 1));
        assertTrue(notMask.get(1, 1));

        BinaryMask clone = m1.copy();
        assertEquals(m1.countActivePixels(), clone.countActivePixels());
        clone.set(0, 1, true);
        assertFalse(m1.get(0, 1));
    }

    @Test
    @DisplayName("Rejet des dimensions négatives ou nulles")
    void testInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new BinaryMask(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new BinaryMask(10, -5));
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=BinaryMaskTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol BinaryMask`).

- [ ] **Step 3: Implémenter BinaryMask**

Créer `src/main/java/com/sam102022/photoshop/core/model/BinaryMask.java` :

```java
package com.sam102022.photoshop.core.model;

import java.util.Arrays;

/**
 * Matrice binaire 2D optimisée représentant un calque de pixels actifs (true) ou inactifs (false).
 */
public class BinaryMask {
    private final int width;
    private final int height;
    private final boolean[] data;

    public BinaryMask(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.data = new boolean[width * height];
    }

    public BinaryMask(int width, int height, boolean[] data) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (data == null || data.length != width * height) {
            throw new IllegalArgumentException("Les données du masque ne correspondent pas aux dimensions spécifiées.");
        }
        this.width = width;
        this.height = height;
        this.data = Arrays.copyOf(data, data.length);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isInBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean get(int x, int y) {
        if (!isInBounds(x, y)) {
            return false;
        }
        return data[y * width + x];
    }

    public void set(int x, int y, boolean value) {
        if (isInBounds(x, y)) {
            data[y * width + x] = value;
        }
    }

    public int countActivePixels() {
        int count = 0;
        for (boolean b : data) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    public BinaryMask copy() {
        return new BinaryMask(width, height, this.data);
    }

    public BinaryMask or(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] || other.data[i];
        }
        return result;
    }

    public BinaryMask and(BinaryMask other) {
        validateSameDimensions(other);
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = this.data[i] && other.data[i];
        }
        return result;
    }

    public BinaryMask not() {
        BinaryMask result = new BinaryMask(width, height);
        for (int i = 0; i < data.length; i++) {
            result.data[i] = !this.data[i];
        }
        return result;
    }

    private void validateSameDimensions(BinaryMask other) {
        if (other == null || other.width != this.width || other.height != this.height) {
            throw new IllegalArgumentException("Dimensions incompatibles pour l'opération matricielle.");
        }
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=BinaryMaskTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/model/BinaryMask.java src/test/java/com/sam102022/photoshop/core/model/BinaryMaskTest.java
git commit -m "feat(core): implement BinaryMask 2D boolean grid"
```

---

### Task 3: Configuration de Recalage SnappingConfig

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java`
- Test: `src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour SnappingConfig**

Créer `src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java` :

```java
package com.sam102022.photoshop.core.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SnappingConfigTest {

    @Test
    @DisplayName("Valeurs par défaut conformes à la spécification")
    void testDefaults() {
        SnappingConfig config = SnappingConfig.defaults();
        assertEquals(40, config.snapDistance());
        assertEquals(1.0f, config.roadSensitivity(), 0.001f);
        assertEquals(1, config.smoothRadius());
        assertEquals(8, config.seedErosionRadius());
        assertEquals(2, config.closingRadius());
    }

    @Test
    @DisplayName("Validation des paramètres invalides")
    void testValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(0, 1.0f, 1, 8, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 0.0f, 1, 8, 2));
        assertThrows(IllegalArgumentException.class, () ->
                new SnappingConfig(40, 1.0f, -1, 8, 2));
    }

    @Test
    @DisplayName("Création avec le Builder")
    void testBuilder() {
        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(50)
                .roadSensitivity(1.5f)
                .smoothRadius(2)
                .seedErosionRadius(6)
                .closingRadius(3)
                .build();

        assertEquals(50, config.snapDistance());
        assertEquals(1.5f, config.roadSensitivity(), 0.001f);
        assertEquals(2, config.smoothRadius());
        assertEquals(6, config.seedErosionRadius());
        assertEquals(3, config.closingRadius());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=SnappingConfigTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol SnappingConfig`).

- [ ] **Step 3: Implémenter SnappingConfig**

Créer `src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java` :

```java
package com.sam102022.photoshop.core.model;

/**
 * Configuration paramétrable pour le calage routier et le détourage.
 */
public record SnappingConfig(
        int snapDistance,
        float roadSensitivity,
        int smoothRadius,
        int seedErosionRadius,
        int closingRadius
) {
    public SnappingConfig {
        if (snapDistance <= 0) {
            throw new IllegalArgumentException("snapDistance doit être > 0 : " + snapDistance);
        }
        if (roadSensitivity <= 0.0f) {
            throw new IllegalArgumentException("roadSensitivity doit être > 0 : " + roadSensitivity);
        }
        if (smoothRadius < 0) {
            throw new IllegalArgumentException("smoothRadius doit être >= 0 : " + smoothRadius);
        }
        if (seedErosionRadius < 0) {
            throw new IllegalArgumentException("seedErosionRadius doit être >= 0 : " + seedErosionRadius);
        }
        if (closingRadius < 0) {
            throw new IllegalArgumentException("closingRadius doit être >= 0 : " + closingRadius);
        }
    }

    public static SnappingConfig defaults() {
        return new SnappingConfig(40, 1.0f, 1, 8, 2);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int snapDistance = 40;
        private float roadSensitivity = 1.0f;
        private int smoothRadius = 1;
        private int seedErosionRadius = 8;
        private int closingRadius = 2;

        public Builder snapDistance(int snapDistance) {
            this.snapDistance = snapDistance;
            return this;
        }

        public Builder roadSensitivity(float roadSensitivity) {
            this.roadSensitivity = roadSensitivity;
            return this;
        }

        public Builder smoothRadius(int smoothRadius) {
            this.smoothRadius = smoothRadius;
            return this;
        }

        public Builder seedErosionRadius(int seedErosionRadius) {
            this.seedErosionRadius = seedErosionRadius;
            return this;
        }

        public Builder closingRadius(int closingRadius) {
            this.closingRadius = closingRadius;
            return this;
        }

        public SnappingConfig build() {
            return new SnappingConfig(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius, closingRadius);
        }
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=SnappingConfigTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/model/SnappingConfig.java src/test/java/com/sam102022/photoshop/core/model/SnappingConfigTest.java
git commit -m "feat(core): implement SnappingConfig with builder and defaults"
```

---

### Task 4: Opérations Morphologiques (MorphologyOps)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/MorphologyOps.java`
- Test: `src/test/java/com/sam102022/photoshop/core/segmentation/MorphologyOpsTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour MorphologyOps**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/MorphologyOpsTest.java` :

```java
package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MorphologyOpsTest {

    @Test
    @DisplayName("Dilatation d'un pixel isolé")
    void testDilateSinglePixel() {
        BinaryMask mask = new BinaryMask(7, 7);
        mask.set(3, 3, true);

        BinaryMask dilated = MorphologyOps.dilate(mask, 1);
        // Le pixel central + ses 8 voisins (rayon 1 boîte) doivent être actifs = 9 pixels
        assertEquals(9, dilated.countActivePixels());
        assertTrue(dilated.get(3, 3));
        assertTrue(dilated.get(2, 2));
        assertTrue(dilated.get(4, 4));
        assertFalse(dilated.get(1, 1));
    }

    @Test
    @DisplayName("Érosion d'un carré de 3x3 pixels")
    void testErodeSquare() {
        BinaryMask mask = new BinaryMask(7, 7);
        for (int y = 2; y <= 4; y++) {
            for (int x = 2; x <= 4; x++) {
                mask.set(x, y, true);
            }
        }
        assertEquals(9, mask.countActivePixels());

        BinaryMask eroded = MorphologyOps.erode(mask, 1);
        // Seul le pixel central doit survivre car tous ses voisins étaient actifs
        assertEquals(1, eroded.countActivePixels());
        assertTrue(eroded.get(3, 3));
    }

    @Test
    @DisplayName("Fermeture morphologique pour combler une brèche de 2 pixels")
    void testCloseFillsGap() {
        BinaryMask mask = new BinaryMask(10, 10);
        // Ligne horizontale avec un trou au milieu (x=4 et x=5 à false)
        for (int x = 0; x <= 3; x++) {
            mask.set(x, 5, true);
        }
        for (int x = 6; x <= 9; x++) {
            mask.set(x, 5, true);
        }
        assertFalse(mask.get(4, 5));
        assertFalse(mask.get(5, 5));

        BinaryMask closed = MorphologyOps.close(mask, 2);
        assertTrue(closed.get(4, 5), "Le trou à x=4 doit être rebouché après fermeture");
        assertTrue(closed.get(5, 5), "Le trou à x=5 doit être rebouché après fermeture");
    }

    @Test
    @DisplayName("Ouverture morphologique pour supprimer le bruit isolé")
    void testOpenRemovesNoise() {
        BinaryMask mask = new BinaryMask(10, 10);
        // Un pixel isolé de bruit
        mask.set(1, 1, true);
        // Une zone solide 4x4
        for (int y = 4; y <= 7; y++) {
            for (int x = 4; x <= 7; x++) {
                mask.set(x, y, true);
            }
        }

        BinaryMask opened = MorphologyOps.open(mask, 1);
        assertFalse(opened.get(1, 1), "Le pixel isolé doit être éliminé");
        assertTrue(opened.get(5, 5), "La zone solide doit être conservée");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=MorphologyOpsTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol MorphologyOps`).

- [ ] **Step 3: Implémenter MorphologyOps**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/MorphologyOps.java` :

```java
package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Opérations de morphologie mathématique 2D sur BinaryMask (dilatation, érosion, ouverture, fermeture).
 */
public final class MorphologyOps {

    private MorphologyOps() {
    }

    public static BinaryMask dilate(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask.get(x, y)) {
                    int minX = Math.max(0, x - radius);
                    int maxX = Math.min(w - 1, x + radius);
                    int minY = Math.max(0, y - radius);
                    int maxY = Math.min(h - 1, y + radius);

                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            result.set(nx, ny, true);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static BinaryMask erode(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask result = new BinaryMask(w, h);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) {
                    continue;
                }
                boolean allNeighborsActive = true;
                int minX = Math.max(0, x - radius);
                int maxX = Math.min(w - 1, x + radius);
                int minY = Math.max(0, y - radius);
                int maxY = Math.min(h - 1, y + radius);

                // Si le voisinage est tronqué par les bords de l'image, on considère les pixels hors champ comme inactifs
                if (x - radius < 0 || x + radius >= w || y - radius < 0 || y + radius >= h) {
                    allNeighborsActive = false;
                } else {
                    checkLoop:
                    for (int ny = minY; ny <= maxY; ny++) {
                        for (int nx = minX; nx <= maxX; nx++) {
                            if (!mask.get(nx, ny)) {
                                allNeighborsActive = false;
                                break checkLoop;
                            }
                        }
                    }
                }

                if (allNeighborsActive) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    public static BinaryMask close(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        return erode(dilate(mask, radius), radius);
    }

    public static BinaryMask open(BinaryMask mask, int radius) {
        if (radius <= 0) {
            return mask.copy();
        }
        return dilate(erode(mask, radius), radius);
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=MorphologyOpsTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/MorphologyOps.java src/test/java/com/sam102022/photoshop/core/segmentation/MorphologyOpsTest.java
git commit -m "feat(core): implement morphological operators (dilate, erode, close, open)"
```

---

### Task 5: Extracteur de Masque Vert (GreenMaskExtractor)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/GreenMaskExtractor.java`
- Test: `src/test/java/com/sam102022/photoshop/core/detection/GreenMaskExtractorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour GreenMaskExtractor**

Créer `src/test/java/com/sam102022/photoshop/core/detection/GreenMaskExtractorTest.java` :

```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class GreenMaskExtractorTest {

    @Test
    @DisplayName("Extraction d'un rectangle vert pur sur fond blanc")
    void testExtractPureGreen() {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 50, 50);

        // Rectangle vert au centre (10,10 à 39,39)
        g.setColor(new Color(0, 220, 0));
        g.fillRect(10, 10, 30, 30);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertTrue(mask.get(20, 20), "Le centre du carré vert doit être actif");
        assertFalse(mask.get(5, 5), "Le fond blanc ne doit pas être actif");
        assertTrue(mask.countActivePixels() > 800, "La majeure partie du carré 30x30 doit être détectée");
    }

    @Test
    @DisplayName("Extraction avec différentes teintes vertes (kaki, émeraude)")
    void testExtractVariousGreens() {
        BufferedImage image = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 30, 30);

        // Vert émeraude
        g.setColor(new Color(20, 180, 80));
        g.fillRect(5, 5, 10, 10);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertTrue(mask.get(10, 10), "Le vert émeraude doit être détecté");
        assertFalse(mask.get(25, 25), "Le fond noir ne doit pas être détecté");
    }

    @Test
    @DisplayName("Image sans pixel vert retourne un masque vide")
    void testExtractNoGreen() {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        GreenMaskExtractor extractor = new GreenMaskExtractor();
        BinaryMask mask = extractor.extract(image);

        assertEquals(0, mask.countActivePixels(), "Aucun pixel ne doit être actif sur une image rouge");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=GreenMaskExtractorTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol GreenMaskExtractor`).

- [ ] **Step 3: Implémenter GreenMaskExtractor**

Créer `src/main/java/com/sam102022/photoshop/core/detection/GreenMaskExtractor.java` :

```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Détecte les composantes vertes dans une image de calque avec filtrage RGB et HSV,
 * complété par un nettoyage morphologique du bruit résiduel.
 */
public class GreenMaskExtractor {

    public BinaryMask extract(BufferedImage maskImage) {
        if (maskImage == null) {
            throw new IllegalArgumentException("L'image de masque ne peut pas être null.");
        }

        int width = maskImage.getWidth();
        int height = maskImage.getHeight();
        BinaryMask rawMask = new BinaryMask(width, height);

        float[] hsv = new float[3];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = maskImage.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                // Ignorer les pixels transparents si canal alpha présent
                if (alpha < 30) {
                    continue;
                }

                boolean isGreen = false;

                // Règle 1 : Dominance RGB stricte (G nettement supérieur à R et B)
                if (g > (r + 25) && g > (b + 25)) {
                    isGreen = true;
                } else {
                    // Règle 2 : Analyse HSV (teinte verte entre 70° et 170°)
                    Color.RGBtoHSB(r, g, b, hsv);
                    float hueDeg = hsv[0] * 360f;
                    float saturation = hsv[1];
                    float brightness = hsv[2];

                    if (hueDeg >= 70f && hueDeg <= 170f && saturation > 0.20f && brightness > 0.20f) {
                        isGreen = true;
                    }
                }

                if (isGreen) {
                    rawMask.set(x, y, true);
                }
            }
        }

        // Nettoyage morphologique : ouverture 1px pour supprimer le bruit isolé
        return MorphologyOps.open(rawMask, 1);
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=GreenMaskExtractorTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/detection/GreenMaskExtractor.java src/test/java/com/sam102022/photoshop/core/detection/GreenMaskExtractorTest.java
git commit -m "feat(core): implement GreenMaskExtractor with RGB/HSV thresholding"
```

---

### Task 6: Détecteur des Axes Routiers (RoadDetector)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java`
- Test: `src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour RoadDetector**

Créer `src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java` :

```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class RoadDetectorTest {

    @Test
    @DisplayName("Détection d'une autoroute orange Google Maps")
    void testDetectOrangeHighway() {
        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        // Fond cartographique beige/gris typique
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 40, 40);

        // Voie rapide orange (R=245, G=165, B=100)
        g.setColor(new Color(245, 165, 100));
        g.fillRect(18, 0, 4, 40);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(20, 20), "La ligne orange d'autoroute doit former une barrière routière");
        assertFalse(roadBarrier.get(5, 5), "Le fond ne doit pas être détecté comme route");
    }

    @Test
    @DisplayName("Détection d'une route principale jaune")
    void testDetectYellowAvenue() {
        BufferedImage map = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 40, 40);

        // Route jaune / crème (R=250, G=230, B=140)
        g.setColor(new Color(250, 230, 140));
        g.fillRect(0, 18, 40, 4);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(20, 20), "L'avenue jaune doit être détectée");
    }

    @Test
    @DisplayName("Rebouchage d'une brèche routière grâce à la fermeture morphologique")
    void testGapFillingThroughClosing() {
        BufferedImage map = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(new Color(230, 226, 219));
        g.fillRect(0, 0, 50, 50);

        // Ligne d'autoroute avec interruption de 2 pixels
        g.setColor(new Color(245, 165, 100));
        g.fillRect(20, 0, 4, 23);
        // trou de y=23 à y=25
        g.fillRect(20, 26, 4, 24);
        g.dispose();

        RoadDetector detector = new RoadDetector();
        BinaryMask roadBarrier = detector.detectRoads(map, SnappingConfig.defaults());

        assertTrue(roadBarrier.get(21, 24), "La brèche dans la route doit être pontée par la fermeture morphologique");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=RoadDetectorTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol RoadDetector`).

- [ ] **Step 3: Implémenter RoadDetector**

Créer `src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java` :

```java
package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import java.awt.image.BufferedImage;

/**
 * Détecte les axes routiers Google Maps par analyse spectrale des couleurs
 * (autoroutes orange, routes jaunes, rues blanches contrastées) et gradient Sobel.
 */
public class RoadDetector {

    public BinaryMask detectRoads(BufferedImage mapImage, SnappingConfig config) {
        if (mapImage == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }

        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        BinaryMask rawBarrier = new BinaryMask(w, h);

        int[][] luminance = new int[w][h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = mapImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                luminance[x][y] = (int) (0.299 * r + 0.587 * g + 0.114 * b);

                if (isRoadColor(r, g, b)) {
                    rawBarrier.set(x, y, true);
                }
            }
        }

        // Détection de contours Sobel pour capturer les délimitations nettes des routes blanches
        float sensitivity = config.roadSensitivity();
        int sobelThreshold = Math.max(30, (int) (75 / sensitivity));

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                if (rawBarrier.get(x, y)) {
                    continue;
                }

                // Gradients Sobel Gx et Gy
                int gx = (-1 * luminance[x - 1][y - 1]) + (1 * luminance[x + 1][y - 1])
                        + (-2 * luminance[x - 1][y]) + (2 * luminance[x + 1][y])
                        + (-1 * luminance[x - 1][y + 1]) + (1 * luminance[x + 1][y + 1]);

                int gy = (-1 * luminance[x - 1][y - 1]) + (-2 * luminance[x][y - 1]) + (-1 * luminance[x + 1][y - 1])
                        + (1 * luminance[x - 1][y + 1]) + (2 * luminance[x][y + 1]) + (1 * luminance[x + 1][y + 1]);

                int mag = (int) Math.sqrt(gx * gx + gy * gy);
                if (mag >= sobelThreshold) {
                    rawBarrier.set(x, y, true);
                }
            }
        }

        // Fermeture morphologique pour ponter les brèches (textes, ponts, intersections)
        return MorphologyOps.close(rawBarrier, config.closingRadius());
    }

    private boolean isRoadColor(int r, int g, int b) {
        // 1. Autoroutes & Voies rapides (orange / saumon)
        if (r >= 210 && g >= 130 && g <= 220 && b >= 50 && b <= 165 && (r - g) >= 15) {
            return true;
        }

        // 2. Routes principales & avenues (jaune / crème)
        if (r >= 220 && g >= 200 && b >= 100 && b <= 210 && Math.abs(r - g) <= 35 && (r - b) >= 30) {
            return true;
        }

        // 3. Rues secondaires & urbaines (blanc cassé très lumineux)
        if (r >= 240 && g >= 240 && b >= 240) {
            return true;
        }

        return false;
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=RoadDetectorTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/detection/RoadDetector.java src/test/java/com/sam102022/photoshop/core/detection/RoadDetectorTest.java
git commit -m "feat(core): implement RoadDetector with color taxonomy and Sobel filter"
```

---

### Task 7: Moteur de Recalage Routier (RoadSnappingEngine)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java`
- Test: `src/test/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngineTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour RoadSnappingEngine**

Créer `src/test/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngineTest.java` :

```java
package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoadSnappingEngineTest {

    @Test
    @DisplayName("Aimantation du masque vert grossier sur les limites d'un rectangle routier")
    void testSnappingToEnclosingRoads() {
        int w = 60;
        int h = 60;

        // Barrière routière formant un cadre de (15,15) à (45,45)
        BinaryMask roadBarrier = new BinaryMask(w, h);
        for (int i = 15; i <= 45; i++) {
            roadBarrier.set(i, 15, true); // haut
            roadBarrier.set(i, 45, true); // bas
            roadBarrier.set(15, i, true); // gauche
            roadBarrier.set(45, i, true); // droite
        }

        // Masque vert grossier : centré mais plus petit (de 22 à 38)
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 22; y <= 38; y++) {
            for (int x = 22; x <= 38; x++) {
                roughMask.set(x, y, true);
            }
        }

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(20)
                .seedErosionRadius(2)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask snapped = engine.snap(roughMask, roadBarrier, config);

        // L'intérieur du cadre routier (ex: 20, 20 et 40, 40) doit être activé
        assertTrue(snapped.get(30, 30), "Le centre doit être conservé");
        assertTrue(snapped.get(18, 18), "Le masque doit s'être étendu jusqu'à la barrière");
        assertTrue(snapped.get(42, 42), "Le masque doit s'être étendu jusqu'à la barrière");

        // Au-delà de la barrière routière (ex: x=10 ou x=50), rien ne doit être actif
        assertFalse(snapped.get(10, 30), "L'extérieur de la barrière ne doit pas être envahi");
        assertFalse(snapped.get(50, 30), "L'extérieur de la barrière ne doit pas être envahi");
    }

    @Test
    @DisplayName("Rétraction des débordements extérieurs au cadre routier")
    void testRetractionOfOverflowingAreas() {
        int w = 60;
        int h = 60;

        // Barrière routière à x=30
        BinaryMask roadBarrier = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            roadBarrier.set(30, y, true);
        }

        // Masque vert grossier situé principalement à gauche (x de 10 à 40)
        // avec noyau à x=20 et débordement à x=35
        BinaryMask roughMask = new BinaryMask(w, h);
        for (int y = 15; y <= 45; y++) {
            for (int x = 10; x <= 40; x++) {
                roughMask.set(x, y, true);
            }
        }

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(25)
                .seedErosionRadius(4)
                .build();

        RoadSnappingEngine engine = new RoadSnappingEngine();
        BinaryMask snapped = engine.snap(roughMask, roadBarrier, config);

        // La partie gauche (noyau) est préservée
        assertTrue(snapped.get(20, 30));
        // Le débordement à droite (séparé par la route x=30) est éliminé
        assertFalse(snapped.get(35, 30), "La partie isolée derrière la route doit être rétractée");
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=RoadSnappingEngineTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol RoadSnappingEngine`).

- [ ] **Step 3: Implémenter RoadSnappingEngine**

Créer `src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java` :

```java
package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Moteur de recalage : effectue une érosion de graine sur le masque vert puis une propagation
 * géodésique BFS bloquée par les barrières routières et bornée par snapDistance.
 */
public class RoadSnappingEngine {

    public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadBarrier, SnappingConfig config) {
        if (roughGreenMask == null || roadBarrier == null || config == null) {
            throw new IllegalArgumentException("Les arguments du snapping engine ne peuvent pas être null.");
        }

        int w = roughGreenMask.getWidth();
        int h = roughGreenMask.getHeight();

        // 1. Extraction du noyau certain (seed) par érosion adaptative
        BinaryMask seed = extractReliableSeed(roughGreenMask, config.seedErosionRadius());
        if (seed.countActivePixels() == 0) {
            // Si le masque est trop petit, on utilise le masque grossier lui-même privé des routes
            seed = roughGreenMask.copy();
        }

        // 2. Détermination de la zone maximale autorisée (bounding zone)
        // La zone autorisée est l'enveloppe dilatée du masque initial par snapDistance
        BinaryMask allowedZone = MorphologyOps.dilate(roughGreenMask, config.snapDistance());

        // 3. Propagation BFS contrainte
        BinaryMask result = new BinaryMask(w, h);
        boolean[] visited = new boolean[w * h];
        Queue<Integer> queue = new ArrayDeque<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                // Le pixel de graine doit être actif et ne pas être lui-même sur une barrière routière
                if (seed.get(x, y) && !roadBarrier.get(x, y)) {
                    visited[idx] = true;
                    result.set(x, y, true);
                    queue.offer(idx);
                }
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int cx = current % w;
            int cy = current / w;

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];

                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    continue;
                }

                int nIdx = ny * w + nx;
                if (visited[nIdx]) {
                    continue;
                }
                visited[nIdx] = true;

                // Si c'est une barrière routière, on stoppe la propagation dans cette direction
                if (roadBarrier.get(nx, ny)) {
                    continue;
                }

                // Si le pixel est hors de la distance maximale permise, on stoppe
                if (!allowedZone.get(nx, ny)) {
                    continue;
                }

                result.set(nx, ny, true);
                queue.offer(nIdx);
            }
        }

        return result;
    }

    private BinaryMask extractReliableSeed(BinaryMask mask, int radius) {
        int r = radius;
        while (r > 0) {
            BinaryMask eroded = MorphologyOps.erode(mask, r);
            if (eroded.countActivePixels() > 0) {
                return eroded;
            }
            r /= 2;
        }
        return mask.copy();
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=RoadSnappingEngineTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngine.java src/test/java/com/sam102022/photoshop/core/segmentation/RoadSnappingEngineTest.java
git commit -m "feat(core): implement RoadSnappingEngine with geodesic BFS propagation"
```

---

### Task 8: Entrées / Sorties et Rendu Alpha (ImageLoader & ImageExporter)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/io/ImageLoader.java`
- Create: `src/main/java/com/sam102022/photoshop/io/ImageExporter.java`
- Test: `src/test/java/com/sam102022/photoshop/io/ImageIoTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour ImageLoader et ImageExporter**

Créer `src/test/java/com/sam102022/photoshop/io/ImageIoTest.java` :

```java
package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageIoTest {

    @Test
    @DisplayName("Vérification de concordance des dimensions des images")
    void testValidateDimensions() {
        BufferedImage img1 = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        BufferedImage img3 = new BufferedImage(150, 200, BufferedImage.TYPE_INT_RGB);

        assertDoesNotThrow(() -> ImageLoader.validateDimensions(img1, img2));
        assertThrows(IllegalArgumentException.class, () -> ImageLoader.validateDimensions(img1, img3));
    }

    @Test
    @DisplayName("Génération de l'image détourée avec canal alpha transparent et export PNG")
    void testCreateClippedImageAndExport(@TempDir Path tempDir) throws IOException {
        BufferedImage map = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = map.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        BinaryMask mask = new BinaryMask(10, 10);
        mask.set(5, 5, true);

        BufferedImage clipped = ImageExporter.createClippedImage(map, mask, 0);
        assertEquals(BufferedImage.TYPE_INT_ARGB, clipped.getType());

        // Le pixel actif doit avoir un alpha plein (255)
        int insideAlpha = (clipped.getRGB(5, 5) >> 24) & 0xFF;
        assertEquals(255, insideAlpha);

        // Le pixel inactif doit être complètement transparent (alpha = 0)
        int outsideAlpha = (clipped.getRGB(0, 0) >> 24) & 0xFF;
        assertEquals(0, outsideAlpha);

        Path outFile = tempDir.resolve("clipped.png");
        ImageExporter.savePng(clipped, outFile);
        assertTrue(outFile.toFile().exists());
        assertTrue(outFile.toFile().length() > 0);
    }

    @Test
    @DisplayName("Génération du PNG de visualisation du masque binaire")
    void testCreateMaskImageAndExport(@TempDir Path tempDir) throws IOException {
        BinaryMask mask = new BinaryMask(10, 10);
        mask.set(2, 2, true);

        BufferedImage maskImage = ImageExporter.createMaskImage(mask);
        int whitePixel = maskImage.getRGB(2, 2) & 0x00FFFFFF;
        int blackPixel = maskImage.getRGB(0, 0) & 0x00FFFFFF;

        assertEquals(0xFFFFFF, whitePixel, "Pixel actif blanc");
        assertEquals(0x000000, blackPixel, "Pixel inactif noir");

        Path maskFile = tempDir.resolve("mask.png");
        ImageExporter.savePng(maskImage, maskFile);
        assertTrue(maskFile.toFile().exists());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=ImageIoTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol ImageLoader / ImageExporter`).

- [ ] **Step 3: Implémenter ImageLoader**

Créer `src/main/java/com/sam102022/photoshop/io/ImageLoader.java` :

```java
package com.sam102022.photoshop.io;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Chargement et validation des dimensions des images cartographiques et calques.
 */
public final class ImageLoader {

    private ImageLoader() {
    }

    public static BufferedImage load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Le chemin de fichier ne peut pas être null.");
        }
        File file = path.toFile();
        if (!file.exists()) {
            throw new IOException("Fichier introuvable : " + path);
        }
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Format d'image non supporté ou fichier corrompu : " + path);
        }
        return image;
    }

    public static void validateDimensions(BufferedImage img1, BufferedImage img2) {
        if (img1 == null || img2 == null) {
            throw new IllegalArgumentException("Les images à comparer ne peuvent pas être null.");
        }
        if (img1.getWidth() != img2.getWidth() || img1.getHeight() != img2.getHeight()) {
            throw new IllegalArgumentException(String.format(
                    "Dimensions discordantes : carte (%dx%d) vs masque (%dx%d)",
                    img1.getWidth(), img1.getHeight(), img2.getWidth(), img2.getHeight()
            ));
        }
    }
}
```

- [ ] **Step 4: Implémenter ImageExporter**

Créer `src/main/java/com/sam102022/photoshop/io/ImageExporter.java` :

```java
package com.sam102022.photoshop.io;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.segmentation.MorphologyOps;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Export des images détourées en PNG transparent (ARGB) avec lissage progressif et export des masques.
 */
public final class ImageExporter {

    private ImageExporter() {
    }

    public static BufferedImage createClippedImage(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
        int w = mapImage.getWidth();
        int h = mapImage.getHeight();
        BufferedImage clipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        if (smoothRadius <= 0) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (mask.get(x, y)) {
                        int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                        clipped.setRGB(x, y, 0xFF000000 | rgb);
                    } else {
                        clipped.setRGB(x, y, 0x00000000);
                    }
                }
            }
            return clipped;
        }

        // Lissage de contour : détection des bords intérieurs par érosion
        BinaryMask coreMask = MorphologyOps.erode(mask, smoothRadius);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!mask.get(x, y)) {
                    clipped.setRGB(x, y, 0x00000000);
                } else if (coreMask.get(x, y)) {
                    int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                    clipped.setRGB(x, y, 0xFF000000 | rgb);
                } else {
                    // Pixel situé sur la bordure : calcul d'un alpha progressif
                    int rgb = mapImage.getRGB(x, y) & 0x00FFFFFF;
                    int alpha = 160; // Atténuation anti-escalier sur le pourtour
                    clipped.setRGB(x, y, (alpha << 24) | rgb);
                }
            }
        }

        return clipped;
    }

    public static BufferedImage createMaskImage(BinaryMask mask) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                image.setRGB(x, y, mask.get(x, y) ? 0xFFFFFF : 0x000000);
            }
        }
        return image;
    }

    public static void savePng(BufferedImage image, Path outputPath) throws IOException {
        File file = outputPath.toFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        boolean written = ImageIO.write(image, "PNG", file);
        if (!written) {
            throw new IOException("Impossible d'écrire l'image au format PNG vers : " + outputPath);
        }
    }
}
```

- [ ] **Step 5: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=ImageIoTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/io/ImageLoader.java src/main/java/com/sam102022/photoshop/io/ImageExporter.java src/test/java/com/sam102022/photoshop/io/ImageIoTest.java
git commit -m "feat(io): implement ImageLoader and ImageExporter with ARGB transparency"
```

---

### Task 9: Module CLI (CliRunner) et Point d'Entrée Principal (Main)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/cli/CliRunner.java`
- Modify: `src/main/java/com/sam102022/photoshop/Main.java`
- Delete: `src/main/java/com/sam102022/Main.java`
- Test: `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour CliRunner**

Créer `src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java` :

```java
package com.sam102022.photoshop.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliRunnerTest {

    @Test
    @DisplayName("Affichage de l'aide avec --help ou -h")
    void testHelpOption() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            int exitCode = CliRunner.run(new String[]{"--help"});
            assertEquals(0, exitCode);
            assertTrue(outContent.toString().contains("Usage:"));
            assertTrue(outContent.toString().contains("--map"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Échec si arguments obligatoires manquants")
    void testMissingArguments() {
        int exitCode = CliRunner.run(new String[]{});
        assertEquals(1, exitCode);
    }

    @Test
    @DisplayName("Exécution complète d'un scénario CLI nominal")
    void testEndToEndCliRun(@TempDir Path tempDir) throws Exception {
        // Créer de fausses images valides
        Path mapFile = tempDir.resolve("map.png");
        Path maskFile = tempDir.resolve("mask.png");
        Path outFile = tempDir.resolve("out.png");
        Path maskOutFile = tempDir.resolve("mask_out.png");

        BufferedImage map = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        BufferedImage mask = new BufferedImage(30, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = mask.createGraphics();
        g.setColor(new Color(0, 220, 0));
        g.fillRect(5, 5, 20, 20);
        g.dispose();

        ImageIO.write(map, "PNG", mapFile.toFile());
        ImageIO.write(mask, "PNG", maskFile.toFile());

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.toString(),
                "--mask", maskFile.toString(),
                "--output", outFile.toString(),
                "--mask-out", maskOutFile.toString(),
                "--snap-distance", "15"
        });

        assertEquals(0, exitCode);
        assertTrue(outFile.toFile().exists());
        assertTrue(maskOutFile.toFile().exists());
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=CliRunnerTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol CliRunner`).

- [ ] **Step 3: Implémenter CliRunner**

Créer `src/main/java/com/sam102022/photoshop/cli/CliRunner.java` :

```java
package com.sam102022.photoshop.cli;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parseur d'arguments et orchestrateur d'exécution en mode ligne de commande.
 */
public final class CliRunner {

    private CliRunner() {
    }

    public static int run(String[] args) {
        if (args.length == 0 || hasOption(args, "--help", "-h")) {
            printHelp();
            return args.length == 0 ? 1 : 0;
        }

        String mapPathStr = getOptionValue(args, "--map");
        String maskPathStr = getOptionValue(args, "--mask");
        String outputPathStr = getOptionValue(args, "--output", "clipped_output.png");
        String maskOutputPathStr = getOptionValue(args, "--mask-out", "mask_output.png");

        if (mapPathStr == null || maskPathStr == null) {
            System.err.println("Erreur : Les options --map et --mask sont obligatoires.");
            printHelp();
            return 1;
        }

        int snapDistance = getIntOption(args, "--snap-distance", 40);
        float roadSensitivity = getFloatOption(args, "--road-sensitivity", 1.0f);
        int smoothRadius = getIntOption(args, "--smooth", 1);

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(snapDistance)
                .roadSensitivity(roadSensitivity)
                .smoothRadius(smoothRadius)
                .build();

        try {
            System.out.println("-> Chargement des images...");
            BufferedImage mapImg = ImageLoader.load(Paths.get(mapPathStr));
            BufferedImage maskImg = ImageLoader.load(Paths.get(maskPathStr));

            ImageLoader.validateDimensions(mapImg, maskImg);

            System.out.println("-> Détection du masque vert grossier...");
            GreenMaskExtractor greenExtractor = new GreenMaskExtractor();
            BinaryMask roughMask = greenExtractor.extract(maskImg);
            int activePixels = roughMask.countActivePixels();
            System.out.printf("   %d pixels verts initiaux détectés.\n", activePixels);

            if (activePixels == 0) {
                System.err.println("Attention : Aucun pixel vert trouvé dans le masque.");
                return 1;
            }

            System.out.println("-> Détection des axes routiers Google Maps...");
            RoadDetector roadDetector = new RoadDetector();
            BinaryMask roadBarrier = roadDetector.detectRoads(mapImg, config);
            System.out.printf("   %d pixels de barrière routière identifiés.\n", roadBarrier.countActivePixels());

            System.out.println("-> Recalage géodésique sur les routes...");
            RoadSnappingEngine engine = new RoadSnappingEngine();
            BinaryMask snappedMask = engine.snap(roughMask, roadBarrier, config);
            System.out.printf("   %d pixels conservés après recalage.\n", snappedMask.countActivePixels());

            System.out.println("-> Création et sauvegarde des exports PNG...");
            BufferedImage clippedImage = ImageExporter.createClippedImage(mapImg, snappedMask, config.smoothRadius());
            BufferedImage maskResultImg = ImageExporter.createMaskImage(snappedMask);

            Path outPath = Paths.get(outputPathStr);
            Path maskOutPath = Paths.get(maskOutputPathStr);
            ImageExporter.savePng(clippedImage, outPath);
            ImageExporter.savePng(maskResultImg, maskOutPath);

            System.out.printf("Succès ! Image détourée : %s | Masque : %s\n", outPath.toAbsolutePath(), maskOutPath.toAbsolutePath());
            return 0;

        } catch (Exception e) {
            System.err.println("Erreur lors du traitement : " + e.getMessage());
            return 1;
        }
    }

    private static boolean hasOption(String[] args, String... options) {
        for (String arg : args) {
            for (String opt : options) {
                if (opt.equalsIgnoreCase(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getOptionValue(String[] args, String option) {
        return getOptionValue(args, option, null);
    }

    private static String getOptionValue(String[] args, String option, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (option.equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static int getIntOption(String[] args, String option, int defaultValue) {
        String val = getOptionValue(args, option);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            System.err.printf("Avertissement : valeur entière invalide pour %s : %s. Valeur par défaut utilisée (%d).\n", option, val, defaultValue);
            return defaultValue;
        }
    }

    private static float getFloatOption(String[] args, String option, float defaultValue) {
        String val = getOptionValue(args, option);
        if (val == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            System.err.printf("Avertissement : valeur flottante invalide pour %s : %s. Valeur par défaut utilisée (%.1f).\n", option, val, defaultValue);
            return defaultValue;
        }
    }

    public static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -jar photoshop.jar --map <path> --mask <path> [options]");
        System.out.println();
        System.out.println("Options obligatoires :");
        System.out.println("  --map <path>              Chemin vers la capture Google Maps");
        System.out.println("  --mask <path>             Chemin vers le calque vert grossier");
        System.out.println();
        System.out.println("Options facultatives :");
        System.out.println("  --output <path>           Fichier PNG détouré final (défaut: clipped_output.png)");
        System.out.println("  --mask-out <path>         Fichier PNG du masque affiné (défaut: mask_output.png)");
        System.out.println("  --snap-distance <int>     Portée maximale d'ajustement en pixels (défaut: 40)");
        System.out.println("  --road-sensitivity <flt>  Sensibilité détection routes 0.5-2.0 (défaut: 1.0)");
        System.out.println("  --smooth <int>            Rayon de lissage des bords (défaut: 1)");
        System.out.println("  --gui                     Lancer l'interface graphique interactive Swing");
        System.out.println("  --help, -h                Afficher cette aide");
    }
}
```

- [ ] **Step 4: Implémenter la classe principale Main**

Supprimer l'ancienne classe modèle `src/main/java/com/sam102022/Main.java` et créer `src/main/java/com/sam102022/photoshop/Main.java` :

```java
package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.CliRunner;
import com.sam102022.photoshop.gui.MainWindow;

import javax.swing.SwingUtilities;

/**
 * Point d'entrée de l'application : aiguillage automatique entre le mode CLI et le mode GUI.
 */
public class Main {

    public static void main(String[] args) {
        boolean launchGui = args.length == 0 || hasGuiOption(args);

        if (launchGui) {
            SwingUtilities.invokeLater(() -> {
                MainWindow window = new MainWindow();
                window.setVisible(true);
            });
        } else {
            int exitCode = CliRunner.run(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        }
    }

    private static boolean hasGuiOption(String[] args) {
        for (String arg : args) {
            if ("--gui".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 5: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=CliRunnerTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 6: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/cli/CliRunner.java src/main/java/com/sam102022/photoshop/Main.java src/test/java/com/sam102022/photoshop/cli/CliRunnerTest.java
git rm src/main/java/com/sam102022/Main.java
git commit -m "feat(cli): implement CliRunner and Main entry point"
```

---

### Task 10: Interface Graphique Interactive Swing (MainWindow)

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/gui/MainWindow.java`
- Test: `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java`

- [ ] **Step 1: Écrire les tests unitaires pour MainWindow (compatible headless)**

Créer `src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java` :

```java
package com.sam102022.photoshop.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class MainWindowTest {

    @Test
    @DisplayName("Initialisation des composants graphiques Swing sans exception")
    void testWindowInitialization() {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Environnement headless détecté, test visuel restreint.");
            return;
        }

        MainWindow window = new MainWindow();
        assertNotNull(window);
        assertEquals("Photoshop - Détourage de Carte Google Maps", window.getTitle());
        assertTrue(window.getWidth() >= 800);
        assertTrue(window.getHeight() >= 600);
        window.dispose();
    }
}
```

- [ ] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Exécuter la commande :
```bash
mvn test -Dtest=MainWindowTest
```
Résultat attendu : Échec de compilation (`Cannot find symbol MainWindow`).

- [ ] **Step 3: Implémenter MainWindow**

Créer `src/main/java/com/sam102022/photoshop/gui/MainWindow.java` :

```java
package com.sam102022.photoshop.gui;

import com.sam102022.photoshop.core.detection.GreenMaskExtractor;
import com.sam102022.photoshop.core.detection.RoadDetector;
import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.core.model.SnappingConfig;
import com.sam102022.photoshop.core.segmentation.RoadSnappingEngine;
import com.sam102022.photoshop.io.ImageExporter;
import com.sam102022.photoshop.io.ImageLoader;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * Interface graphique Swing pour la visualisation et le détourage interactif.
 */
public class MainWindow extends JFrame {

    private BufferedImage mapImage;
    private BufferedImage maskImage;
    private BufferedImage clippedImage;
    private BinaryMask resultMask;

    private final JLabel mapLabel = new JLabel("Carte : Aucune sélectionnée");
    private final JLabel maskLabel = new JLabel("Calque : Aucun sélectionné");
    private final JLabel statusLabel = new JLabel("Prêt");

    private final ImagePanel previewOriginalPanel = new ImagePanel("Aperçu Original (Carte + Masque)");
    private final ImagePanel previewResultPanel = new ImagePanel("Résultat Détouré (Transparence)", true);

    private final JSlider snapDistanceSlider = new JSlider(5, 100, 40);
    private final JSlider roadSensitivitySlider = new JSlider(5, 20, 10);
    private final JSlider smoothSlider = new JSlider(0, 5, 1);

    private final JButton clipButton = new JButton("Détourer");
    private final JButton exportButton = new JButton("Exporter...");

    public MainWindow() {
        setTitle("Photoshop - Détourage de Carte Google Maps");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);

        initUi();
    }

    private void initUi() {
        setLayout(new BorderLayout());

        // 1. Panneau Supérieur : Sélecteurs de Fichiers
        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        topPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMapBtn = new JButton("Parcourir Carte...");
        loadMapBtn.addActionListener(e -> chooseMapFile());
        mapRow.add(loadMapBtn);
        mapRow.add(mapLabel);

        JPanel maskRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadMaskBtn = new JButton("Parcourir Calque...");
        loadMaskBtn.addActionListener(e -> chooseMaskFile());
        maskRow.add(loadMaskBtn);
        maskRow.add(maskLabel);

        topPanel.add(mapRow);
        topPanel.add(maskRow);
        add(topPanel, BorderLayout.NORTH);

        // 2. Panneau Central : Affichage Avant / Après en Split
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, previewOriginalPanel, previewResultPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        // 3. Panneau Inférieur : Commandes, Sliders et Barre d'État
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));

        snapDistanceSlider.setPaintTicks(true);
        snapDistanceSlider.setMajorTickSpacing(25);
        controlsPanel.add(createSliderBox("Distance Snap (px):", snapDistanceSlider));

        roadSensitivitySlider.setPaintTicks(true);
        roadSensitivitySlider.setMajorTickSpacing(5);
        controlsPanel.add(createSliderBox("Sensibilité Routes (x0.1):", roadSensitivitySlider));

        smoothSlider.setPaintTicks(true);
        smoothSlider.setMajorTickSpacing(1);
        controlsPanel.add(createSliderBox("Lissage Bords (px):", smoothSlider));

        clipButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        clipButton.addActionListener(e -> runClipping());
        controlsPanel.add(clipButton);

        exportButton.setEnabled(false);
        exportButton.addActionListener(e -> exportResult());
        controlsPanel.add(exportButton);

        bottomPanel.add(controlsPanel, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.add(statusLabel);
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createSliderBox(String title, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        return panel;
    }

    private void chooseMapFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                mapImage = ImageLoader.load(file.toPath());
                mapLabel.setText(String.format("Carte : %s (%dx%d)", file.getName(), mapImage.getWidth(), mapImage.getHeight()));
                updateOriginalPreview();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void chooseMaskFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                maskImage = ImageLoader.load(file.toPath());
                maskLabel.setText(String.format("Calque : %s (%dx%d)", file.getName(), maskImage.getWidth(), maskImage.getHeight()));
                updateOriginalPreview();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur de chargement : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateOriginalPreview() {
        if (mapImage == null) return;
        if (maskImage == null) {
            previewOriginalPanel.setImage(mapImage);
            return;
        }

        BufferedImage composite = new BufferedImage(mapImage.getWidth(), mapImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = composite.createGraphics();
        g.drawImage(mapImage, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.drawImage(maskImage, 0, 0, null);
        g.dispose();

        previewOriginalPanel.setImage(composite);
    }

    private void runClipping() {
        if (mapImage == null || maskImage == null) {
            JOptionPane.showMessageDialog(this, "Veuillez charger une carte ET un calque.", "Attention", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            ImageLoader.validateDimensions(mapImage, maskImage);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Erreur de dimensions", JOptionPane.ERROR_MESSAGE);
            return;
        }

        clipButton.setEnabled(false);
        statusLabel.setText("Détourage en cours...");

        int snapDist = snapDistanceSlider.getValue();
        float sensitivity = roadSensitivitySlider.getValue() / 10.0f;
        int smooth = smoothSlider.getValue();

        SnappingConfig config = SnappingConfig.builder()
                .snapDistance(snapDist)
                .roadSensitivity(sensitivity)
                .smoothRadius(smooth)
                .build();

        long startTime = System.currentTimeMillis();

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override
            protected BufferedImage doInBackground() {
                GreenMaskExtractor greenExtractor = new GreenMaskExtractor();
                BinaryMask roughMask = greenExtractor.extract(maskImage);

                RoadDetector roadDetector = new RoadDetector();
                BinaryMask roadBarrier = roadDetector.detectRoads(mapImage, config);

                RoadSnappingEngine engine = new RoadSnappingEngine();
                resultMask = engine.snap(roughMask, roadBarrier, config);

                return ImageExporter.createClippedImage(mapImage, resultMask, config.smoothRadius());
            }

            @Override
            protected void done() {
                try {
                    clippedImage = get();
                    previewResultPanel.setImage(clippedImage);
                    exportButton.setEnabled(true);
                    long elapsed = System.currentTimeMillis() - startTime;
                    statusLabel.setText(String.format("Détourage terminé en %d ms (Pixels détourés : %d)", elapsed, resultMask.countActivePixels()));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainWindow.this, "Erreur de détourage : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Erreur");
                } finally {
                    clipButton.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void exportResult() {
        if (clippedImage == null || resultMask == null) return;

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("clipped_output.png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path outPath = chooser.getSelectedFile().toPath();
                ImageExporter.savePng(clippedImage, outPath);

                // Sauvegarder également le masque associé
                Path maskPath = outPath.resolveSibling("mask_" + outPath.getFileName());
                ImageExporter.savePng(ImageExporter.createMaskImage(resultMask), maskPath);

                JOptionPane.showMessageDialog(this, "Images exportées avec succès !", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de l'export : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class ImagePanel extends JPanel {
        private final String title;
        private final boolean showCheckerboard;
        private BufferedImage image;

        public ImagePanel(String title) {
            this(title, false);
        }

        public ImagePanel(String title, boolean showCheckerboard) {
            this.title = title;
            this.showCheckerboard = showCheckerboard;
            setBorder(BorderFactory.createTitledBorder(title));
        }

        public void setImage(BufferedImage image) {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            if (showCheckerboard) {
                drawCheckerboard(g, w, h);
            }

            if (image != null) {
                // Centrage et mise à l'échelle conservant le ratio
                double scale = Math.min((double) (w - 20) / image.getWidth(), (double) (h - 40) / image.getHeight());
                int drawW = (int) (image.getWidth() * scale);
                int drawH = (int) (image.getHeight() * scale);
                int drawX = (w - drawW) / 2;
                int drawY = 20 + (h - 40 - drawH) / 2;

                g.drawImage(image, drawX, drawY, drawW, drawH, null);
            } else {
                g.setColor(Color.GRAY);
                g.drawString("Aucune image", w / 2 - 40, h / 2);
            }
        }

        private void drawCheckerboard(Graphics g, int w, int h) {
            int cellSize = 12;
            for (int y = 0; y < h; y += cellSize) {
                for (int x = 0; x < w; x += cellSize) {
                    g.setColor(((x / cellSize) + (y / cellSize)) % 2 == 0 ? new Color(220, 220, 220) : Color.WHITE);
                    g.fillRect(x, y, cellSize, cellSize);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Exécuter le test pour vérifier qu'il passe**

Exécuter la commande :
```bash
mvn test -Dtest=MainWindowTest
```
Résultat attendu : `BUILD SUCCESS`, `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/gui/MainWindow.java src/test/java/com/sam102022/photoshop/gui/MainWindowTest.java
git commit -m "feat(gui): implement interactive Swing MainWindow with live preview and controls"
```

---

### Task 11: Validation d'Intégration Bout-en-Bout avec les Échantillons Réels

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java`

- [ ] **Step 1: Écrire le test d'intégration complet avec les images réelles de `src/main/resources/sample/`**

Créer `src/test/java/com/sam102022/photoshop/IntegrationCliTest.java` :

```java
package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.CliRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationCliTest {

    @Test
    @DisplayName("Exécution complète bout-en-bout avec les images réelles de capture et de masque")
    void testEndToEndWithSampleImages(@TempDir Path tempDir) throws Exception {
        Path sampleDir = Paths.get("src", "main", "resources", "sample");
        File mapFile = sampleDir.resolve("a découper.jpg").toFile();
        File maskFile = sampleDir.resolve("mask01 - Copie.jpg").toFile();

        assertTrue(mapFile.exists(), "L'image carte réelle d'exemple doit exister");
        assertTrue(maskFile.exists(), "L'image masque réelle d'exemple doit exister");

        Path outClipped = tempDir.resolve("clipped_real.png");
        Path outMask = tempDir.resolve("mask_real.png");

        int exitCode = CliRunner.run(new String[]{
                "--map", mapFile.getAbsolutePath(),
                "--mask", maskFile.getAbsolutePath(),
                "--output", outClipped.toString(),
                "--mask-out", outMask.toString(),
                "--snap-distance", "40",
                "--road-sensitivity", "1.0",
                "--smooth", "1"
        });

        assertEquals(0, exitCode, "Le traitement CLI doit se terminer avec un code de succès (0)");
        assertTrue(outClipped.toFile().exists(), "Le fichier détouré doit exister");
        assertTrue(outMask.toFile().exists(), "Le fichier de masque affiné doit exister");

        BufferedImage clippedImg = ImageIO.read(outClipped.toFile());
        BufferedImage maskImg = ImageIO.read(outMask.toFile());

        assertNotNull(clippedImg);
        assertNotNull(maskImg);
        assertEquals(clippedImg.getWidth(), maskImg.getWidth());
        assertEquals(clippedImg.getHeight(), maskImg.getHeight());

        // Vérifier la présence de pixels transparents et de pixels opaques
        boolean hasTransparent = false;
        boolean hasOpaque = false;
        for (int y = 0; y < clippedImg.getHeight(); y += 10) {
            for (int x = 0; x < clippedImg.getWidth(); x += 10) {
                int alpha = (clippedImg.getRGB(x, y) >> 24) & 0xFF;
                if (alpha == 0) hasTransparent = true;
                if (alpha == 255) hasOpaque = true;
            }
        }

        assertTrue(hasTransparent, "L'image détourée doit contenir des zones transparentes");
        assertTrue(hasOpaque, "L'image détourée doit contenir des zones opaques");
    }
}
```

- [ ] **Step 2: Exécuter tous les tests du projet**

Exécuter la commande :
```bash
mvn clean test
```
Résultat attendu : `BUILD SUCCESS`, tous les tests passent avec 0 échecs et 0 erreurs.

- [ ] **Step 3: Commiter**

```bash
git add src/test/java/com/sam102022/photoshop/IntegrationCliTest.java
git commit -m "test: add full end-to-end integration test with sample map images"
```
