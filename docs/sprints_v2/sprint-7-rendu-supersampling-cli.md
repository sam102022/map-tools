# Sprint 7 (V2) — Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Intégration CLI

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.render` / `com.sam102022.photoshop.v2.pipeline` / `com.sam102022.photoshop.cli`  
**Documents associés :**
* Spécification générale de l'algorithme : [`docs/SPEC_V2_ALGORITHME.md`](../SPEC_V2_ALGORITHME.md)
* Sommaire et planification V2 : [`docs/SPRINTS_V2.md`](../SPRINTS_V2.md)
* Prototype Python étalon : [`maps/python/snap_cells_prototype_v5.py`](../../maps/python/snap_cells_prototype_v5.py)
* Architecture Decision Records associés :
  * [`ADR-001`](../adr/ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md) : Socle 100% Java standard sans dépendances natives.
  * [`ADR-002`](../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md) : Modèle de masques matriciels hybrides (`BinaryMask` & `CoverageMask`).
  * [`ADR-003`](../adr/ADR-003-double-mode-d-execution-cli-et-gui-swing.md) : Double mode d'exécution CLI headless et GUI Swing interactive.
  * [`ADR-004`](../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md) : Immutabilité de la configuration via records (`V2Config`).
  * [`ADR-008`](../adr/ADR-008-une-seule-responsabilite-par-classe.md) : Une seule responsabilité par classe (SRP).
  * [`ADR-009`](../adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md) : Logs structurés en français.
  * [`ADR-010`](../adr/ADR-010-suivi-et-trace-journaliere-de-l-evolution.md) : Traçabilité journalière.
  * [`ADR-011`](../adr/ADR-011-limitation-complexite-cognitive.md) : Complexité cognitive plafonnée à 15 par méthode.
  * [`ADR-012`](../adr/ADR-012-usage-des-imports-explicites-et-syntaxe-simplifiee.md) : Usage des imports explicites.
  * [`ADR-014`](../adr/ADR-014-javadoc-obligatoire-classes-methodes.md) : Documentation Javadoc exhaustive en français.

---

## 🎯 1. Objectifs du Sprint

Le **Sprint 7** constitue l'étape finale d'aboutissement de la refonte architecturale V2. Il remplit une double mission stratégique :

1. **Rendu Graphique Sub-Pixel Haute-Fidélité ($SS=4$) :**
   * Remplacer tout flou matriciel heuristique par un suréchantillonnage vectoriel quadruplé ($SS=4$) et un sous-échantillonnage par moyenne de boîte (*box filter*), produisant un masque de couverture continue `CoverageMask` conforme à l'[ADR-002](../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md).
   * Assembler le trio de livrables graphiques de production :
     * `clipped.png` : Image détourée RGBA 32-bit avec composition alpha douce sans crénelage (*anti-aliasing* géométrique exact) ;
     * `mask.png` : Masque monochrome 8-bit en niveaux de gris (`TYPE_BYTE_GRAY`) préservant les nuances continues de couverture $[0..255]$ ;
     * `overlay.png` : Image de contrôle qualité diagnostic superposant le contour frontière extérieur en rouge vif (`#FF0000`) sur la carte source d'origine.
2. **Orchestration Globale & Point d'Entrée CLI V2 :**
   * Encapsuler l'ensemble des 7 étapes algorithmiques dans un orchestrateur unifié sans état (`V2Pipeline`), réutilisable à la fois par la CLI et par la future interface graphique Swing ([ADR-003](../adr/ADR-003-double-mode-d-execution-cli-et-gui-swing.md)).
   * Définir le record immuable de configuration `V2Config` ([ADR-004](../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md)) regroupant tous les hyperparamètres calibrés sur l'étalon Python V5.
   * Rendre le pipeline V2 accessible en ligne de commande via `V2CliRunner` (drapeau `--v2` ou commande dédiée), avec journalisation hiérarchisée en français ([ADR-009](../adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md)).

---

## 📦 2. Livrables & Composants Clés

