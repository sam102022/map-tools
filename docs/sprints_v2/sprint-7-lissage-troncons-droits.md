# Sprint 7 (V2) — Lissage Adaptatif Multi-Échelle des Tronçons Droits (LQR Élargi)

**Statut :** ⏳ À venir  
**Package cible :** `com.sam102022.photoshop.v2.contour`  
**Documents associés :**
* Spécification de conception : [`docs/superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md`](../superpowers/specs/2026-10-04-v2-sprints-7-8-9-python-v7-alignment-design.md)
* Sommaire et planification V2 : [`docs/SPRINTS_V2.md`](../SPRINTS_V2.md)
* Script Python étalon : [`maps/python/snap_cells_prototype_v7.py`](../../maps/python/snap_cells_prototype_v7.py) (Étape 7)
* Architecture Decision Records associés :
  * [`ADR-001`](../adr/ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md) : Socle 100% Java standard sans dépendances natives.
  * [`ADR-004`](../adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md) : Immutabilité de la configuration via records.
  * [`ADR-008`](../adr/ADR-008-une-seule-responsabilite-par-classe.md) : Une seule responsabilité par classe (SRP).
  * [`ADR-009`](../adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md) : Logs structurés en français.
  * [`ADR-011`](../adr/ADR-011-limitation-complexite-cognitive.md) : Complexité cognitive plafonnée à 15 par méthode.
  * [`ADR-014`](../adr/ADR-014-javadoc-obligatoire-classes-methodes.md) : Documentation Javadoc exhaustive en français.

---

## 🎯 1. Objectifs du Sprint

Dans les zones urbaines et périurbaines, les axes routiers principaux (avenues, rocades, autoroutes) forment de longs tronçons géométriquement rectilignes. Cependant, le tracé brut issu du Marching Squares présente souvent des ondulations ou des déviations locales (encoches de 3 à 5 px) induites par la présence d'icônes cartographiques (panneaux, numéros de route, flèches de sens de circulation).

Le **Sprint 7** enrichit le moteur de lissage vectoriel du Sprint 6 en implémentant un **lissage LQR multi-échelle adaptatif** :
1. **Double régression LQR :** Calcul d'un lissage fin local ($\sigma = 22.0$) et d'un lissage large ($\sigma_{wide} = 2.7 \times \sigma = 59.4$) sur le résidu soustrait de la corde directrice.
2. **Détection robuste de rectitude :** Mesure de la déviation euclidienne $\Delta = \|\mathbf{sm}_b - \mathbf{sm}_a\|$ entre les deux lissages.
3. **Transition continue $C^1$ :** Lissage par moyenne mobile de boîte ($k = 10$, fenêtre de 21 points) sur la pondération de rectitude, évitant tout à-coup ou rupture de courbure.
4. **Effacement des encoches sans aplatissement :** Redressement parfait des tronçons droits tout en préservant intacts les virages et courbures authentiques où $\Delta$ est élevé.

---

## 📦 2. Livrables & Composants Clés

```text
com.sam102022.photoshop.v2.contour
├── StraightSegmentSmoother.java     (Module SRP calculant le double LQR et la transition C1)
├── ContourSmoothingConfig.java      (Extension du record avec les hyperparamètres de rectitude)
└── ContourSmoothingEngine.java      (Intégration du StraightSegmentSmoother dans le pipeline vectoriel)
```

### 2.1. `StraightSegmentSmoother`
Prend en charge l'analyse et la fusion continue sur un segment de points $[P_0, \dots, P_1]$ :
* Vérification de la longueur minimale admissible ($n \ge 2.5 \sigma$) ;
* Calcul de la droite de référence $P_0 \to P_1$ et du vecteur résidu ;
* Appel au solveur `RobustLqrSmoother` pour les deux échelles $\sigma$ et $2.7 \sigma$ ;
* Calcul de l'écart euclidien $\Delta(i)$ et du poids brut $w_{\text{raw}}(i) = \text{clamp}\left(\frac{ST\_T - \Delta(i)}{ST\_T / 2}, 0.0, 1.0\right)$ ;
* Application du filtre boîte 1D avec padding de réplication aux extrémités ($k = 10$) ;
* Recombinaison : $\mathbf{sm}(i) = \mathbf{sm}_a(i) + w_{\text{st}}(i) \cdot (\mathbf{sm}_b(i) - \mathbf{sm}_a(i))$.

### 2.2. Évolution de `ContourSmoothingConfig` (Record immuable)
Enrichissement des paramètres avec valeurs par défaut étalonnées sur Python V7 :
* `straightFactor` : Facteur multiplicateur pour la fenêtre large (défaut : `2.7`).
* `straightScale` : Tolérance de l'estimateur de Cauchy pour la fenêtre large (défaut : `3.0`).
* `straightThreshold` : Seuil de déviation maximale $ST\_T$ en pixels (défaut : `3.0`).
* `straightTransitionK` : Demi-largeur du noyau de convolution boîte (défaut : `10`).

---

## 🔄 3. Contrat d'Entrée / Sortie

```text
Entrée :
  - Segment de points vectoriels (List<PixelPoint>)
  - ContourSmoothingConfig (hyperparamètres)

Sortie :
  - Segment de points lissés et redressés (List<PixelPoint>)
```

---

## 🧪 4. Critères de Validation & Tests

1. **Test unitaire `StraightSegmentSmootherTest` :**
   - Tronçon droit perturbé par une encoche d'icône de 4 px $\to$ l'encoche est absorbée, la droite est restaurée avec une déviation $< 0.3\text{ px}$.
   - Arc de cercle prononcé $\to$ le poids de rectitude $w_{\text{st}}$ s'annule, la géométrie du virage est strictement conservée.
2. **Test de non-régression d'intégration `Sprint7IntegrationTest` sur CA01 :**
   - Comparaison avec l'étalon `contour_smooth.npy` de Python V7 :
     - Distance de Hausdorff maximale $\le 2.0\text{ px}$ ;
     - RMS globale $\le 0.5\text{ px}$.
   - Temps d'exécution du lissage $\le 300\text{ ms}$.
