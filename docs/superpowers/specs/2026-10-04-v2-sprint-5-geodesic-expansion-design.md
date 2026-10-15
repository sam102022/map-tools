# Spécification Technique V2 — Sprint 5 : Reconstruction des Frontières Routières & Expansion Géodésique Matricielle

**Date :** 04 octobre 2026  
**Auteur :** Équipe Photoshop Map Snapping  
**Statut :** Spécification Validée  
**Document cible :** `docs/superpowers/specs/2026-10-04-v2-sprint-5-geodesic-expansion-design.md`  
**Conformité ADR :** ADR-001 (100% Java standard sans lib native), ADR-002 (Modèle matriciel hybride), ADR-004 (Immutabilité via Records), ADR-006 (Spécification préalable), ADR-007 (Découpage par sprints), ADR-008 (Responsabilité unique SRP), ADR-009 (Logs hiérarchisés), ADR-011 (Complexité cognitive <= 15), ADR-012 (Imports explicites), ADR-014 (Javadoc intégrale en FR).

---

## 1. Contexte & Enjeux Métier

Dans l'architecture V2 du découpage topologique ([docs/SPRINTS_V2.md](../../SPRINTS_V2.md) et [docs/SPEC_V2_ALGORITHME.md](../../SPEC_V2_ALGORITHME.md)), le **Sprint 5** réalise la transition fondamentale entre la sélection discrète des îlots urbains (produite au Sprint 4 avec le masque $T$) et l'extraction continue du contour sub-pixel (opérée au Sprint 6).

### Le défi algorithmique de la bordure routière
Les parcelles intérieures sélectionnées ($T$) s'arrêtent au bord intérieur des trottoirs : la surface de la chaussée appartient au réseau routier ($R_c$). Le polygone géographique réel d'un territoire cadastral englobe généralement la voirie mitoyenne jusqu'à son bord extérieur (ou jusqu'à son axe médian en cas de mitoyenneté entre deux zones contiguës).

```text
               TERRITOIRE EXTÉRIEUR (OUTSIDE)
════════════════════════════════════════════════════════════  Bord extérieur de la route
▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ CHAUSSÉE (RoadMask Rc) ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒  Épaisseur 2 * hw
────────────────────────────────────────────────────────────  Bord intérieur (trottoir)
████████████████████ CELLULE INTÉRIEURE (INSIDE) ███████████  Masque T (Sprint 4)
```

L'objectif du Sprint 5 est de reconstruire un masque binaire plein, lisse et étanche **`ConsolidatedMask`** ($M_c$) en résolvant trois problèmes cruciaux :
1. **Intégration contrôlée de l'épaisseur des routes de bordure :**  
   Propager le territoire à travers la chaussée limitrophe jusqu'à sa bordure extérieure réelle sans jamais "déborder" ni "fuiter" le long des rues transversales extérieures qui s'éloignent du territoire.
2. **Régularisation morphologique sans altération des blocs intérieurs :**  
   Lisser les indentations de la propagation routière par une ouverture morphologique circulaire sans raboter ni arrondir les angles authentiques des parcelles intérieures.
3. **Résolution des îlots résiduels et ronds-points :**  
   Détecter et combler automatiquement les cours fermées, îlots routiers et centres de ronds-points compacts ($\text{surface} \le 15\,000\text{ px}$).

---

## 2. Décisions Architecturales & Levée des Ambiguïtés

### 2.1 Levée de l'ambiguïté : `BoundaryRoadExtractor` vs Expansion Globale Bornée
* **Analyse de la divergence :** La note d'intention initiale prévoyait de contraindre la propagation aux seules composantes de routes identifiées comme frontières par le graphe `CellGraph`. En pratique, le prototype étalon (`maps/python/snap_cells_prototype_v5.py`) démontre qu'une propagation géodésique Dijkstra (MCP) opérée sur le masque local $R_c$, amorcée depuis le bord de $T$ et **strictement bornée par la demi-largeur locale** ($d_G(p) \le 2 \times hw_{\max}(p) + \varepsilon$), résout naturellement l'étanchéité aux carrefours :
  * Les voies perpendiculaires extérieures ont une demi-largeur locale typique $hw \in [6, 12]\text{ px}$.
  * La condition d'arrêt interrompt la marche dès que la distance dépasse $2 \times hw + \varepsilon \approx 16\text{ à } 28\text{ px}$, bloquant instantanément toute fuite dans les rues transversales sans exiger un partitionnement topologique artificiel des carrefours.