Conformément à l'[ADR-008](../adr/ADR-008-une-seule-responsabilite-par-classe.md) (responsabilité unique), les rôles sont strictement cloisonnés :

```text
com.sam102022.photoshop
├── v2.pipeline
│   ├── V2Config.java                (Record immuable des hyperparamètres V2)
│   └── V2Pipeline.java              (Orchestrateur de bout en bout Sprints 1 -> 7)
├── v2.render
│   ├── SupersampleRenderer.java     (Rastérisation SS=4 sur ROI + box filter -> CoverageMask)
│   ├── ImageClipper.java            (Assemblage RGBA, export TYPE_BYTE_GRAY et overlay de contrôle)
│   └── RenderResult.java            (Record immuable regroupant clipped, mask, overlay et coverage)
└── cli
    └── V2CliRunner.java             (Parseur CLI, validation des drapeaux et exécution du pipeline)
```

### 2.1. `V2Config` (Record immuable de configuration)
Encapsule l'ensemble des seuils et paramètres du pipeline V2 avec validation des bornes et fabrique de valeurs par défaut :
* `hi` : Seuil haut de couverture pour sélection intégrale `INSIDE` (défaut: `0.60`).
* `lo` : Seuil bas de couverture pour rejet total `OUTSIDE` (défaut: `0.05`).
* `eps` : Marge géodésique $\varepsilon$ sur la chaussée en pixels (défaut: `4.0`).
* `rho` : Rayon du disque d'ouverture morphologique post-expansion (défaut: `5`).
* `sig` : Échelle gaussienne $\sigma$ du lissage LQR en pixels d'arc (défaut: `22.0`).
* `cornerAngle` : Seuil angulaire en degrés pour la détection de coins vifs (défaut: `38.0`).
* `cornerWindowL` : Demi-fenêtre d'estimation tangentielle des coins (défaut: `80`).
* `r0` : Rayon intérieur de gel du contour près des coins (défaut: `35.0`).
* `r1` : Rayon extérieur de fin de fondu Hermite smoothstep (défaut: `95.0`, soit $R_0 + 60$).
* `roundaboutRadiusZr` : Seuil de proximité normalisée pour îlots de ronds-points (défaut: `2.0`).
* `supersamplingFactor` : Facteur de suréchantillonnage sub-pixel $SS$ (défaut: `4`).
* `cropMargin` : Marge de sécurité de la boîte englobante en pixels (défaut: `90`).
* `residualHoleMaxArea` : Surface maximale des cours intérieures à combler (défaut: `15000`).

### 2.2. `SupersampleRenderer`
* **Rastérisation sub-pixel sur ROI rognée :** Pour éviter d'allouer une image monochrome géante à l'échelle du canevas complet ($15240 \times 8520\text{ px} \approx 130\text{ Mo}$), la rastérisation s'opère exclusivement sur la fenêtre d'intérêt `CropWindow` ($w_{crop} \cdot SS \times h_{crop} \cdot SS$).
* **Correction géométrique de demi-pixel ($+0.5\text{ px}$) :** Les sommets vectoriels issus de `SmoothVectorContour` (Marching Squares) référencent les centres des pixels. Un décalage de $+0.5\text{ px}$ est appliqué lors du passage dans le repère agrandi :
  $$X_{ss} = (x_{local} + 0.5) \times SS, \quad Y_{ss} = (y_{local} + 0.5) \times SS$$
* **Rastérisation binaire haute résolution :** Remplissage vectoriel via `Graphics2D.fill(Path2D)` sur une `BufferedImage` monochrome à deux niveaux sans antialiasing logiciel (`RenderingHints.VALUE_ANTIALIAS_OFF`), afin de laisser le filtre boîte effectuer l'intégration surfacique analytique exacte.
* **Filtre boîte (*Box Filter*) :** Pour chaque pixel nominal $(x, y)$ de la ROI, calcul de la moyenne arithmétique des $SS \times SS = 16$ sous-pixels, produisant une couverture dans $[0..255]$.
* **Réinsertion globale :** Injection de la matrice rognée dans un `CoverageMask` de dimensions globales $W \times H$ aux coordonnées $(x_0, y_0)$ de la `CropWindow`.

