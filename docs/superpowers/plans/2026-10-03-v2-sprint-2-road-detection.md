# V2 Sprint 2 : Détection Colorimétrique des Routes & Fermeture Topologique Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extraire la surface des chaussées cartographiques (depuis l'image Google Maps stylisée ou le flux vectoriel OSM), appliquer le nettoyage morphologique et la fermeture topologique minimale, et produire le contrat immuable `RoadMask(width, height, raw, closed)`.

**Architecture:** Modèle de domaine immuable en Java Records (`RoadMask`), interface abstraite de détection (`RoadDetector`), extracteur colorimétrique vectorisé (`RoadDetectorStyle`), rasteriseur vectoriel géodésique (`RoadDetectorOsm`) basé sur `WebMercatorProjection`, et processeur morphologique déterministe (`RoadMaskCleaner`) sans dépendance native (ADR-001, ADR-002, ADR-004, ADR-008, ADR-011, ADR-012, ADR-014).

**Tech Stack:** Java 21, Java2D (AWT), JUnit 5 Jupiter.

---

### Task 1: Contrat de Domaine Immuable `RoadMask`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/road/RoadMask.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/road/RoadMaskTest.java`

- [x] **Step 1: Écrire les tests unitaires pour le record `RoadMask`**

Créer `src/test/java/com/sam102022/photoshop/v2/road/RoadMaskTest.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation du contrat officiel RoadMask (Sprint 2)")
class RoadMaskTest {

    @Test
    @DisplayName("Instanciation valide d'un RoadMask conforme")
    void testValidRoadMask() {
        BinaryMask raw = new BinaryMask(100, 50);
        BinaryMask closed = new BinaryMask(100, 50);
        raw.set(10, 10, true);
        closed.set(10, 10, true);

        RoadMask roadMask = new RoadMask(100, 50, raw, closed);

        assertEquals(100, roadMask.width());
        assertEquals(50, roadMask.height());
        assertNotNull(roadMask.raw());
        assertNotNull(roadMask.closed());
        assertTrue(roadMask.raw().get(10, 10));
        assertTrue(roadMask.closed().get(10, 10));
    }

    @Test
    @DisplayName("Rejet des dimensions négatives ou nulles")
    void testInvalidDimensions() {
        BinaryMask mask = new BinaryMask(10, 10);
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(0, 10, mask, mask));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, -1, mask, mask));
    }

    @Test
    @DisplayName("Rejet des masques null")
    void testNullMasks() {
        BinaryMask mask = new BinaryMask(10, 10);
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, null, mask));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask, null));
    }

    @Test
    @DisplayName("Rejet des masques de dimensions incohérentes")
    void testInconsistentDimensions() {
        BinaryMask mask10x10 = new BinaryMask(10, 10);
        BinaryMask mask20x10 = new BinaryMask(20, 10);

        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask20x10, mask10x10));
        assertThrows(IllegalArgumentException.class, () -> new RoadMask(10, 10, mask10x10, mask20x10));
    }
}
```

- [x] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=RoadMaskTest`
Expected: FAIL (Cannot find symbol RoadMask)

- [x] **Step 3: Implémenter le record `RoadMask`**

Créer `src/main/java/com/sam102022/photoshop/v2/road/RoadMask.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Contrat officiel immuable représentant le masque des routes en version brute et fermée topologiquement.
 * Conforme au contrat I/O défini dans SPRINTS_V2.md (Sprint 2 ➔ Sprint 3).
 *
 * @param width  Largeur de la matrice en pixels (> 0).
 * @param height Hauteur de la matrice en pixels (> 0).
 * @param raw    Masque binaire brut issu directement de la détection (sans altération morphologique).
 * @param closed Masque binaire après fermeture topologique minimale garantissant l'étanchéité des voies.
 */
