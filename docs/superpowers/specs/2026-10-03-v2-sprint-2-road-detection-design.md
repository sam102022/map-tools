# Spécification Technique V2 — Sprint 2 : Détection Colorimétrique des Routes & Fermeture Topologique

**Date :** 03 octobre 2026  
**Auteur :** Équipe Photoshop Map Snapping  
**Statut :** Validé  
**Document cible :** `docs/superpowers/specs/2026-10-03-v2-sprint-2-road-detection-design.md`  
**Conformité ADR :** ADR-001, ADR-002, ADR-004, ADR-007, ADR-008, ADR-009, ADR-011, ADR-012, ADR-014.

---

## 1. Contexte & Objectif

Dans l'architecture V2 du découpage topologique cartographique (défini dans [docs/SPRINTS_V2.md](../../SPRINTS_V2.md) et [docs/SPEC_V2_ALGORITHME.md](../../SPEC_V2_ALGORITHME.md)), le **Sprint 2** a pour responsabilité d'extraire la surface des chaussées et de garantir leur étanchéité topologique.

Le résultat produit par ce sprint est le masque `RoadMask`, composé :
1. D'un masque binaire brut `raw` correspondant à l'extraction directe des chaussées ;
2. D'un masque binaire fermé `closed` garantissant l'étanchéité continue des frontières routières (fermeture des coupures d'anti-aliasing et suppression des bruits ponctuels).

Ce masque `closed` sert de frontière imperméable pour le calcul des composantes connexes non routières ($\neg R_{closed}$) du Sprint 3 (cellules et `CellGraph`).

---

## 2. Architecture & Pipeline de Données

Le package cible pour l'ensemble des livrables de ce sprint est `com.sam102022.photoshop.v2.road`.

```text
                               ┌─────────────────────────┐
                               │  BufferedImage source   │
                               │  (carte Google Maps)    │
                               └────────────┬────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │   RoadDetectorStyle     │
                               └────────────┬────────────┘
                                            │ (BinaryMask raw)
                                            ▼
┌─────────────────────────┐    ┌─────────────────────────┐
│     osm_roads.json      │───►│     RoadMaskCleaner     │
│           +             │    │                         │
│    RoadDetectorOsm      │    │  1. binary_closing(1px) │
└─────────────────────────┘    │  2. binary_opening(2x2) │
                               └────────────┬────────────┘
                                            │
                                            ▼
                               ┌─────────────────────────┐
                               │     record RoadMask     │
                               │  - raw: BinaryMask      │
                               │  - closed: BinaryMask   │
                               └─────────────────────────┘
```

---

## 3. Modèles de Données & Contrats d'Interface

### 3.1 Contrat de Sortie Sprint 2 ➔ Sprint 3 : `RoadMask`

Conformément à la planification V2, `RoadMask` est un Java Record immuable (ADR-004) :

```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Contrat immuable représentant le masque des routes en version brute et fermée topologiquement.
 *
 * @param width  Largeur de la matrice en pixels (> 0).
 * @param height Hauteur de la matrice en pixels (> 0).
 * @param raw    Masque brut issu de l'extraction (sans modification topologique).
 * @param closed Masque après fermeture morphologique garantissant l'étanchéité des voies.
 */
public record RoadMask(
        int width,
        int height,
        BinaryMask raw,
        BinaryMask closed
) {
    public RoadMask {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Les dimensions doivent être strictement positives : " + width + "x" + height);
        }
        if (raw == null || closed == null) {
            throw new IllegalArgumentException("Les masques raw et closed ne peuvent pas être null.");
        }
        if (raw.getWidth() != width || raw.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour raw : " + raw.getWidth() + "x" + raw.getHeight());
        }
        if (closed.getWidth() != width || closed.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incohérentes pour closed : " + closed.getWidth() + "x" + closed.getHeight());
        }
    }
}
```

### 3.2 Interface Abstraite : `RoadDetector`

Afin de permettre l'interchangeabilité ou la fusion future (`STYLE`, `OSM`, `HYBRID`), une interface normalise la détection :

```java
package com.sam102022.photoshop.v2.road;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.awt.image.BufferedImage;

/**
 * Contrat générique d'un détecteur de surface routière.
 */
public interface RoadDetector {
    /**
     * Détecte les pixels de route à partir de l'image source.
     *
     * @param image Image cartographique source.
     * @return Masque binaire brut des routes identifiées.
     */
    BinaryMask detect(BufferedImage image);
}
```

---

## 4. Spécification des Composants

### 4.1 `RoadDetectorStyle`

Extrait les axes routiers à partir de l'image cartographique contrastée (`05_style_contraste_sans_rien.png`).

* **Règles colorimétriques de référence (issues de `../../../maps/python/script.py`) :**
  Pour chaque pixel aux coordonnées $(x, y)$, avec $R, G, B \in [0, 255]$ :
  ```text
  isRoad = (B - R >= 12)
        && (B - G >= 2)
        && (B - G <= 22)
        && (R < 228)
  ```
* **Performance et implémentation :**
  * Extraction directe du tableau entier de pixels via `image.getRGB(0, 0, width, height, pixels, 0, width)`.
  * Parcours séquentiel sans allocations mémoire intermédiaires dans la boucle critique.
  * Complexité temporelle $O(W \times H)$, temps d'exécution $< 100\text{ ms}$ sur une image $3810 \times 2130$.

### 4.2 `RoadMaskCleaner`

Applique les opérations de morphologie mathématique nécessaires à la constitution de `RoadMask` :

1. **Fermeture morphologique minimale (rayon 1 px) :**
   * Dilatation de rayon 1 suivie d'une érosion de rayon 1.
   * Rôle : colmater les coupures d'un pixel introduites par l'anti-aliasing ou le rendu vectoriel de la carte Google, assurant ainsi la séparation étanche des cellules complémentaires.
2. **Ouverture morphologique 2×2 (structurant `np.ones((2, 2))`) :**
   * Érosion 2×2 suivie d'une dilatation 2×2.
   * Rôle : éliminer les artefacts fins ponctuels (pixels isolés, résidus d'icônes ou de labels).
3. **Construction du résultat :**
   * Retourne l'instance `RoadMask(width, height, raw, closed)`.

### 4.3 `RoadDetectorOsm`

Génère un masque routier à partir des géométries vectorielles OpenStreetMap (`osm_roads.json`).

* **Parsing JSON autonome :**
  * Parseur récursif intégré sans dépendance tierce (conforme à l'ADR-001).
* **Projection Web Mercator :**
  * Réutilisation du composant V2 `WebMercatorProjection` et du `MapContext` pour projeter chaque point GPS $(lat, lon)$ en coordonnée pixel locale $(x, y)$.
* **Typologie et largeur de voie :**
  * Largeur calculée en mètres à partir des tags `width`, `lanes` ou de la classe `highway` :
    * `motorway`, `trunk` : 2 voies par défaut, 3.6 m / voie ;
    * `primary`, `secondary` : 2 voies par défaut, 3.3 m / voie ;
    * `tertiary`, `unclassified`, `residential`, `living_street`, `service` : 1 voie par défaut, 3.0 m / voie.
  * Conversion en pixels via l'échelle locale :
    $$\text{metersPerPixel} = \frac{\cos(\text{centerLat}) \times 40\,075\,016.686}{256 \times 2^{\text{zoom}}}$$
    $$\text{strokeWidthPixels} = \max\left(1.0, \frac{\text{widthMeters}}{\text{metersPerPixel}}\right)$$
* **Rasterisation Java2D :**
  * Dessin sur `BufferedImage(TYPE_BYTE_BINARY)` avec `BasicStroke(strokeWidth, CAP_ROUND, JOIN_ROUND)` puis conversion en `BinaryMask`.

---

## 5. Stratégie de Validation et Critères d'Acceptation

### 5.1 Tests Unitaires TDD
* `RoadMaskTest` : vérification des validations et immutabilité.
* `RoadDetectorStyleTest` : validation sur palette de couleurs synthétiques (bleu-gris routier, vert, blanc, eau saturée, seuil de rouge).
* `RoadMaskCleanerTest` : validation de la fermeture des discontinuités 1 px et suppression des pixels isolés.
* `RoadDetectorOsmTest` : validation de la projection vectorielle OSM et du calcul des largeurs.

### 5.2 Test d'Intégration Pivot CA01 (`Sprint2IntegrationTest`)
* Exécution complète sur l'image étalon `05_style_contraste_sans_rien.png` de `src/test/resources/v2/fixtures/CA01/`.
* Comparaison avec le fichier témoin de référence `maps/road.png` généré par `script.py`.
* Critère quantitatif : concordance de classification $> 99.8\%$ et préservation intégrale des axes secondaires.
* Temps d'exécution de la détection et du nettoyage : $\le 1.0\text{ s}$.

---

## 6. Revue de Spécification (Self-Review)

- [x] **Placeholder scan :** Aucun TODO, TBD ou paramètre indéfini.
- [x] **Internal consistency :** Architecture alignée avec `SPRINTS_V2.md` et les ADRs (ADR-001, ADR-004, ADR-008, ADR-011, ADR-014).
- [x] **Scope check :** Délimité strictement au périmètre du Sprint 2 (détection colorimétrique, rasterisation OSM, nettoyage morphologique, record `RoadMask`).
- [x] **Ambiguity check :** Formules colorimétriques et paramètres de morphologie explicités sans ambiguïté.