### 2.3. `ImageClipper`
* **Génération de `clipped.png` (ARGB 32-bit) :**
  Application stricte de la formule de composition alpha issue de l'[ADR-002](../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md) pour chaque pixel :
  $$\alpha_{final}(x, y) = \left\lfloor \frac{\alpha_{source}(x, y) \times \text{coverage}(x, y) + 127}{255} \right\rfloor$$
  Les composantes de couleur RGB de l'image originale sont conservées intactes.
* **Génération de `mask.png` (`TYPE_BYTE_GRAY`) :**
  Image monochrome 8-bit représentant directement la matrice continue $[0..255]$, lisible nativement par tout logiciel tiers de SIG ou de retouche photographique.
* **Génération de `overlay.png` (RGB 24-bit de contrôle) :**
  Superposition sur la carte source du contour extérieur rouge vif (`#FF0000`) :
  * Détection de bordure par différence matricielle sur le masque seuillé à 128 :
    $$\text{Edge} = M_{full} \land \neg\,\text{erode}(M_{full}) \quad \text{avec } M_{full}(x, y) = (\text{coverage}(x, y) \ge 128)$$
  * Coloration des pixels d'`Edge` en rouge pur (`0xFF0000`).

### 2.4. `V2Pipeline` (Orchestrateur de Production)
Coordonne l'ensemble des modules développés du Sprint 1 au Sprint 7 :
1. Projection cartographique du polygone d'intention $P$ (`WebMercatorProjection`, `PolygonRasterizer` — Sprint 1) ;
2. Détection spectrale des routes et fermeture topologique minimale (`RoadDetectorStyle` / `RoadDetectorOsm`, `RoadMaskCleaner` — Sprint 2) ;
3. Recadrage boîte englobante, étiquetage 4-connexe des cellules urbaines et graphe d'adjacence (`BoundingBoxCropper`, `CellLabeler`, `RoadInterfaceExtractor` — Sprint 3) ;
4. Vote topologique et classification des cellules (`CellClassifier`, `PartialCellResolver` — Sprint 4) ;
5. Reconstruction des barrières routières, propagation géodésique bornée et comblement des îlots (`BoundaryRoadExtractor`, `GeodesicRoadExpander`, `ResidualHoleResolver` — Sprint 5) ;
6. Extraction sub-pixel Marching Squares, détection des angles vifs, lissage LQR robuste et modélisation des ronds-points (`SubpixelContourExtractor`, `CornerDetector`, `RobustLqrSmoother`, `RoundaboutDetector`, `HermiteSplineConnector` — Sprint 6) ;
7. Supersampling sub-pixel $SS=4$, réduction box filter et assemblage RGBA (`SupersampleRenderer`, `ImageClipper` — Sprint 7).

### 2.5. `V2CliRunner` (Interface CLI)
Parseur en ligne de commande dédié offrant une ergonomie moderne, une gestion complète des hyperparamètres, des messages d'erreur détaillés en français et le pilotage du flag `--v2`.

---

## 🔄 3. Contrat d'Entrée / Sortie & Signatures Officielles

```text
Entrée : 
  - Image cartographique source (BufferedImage RGB/RGBA, taille W x H)
  - Géométrie vectorielle du territoire (TerritoryGeometry)
  - Contexte cartographique (MapContext : centre, zoom, dimensions)
  - Mode d'opération (OperationMode : TERRITORY ou ZONE)
  - Configuration d'exécution (V2Config)

Sortie : 
  - Record immuable RenderResult (clipped, mask, overlay, coverageMask)
```

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
    public RenderResult {
        if (clipped == null || mask == null || overlay == null || coverageMask == null) {
            throw new IllegalArgumentException("Aucun composant de RenderResult ne peut être null.");
        }
    }
}
```

```java
package com.sam102022.photoshop.v2.pipeline;

/**
 * Configuration immuable regroupant l'intégralité des hyperparamètres du pipeline V2.
 */