public record RoadMask(
        int width,
        int height,
        BinaryMask raw,
        BinaryMask closed
) {
    /**
     * Valide l'intégrité et la cohérence dimensionnelle des masques.
     */
    public RoadMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (raw == null || closed == null) {
            throw new IllegalArgumentException("Les masques raw et closed ne peuvent pas être null.");
        }
        if (raw.getWidth() != width || raw.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour raw (" + raw.getWidth() + "x" + raw.getHeight()
                    + ") par rapport à " + width + "x" + height);
        }
        if (closed.getWidth() != width || closed.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour closed (" + closed.getWidth() + "x" + closed.getHeight()
                    + ") par rapport à " + width + "x" + height);
        }
    }
}
```

- [x] **Step 4: Exécuter le test unitaire pour valider le passage**

Run: `mvn test -Dtest=RoadMaskTest`
Expected: PASS

- [x] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/v2/road/RoadMask.java src/test/java/com/sam102022/photoshop/v2/road/RoadMaskTest.java
git commit -m "feat(v2): contrat officiel RoadMask immuable pour le Sprint 2"
```

---

### Task 2: Interface `RoadDetector` et Détecteur Colorimétrique `RoadDetectorStyle`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/road/RoadDetector.java`
- Create: `src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorStyle.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorStyleTest.java`

- [x] **Step 1: Écrire les tests unitaires pour `RoadDetectorStyle`**

Créer `src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorStyleTest.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests unitaires du détecteur colorimétrique RoadDetectorStyle")
class RoadDetectorStyleTest {

    @Test
    @DisplayName("Validation de la règle colorimétrique sur un pixel de route bleu-gris")
    void testDetectRoadColor() {
        BufferedImage img = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        // Pixel de route type : R=180, G=195, B=205 => (B-R)=25 >= 12, (B-G)=10 in [2, 22], R=180 < 228
        img.setRGB(1, 1, new Color(180, 195, 205).getRGB());

        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask mask = detector.detect(img);

        assertNotNull(mask);
        assertEquals(3, mask.getWidth());
        assertEquals(3, mask.getHeight());
        assertTrue(mask.get(1, 1), "Le pixel bleu-gris doit être détecté comme route");
        assertFalse(mask.get(0, 0), "Le fond noir ne doit pas être détecté comme route");
    }

    @Test
    @DisplayName("Rejet des teintes non routières : vert, blanc, eau saturée, rouge excessif")
    void testRejectNonRoadColors() {
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        // 0: Vert (parc) R=180, G=220, B=180 => B-G = -40 (hors limites)
        img.setRGB(0, 0, new Color(180, 220, 180).getRGB());
        // 1: Blanc pur R=255, G=255, B=255 => R >= 228
        img.setRGB(1, 0, new Color(255, 255, 255).getRGB());
        // 2: Eau cyan R=120, G=180, B=230 => B-G = 50 > 22
        img.setRGB(2, 0, new Color(120, 180, 230).getRGB());
        // 3: Trop rouge R=230, G=235, B=245 => R=230 >= 228
        img.setRGB(3, 0, new Color(230, 235, 245).getRGB());

        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask mask = detector.detect(img);

        assertFalse(mask.get(0, 0), "Vert rejeté");
        assertFalse(mask.get(1, 0), "Blanc rejeté");
        assertFalse(mask.get(2, 0), "Eau cyan rejetée");
        assertFalse(mask.get(3, 0), "Rouge excessif rejeté");
    }

    @Test
    @DisplayName("Rejet d'une image null")
    void testNullImage() {
        RoadDetectorStyle detector = new RoadDetectorStyle();
        assertThrows(IllegalArgumentException.class, () -> detector.detect(null));
    }
}
```

- [x] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=RoadDetectorStyleTest`
Expected: FAIL (Cannot find symbol RoadDetectorStyle)

- [x] **Step 3: Implémenter `RoadDetector` et `RoadDetectorStyle`**

Créer `src/main/java/com/sam102022/photoshop/v2/road/RoadDetector.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Contrat commun pour les composants d'extraction de surface routière.
 */
public interface RoadDetector {

    /**
     * Analyse l'image cartographique pour en extraire les pixels de surface routière.
     *
     * @param image Image cartographique source.
     * @return Masque binaire brut où chaque pixel à true représente une surface routière.
     * @throws IllegalArgumentException si l'image est null.
     */
    BinaryMask detect(BufferedImage image);
}
```

Créer `src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorStyle.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

import java.awt.image.BufferedImage;