* **Décision retenue :**
  1. En mode **`TERRITORY`**, l'expansion est conduite sur le masque local de voirie $R_c$ complet via `GeodesicRoadExpander`, piloté par la borne de distance locale $2 \times hw_{\max} + \varepsilon$. Le graphe `CellGraph` et les `RoadInterface` sont exploités pour la validation analytique et le reporting diagnostique (`BoundaryRoadReport`).
  2. En mode **`ZONE`**, la composante `BoundaryRoadPartitioner` utilise explicitement les `RoadInterface` reliant deux zones disjointes (`cellA ∈ Zone1`, `cellB ∈ Zone2`) pour effectuer un partage équitable le long de la ligne de partage des eaux (Voronoi géodésique) sans aucun recouvrement.

### 2.2 Algorithmes 100% Java Pur en Temps Linéaire $O(N)$ (ADR-001)
Le calcul s'opère sur la boîte englobante locale ($1505 \times 1783 = 2\,683\,415\text{ pixels}$ sur CA01). Les algorithmes sont rigoureusement conçus pour garantir un budget temps total $\le 1200\text{ ms}$ :
* **Transformée de distance euclidienne (EDT) en $O(N)$ :**  
  Implémentation de l'algorithme séparable exact de **Meijster, Roerdink et Hesselink (2000)**. En deux passes 1D linéaires indépendantes (colonnes puis lignes avec enveloppes paraboliques), la carte de distance euclidienne exacte $hw(x, y)$ est calculée en ~40 ms sans aucune dépendance native.
* **Filtre maximum local séparable en $O(N)$ :**  
  Le calcul de $hw_{\max}(x, y)$ sur une fenêtre carrée de taille $41 \times 41$ ($k = 20\text{ px}$) est décomposé en deux filtres glissants 1D séparables (horizontal puis vertical) utilisant une **file monotone à double extrémité (algorithme de Lemire)**. La complexité est strictement $O(1)$ amortie par pixel, réduisant le temps d'exécution à ~35 ms (contre plus de 15 secondes pour un balayage naïf).
* **Parcours Dijkstra 8-connexe optimisé :**  
  File de priorité ordonnée sur le coût géodésique accumulé avec indexation directe sur tableau linéaire 1D (`double[] dist` de taille $W \times H$). La métrique géométrique utilise $1.0$ pour les voisins cardinaux et $\sqrt{2} \approx 1.41421356$ pour les diagonales.

### 2.3 Sanctuarisation des Cellules Intérieures ($T$)
L'ouverture morphologique par un disque de rayon $\rho = 5\text{ px}$ appliquée sur $U = T \cup ext$ arrondirait irrémédiablement les angles authentiques des parcelles intérieures de $T$. La formulation retenue garantit la sanctuarisation absolue de $T$ :
$$M_c = T \cup \text{opening}(T \cup ext, \text{disk}(\rho))$$
Les parcelles retenues par le vote du Sprint 4 ne subissent aucune dégradation géométrique. Seule l'extension routière extérieure ($ext$) est régularisée.

---

## 3. Architecture des Composants & Modèle de Données

Le package cible est **`com.sam102022.photoshop.v2.expansion`**.