public record V2Config(
        double hi,
        double lo,
        double eps,
        int rho,
        double sig,
        double cornerAngle,
        int cornerWindowL,
        double r0,
        double r1,
        double roundaboutRadiusZr,
        int supersamplingFactor,
        int cropMargin,
        int residualHoleMaxArea
) {
    /**
     * Instancie la configuration par défaut calibrée sur le prototype de référence Python V5.
     *
     * @return Instance V2Config nominale.
     */
    public static V2Config defaultConfig() {
        return new V2Config(
                0.60,   // hi : inclusion pleine cellule
                0.05,   // lo : rejet cellule extérieure
                4.0,    // eps : marge géodésique sur chaussée (px)
                5,      // rho : rayon de l'ouverture morphologique (px)
                22.0,   // sig : échelle de lissage LQR (px d'arc)
                38.0,   // cornerAngle : seuil d'angle vif (degrés)
                80,     // cornerWindowL : demi-fenêtre d'angle (px)
                35.0,   // r0 : rayon de gel du contour brut près des coins (px)
                95.0,   // r1 : fin de fondu Hermite smoothstep (px)
                2.0,    // roundaboutRadiusZr : zone d'influence des ronds-points
                4,      // supersamplingFactor : suréchantillonnage vectoriel SS=4
                90,     // cropMargin : marge de recadrage ROI (px)
                15000   // residualHoleMaxArea : seuil de comblement des îlots (px)
        );
    }
}
```

---

## 💻 4. Spécification Complète de la Ligne de Commande (CLI V2)

Le pipeline V2 est activé via le drapeau `--v2` passé à l'application CLI ou par la classe `V2CliRunner`.

### 4.1. Options d'Entrées / Sorties (I/O)

| Option | Alias | Type | Obligatoire | Valeur par défaut | Description |
| :--- | :--- | :--- | :---: | :--- | :--- |
| `--v2` | - | Drapeau | Oui | - | Active explicitement le moteur algorithmique V2. |
| `--map` | `-m` | Chemin | Oui | - | Fichier image cartographique source (`05_style_contraste_sans_rien.png`). |
| `--json` | `-j` | Chemin | Oui | - | Fichier JSON contenant la métadonnée et le polygone d'intention (`01_plan_avec_territoires.json`). |
| `--mode` | - | String | Non | `TERRITORY` | Mode d'opération : `TERRITORY` (détourage global) ou `ZONE` (partage médian). |
| `--output` | `-o` | Chemin | Non | `clipped.png` | Destination de l'image détourée RGBA finale. |
| `--mask-out` | `-mo` | Chemin | Non | `mask.png` | Destination du masque monochrome 8-bit. |
| `--overlay-out`| `-oo` | Chemin | Non | `overlay.png` | Destination de l'image de diagnostic avec tracé rouge. |
| `--osm-roads` | - | Chemin | Non | *Auto-détecté* | Fichier vectoriel GeoJSON/OSM des routes alternatives. |
| `--debug` | `-d` | Drapeau | Non | `false` | Exporte les étapes intermédiaires (`roads.png`, `cells.png`). |
| `--help` | `-h` | Drapeau | Non | - | Affiche le manuel d'aide détaillé et quitte l'application. |

### 4.2. Hyperparamètres Algorithmiques V2

| Option | Type | Défaut | Description technique & impact algorithmique |
| :--- | :--- | :--- | :--- |
| `--hi` | Décimal | `0.60` | Seuil de couverture au-delà duquel une cellule est retenue à 100% (`INSIDE`). |
| `--lo` | Décimal | `0.05` | Seuil de couverture en dessous duquel une cellule est exclue (`OUTSIDE`). |
| `--eps` | Décimal | `4.0` | Tolérance géodésique $\varepsilon$ en pixels sur la chaussée ($d_G \le 2 \cdot hw + \varepsilon$). |
| `--rho` | Entier | `5` | Rayon du disque morphologique régularisant la propagation géodésique. |
| `--sig` | Décimal | `22.0` | Échelle $\sigma$ de la régression quadratique locale robuste (LQR). |
| `--corner` | Décimal | `38.0` | Angle minimal en degrés déclenchant la détection d'un coin vif à figer. |
| `--l` | Entier | `80` | Demi-fenêtre $L$ de calcul des tangentes d'angles le long du contour. |
| `--r0` | Décimal | `35.0` | Rayon $R_0$ de gel strict du contour brut aux abords d'un coin vif. |
| `--r1` | Décimal | `95.0` | Rayon $R_1$ de fin du fondu progressif Hermite Smoothstep ($R_1 > R_0$). |
| `--zr` | Décimal | `2.0` | Rayon normalisé d'influence pour l'accrochage des ronds-points. |
| `--ss` | Entier | `4` | Facteur de suréchantillonnage vectoriel ($SS=4 \Rightarrow$ grille $16\times$). |
| `--margin` | Entier | `90` | Marge $mg$ de recadrage de la boîte englobante (ROI). |

### 4.3. Codes de Sortie du Processus (Exit Codes)
* **`0` :** Succès complet, images exportées avec succès.
* **`1` :** Erreur utilisateur (argument manquant, fichier introuvable, syntaxe invalide).
* **`2` :** Échec d'exécution du pipeline (incohérence géométrique, mémoire insuffisante, erreur I/O).

---

## 🧪 5. Critères de Validation & Stratégie de Test

Conformément à l'[ADR-007](../adr/ADR-007-developpement-par-sprints.md) et aux standards de test Java 21 :

### 5.1. Tests Unitaires (`com.sam102022.photoshop.v2.render` & `cli`)
* **`SupersampleRendererTest` :**
  * Validation géométrique sur formes analytiques simples : carré de $10 \times 10\text{ px}$ centré $\to$ couverture exactement égale à 255 à l'intérieur et 0 à l'extérieur ;
  * Transition sub-pixel sur segment oblique à $45^\circ$ : décroissance régulière et symétrique des niveaux de gris sans artefact de crénelage ;
  * Respect des bornes et du décalage $+0.5\text{ px}$ : aucun débordement de boîte englobante ;
  * Vérification de l'insertion correcte dans le canevas global avec offset `CropWindow`.
* **`ImageClipperTest` :**
  * Conservation rigoureuse des canaux de couleur RVB source ;
  * Application de la formule alpha : pixel à 50% de couverture $\to \alpha = 128$ ;
  * Génération de `mask.png` au format natif `TYPE_BYTE_GRAY` ;
  * Génération de `overlay.png` : présence exclusive de pixels `#FF0000` sur le périmètre de démarcation.