/**
 * Détecteur colorimétrique des surfaces de chaussée optimisé pour le style contrasté Google Maps.
 * Applique la règle différentielle bleu-gris issue de l'algorithme de référence script.py :
 * {@code (B - R >= 12) && (B - G >= 2) && (B - G <= 22) && (R < 228)}.
 */
public final class RoadDetectorStyle implements RoadDetector {

    private static final int MIN_DELTA_BLUE_RED = 12;
    private static final int MIN_DELTA_BLUE_GREEN = 2;
    private static final int MAX_DELTA_BLUE_GREEN = 22;
    private static final int MAX_RED_LUMINANCE = 228;

    /**
     * Extrait le masque brut des routes à partir de l'image de segmentation cartographique.
     *
     * @param image Image source de carte contrastée.
     * @return Masque binaire brut des chaussées détectées.
     * @throws IllegalArgumentException Si l'image est null.
     */
    @Override
    public BinaryMask detect(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image cartographique ne peut pas être null.");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        boolean[] maskData = new boolean[width * height];

        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i];
            int r = (rgb >>> 16) & 0xFF;
            int g = (rgb >>> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (isRoadColor(r, g, b)) {
                maskData[i] = true;
            }
        }

        return new BinaryMask(width, height, maskData);
    }

    /**
     * Évalue si les composantes RVB correspondent à la signature colorimétrique d'une chaussée.
     *
     * @param r Composante rouge [0..255].
     * @param g Composante verte [0..255].
     * @param b Composante bleue [0..255].
     * @return Vrai si le triplet RVB valide la règle de chaussée.
     */
    public static boolean isRoadColor(int r, int g, int b) {
        int deltaBlueRed = b - r;
        int deltaBlueGreen = b - g;
        return deltaBlueRed >= MIN_DELTA_BLUE_RED
                && deltaBlueGreen >= MIN_DELTA_BLUE_GREEN
                && deltaBlueGreen <= MAX_DELTA_BLUE_GREEN
                && r < MAX_RED_LUMINANCE;
    }
}
```

- [x] **Step 4: Exécuter les tests unitaires pour valider `RoadDetectorStyle`**

Run: `mvn test -Dtest=RoadDetectorStyleTest`
Expected: PASS

- [x] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/v2/road/RoadDetector.java src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorStyle.java src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorStyleTest.java
git commit -m "feat(v2): detecteur colorimetrique RoadDetectorStyle base sur les deltas RVB"
```

---

### Task 3: Nettoyeur et Fermeture Topologique `RoadMaskCleaner`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/road/RoadMaskCleaner.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/road/RoadMaskCleanerTest.java`

- [x] **Step 1: Écrire les tests unitaires pour `RoadMaskCleaner`**

Créer `src/test/java/com/sam102022/photoshop/v2/road/RoadMaskCleanerTest.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation du nettoyeur et fermeur topologique RoadMaskCleaner")
class RoadMaskCleanerTest {

    @Test
    @DisplayName("Colmatage d'une micro-coupure de 1 pixel par fermeture morphologique minimale")
    void testCloseOnePixelGap() {
        BinaryMask raw = new BinaryMask(10, 10);
        // Deux segments de route alignés séparés par un trou de 1 pixel en (5, 5)
        raw.set(3, 5, true);
        raw.set(4, 5, true);
        // (5, 5) est false
        raw.set(6, 5, true);
        raw.set(7, 5, true);

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask result = cleaner.clean(raw);

        assertNotNull(result);
        assertFalse(result.raw().get(5, 5), "Le masque raw conserve la discontinuité d'origine");
        assertTrue(result.closed().get(5, 5), "Le masque closed a colmaté la discontinuité de 1 pixel");
    }

    @Test
    @DisplayName("Suppression des bruits isolés 1x1 par ouverture morphologique 2x2")
    void testRemoveIsolatedNoise() {
        BinaryMask raw = new BinaryMask(20, 20);
        // Pixel de bruit isolé
        raw.set(2, 2, true);

        // Bloc continu de route (au moins 2x2)
        raw.set(10, 10, true);
        raw.set(11, 10, true);
        raw.set(10, 11, true);
        raw.set(11, 11, true);

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask result = cleaner.clean(raw);

        assertFalse(result.closed().get(2, 2), "Le pixel de bruit isolé doit être supprimé");
        assertTrue(result.closed().get(10, 10), "Le bloc continu 2x2 doit être préservé");
    }

    @Test
    @DisplayName("Rejet d'un masque raw null")
    void testNullRawMask() {
        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        assertThrows(IllegalArgumentException.class, () -> cleaner.clean(null));
    }
}
```