```text
com.sam102022.photoshop.v2.expansion/
├── BoundedGeodesicExpander.java         # Moteur de propagation Dijkstra bornée (MCP)
├── BoundaryRoadPartitioner.java         # Partage médian équitable en mode ZONE
├── ConsolidatedMask.java                # Contrat de domaine immuable de sortie
├── DistanceMap.java                     # Matrice float[] de distances euclidiennes
├── EuclideanDistanceTransform.java      # Algorithme linéaire Meijster O(N)
├── ExpansionConfig.java                 # Record de configuration et hyperparamètres
├── LocalMaxFilter.java                  # Filtre max 1D séparable de Lemire O(N)
├── MorphologicalConsolidator.java       # Union, padding et ouverture par disque
├── ResidualHoleResolver.java            # Inondation inverse et comblement <= 15 000 px
└── RoadBoundaryConsolidator.java        # Orchestrateur de haut niveau du Sprint 5
```

### 3.1 Contrat de Configuration (`ExpansionConfig`)
Record immuable encapsulant les hyperparamètres du pipeline :
```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.OperationMode;

/**
 * Configuration immuable pour l'expansion géodésique et la consolidation matricielle.
 *
 * @param epsilon              Marge de sécurité géométrique en pixels (défaut : 4.0).
 * @param maxFilterSize        Taille de la fenêtre carrée du filtre maximum local (défaut : 41).
 * @param openingDiskRadius    Rayon de l'élément structurant circulaire d'ouverture (défaut : 5).
 * @param maxHoleArea          Surface maximale des îlots compacts comblés (défaut : 15 000 px).
 * @param dilationSeedSteps    Nombre d'itérations de dilatation pour les graines d'amorce (défaut : 2).
 * @param operationMode        Mode d'opération (TERRITORY ou ZONE).
 */
public record ExpansionConfig(
        double epsilon,
        int maxFilterSize,
        int openingDiskRadius,
        long maxHoleArea,
        int dilationSeedSteps,
        OperationMode operationMode
) {
    public static final double DEFAULT_EPSILON = 4.0;
    public static final int DEFAULT_MAX_FILTER_SIZE = 41;
    public static final int DEFAULT_OPENING_RADIUS = 5;
    public static final long DEFAULT_MAX_HOLE_AREA = 15_000L;
    public static final int DEFAULT_SEED_DILATION_STEPS = 2;

    public static ExpansionConfig defaultTerritory() {
        return new ExpansionConfig(
                DEFAULT_EPSILON,
                DEFAULT_MAX_FILTER_SIZE,
                DEFAULT_OPENING_RADIUS,
                DEFAULT_MAX_HOLE_AREA,
                DEFAULT_SEED_DILATION_STEPS,
                OperationMode.TERRITORY
        );
    }

    public static ExpansionConfig defaultZone() {
        return new ExpansionConfig(
                DEFAULT_EPSILON,
                DEFAULT_MAX_FILTER_SIZE,
                DEFAULT_OPENING_RADIUS,
                DEFAULT_MAX_HOLE_AREA,
                DEFAULT_SEED_DILATION_STEPS,
                OperationMode.ZONE
        );
    }
}
```

### 3.2 Matrice de Distances (`DistanceMap`)
Structure optimisée stockant les valeurs réelles continues sous forme d'un tableau unidimensionnel primitif (`float[]`) évitant toute allocation d'objets :
```java
package com.sam102022.photoshop.v2.expansion;

/**
 * Matrice 2D continue de distances réelles stockée sur tableau plat float[].
 *
 * @param width  Largeur de la matrice.
 * @param height Hauteur de la matrice.
 * @param data   Tableau linéaire de dimensions width * height.
 */
public record DistanceMap(
        int width,
        int height,
        float[] data
) {
    public DistanceMap {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensions invalides pour DistanceMap.");
        }
        if (data == null || data.length != width * height) {
            throw new IllegalArgumentException("Taille du tableau data incohérente avec width * height.");
        }
    }

    public float get(int x, int y) {
        return data[y * width + x];
    }

    public void set(int x, int y, float value) {
        data[y * width + x] = value;
    }
}
```