* **`V2ConfigTest` :**
  * Immutabilité et validation des invariants ($0 \le lo < hi \le 1$, $sig > 0$, $r1 > r0$, $ss \ge 1$) ;
  * Présence et exactitude des valeurs par défaut.
* **`V2CliRunnerTest` :**
  * Parsing nominal de la ligne de commande complète ;
  * Rejet propre avec message explicatif en français en cas de fichier absent ou d'argument malformé ;
  * Gestion du drapeau `--help` avec code de retour `0`.

### 5.2. Test d'Intégration Pivot CA01 (`Sprint7IntegrationTest`)
Exécution de bout en bout du pipeline complet sur le territoire de référence `CA01` :
* **Performance :** Temps d'exécution total du pipeline (Sprints 1 à 7 inclus) $\le 5{,}0\text{ secondes}$ sur machine de développement type.
* **Concordance métrique avec l'étalon Python V5 :**
  * $\text{Intersection over Union (IoU)} \ge 0{,}99$ par rapport à `CA01_mask_v5.png` ;
  * Écart relatif de surface couverte $< 0{,}5\%$ ;
  * Aucune fuite d'opacité vers les cellules `OUTSIDE` ;
  * Raccordement parfait des ronds-points sans saillie ni discontinuité.
* **Livrables :** Création effective sur disque des 3 fichiers `clipped.png`, `mask.png` et `overlay.png` directement vérifiables visuellement.