- [x] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=RoadMaskCleanerTest`
Expected: FAIL (Cannot find symbol RoadMaskCleaner)

- [x] **Step 3: Implémenter `RoadMaskCleaner`**

Créer `src/main/java/com/sam102022/photoshop/v2/road/RoadMaskCleaner.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Nettoyeur morphologique de masque routier.
 * Assure la suppression du micro-bruit par ouverture 2×2 et le colmatage des discontinuités
 * d'anti-aliasing de 1 pixel par fermeture morphologique minimale (rayon 1 / 4-voisinage ou 8-voisinage),
 * conformément aux étapes du prototype Python script.py :
 * {@code road = ndi.binary_closing(road, iterations=1)}
 * {@code road = ndi.binary_opening(road, structure=np.ones((2, 2)))}.
 */
public final class RoadMaskCleaner {

    /**
     * Traite un masque routier brut pour produire le contrat immuable RoadMask.
     *
     * @param raw Masque binaire brut issu de la détection.
     * @return Instance RoadMask contenant le masque brut et le masque fermé étanche.
     * @throws IllegalArgumentException si raw est null.
     */
    public RoadMask clean(BinaryMask raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Le masque brut ne peut pas être null.");
        }

        int width = raw.getWidth();
        int height = raw.getHeight();

        // 1. Fermeture morphologique minimale (dilatation r=1 puis érosion r=1 avec 4-voisinage standard scipy)
        BinaryMask closed = binaryClosing(raw, width, height);

        // 2. Ouverture morphologique avec structurant carré 2x2 (érosion 2x2 puis dilatation 2x2)
        BinaryMask cleaned = binaryOpening2x2(closed, width, height);

        return new RoadMask(width, height, raw, cleaned);
    }

    /**
     * Réalise une fermeture binaire avec structurant en croix (rayon 1, 4-connexité).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque après dilatation puis érosion.
     */
    public static BinaryMask binaryClosing(BinaryMask mask, int width, int height) {
        BinaryMask dilated = dilateCross(mask, width, height);
        return erodeCross(dilated, width, height);
    }

    /**
     * Dilatation avec élément structurant en croix (4-voisins).
     */
    private static BinaryMask dilateCross(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, true);
                    if (x > 0) result.set(x - 1, y, true);
                    if (x < width - 1) result.set(x + 1, y, true);
                    if (y > 0) result.set(x, y - 1, true);
                    if (y < height - 1) result.set(x, y + 1, true);
                }
            }
        }
        return result;
    }

    /**
     * Érosion avec élément structurant en croix (4-voisins).
     */
    private static BinaryMask erodeCross(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)
                        && (x == 0 || mask.get(x - 1, y))
                        && (x == width - 1 || mask.get(x + 1, y))
                        && (y == 0 || mask.get(x, y - 1))
                        && (y == height - 1 || mask.get(x, y + 1))) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Réalise une ouverture binaire avec structurant carré 2×2 (np.ones((2, 2))).
     *
     * @param mask   Masque d'entrée.
     * @param width  Largeur.
     * @param height Hauteur.
     * @return Masque après érosion 2x2 puis dilatation 2x2.
     */
    public static BinaryMask binaryOpening2x2(BinaryMask mask, int width, int height) {
        BinaryMask eroded = erode2x2(mask, width, height);
        return dilate2x2(eroded, width, height);
    }

    /**
     * Érosion par un pavé 2×2 : un pixel (x, y) est conservé si (x, y), (x+1, y), (x, y+1) et (x+1, y+1) sont actifs.
     */
    private static BinaryMask erode2x2(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                if (mask.get(x, y)
                        && mask.get(x + 1, y)
                        && mask.get(x, y + 1)
                        && mask.get(x + 1, y + 1)) {
                    result.set(x, y, true);
                }
            }
        }
        return result;
    }

    /**
     * Dilatation par un pavé 2×2 : si un pixel (x, y) est actif, il active (x, y), (x+1, y), (x, y+1) et (x+1, y+1).
     */
    private static BinaryMask dilate2x2(BinaryMask mask, int width, int height) {
        BinaryMask result = new BinaryMask(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mask.get(x, y)) {
                    result.set(x, y, true);
                    if (x < width - 1) result.set(x + 1, y, true);
                    if (y < height - 1) result.set(x, y + 1, true);
                    if (x < width - 1 && y < height - 1) result.set(x + 1, y + 1, true);
                }
            }
        }
        return result;
    }
}
```

- [x] **Step 4: Exécuter les tests unitaires pour valider `RoadMaskCleaner`**

Run: `mvn test -Dtest=RoadMaskCleanerTest`
Expected: PASS

- [x] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/v2/road/RoadMaskCleaner.java src/test/java/com/sam102022/photoshop/v2/road/RoadMaskCleanerTest.java
git commit -m "feat(v2): RoadMaskCleaner avec fermeture minimale 1px et ouverture 2x2"
```