### 3.3 Contrat de Sortie Officiel (`ConsolidatedMask`)
Record immuable officiel échangé vers le Sprint 6 :
```java
package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.CropWindow;

/**
 * Masque matriciel binaire consolidé après intégration des routes frontières,
 * régularisation morphologique et résolution des îlots résiduels.
 *
 * @param width      Largeur du masque local.
 * @param height     Hauteur du masque local.
 * @param mask       Masque binaire étanche (1 = territoire consolidé, 0 = extérieur).
 * @param cropWindow Fenêtre de recadrage d'origine par rapport à l'image cartographique globale.
 */
public record ConsolidatedMask(
        int width,
        int height,
        BinaryMask mask,
        CropWindow cropWindow
) {
    public ConsolidatedMask {
        if (mask.getWidth() != width || mask.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions du masque incohérentes avec width/height.");
        }
        if (cropWindow.width() != width || cropWindow.height() != height) {
            throw new IllegalArgumentException("Dimensions de CropWindow incohérentes avec le masque local.");
        }
    }

    /**
     * @return Décalage horizontal (X) par rapport au repère global de l'image.
     */
    public int offsetX() {
        return cropWindow.x0();
    }

    /**
     * @return Décalage vertical (Y) par rapport au repère global de l'image.
     */
    public int offsetY() {
        return cropWindow.y0();
    }
}
```

---

## 4. Spécification Détaillée des Étapes Algorithmiques

```text
CellSelection.retainedMask (T) ──┐
Cropped RoadMask (Rc) ───────────┼──► [Étape 1 : EDT Meijster O(N)] ──► hw(x, y)
                                 │              │
                                 │              ▼
                                 │    [Étape 2 : Max Filter Lemire O(N)] ──► hwm(x, y)
                                 │              │
                                 ▼              ▼
                               [Étape 3 : Dijkstra 8-connexe borné] ──► ext(x, y)
                                                │
                                                ▼
                               [Étape 4 : Morpho Consolidation] ────► U = T | ext
                                      Mc = T | Opening(U, disk(5))
                                                │
                                                ▼
                               [Étape 5 : Comblement trous <= 15000] ──► ConsolidatedMask
```

### Étape 1 : Transformée de Distance Euclidienne Exacte $O(N)$ (`EuclideanDistanceTransform`)
* **Objectif :** Calculer pour chaque pixel où $R_c(x, y) = 1$ sa distance euclidienne minimale $hw(x, y)$ au bord le plus proche de la chaussée (pixel où $R_c = 0$).
* **Formulation mathématique :**
  $$hw(x, y) = \begin{cases} \min_{(x', y') \notin R_c} \sqrt{(x - x')^2 + (y - y')^2} & \text{si } R_c(x, y) = 1 \\ 0 & \text{si } R_c(x, y) = 0 \end{cases}$$
