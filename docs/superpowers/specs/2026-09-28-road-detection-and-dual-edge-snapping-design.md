# Spécification Technique : Détection Fine des Chaussées et Calage Géométrique Bi-Mode (INNER / OUTER)

- **Date :** 2026-09-28
- **Auteur :** Samuel / Assistant Gemini
- **Statut :** Validé
- **Langage / Environnement :** Java 21, Maven, Java2D, Swing (100% Java standard, zéro dépendance native)

---

## 1. Contexte & Problématique

L'application permet d'extraire des cartes Google Maps en calant les contours géométriques sur les axes routiers :
- En mode **Territoire** : le contour doit englober le territoire et ses voies limitrophes jusqu'au bord extérieur de la route.
- En mode **Zone** : le contour de la zone interne doit épouser le bord intérieur de la chaussée sans mordre sur la route.

L'analyse de l'implémentation existante a révélé deux lacunes géométriques et algorithmiques fondamentales :
1. **Omission des rues locales blanches et grises :**
   Dans `RoadCandidateDetector.java`, seules les autoroutes (orange) et routes secondaires (jaunes) étaient détectées. Les rues résidentielles et voies locales (qui constituent la majeure partie du réseau routier Google Maps et délimitent les zones internes) sont blanches ou gris clair neutre et étaient explicitement exclues (`roadScore == 0.0`).
2. **Inactivité du paramètre `roadSensitivity` :**
   La méthode `RoadDetector.detectRoads(mapImage, config)` appelait `RoadCandidateDetector.detect(mapImage)` sans jamais lui transmettre `config.roadSensitivity()`. Ce réglage présent dans le CLI et sur l'IHM était donc inopérant.
3. **Absence de distinction de bord dans `RoadSnapper` :**
   `RoadSnapper.java` balayait uniquement le vecteur normal jusqu'à l'extrémité du ruban routier (`roadEnd + 1`). Il n'existait aucun moyen de caler un sommet au bord intérieur (`roadStart - 1`) pour les zones, ni de rétracter un sommet situé sur la chaussée.

L'objectif de cette évolution est de corriger la détection des chaussées au niveau pixel et de doter `RoadSnapper` d'une distinction géométrique formelle entre bord intérieur (`INNER`) et bord extérieur (`OUTER`).

---

## 2. Architecture & Découpage Modulaire (SRP)

Conformément à l'**ADR-008 (Une seule responsabilité par classe)**, les responsabilités sont clarifiées et enrichies :

```
src/main/java/com/sam102022/photoshop/
├── core/
│   ├── detection/
│   │   ├── RoadCandidateDetector.java # MODIFIÉ : Détection hybride (couleurs majeures + rues blanches/grises avec roadSensitivity)
│   │   └── RoadDetector.java          # MODIFIÉ : Transmission effective de roadSensitivity
│   ├── geometry/
│   │   ├── SnapTargetEdge.java        # NOUVEAU : Énumération INNER (bord intérieur) et OUTER (bord extérieur)
│   │   └── RoadSnapper.java           # MODIFIÉ : Prise en charge de SnapTargetEdge et balayage bidirectionnel
│   └── segmentation/
│       ├── RoadSnappingEngine.java    # MODIFIÉ : Utilisation de SnapTargetEdge.OUTER pour le territoire
│       └── ZoneSegmentationEngine.java # MODIFIÉ : Utilisation de SnapTargetEdge.INNER pour la zone
```

---

## 3. Algorithmes Détaillés

### 3.1. Détection des Rues Blanches et Grises (`RoadCandidateDetector`)

La méthode de détection est paramétrée par la sensibilité :
`public BinaryMask detect(BufferedImage image, float roadSensitivity)`

Un pixel $(x, y)$ est classé comme candidat routier si :

#### A. Axes Majeurs et Secondaires Colorés (Rétrocompatibilité)
- **Autoroutes orange :** $R \ge 210, G \ge 130, B \le 165, R - G \ge 15$.
- **Routes jaunes :** $R \ge 220, G \ge 190, B \in [100..190], |R - G| \le 35, R - B \ge 40$.