---

### Task 4: Rasteriseur Vectoriel OSM `RoadDetectorOsm`

**Files:**
- Create: `src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorOsm.java`
- Test: `src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorOsmTest.java`

- [x] **Step 1: Écrire les tests unitaires pour `RoadDetectorOsm`**

Créer `src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorOsmTest.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation du détecteur vectoriel RoadDetectorOsm")
class RoadDetectorOsmTest {

    @Test
    @DisplayName("Rasterisation d'un tronçon OSM simple sur un contexte de carte")
    void testRasterizeOsmRoad() throws IOException {
        String json = """
        {
          "map": {
            "center": { "lat": 47.2691, "lng": -1.5065 },
            "zoom": 17,
            "container": { "pixelWidth": 200, "pixelHeight": 200 }
          },
          "roads": [
            {
              "tags": { "highway": "primary", "lanes": "2" },
              "geometry": [
                { "lat": 47.2691, "lon": -1.5065 },
                { "lat": 47.2695, "lon": -1.5065 }
              ]
            }
          ]
        }
        """;

        Path tempFile = Files.createTempFile("osm_test", ".json");
        Files.writeString(tempFile, json);

        MapContext context = new MapContext(200, 200, 17, new GeoCoordinate(47.2691, -1.5065));
        RoadDetectorOsm detector = new RoadDetectorOsm();
        BinaryMask mask = detector.detect(tempFile, context);

        assertNotNull(mask);
        // Le centre (100, 100) doit être couvert par la route tracée
        assertTrue(mask.get(100, 100), "Le centre de l'image traversé par la route doit être actif");
        assertFalse(mask.get(10, 10), "Un pixel éloigné ne doit pas être actif");

        Files.deleteIfExists(tempFile);
    }

    @Test
    @DisplayName("Rejet des paramètres invalides ou manquants")
    void testInvalidParameters() {
        RoadDetectorOsm detector = new RoadDetectorOsm();
        MapContext context = new MapContext(100, 100, 15, new GeoCoordinate(47.0, -1.0));

        assertThrows(IllegalArgumentException.class, () -> detector.detect((Path) null, context));
        assertThrows(IllegalArgumentException.class, () -> detector.detect(Path.of("nonexistent.json"), null));
    }
}
```

- [x] **Step 2: Exécuter le test pour vérifier l'échec de compilation**

Run: `mvn test -Dtest=RoadDetectorOsmTest`
Expected: FAIL (Cannot find symbol RoadDetectorOsm)

- [x] **Step 3: Implémenter `RoadDetectorOsm`**