* **Algorithme de Meijster (séparable en 2 passes) :**
  1. *Passe 1 (verticale) :* Pour chaque colonne $x \in [0, W-1]$, calculer la distance au carré au zéro vertical le plus proche :
     $$G(x, y) = \min_{y'} \left( (y - y')^2 \right) \quad \text{tel que } R_c(x, y') = 0$$
     Effectué en deux balayages simples (haut-bas puis bas-haut) en temps $O(H)$.
  2. *Passe 2 (horizontale) :* Pour chaque ligne $y \in [0, H-1]$, déterminer l'enveloppe inférieure des paraboles :
     $$F(x, y) = \min_{x'} \left( (x - x')^2 + G(x', y) \right)$$
     Géré par maintien d'une liste de paraboles actives et de leurs points d'intersection en temps $O(W)$.
  3. Extraction finale : $hw(x, y) = \sqrt{F(x, y)} \cdot R_c(x, y)$.

### Étape 2 : Filtrage du Maximum Local Séparable $O(N)$ (`LocalMaxFilter`)
* **Objectif :** Estimer la demi-largeur locale maximale $hw_{\max}(x, y)$ sur une fenêtre carrée centrée de taille $41 \times 41$ ($k = 20\text{ px}$) pour tolérer les variations locales de largeur et les îlots centraux sans discontinuité.
* **Algorithme de Lemire (Monotonic Deque 1D) :**
  1. La fenêtre 2D étant séparable : $\max_{u, v} hw(x+u, y+v) = \max_u \left( \max_v hw(x+u, y+v) \right)$.
  2. Passe 1 : Appliquer le filtre glissant 1D de fenêtre 41 sur chaque ligne horizontale indépendamment.
  3. Passe 2 : Appliquer le filtre glissant 1D de fenêtre 41 sur chaque colonne verticale du résultat intermédiaire.
  4. Masquage strict par la voirie : $hw_{\max}(x, y) = hw_{\max}(x, y) \times R_c(x, y)$.

### Étape 3 : Propagation Géodésique Dijkstra Bornée (`BoundedGeodesicExpander`)
* **Objectif :** Propager le territoire $T$ dans l'épaisseur de la chaussée limitrophe jusqu'à sa lisière extérieure réelle.
* **Initialisation des Graines (*Seeds*) :**
  1. Dilater le masque des cellules sélectionnées $T$ de 2 itérations en 8-connexité : $T_{\text{dilated}} = \text{dilate}(T, 2)$.
  2. Définir les germes routiers : $\text{seeds} = R_c \cap T_{\text{dilated}}$.
* **Front de propagation 8-connexe (MCP / Dijkstra) :**
  * Structure : file de priorité de nœuds `(x, y, cost)` ordonnée par coût croissant.
  * Tableau `double[] dist` initialisé à $+\infty$ sur l'ensemble de la grille $W \times H$.
  * Pour chaque pixel germe $(x, y) \in \text{seeds}$ :
    $$\text{dist}[x, y] = 0.0, \quad \text{queue.offer}(x, y, 0.0)$$
  * **Boucle de propagation :**
    Tant que la file n'est pas vide :
    1. Dépiler le pixel courant $p = (x, y)$ avec son coût $d_G(p)$. Si $d_G(p) > \text{dist}[p]$, ignorer.
    2. Pour chacun des 8 voisins $q \in \mathcal{N}_8(p)$ :
       * Si $R_c(q) == 0$ (hors voirie), transition interdite.
       * Coût d'arête : $w(p, q) = 1.0$ (cardinal) ou $\sqrt{2}$ (diagonal).
       * Nouveau coût candidat : $d_{\text{cand}} = d_G(p) + w(p, q)$.
       * Borne locale d'arrêt : $\text{bound}(q) = 2 \times hw_{\max}(q) + \varepsilon$ (avec $\varepsilon = 4.0\text{ px}$).
       * Si $d_{\text{cand}} \le \text{bound}(q)$ et $d_{\text{cand}} < \text{dist}[q]$ :
         $$\text{dist}[q] = d_{\text{cand}}, \quad \text{queue.offer}(q, d_{\text{cand}})$$
* **Masque d'extension routière ($ext$) :**
  $$ext(x, y) = 1 \iff (R_c(x, y) == 1) \land (\text{dist}[x, y] \le 2 \times hw_{\max}(x, y) + \varepsilon)$$

### Étape 4 : Régularisation Morphologique avec Sanctuarisation (`MorphologicalConsolidator`)
* **Union brute :** $U = T \cup ext$.
* **Marge de sécurité (*Padding*) :**
  Pour éviter tout artefact sur les bordures de la fenêtre ROI, appliquer un padding miroir ou nul de $\text{pad} = \rho + 2 = 7\text{ px}$ autour de $U$.
* **Ouverture par élément structurant circulaire ($\rho = 5\text{ px}$) :**
  * L'élément structurant est un disque euclidien $B$ de rayon 5 ($dx^2 + dy^2 \le 25$).
  * L'ouverture $\text{open}(U, B) = \text{dilate}(\text{erode}(U, B), B)$ élimine les fines excroissances et indentations issues de la propagation discrète.
* **Sanctuarisation de $T$ :**
  $$M_c^{\text{morpho}} = T \cup \text{open}(U, B)$$

### Étape 5 : Résolution des Îlots Résiduels & Ronds-points (`ResidualHoleResolver`)
* **Objectif :** Combler les cours fermées, terre-pleins centraux et centres d'îlots giratoires de surface compacte ($\le 15\,000\text{ px}$).
* **Algorithme d'inondation inverse :**
  1. Inversion du masque : $V = \neg M_c^{\text{morpho}}$.
  2. Identifier l'extérieur infini (*true background*) : effectuer un parcours en largeur (BFS) sur $V$ en injectant l'ensemble des pixels du périmètre extérieur (les 4 bordures $x=0$, $x=W-1$, $y=0$, $y=H-1$).
  3. L'ensemble des pixels de $V$ non atteints par l'extérieur infini constitue les cavités fermées :
     $$\text{holes} = V \setminus \text{background}$$
  4. Étiqueter les composantes connexes disjointes de $\text{holes}$ en 4-connexité.
  5. Pour chaque composante $H_k$ :
     $$\text{si } \text{Surface}(H_k) \le 15\,000\text{ px} \implies M_c \gets M_c \cup H_k$$
  6. Si $\text{Surface}(H_k) > 15\,000\text{ px}$, la composante est préservée intacte (trou légitime ou enclave extérieure majeure).

---

## 5. Mode `ZONE` : Partage Médian Équitable (`BoundaryRoadPartitioner`)

Lorsque deux zones adjacentes ($Z_1$ et $Z_2$) se partagent une voie mitoyenne, le mode `ZONE` garantit l'invariance stricte $Z_1 \cap Z_2 = \emptyset$.
1. **Extraction des interfaces mitoyennes :**  
   Identifier dans le graphe `CellGraph` toutes les `RoadInterface` séparant une cellule de $Z_1$ d'une cellule de $Z_2$.
2. **Voronoi Géodésique à Double Front :**  
   * Lancer simultanément deux fronts Dijkstra dans la surface de ces interfaces routières :
     * Front 1 amorcé depuis le bord de $Z_1$ ;
     * Front 2 amorcé depuis le bord de $Z_2$.
   * Chaque pixel routier $p$ est attribué à la zone dont la distance d'accès géodésique est strictement inférieure :
     $$p \in Z_1 \iff d_{G, Z_1}(p) < d_{G, Z_2}(p)$$
   * Les pixels à équidistance exacte forment la ligne médiane neutre de séparation.

---

## 6. Métriques de Référence & Critères de Validation sur CA01

Le cas étalon officiel est le territoire **CA01** issu des fixtures de référence :
* Fichier JSON : `src/test/resources/v2/fixtures/CA01/01_plan_avec_territoires.json`
* Masque de routes : `maps/road.png`
* Fenêtre de recadrage : $1505 \times 1783\text{ px}$ (offset $x_0 = 1732$, $y_0 = 874$)
* Masque de référence étalon Python : `maps/python/CA01_mask_v5.png`

| Métrique | Valeur Cible Attendue | Tolérance / Seuil d'Acceptation |
| :--- | :--- | :--- |
| **Surface cellules retenues $T$ (Sprint 4)** | ~1 047 800 px | Concordance déterministe |
| **Surface $M_c$ consolidée (Sprint 5)** | **1 215 383 px** | $\ge 99{,}7\%$ de recouvrement avec `CA01_mask_v5.png` |
| **Nombre d'îlots résiduels comblés** | Comblement exact des 3 îlots compacts intérieurs | $100\%$ des trous $\le 15\,000\text{ px}$ comblés |
| **Étanchéité extérieure** | 0 fuite dans les rues perpendiculaires | Débordement extérieur nul sur les parcelles OUTSIDE |
| **Temps d'exécution global Sprint 5** | **$\le 1200\text{ ms}$** sur $1505 \times 1783$ px | Seuil maximal admissible : $1500\text{ ms}$ |
| **Taux de succès des tests unitaires & intégration** | **100% de réussite** | 0 régression sur l'ensemble de la suite V2 |
