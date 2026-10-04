# Sprint 9 (V2) — Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & Intégration CLI

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.render` / `com.sam102022.photoshop.v2.pipeline` / `com.sam102022.photoshop.cli`  
**Documents associés :**
* Spécification de conception : [`docs/superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md`](../superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md)
* Sommaire et planification V2 : [`docs/SPRINTS_V2.md`](../SPRINTS_V2.md)
* Script Python étalon : [`maps/python/snap_cells_prototype_v7.py`](../../maps/python/snap_cells_prototype_v7.py)
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

Le **Sprint 9** constitue le jalon final de parachèvement de l'architecture V2. Il remplit une double mission stratégique :

1. **Rendu Graphique Sub-Pixel Haute-Fidélité ($SS=4$) & Post-Affinage :**
   * Rastérisation vectorielle sub-pixel quadruplée ($SS=4$) de `SmoothVectorContour` (Sprint 7) sur la zone d'intérêt locale `CropWindow` avec décalage de demi-pixel ($+0.5\text{ px}$).
   * Réduction par filtre boîte (*Box Filter*) produisant une matrice locale continue $\alpha_{ss} \in [0.0 .. 1.0]$.
   * Modulation directe par la matrice d'affinage spectral `AlphaRefinementMap` (Sprint 8) : $\alpha_{\text{final}} = \alpha_{ss} \times \text{factor}$.
   * Injection dans le canevas global pour former le `CoverageMask` continu conforme à l'[ADR-002](../adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md).
   * Assemblage du trio officiel de livrables graphiques de production :
     * `clipped.png` : Image détourée RGBA 32-bit avec composition alpha exacte sans crénelage ;
     * `mask.png` : Masque monochrome 8-bit (`TYPE_BYTE_GRAY`) préservant les nuances $[0..255]$ ;
     * `overlay.png` : Image diagnostique de contrôle superposant le contour extérieur rouge vif (`#FF0000`) sur la carte source.
2. **Orchestration Globale & Point d'Entrée CLI V2 :**
   * Encapsuler l'ensemble des 9 étapes algorithmiques dans un orchestrateur unifié sans état (`V2Pipeline`), réutilisable à la fois par la CLI et par la future interface graphique Swing ([ADR-003](../adr/ADR-003-double-mode-d-execution-cli-et-gui-swing.md)).
   * Définir le record immuable de configuration `V2Config` ([ADR-004](../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md)) regroupant tous les hyperparamètres calibrés sur l'étalon Python V7.
   * Rendre le pipeline V2 accessible en ligne de commande via `V2CliRunner` (drapeau `--v2` ou commande dédiée), avec journalisation hiérarchisée en français ([ADR-009](../adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md)).

---

## 📦 2. Livrables & Composants Clés

```text
com.sam102022.photoshop
├── v2.pipeline
│   ├── V2Config.java                (Record immuable des hyperparamètres V2 unifiés)
│   └── V2Pipeline.java              (Orchestrateur de bout en bout Sprints 1 -> 9)
├── v2.render
│   ├── SupersampleRenderer.java     (Rastérisation SS=4 sur ROI + box filter + modulation d'affinage)
│   ├── ImageClipper.java            (Assemblage RGBA, export TYPE_BYTE_GRAY et overlay de contrôle)
│   └── RenderResult.java            (Record immuable regroupant clipped, mask, overlay et coverage)
└── cli
    └── V2CliRunner.java             (Parseur CLI, validation des drapeaux et exécution du pipeline)
```

### 2.1. `V2Config` (Record immuable de configuration)
Encapsule l'ensemble des seuils et paramètres du pipeline V2 complet (Sprints 1 à 8) avec validation des bornes et fabrique de valeurs par défaut :
* Sprints 1-4 : `hi` (0.60), `lo` (0.05), `cropMargin` (90).
* Sprint 5 : `eps` (4.0), `rho` (5), `residualHoleMaxArea` (15000).
* Sprint 6 : `sig` (22.0), `cornerAngle` (38.0), `cornerWindowL` (80), `r0` (35.0), `r1` (95.0), `roundaboutRadiusZr` (2.0).
* Sprint 7 : `straightFactor` (2.7), `straightScale` (3.0), `straightThreshold` (3.0), `straightTransitionK` (10).
* Sprint 8 : `gaussianBlurSigma` (1.4), `contrastStiffness` (2.0), `roundaboutBufferInner` (2.3), `roundaboutBufferOuter` (3.0), `roadHoleMaxArea` (2000).
* Sprint 9 : `supersamplingFactor` (4).

### 2.2. `SupersampleRenderer`
* Rastérisation sub-pixel sur ROI rognée $SS=4$ avec décalage de $+0.5\text{ px}$.
* Réduction par moyenne de boîte (*Box Filter* 16 échantillons).
* Application point par point du facteur d'affinage issu de `AlphaRefinementMap` : $\alpha_{\text{final}} = \alpha_{ss} \times \text{factor}$.
* Injection dans le `CoverageMask` aux coordonnées d'origine $(x_0, y_0)$ de la `CropWindow`.

### 2.3. `ImageClipper`
* `clipped.png` (ARGB 32-bit) : $\alpha_{\text{final}}(x, y) = \lfloor (\alpha_{\text{source}}(x, y) \times \text{coverage}(x, y) + 127) / 255 \rfloor$.
* `mask.png` (`TYPE_BYTE_GRAY`) : matrice de niveaux de gris directe.
* `overlay.png` (RGB 24-bit) : détection de bordure $\text{Edge} = M_{full} \land \neg\,\text{erode}(M_{full})$ (seuil 128) peinte en rouge `#FF0000`.

### 2.4. `V2Pipeline` (Orchestrateur de Production)
Coordonne l'ensemble des modules développés du Sprint 1 au Sprint 9 :
1. Projection cartographique du polygone d'intention $P$ (Sprint 1) ;
2. Détection spectrale des routes et fermeture topologique minimale (Sprint 2) ;
3. Recadrage boîte englobante, étiquetage 4-connexe des cellules urbaines et graphe d'adjacence (Sprint 3) ;
4. Vote topologique et classification des cellules (Sprint 4) ;
5. Reconstruction des barrières routières, propagation géodésique bornée et comblement des îlots (Sprint 5) ;
6. Extraction sub-pixel Marching Squares, détection des angles vifs, lissage LQR et modélisation des ronds-points (Sprint 6) ;
7. Lissage adaptatif multi-échelle des tronçons droits (Sprint 7) ;
8. Affinage spectral de la couverture alpha et protection des ronds-points (Sprint 8) ;
9. Supersampling sub-pixel $SS=4$, réduction box filter, modulation et assemblage RGBA (Sprint 9).

### 2.5. `V2CliRunner` (Interface CLI)
Point d'entrée CLI dédié supportant le drapeau `--v2`, les options de configuration et la journalisation en français.

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

---

## 🧪 4. Critères de Validation & Tests (Test Pivot `CA01`)

* **Validation unitaire :**
  * Exactitude du box filter sur motif alterné.
  * Respect strict de la formule de composition alpha ARGB sans distorsion des couleurs d'origine.
  * Génération conforme du masque monochrome 8-bit et de l'overlay rouge.
* **Test d'intégration pivot sur Territoire CA01 ($3810 \times 2130\text{ px}$) :**
  * Concordance avec `CA01_clipped_v7.png` et `CA01_mask_v7.png` : $\text{IoU} \ge 0.99$.
  * Temps d'exécution total de bout en bout (Sprints 1 à 9) $\le 2.5\text{ s}$.