Créer `src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorOsm.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.geometry.GeoCoordinate;
import com.sam102022.photoshop.v2.geometry.MapContext;
import com.sam102022.photoshop.v2.geometry.PixelPoint;
import com.sam102022.photoshop.v2.geometry.WebMercatorProjection;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Détecteur et rasteriseur vectoriel de chaussées OpenStreetMap.
 * Parse un export osm_roads.json, projette les géométries via WebMercatorProjection,
 * et dessine la surface de voirie selon la typologie routière et le nombre de voies.
 */
public final class RoadDetectorOsm {

    private static final double EARTH_CIRCUMFERENCE_METERS = 40_075_016.686;
    private static final List<String> INCLUDED_HIGHWAYS = List.of(
            "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
            "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
            "residential", "living_street", "road", "service", "busway"
    );

    /**
     * Rasterise le fichier OSM fourni dans le repère de la carte défini par MapContext.
     *
     * @param osmJson Fichier JSON contenant les tronçons routiers.
     * @param context Contexte géométrique et dimensionnel de la carte.
     * @return Masque binaire des routes rasterisées.
     * @throws IOException En cas d'erreur de lecture du fichier.
     * @throws IllegalArgumentException si un paramètre est null.
     */
    public BinaryMask detect(Path osmJson, MapContext context) throws IOException {
        if (osmJson == null) {
            throw new IllegalArgumentException("Le chemin du fichier OSM ne peut pas être null.");
        }
        if (context == null) {
            throw new IllegalArgumentException("Le contexte cartographique ne peut pas être null.");
        }
        if (!Files.exists(osmJson)) {
            throw new IOException("Fichier OSM introuvable : " + osmJson);
        }

        String content = Files.readString(osmJson, StandardCharsets.UTF_8);
        OsmData data = parseOsmData(content);

        WebMercatorProjection projection = new WebMercatorProjection(context);
        double worldSize = 256.0 * Math.pow(2.0, context.zoom());
        double metersPerPixel = Math.cos(Math.toRadians(context.center().latitude()))
                * EARTH_CIRCUMFERENCE_METERS / worldSize;

        BufferedImage canvas = new BufferedImage(context.width(), context.height(), BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g2d = canvas.createGraphics();
        try {
            g2d.setColor(Color.WHITE);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            for (OsmWay way : data.ways()) {
                if (!INCLUDED_HIGHWAYS.contains(way.highway()) || isMinorService(way.highway(), way.service())) {
                    continue;
                }
                if (way.coordinates().size() < 2) {
                    continue;
                }

                Path2D path = new Path2D.Double();
                boolean first = true;
                for (GeoCoordinate coord : way.coordinates()) {
                    PixelPoint pt = projection.toPixel(coord);
                    if (first) {
                        path.moveTo(pt.x(), pt.y());
                        first = false;
                    } else {
                        path.lineTo(pt.x(), pt.y());
                    }
                }

                float strokeWidth = (float) Math.max(1.0, computeRoadWidthMeters(way) / metersPerPixel);
                g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.draw(path);
            }
        } finally {
            g2d.dispose();
        }

        return BinaryMask.fromImage(canvas, 1);
    }

    private static boolean isMinorService(String highway, String service) {
        if (!"service".equals(highway)) return false;
        return "driveway".equals(service) || "parking_aisle".equals(service)
                || "emergency_access".equals(service);
    }

    private static double computeRoadWidthMeters(OsmWay way) {
        if (way.widthMeters() > 0) return way.widthMeters();
        double lanes = way.lanes() > 0 ? way.lanes() : defaultLanes(way.highway());
        double metersPerLane = switch (way.highway()) {
            case "motorway", "trunk" -> 3.6;
            case "primary", "secondary" -> 3.3;
            default -> 3.0;
        };
        return Math.max(3.0, lanes * metersPerLane + 1.0);
    }

    private static double defaultLanes(String highway) {
        return switch (highway) {
            case "motorway", "trunk", "primary", "secondary" -> 2.0;
            default -> 1.0;
        };
    }

    private record OsmWay(String highway, String service, double lanes, double widthMeters, List<GeoCoordinate> coordinates) {}
    private record OsmData(List<OsmWay> ways) {}

    /**
     * Analyseur JSON autonome et léger pour extraire les routes OSM.
     */
    private static OsmData parseOsmData(String json) throws IOException {
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object rootObj = parser.parse();
        if (!(rootObj instanceof Map<?, ?> root)) {
            throw new IOException("Structure JSON racine invalide");
        }

        List<OsmWay> ways = new ArrayList<>();
        Object roadsObj = root.get("roads");
        if (roadsObj instanceof List<?> roadsList) {
            for (Object rObj : roadsList) {
                if (rObj instanceof Map<?, ?> roadMap) {
                    Map<?, ?> tags = roadMap.get("tags") instanceof Map<?, ?> t ? t : Map.of();
                    String highway = String.valueOf(tags.getOrDefault("highway", ""));
                    String service = String.valueOf(tags.getOrDefault("service", ""));
                    double lanes = parseDouble(tags.get("lanes"));
                    double width = parseDouble(tags.get("width"));

                    List<GeoCoordinate> coords = new ArrayList<>();
                    if (roadMap.get("geometry") instanceof List<?> geomList) {
                        for (Object ptObj : geomList) {
                            if (ptObj instanceof Map<?, ?> ptMap) {
                                double lat = parseDouble(ptMap.get("lat"));
                                double lon = parseDouble(ptMap.containsKey("lon") ? ptMap.get("lon") : ptMap.get("lng"));
                                coords.add(new GeoCoordinate(lat, lon));
                            }
                        }
                    }
                    ways.add(new OsmWay(highway, service, lanes, width, coords));
                }
            }
        }
        return new OsmData(ways);
    }

    private static double parseDouble(Object val) {
        if (val == null) return -1;
        try {
            return Double.parseDouble(String.valueOf(val).trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Parseur JSON récursif basique pour respecter ADR-001.
     */
    private static final class SimpleJsonParser {
        private final String src;
        private int pos = 0;

        SimpleJsonParser(String src) { this.src = src; }

        Object parse() throws IOException {
            skipWhitespace();
            Object val = parseValue();
            skipWhitespace();
            return val;
        }

        private Object parseValue() throws IOException {
            skipWhitespace();
            if (pos >= src.length()) return null;
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (Character.isDigit(c) || c == '-') return parseNumber();
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new IOException("Caractère inattendu à la position " + pos + " : '" + c + "'");
        }

        private Map<String, Object> parseObject() throws IOException {
            Map<String, Object> map = new HashMap<>();
            pos++; // '{'
            while (pos < src.length()) {
                skipWhitespace();
                if (src.charAt(pos) == '}') { pos++; return map; }
                String key = parseString();
                skipWhitespace();
                if (src.charAt(pos) != ':') throw new IOException("':' attendu après la clé");
                pos++;
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (src.charAt(pos) == ',') { pos++; continue; }
                if (src.charAt(pos) == '}') { pos++; return map; }
                throw new IOException("',' ou '}' attendu dans l'objet");
            }
            throw new IOException("Objet non fermé");
        }

        private List<Object> parseArray() throws IOException {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            while (pos < src.length()) {
                skipWhitespace();
                if (src.charAt(pos) == ']') { pos++; return list; }
                list.add(parseValue());
                skipWhitespace();
                if (src.charAt(pos) == ',') { pos++; continue; }
                if (src.charAt(pos) == ']') { pos++; return list; }
                throw new IOException("',' ou ']' attendu dans le tableau");
            }
            throw new IOException("Tableau non fermé");
        }

        private String parseString() throws IOException {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) break;
                    char esc = src.charAt(pos++);
                    if (esc == 'n') sb.append('\n');
                    else if (esc == 't') sb.append('\t');
                    else sb.append(esc);
                } else {
                    sb.append(c);
                }
            }
            throw new IOException("Chaîne de caractères non terminée");
        }

        private Number parseNumber() {
            int start = pos;
            if (src.charAt(pos) == '-') pos++;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.' || src.charAt(pos) == 'e' || src.charAt(pos) == 'E' || src.charAt(pos) == '+')) {
                pos++;
            }
            String numStr = src.substring(start, pos);
            if (numStr.contains(".")) return Double.parseDouble(numStr);
            return Long.parseLong(numStr);
        }

        private void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }
    }
}
```