#### B. Rues Locales Blanches et Grises Google Maps
- **Neutralité chromatique stricte :**
  $$\max(|R - G|, |R - B|, |G - B|) \le 10$$
  (élimine immédiatement la végétation verdâtre, les toits en tuile rouge/orange et l'eau bleutée).
- **Luminance de chaussée claire :**
  Calculée par la formule standard UIT-R BT.601 :
  $$Y = 0.299R + 0.587G + 0.114B$$
  Le seuil minimal est modulé par `roadSensitivity` :
  $$Y_{\min} = 240 - 15 \times (\text{roadSensitivity} - 1.0)$$
  Pour `sensitivity = 1.0` : $Y \ge 240$.
  Pour `sensitivity = 1.5` : $Y \ge 232$.
- **Validation du corridor routier (présence de bordures plus sombres) :**
  Pour ne pas classifier un grand bâtiment blanc uniforme comme une rue, on vérifie que dans une fenêtre transversale de 2 à 8 pixels autour du pixel clair, il existe une transition vers un niveau de gris plus sombre ($Y \le 215$).
- **Bordures de chaussée (casing gris) :**
  Les pixels neutres délimitant la chaussée ($Y \in [180..220]$ avec neutralité $\le 10$) sont également intégrés au ruban routier s'ils jouxtent un pixel de chaussée claire.

---

### 3.2. Recalage Géométrique Bi-Mode (`RoadSnapper`)

#### A. Énumération `SnapTargetEdge`
```java
public enum SnapTargetEdge {
    OUTER, // Bord extérieur du ruban (englobe la route, mode Territoire)
    INNER  // Bord intérieur du ruban (s'arrête au pied de la chaussée, mode Zone)
}
```

#### B. Analyse d'Intervalle Routier $[k_{\text{start}}, k_{\text{end}}]$ le long de la Normale
Pour un sommet $P_0 = (x_0, y_0)$ et sa normale extérieure unitaire $\vec{n} = (n_x, n_y)$ :

1. **Cas nominal : le sommet est hors de la chaussée ($\text{isRoad}(P_0) == \text{false}$)** :
   On balaye vers l'avant le long du rayon $k \in [1..\text{radius}]$ :
   - $k_{\text{start}}$ : premier pas où un pixel routier est rencontré avec support tangentiel continu.
   - $k_{\text{end}}$ : dernier pas consécutif appartenant à ce ruban routier.
   - **Positionnement :**
     * En mode `INNER` : $\text{targetStep} = \max(0, k_{\text{start}} - 1)$ (affleure le bord intérieur sans entrer sur la route).
     * En mode `OUTER` : $\text{targetStep} = \min(k_{\text{end}} + 1, \text{radius})$ (dépasse le bord extérieur pour englober la route).

2. **Cas particulier : le sommet est déjà sur la chaussée ($\text{isRoad}(P_0) == \text{true}$)** :
   - **En mode `OUTER` :** On avance vers l'avant jusqu'à la fin de la route ($k_{\text{end}} + 1$).
   - **En mode `INNER` :** Le point empiète sur la route. On effectue un balayage inverse le long du vecteur opposé $-\vec{n}$ pour trouver la sortie intérieure de la chaussée et reculer le sommet hors de la route.

---

## 4. Intégration dans les Moteurs d'Exécution

1. **`RoadDetector` :**
   ```java
   public BinaryMask detectRoads(BufferedImage mapImage, SnappingConfig config) {
       BinaryMask candidates = new RoadCandidateDetector().detect(mapImage, config.roadSensitivity());
       return config.closingRadius() == 0 ? candidates : MorphologyOps.close(candidates, config.closingRadius());
   }
   ```
2. **`RoadSnappingEngine` (Territoire) :**
   Appelle `RoadSnapper.snapContour(contour, candidates, W, H, radius, SnapTargetEdge.OUTER)`.
3. **`ZoneSegmentationEngine` (Zone) :**
   Après l'inondation de zone, affine le contour vectoriel avec `RoadSnapper.snapContour(contour, candidates, W, H, radius, SnapTargetEdge.INNER)`, garantissant un alignement net sur les bordures intérieures des rues, suivi du masquage strict à zéro des pixels routiers.

---

## 5. Stratégie de Validation & Tests (TDD)

### 5.1. Tests Unitaires
- `RoadCandidateDetectorTest` :
  - Détection des rues blanches ($R=G=B=245$) et grises ($R=G=B=210$).
  - Rejet des surfaces blanches uniformes sans bordures.
  - Test de modulation de `roadSensitivity` (0.5 vs 1.0 vs 1.5).
- `RoadSnapperTest` :
  - Test géométrique d'un carré face à une route de 4 px d'épaisseur :
    - `INNER` avance de $k_{\text{start}} - 1$.
    - `OUTER` avance de $k_{\text{end}} + 1$.
  - Test de rétraction arrière en mode `INNER` quand le sommet initial est sur la route.
- `SnapTargetEdgeTest` :
  - Validation de l'énumération `SnapTargetEdge`.

### 5.2. Tests d'Intégration et Non-Régression
- `IntegrationCliTest` :
  - Sample 01 : Maintien du comportement historique Territoire (détourage englobant les routes).
  - Sample 02 : Découpage de Zone 1 avec calage géométrique précis au bord des rues blanches locales délimitant la zone.
- Exécution de 100% de la suite de tests (`mvn test`).
- Respect strict des standards : Java 21 standard, Javadoc complète en français, complexité cognitive $\le 15$, zéro FQCN.
