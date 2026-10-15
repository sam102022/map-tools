# Spécification Technique : Anti-Aliasing Sub-Pixel et Adoucissement des Bords

- **Date :** 2026-09-27
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé
- **Langage / Environnement :** Java 21, Maven, Swing, Java2D (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Objectif

L'application permet d'extraire une portion de carte Google Maps à partir d'un calque vert et d'aligner automatiquement son contour sur le réseau routier.

Jusqu'à présent, la découpe finale de l'image détourée reposait sur un masque binaire (`BinaryMask`), produisant des bords crénelés (effet d'escalier), ou sur un lissage rudimentaire appliquant un alpha constant arbitraire (160) sur les pixels d'une bordure érodée.

L'objectif de cette évolution est d'introduire :
1. Une option d'**anti-aliasing sub-pixel** désactivable (activée par défaut), calculant une couverture géométrique précise de 0 à 255 par pixel le long du contour vectoriel via Java2D.
2. Un modèle de données dédié `CoverageMask` pour manipuler des opacités continues [0..255] avec des opérations d'union spécifiques (préservation du noyau intérieur opaque).
3. Une formule de composition alpha rigoureuse respectant l'alpha initial de l'image source :
   $$\text{finalAlpha} = \frac{\text{sourceAlpha} \times \text{effectiveCoverage} + 127}{255}$$
4. Une articulation claire entre l'**anti-aliasing** (couverture sub-pixel sur la frontière immédiate) et le **lissage** (`smoothRadius` modulant progressivement l'alpha sur plusieurs pixels).
5. Des contrôles dédiés dans l'interface Swing (case à cocher) et en ligne de commande CLI (`--no-antialias` / `--antialias` et leurs alias), avec détection des conflits d'arguments.
6. L'export du masque noir et blanc standardisé via un seuillage strict documenté à 128 ($\text{coverage} \ge 128$).

---

## 2. Architecture & Modèle de Données

### 2.1. Nouveau modèle `CoverageMask`
Package : `com.sam102022.photoshop.core.model.CoverageMask`

Stocke la couverture de chaque pixel par le masque :
- `0` : pixel entièrement transparent / extérieur.
- `255` : pixel entièrement opaque / intérieur.
- `1..254` : couverture partielle du pixel par le polygone (anti-aliasing de bordure).

Structure interne :
- `int width`
- `int height`
- `byte[] data` : tableau à 1 dimension de taille `width * height`. Chaque octet est lu comme un entier non signant via `data[index] & 0xFF`.

Méthodes publiques :
- `CoverageMask(int width, int height)` : initialise un masque vide (tous les pixels à 0). Valide que `width > 0` et `height > 0`.
- `int getWidth()` et `int getHeight()`.
- `int get(int x, int y)` : retourne une valeur dans $[0, 255]$. Lève une `IndexOutOfBoundsException` si les coordonnées sont hors limites.
- `void set(int x, int y, int value)` : stocke la valeur bornée dans l'intervalle $[0, 255]$ :
  $$\text{clamped} = \max(0, \min(255, \text{value}))$$
- `BinaryMask toBinaryMask(int threshold)` : convertit en `BinaryMask` binaire. Vérifie que `threshold` est compris dans $[0, 255]$. Un pixel est actif si :
  $$\text{coverage} \ge \text{threshold}$$
- `static CoverageMask fromBinaryMask(BinaryMask mask)` : méthode utilitaire. Convertit un masque binaire en `CoverageMask` (`true` $\rightarrow 255$, `false` $\rightarrow 0$).
- `CoverageMask max(CoverageMask other)` : retourne une nouvelle instance de `CoverageMask` contenant $\max(\text{this}(x, y), \text{other}(x, y))$ pour chaque pixel. Vérifie la concordance stricte des dimensions. Son rôle est de préserver une couverture déjà opaque (comme le noyau intérieur) sans diminuer les couvertures partielles calculées.

### 2.2. Évolution de `SnappingConfig`
Package : `com.sam102022.photoshop.core.model.SnappingConfig`

- Ajout du champ `boolean antialiasing` dans le record :
  ```java
  public record SnappingConfig(
          int snapDistance,
          float roadSensitivity,
          int smoothRadius,
          int seedErosionRadius,
          int closingRadius,
          boolean antialiasing
  )
  ```
- Constructeur de compatibilité pour éviter la rupture des appels existants à 5 paramètres :
  ```java
  public SnappingConfig(int snapDistance, float roadSensitivity, int smoothRadius, int seedErosionRadius, int closingRadius) {
      this(snapDistance, roadSensitivity, smoothRadius, seedErosionRadius, closingRadius, true);
  }
  ```
- Valeur par défaut dans `defaults()` : `antialiasing = true`.
- Évolution du `Builder` :
  - Champ `private boolean antialiasing = true;`
  - Méthode `public Builder antialiasing(boolean antialiasing)`

---

## 3. Rasterisation Vectorielle, Recalage & Rendu d'Export

### 3.1. Rasterisation vectorielle dans `PolygonBuilder`
Package : `com.sam102022.photoshop.core.geometry.PolygonBuilder`

- **Nouvelle méthode principale :**
  ```java
  public CoverageMask rasterizeCoverage(int width, int height, List<Point> polygon, boolean antialiasing)
  ```
  - Vérifie les dimensions (`width > 0`, `height > 0`) et la non-nullité du polygone.
  - Si `polygon.size() < 3` : retourne un `CoverageMask` vide de dimensions $(width, height)$.
  - Construit un `Path2D.Double` fermé avec `WIND_NON_ZERO`.
  - Instancie un `BufferedImage` de type `BufferedImage.TYPE_BYTE_GRAY`.
  - Configure le `Graphics2D` :
    ```java
    g2d.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        antialiasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF
    );
    g2d.setColor(Color.WHITE);
    g2d.fill(path);
    g2d.dispose();
    ```
  - Transfère les octets du raster vers une nouvelle instance de `CoverageMask` en appliquant `sample & 0xFF`.
- **Méthode historique compatible :**
  ```java
  public BinaryMask rasterize(int width, int height, List<Point> polygon) {
      return rasterizeCoverage(width, height, polygon, false).toBinaryMask(128);
  }
  ```

### 3.2. Moteur de recalage dans `RoadSnappingEngine`
Package : `com.sam102022.photoshop.core.segmentation.RoadSnappingEngine`

- **Nouvelle méthode retournant la couverture :**
  ```java
  public CoverageMask snapCoverage(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config)
  ```
- **Gestion des cas limites et replis explicites (sans perte de données) :**
  1. Si `roughGreenMask.countActivePixels() == 0` : retourne un `CoverageMask` vide.
  2. Si `roadCandidates.countActivePixels() == 0` : pas de routes détectées pour caler le contour.
     - Si le contour extrait du masque initial a au moins 3 points, le rasteriser en `CoverageMask` avec `config.antialiasing()` pour que ses bords bénéficient tout de même de l'anti-aliasing sub-pixel.
     - Sinon, repli sur `CoverageMask.fromBinaryMask(roughGreenMask)`.
  3. Extraction du plus grand contour : `List<Point> contour = contourExtractor.extractLargestContour(roughGreenMask)`.
     - Si `contour.size() < 3` : repli sur `CoverageMask.fromBinaryMask(roughGreenMask)`.
  4. Simplification du contour raster : `simplified = contourSimplifier.simplify(contour, 1.0)`.
  5. Calage sur les candidats routiers : `adjustedContour = roadSnapper.snap(...)`.
  6. Si le contour n'a pas bougé :
     - Rasteriser `simplified` avec `config.antialiasing()`.
     - Combiner avec le noyau intérieur : `polyCoverage.max(CoverageMask.fromBinaryMask(MorphologyOps.erode(roughGreenMask, 1)))`.
  7. Si le contour a bougé :
     - `CoverageMask polyCoverage = polygonBuilder.rasterizeCoverage(w, h, adjustedContour, config.antialiasing());`
     - Préservation du noyau intérieur opaque :
       ```java
       BinaryMask interiorCore = MorphologyOps.erode(roughGreenMask, 1);
       return polyCoverage.max(CoverageMask.fromBinaryMask(interiorCore));
       ```
- **Méthode historique compatible :**
  ```java
  public BinaryMask snap(BinaryMask roughGreenMask, BinaryMask roadCandidates, SnappingConfig config) {
      return snapCoverage(roughGreenMask, roadCandidates, config).toBinaryMask(128);
  }
  ```

### 3.3. Export de l'image détourée dans `ImageExporter`
Package : `com.sam102022.photoshop.io.ImageExporter`

- **Méthode principale :**
  ```java
  public static BufferedImage createClippedImage(BufferedImage mapImage, CoverageMask coverageMask, int smoothRadius)
  ```
- **Calcul de l'alpha effectif :**
  - Si `smoothRadius <= 0` :
    $$\text{effectiveCoverage} = \text{coverageMask.get}(x, y)$$
  - Si `smoothRadius > 0` :
    Pour éviter l'ancien alpha fixe à 160, le lissage applique une modulation progressive :
    - On identifie le noyau intérieur dur via érosion du masque seuillé :
      `BinaryMask binary = coverageMask.toBinaryMask(128);`
      `BinaryMask core = MorphologyOps.erode(binary, smoothRadius);`
    - Pour un pixel où `core.get(x, y)` est vrai, `effectiveCoverage = coverageMask.get(x, y)`.
    - Pour un pixel de bordure (`coverageMask.get(x, y) > 0` mais hors du noyau dur), on applique une atténuation progressive proportionnelle à la couverture sub-pixel et à la distance de lissage.
- **Composition avec l'alpha source :**
  Pour chaque pixel $(x, y)$ :
  ```java
  int sourcePixel = mapImage.getRGB(x, y);
  int sourceAlpha = (sourcePixel >>> 24) & 0xFF;
  int cov = effectiveCoverage; // [0, 255]
  int finalAlpha = (sourceAlpha * cov + 127) / 255;

  if (finalAlpha == 0) {
      clipped.setRGB(x, y, 0x00000000);
  } else {
      int rgb = sourcePixel & 0x00FFFFFF;
      clipped.setRGB(x, y, (finalAlpha << 24) | rgb);
  }
  ```
- **Surcharges compatibles :**
  ```java
  public static BufferedImage createClippedImage(BufferedImage mapImage, BinaryMask mask, int smoothRadius) {
      return createClippedImage(mapImage, CoverageMask.fromBinaryMask(mask), smoothRadius);
  }
  ```

---

## 4. Interfaces Utilisateur & Ligne de Commande

### 4.1. Ligne de commande CLI (`CliRunner`)
- **Options d'anti-aliasing :**
  - `--no-antialias`, `--no-aa` : désactive l'anti-aliasing (`antialiasing = false`).
  - `--antialias`, `--aa` : active explicitement l'anti-aliasing (`antialiasing = true`).
  - Par défaut (aucun drapeau fourni) : `antialiasing = true`.
- **Règle de gestion des conflits :**
  - Si un drapeau d'activation ET un drapeau de désactivation sont tous deux fournis (ex: `--aa --no-aa`), le CLI lève une `IllegalArgumentException` explicite :
    `"Conflit d'options : impossible de spécifier simultanément l'activation et la désactivation de l'anti-aliasing."`
- **Flux d'exécution CLI :**
  - Appel de `engine.snapCoverage(roughMask, roadCandidates, config)`.
  - Génération de l'image détourée avec `ImageExporter.createClippedImage(mapImg, coverageMask, config.smoothRadius())`.
  - Sauvegarde du masque noir et blanc sur disque (`--mask-out`) :
    `BufferedImage maskResultImg = ImageExporter.createMaskImage(coverageMask.toBinaryMask(128));`
  - Mise à jour de `printHelp` avec la documentation des nouvelles options et alias.

### 4.2. Interface graphique Swing (`MainWindow`)
- **Composant UI :**
  - Ajout d'une case à cocher :
    ```java
    private final JCheckBox antialiasingCheckbox = new JCheckBox("Anti-aliasing", true);
    ```
  - Insérée dans le panneau de contrôle à proximité du slider de lissage.
- **Worker d'arrière-plan :**
  - Construit la `SnappingConfig` en lisant `antialiasingCheckbox.isSelected()`.
  - Exécute `snapCoverage(...)`.
  - Stocke à la fois `BufferedImage image`, `CoverageMask coverageMask` et `BinaryMask mask` (obtenu via `coverageMask.toBinaryMask(128)`) dans le résultat.
- **Aperçu et Export :**
  - L'aperçu `previewResultPanel` restitue l'image avec les nuances d'alpha sub-pixel sur le fond transparent à damier.
  - L'export enregistre l'image détourée avec les valeurs sub-pixel et le masque binaire avec le seuil 128.

---

## 5. Stratégie de Test et Validation

### 5.1. Tests Unitaires
1. **`CoverageMaskTest`** :
   - Lecture / écriture et validation du clamping $[0, 255]$.
   - Débordement des index (`IndexOutOfBoundsException`).
   - Dimensions invalides (`IllegalArgumentException`).
   - Conversion `toBinaryMask(int threshold)` : cas limite exact où `coverage == 128` (doit être `true`), `coverage == 127` (doit être `false`).
   - Rejet de seuil hors $[0, 255]$ pour `toBinaryMask`.
   - Méthode `max(CoverageMask other)` : vérifie que $\max(255, 100) = 255$ et $\max(0, 50) = 50$.
   - Conversion `fromBinaryMask`.
2. **`PolygonBuilderTest`** :
   - Rasterisation d'un triangle / polygone oblique avec `antialiasing = true` : présence attestée de valeurs strictement intermédiaires $]0, 255[$ sur la frontière.
   - Rasterisation avec `antialiasing = false` : tous les pixels sont strictement $0$ ou $255$.
   - Polygone avec $< 3$ points ou vide : vérification que le masque produit est entièrement à 0.
3. **`ImageExporterTest`** :
   - Test de composition : pixel source avec alpha 128 et couverture 255 donne un pixel avec alpha final 128.
   - Pixel source avec alpha 255 et couverture 0 donne le pixel transparent `0x00000000`.
   - Pixel source avec alpha 255 et couverture 128 donne un pixel avec alpha final 128.
   - Préservation des composantes RGB de l'image source.
   - Rejet des dimensions incompatibles.
4. **`CliRunnerTest`** :
   - Vérification que l'anti-aliasing est actif par défaut dans `SnappingConfig`.
   - Vérification de la prise en compte de `--no-antialias` et `--no-aa`.
   - Vérification de la détection d'erreur en cas de conflit d'arguments (`--aa` et `--no-aa`).

### 5.2. Tests d'Intégration
- **`SmokeTest` & `IntegrationCliTest`** :
  - Exécution complète du pipeline avec génération de l'image détourée et du masque noir et blanc seuillé à 128.
  - Vérification de la non-régression sur les jeux d'échantillons `sample/`.