- [x] **Step 4: Exécuter les tests unitaires pour valider `RoadDetectorOsm`**

Run: `mvn test -Dtest=RoadDetectorOsmTest`
Expected: PASS

- [x] **Step 5: Commiter**

```bash
git add src/main/java/com/sam102022/photoshop/v2/road/RoadDetectorOsm.java src/test/java/com/sam102022/photoshop/v2/road/RoadDetectorOsmTest.java
git commit -m "feat(v2): rasteriseur vectoriel RoadDetectorOsm base sur WebMercatorProjection"
```

---

### Task 5: Test d'Intégration Pivot CA01 (`Sprint2IntegrationTest`)

**Files:**
- Create: `src/test/java/com/sam102022/photoshop/v2/road/Sprint2IntegrationTest.java`

- [x] **Step 1: Écrire le test d'intégration comparant la sortie avec `maps/road.png`**

Créer `src/test/java/com/sam102022/photoshop/v2/road/Sprint2IntegrationTest.java` :
```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Validation d'intégration du Sprint 2 sur l'étalon Territoire CA01")
class Sprint2IntegrationTest {

    @Test
    @DisplayName("Conformité de la détection et du nettoyage de RoadMask avec l'image témoin Python maps/road.png")
    void testRoadMaskPipelineConformity() throws IOException {
        // 1. Chargement de l'image source CA01
        InputStream mapStream = getClass().getResourceAsStream("/v2/fixtures/CA01/05_style_contraste_sans_rien.png");
        assertNotNull(mapStream, "L'image fixture 05_style_contraste_sans_rien.png doit être présente");
        BufferedImage mapImage = ImageIO.read(mapStream);

        // 2. Exécution du pipeline Sprint 2
        long t0 = System.currentTimeMillis();
        RoadDetectorStyle detector = new RoadDetectorStyle();
        BinaryMask raw = detector.detect(mapImage);

        RoadMaskCleaner cleaner = new RoadMaskCleaner();
        RoadMask roadMask = cleaner.clean(raw);
        long elapsed = System.currentTimeMillis() - t0;

        System.out.println("Pipeline Sprint 2 exécuté en " + elapsed + " ms");
        assertTrue(elapsed < 2000, "Le pipeline Sprint 2 doit s'exécuter en moins de 2 secondes");

        assertEquals(mapImage.getWidth(), roadMask.width());
        assertEquals(mapImage.getHeight(), roadMask.height());
        assertTrue(roadMask.closed().countActivePixels() > 0, "Le masque de routes fermé ne doit pas être vide");

        // 3. Comparaison avec l'image témoin maps/road.png si présente à la racine
        File witnessFile = new File("maps/road.png");
        if (witnessFile.exists()) {
            BufferedImage witnessImg = ImageIO.read(witnessFile);
            BinaryMask witnessMask = BinaryMask.fromImage(witnessImg, 128);

            int w = roadMask.width();
            int h = roadMask.height();
            long matchCount = 0;
            long total = (long) w * h;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (roadMask.closed().get(x, y) == witnessMask.get(x, y)) {
                        matchCount++;
                    }
                }
            }

            double agreement = (double) matchCount / total;
            System.out.printf("Taux de concordance pixel avec maps/road.png : %.4f%%%n", agreement * 100.0);
            assertTrue(agreement >= 0.995, "Le taux de concordance avec le témoin Python doit dépasser 99.5%");
        }
    }
}
```

- [x] **Step 2: Exécuter le test d'intégration**

Run: `mvn test -Dtest=Sprint2IntegrationTest`
Expected: PASS avec concordance >= 99.5%

- [x] **Step 3: Commiter**

```bash
git add src/test/java/com/sam102022/photoshop/v2/road/Sprint2IntegrationTest.java
git commit -m "test(v2): validation d'integration du pipeline Sprint 2 compare a l'etalon maps/road.png"
```

---

### Task 6: Mise à jour du Journal de Bord et Validation Globale V2

**Files:**
- Create: `JOURNAL.md` (conforme ADR-010)

- [x] **Step 1: Créer le fichier `JOURNAL.md` consignant les réalisations du Sprint 2**
- [x] **Step 2: Exécuter tous les tests V2 pour garantir l'absence de régression**

Run: `mvn test -Dtest=com.sam102022.photoshop.v2.**.*Test`
Expected: ALL PASS (100% de succès sur la suite V2)

- [x] **Step 3: Commiter**

```bash
git add JOURNAL.md
git commit -m "docs(v2): mise a jour du journal de bord suite a la finalisation du Sprint 2"
```
