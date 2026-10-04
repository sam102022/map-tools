# Journal de Bord — Évolution du Projet Photoshop Map Snapping

Conformément à l'[ADR-010](docs/adr/ADR-010-suivi-et-trace-journaliere-de-l-evolution.md), ce document centralise la traçabilité des choix techniques, la chronologie des réalisations par sprint, les entrées journalières datées et l'état opérationnel du projet.

---

## 🏛️ 1. Rappel des Principes Directeurs (ADRs)

Le projet est régi par les décisions d'architecture (ADR) consignées dans [`docs/adr/`](docs/adr/) :
*   **ADR-001 :** Socle 100% Java standard sans dépendances natives (Java 21, Java2D, Swing).
*   **ADR-002 :** Modèle de masques matriciels hybrides (`BinaryMask` & `CoverageMask`).
*   **ADR-003 :** Double mode d'exécution CLI headless et GUI Swing interactive.
*   **ADR-004 :** Immutabilité de la configuration et des modèles de domaine via les Java Records (`SnappingConfig`, `RoadMask`, `PolygonMask`).
*   **ADR-005 :** Recalage par propagation géodésique et barrières routières.
*   **ADR-006 :** Le projet est documenté avant d'être développé (Spécification ➔ Plan ➔ Code).
*   **ADR-007 :** Développement par sprints thématiques incrémentaux.
*   **ADR-008 :** Une seule responsabilité par classe (Single Responsibility Principle - SRP).
*   **ADR-009 :** Logs en français suivant une hiérarchie stricte (INFO, DEBUG, ERROR).
*   **ADR-010 :** Suivi et trace journalière de l'évolution du projet (`JOURNAL.md`).
*   **ADR-011 :** Limitation stricte de la complexité cognitive à 15 par méthode.
*   **ADR-012 :** Usage des imports explicites et proscription des noms pleinement qualifiés (FQCN).
*   **ADR-013 :** Standardisation du fuseau horaire (`LocalDate.now(ZoneId.systemDefault())`).
*   **ADR-014 :** Documentation Javadoc exhaustive en français obligatoire sur l'ensemble des types et méthodes.

---

## 📅 2. Frise Chronologique par Sprints (Architecture V2)

| Sprint | Thématique | Date de validation | Statut | Livrables majeurs |
| :--- | :--- | :--- | :--- | :--- |
| **Sprint 1** | Socle Géométrique, Projection Web Mercator & Rasterisation | 02/10/2026 | ✅ Validé | `GeoCoordinate`, `PixelPoint`, `MapContext`, `TerritoryGeometry`, `WebMercatorProjection`, `JsonTerritoryLoader`, `PolygonRasterizer`, `PolygonMask` |
| **Sprint 2** | Détection Colorimétrique des Routes & Fermeture Topologique | 03/10/2026 | ✅ Validé | `RoadMask`, `RoadDetector`, `RoadDetectorStyle`, `RoadMaskCleaner`, `RoadDetectorOsm` |
| **Sprint 3** | Segmentation en Cellules (4-connexité) & `CellGraph` | 04/10/2026 | ✅ Validé | `CropWindow`, `BoundingBoxCropper`, `Cell`, `CellLabelMap`, `CellLabeler`, `RoadInterface`, `RoadInterfaceExtractor`, `CellGraph` |
| **Sprint 4** | Moteur de Vote Topologique & Résolution des Parcelles | Cadré (04/10/2026) | ⏳ Prêt pour dév | `CellState`, `CellSelectionPolicy`, `CellDecision`, `CellSelection`, `CellCoverageCalculator`, `CellClassifier`, `PartialCellResolver`, `TopologicalVoteEngine` |
| **Sprint 5** | Reconstruction des Frontières Routières & Expansion Géodésique | Cadré (04/10/2026) | ⏳ Prêt pour dév | `ExpansionConfig`, `DistanceMap`, `ConsolidatedMask`, `EuclideanDistanceTransform`, `LocalMaxFilter`, `BoundedGeodesicExpander`, `MorphologicalConsolidator`, `ResidualHoleResolver`, `BoundaryRoadPartitioner`, `RoadBoundaryConsolidator` |
| **Sprint 6** | Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points | Cadré (04/10/2026) | ⏳ Spécifié | `SubpixelContourExtractor`, `CornerDetector`, `RobustLqrSmoother`, `CornerPreservationBlender`, `AlgebraicEllipseFitter`, `RoundaboutDetector`, `HermiteSplineConnector`, `ContourSmoothingEngine` |
| **Sprint 7** | Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 | Cadré (04/10/2026) | ⏳ Spécifié | `V2Config`, `SupersampleRenderer`, `ImageClipper`, `RenderResult`, `V2Pipeline`, `V2CliRunner` |

---

## 📝 3. Entrées Journalières Datées

### Dimanche 04 octobre 2026 : Cadrage Formel (Design Spec & Plan TDD) du Sprint 5 V2 (Reconstruction des Frontières Routières & Expansion Géodésique)

*   **Audit Préalable et Résolution des 6 Faiblesses Documentaires :**
    *   **Levée de l'ambiguïté architecturale (`BoundaryRoadExtractor` vs Expansion globale) :** Clarification du rôle de la borne géodésique $d_G \le 2 \times hw_{\max} + \varepsilon$ ($\varepsilon = 4.0\text{ px}$) qui arrête naturellement la propagation Dijkstra au bord extérieur de la chaussée et élimine tout risque de fuite dans les rues perpendiculaires extérieures, sans nécessiter de découpage topologique complexe préalable des carrefours.
    *   **Spécification d'algorithmes 100% Java standard en $O(N)$ (ADR-001) :**
        *   Remplacement de l'appel scipy `distance_transform_edt` par l'algorithme exact et linéaire en deux passes 1D séparables de **Meijster, Roerdink & Hesselink (2000)** (~40 ms pour 2,68 Mpx) ;
        *   Remplacement du filtre 2D lourd par un filtre maximum local séparable horizontal/vertical en $O(N)$ basé sur la **file monotone 1D de Lemire** (~35 ms pour une fenêtre $41 \times 41$).
    *   **Sanctuarisation des cellules intérieures ($T$) :** Correction de la formule morphologique pour empêcher que l'ouverture circulaire par disque ($\rho = 5\text{ px}$) n'érode les angles authentiques des parcelles intérieures : formulation stricte $M_c = T \cup \text{open}(T \cup ext, \text{disk}(5))$.
    *   **Cohérence du repère spatial (ROI) et contrat I/O :** Intégration de `CropWindow` dans le modèle immuable de sortie `ConsolidatedMask` avec méthodes `offsetX()` et `offsetY()`, alignant la chaîne avec Sprint 3, Sprint 4 et Sprint 6.
    *   **Spécification du mode `ZONE` :** Formalisation de `BoundaryRoadPartitioner` pour le partage équitable de la voirie mitoyenne par Voronoi géodésique à double front (ligne médiane neutre), garantissant l'invariance stricte $Z_1 \cap Z_2 = \emptyset$.
    *   **Comblement sélectif des îlots résiduels :** Algorithme par inondation BFS inverse depuis le périmètre extérieur marquant le fond infini, puis identification des cavités et comblement sélectif des trous $\le 15\,000\text{ px}$.
    *   **Métriques de validation quantifiées sur CA01 :** Surface consolidée cible $M_c$ calibrée sur l'étalon Python à **1 215 383 px** ($\pm 0{,}5\%$, $\text{IoU} \ge 99{,}5\%$), budget temps total $\le 1200\text{ ms}$ (seuil max $1500\text{ ms}$).
*   **Documents de Référence Créés et Mis à Niveau (ADR-006) :**
    *   Spécification technique détaillée : [`docs/superpowers/specs/2026-10-04-v2-sprint-5-geodesic-expansion-design.md`](docs/superpowers/specs/2026-10-04-v2-sprint-5-geodesic-expansion-design.md) ;
    *   Plan d'implémentation opérationnel TDD (10 tâches découpées) : [`docs/superpowers/plans/2026-10-04-v2-sprint-5-geodesic-expansion.md`](docs/superpowers/plans/2026-10-04-v2-sprint-5-geodesic-expansion.md) ;
    *   Fiche de sprint V2 mise à jour : [`docs/sprints_v2/sprint-5-expansion-geodesique.md`](docs/sprints_v2/sprint-5-expansion-geodesique.md).

### Dimanche 04 octobre 2026 : Cadrage Formel & Spécification Complète du Sprint 7 V2 (Supersampling SS=4, Export RGBA & CLI V2)

*   **Audit et Résolution des Zones d'Ombre Initiales :**
    *   **Résolution du repère spatial (ROI vs Pleine image) :** Clarification du calcul de rastérisation vectorielle sub-pixel opéré exclusivement sur la boîte englobante locale `CropWindow` $\times SS$ (évitant une empreinte mémoire démesurée de ~130 Mo à l'échelle globale) avec correction de décalage demi-pixel $+0.5\text{ px}$, avant réinsertion dans le repère global $W \times H$.
    *   **Conformité stricte au modèle `CoverageMask` (ADR-002) :** Abandon de tout flou matriciel artificiel au profit du calcul surfacique exact par moyenne de boîte (*box filter*) sur les 16 sous-pixels, produisant un `CoverageMask` continu $[0..255]$ et garantissant la composition alpha normalisée.
    *   **Formalisation des 3 livrables graphiques de production :** Spécification exacte de `clipped.png` (ARGB 32-bit), `mask.png` (8-bit niveaux de gris natif `TYPE_BYTE_GRAY`) et `overlay.png` (contrôle diagnostic par superposition du contour frontière en rouge vif `#FF0000`).
    *   **Conception architecturale SRP (ADR-008) & Immutabilité (ADR-004) :**
        *   Introduction du record de configuration immuable `V2Config` regroupant les 13 hyperparamètres calibrés sur l'étalon Python V5 ;
        *   Introduction de l'orchestrateur autonome `V2Pipeline` enchaînant les Sprints 1 à 7 de manière découplée, réutilisable par la CLI et la future GUI Swing (ADR-003) ;
        *   Cloisonnement strict entre `SupersampleRenderer` (calcul géométrique de couverture) et `ImageClipper` (assemblage et colorimétrie).
    *   **Spécification d'ingénierie CLI :** Tableau exhaustif de l'ensemble des arguments E/S (`--map`, `--json`, `--mode`, `--output`, `--mask-out`, `--overlay-out`, `--osm-roads`, `--debug`) et des hyperparamètres, protocole d'erreurs et codes de sortie (0, 1, 2), journalisation hiérarchisée en français (ADR-009).
    *   **Mise à niveau documentaire :** Rédaction intégrale de [`docs/sprints_v2/sprint-7-rendu-supersampling-cli.md`](docs/sprints_v2/sprint-7-rendu-supersampling-cli.md) et alignement du schéma directeur [`docs/SPRINTS_V2.md`](docs/SPRINTS_V2.md).
    *   **Critères d'acceptation stricts sur cas pivot CA01 :** Temps d'exécution total $\le 5{,}0\text{ s}$, concordance $\text{IoU} \ge 0{,}99$ par rapport à l'étalon `CA01_mask_v5.png`, écart de surface $< 0{,}5\%$.

### Dimanche 04 octobre 2026 : Cadrage Formel & Spécification Technique Complète du Sprint 6 V2 (Géométrie Sub-Pixel & Ronds-points)

*   **Audit et Identification des Faiblesses Initiales :**
    *   Mise en évidence d'un blocage d'interface I/O : la détection des ronds-points (`RoundaboutDetector`) nécessite impérativement `RoadMask` (Sprint 2) pour le sondage radial (240 rayons) et `CellLabelMap` (Sprint 3) pour l'isolation des îlots compacts, absents du contrat d'entrée initial.
    *   Absence de spécification mathématique en Java pur standard (ADR-001) pour remplacer l'appel externe `skimage.measure.EllipseModel`.
    *   Formulation matricielle $3 \times 3$ du LQR sous-spécifiée et critères de validation purement qualitatifs.
*   **Conception Formelle Exhaustive (ADR-006 & ADR-001) :**
    *   Rédaction de la spécification technique détaillée : [`docs/superpowers/specs/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts-design.md`](docs/superpowers/specs/2026-10-04-v2-sprint-6-subpixel-smoothing-roundabouts-design.md).
    *   Spécification complète du solveur d'ellipse direct en **100% Java standard sans lib externe** via l'algorithme direct de **Halir & Flusser (1998)** aux moindres carrés sous contrainte $4AC - B^2 = 1$ avec réduction $3 \times 3$ et calcul des valeurs propres.
    *   Formalisation matricielle de la Régression Quadratique Locale Robuste (LQR) avec résolution explicite par déterminants de Cramer et M-estimateur de Cauchy/Tukey (6 itérations).
    *   Mise à jour et alignement complet de la fiche de cadrage : [`docs/sprints_v2/sprint-6-lissage-subpixel-ronds-points.md`](docs/sprints_v2/sprint-6-lissage-subpixel-ronds-points.md).
    *   Mise à jour du schéma directeur dans [`docs/SPRINTS_V2.md`](docs/SPRINTS_V2.md).
    *   Établissement de critères quantitatifs d'acceptation stricts sur le cas pivot CA01 : détection de 10 à 16 coins vifs, substitution de l'arc externe sur exactement 1 rond-point (boulevard sud-est), distance de Hausdorff $\le 2.5\text{ px}$ par rapport au contour étalon Python `contour_smooth.npy`.

### Dimanche 04 octobre 2026 : Cadrage Formel (Design Spec & Plan TDD) du Sprint 4 V2 (Vote Topologique)

*   **Cadrage et Conception formelle préalable (ADR-006) :**
    *   Rédaction de la spécification technique exhaustive : [`docs/superpowers/specs/2026-10-04-v2-sprint-4-topological-voting-design.md`](docs/superpowers/specs/2026-10-04-v2-sprint-4-topological-voting-design.md).
    *   Rédaction du plan d'implémentation opérationnel TDD : [`docs/superpowers/plans/2026-10-04-v2-sprint-4-topological-voting.md`](docs/superpowers/plans/2026-10-04-v2-sprint-4-topological-voting.md).
    *   Mise à jour et alignement complet de la fiche de sprint : [`docs/sprints_v2/sprint-4-vote-topologique.md`](docs/sprints_v2/sprint-4-vote-topologique.md).
    *   Résolution des 4 zones d'ombre architecturales :
        1. **Contrat d'entrée I/O :** Ajout explicite de la matrice d'étiquettes `CellLabelMap` indispensable au calcul pixel par pixel des intersections.
        2. **Repère local ROI :** Découpe préalable du polygone d'intention $P_c = P[sl]$ sur la `CropWindow` ($1505 \times 1783\text{ px}$) assurant une concordance spatiale directe.
        3. **Masque d'amorçage $T$ (`retainedMask`) :** Fourniture directe dans `CellSelection` du masque binaire prêt pour la propagation géodésique du Sprint 5.
        4. **Gestion déterministe des cellules ouvertes :** Confirmation de la règle uniforme d'intersection binaire stricte $C \cap P_c$ sans heuristique empirique superflue.
    *   Définition des métriques cibles étalons sur CA01 : 130 cellules $\rightarrow$ **21 INSIDE**, **6 PARTIAL**, **103 OUTSIDE**.

### Dimanche 04 octobre 2026 : Cadrage (Spec & Plan) et Finalisation Intégrale du Sprint 3 V2

*   **Cadrage et Conception formelle préalable (ADR-006) :**
    *   Rédaction de la spécification technique exhaustive : [`docs/superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md`](docs/superpowers/specs/2026-10-04-v2-sprint-3-cell-segmentation-design.md).
    *   Rédaction du plan d'implémentation opérationnel TDD : [`docs/superpowers/plans/2026-10-04-v2-sprint-3-cell-segmentation.md`](docs/superpowers/plans/2026-10-04-v2-sprint-3-cell-segmentation.md).
    *   Résolution des 4 zones d'ombre algorithmiques :
        1. Avancement du `BoundingBoxCropper` au Sprint 3 (avec marge $mg = 90\text{ px}$ alignée sur la troncature entière du prototype Python) divisant la surface de calcul par 3 ($1505 \times 1783$ pixels).
        2. Définition opérationnelle de `RoadInterface` via détection directe sur routes minces et propagation Voronoi multi-sources (BFS) sur routes larges.
        3. Stockage matriciel compact des labels via `CellLabelMap` ($int[]$).
        4. Figeage des métriques d'acceptation déterministes sur CA01 : exactement 130 cellules.

*   **Fonctionnalités et modules implémentés :**
    *   **Contrats de domaine immuables (`CropWindow`, `CellLabelMap`, `Cell`, `RoadInterface`, `CellLabelingResult`, `CellGraph`) :** Modèles de données avec validation défensive des préconditions et méthodes d'assistance à la navigation d'adjacence.
    *   **Découpeur de boîte englobante `BoundingBoxCropper` :** Calcul de la fenêtre de recadrage avec marge de sécurité et découpe matricielle de `BinaryMask`.
    *   **Étiqueteur en 4-connexité stricte `CellLabeler` :** Algorithme Two-Pass avec structure d'équivalences *Union-Find* (compression de chemin et fusion par rang), éliminant tout pont diagonal à travers les carrefours.
    *   **Extracteur d'interfaces routières `RoadInterfaceExtractor` optimisé :** Détection d'adjacence directe zéro-allocation (tampon local `int[4]`), propagation Voronoi BFS dans la chaussée, déduplication sans surcoût mémoire et construction automatique du `CellGraph`.
    *   **Documentation Javadoc exhaustive en français (ADR-014) :** Intégration systématique des commentaires Javadoc normalisés (`@param`, `@return`, `@throws`) sur l'intégralité des classes, records, interfaces, constructeurs et méthodes (publiques et privées), y compris les classes de tests unitaires.

*   **Validation & Métriques :**
    *   **Test d'intégration pivot `Sprint3IntegrationTest` sur Territoire CA01 :**
        *   Nombre de cellules détectées : **exactement 130 cellules** (concordance déterministe à 100% avec l'étalon Python `ndi.label(~Rc)`) ;
        *   Nombre d'interfaces routières identifiées : **269 interfaces** (concordance exacte avec l'analyse d'adjacence Voronoi Python) ;
        *   Temps d'exécution du pipeline algorithmique : **~500 ms** (critère $\le 1000\text{ ms}$, test stable même sur JVM froide).
    *   **Suite de tests V2 :** 54/54 tests réussis (100% de succès sur l'ensemble des packages V2 geometry, road, cell et vote).

### Samedi 03 octobre 2026 : Implémentation et Validation Complète du Sprint 2 V2

*   **Fonctionnalités et modules implémentés :**
    *   **Contrat de domaine immuable `RoadMask` :** Java Record regroupant les masques `raw` (détection brute) et `closed` (fermeture topologique étanche), avec validations dimensionnelles strictes.
    *   **Interface générique `RoadDetector` :** Contrat abstrait de détection de chaussée cartographique.
    *   **Détecteur colorimétrique `RoadDetectorStyle` :** Extraction vectorisée des axes routiers sur le fond Google Maps contrasté (`05_style_contraste_sans_rien.png`) selon les deltas chromatiques RVB de l'algorithme Python étalon `script.py` : `(B - R >= 12) && (B - G >= 2) && (B - G <= 22) && (R < 228)`.
    *   **Nettoyeur morphologique `RoadMaskCleaner` :** 
        *   Fermeture binaire minimale (dilatation $r=1$ puis érosion $r=1$ en croix 4-connexe) garantissant l'étanchéité des coupures d'anti-aliasing de 1 pixel ;
        *   Ouverture binaire par élément structurant carré $2 \times 2$ supprimant les artefacts et micro-bruits isolés.
    *   **Rasteriseur vectoriel `RoadDetectorOsm` :** 
        *   Parseur JSON récursif autonome intégré sans dépendance tierce (ADR-001) pour `osm_roads.json` ;
        *   Projection géographique vers pixel via `WebMercatorProjection` et conversion d'échelle métrique locale ;
        *   Rasterisation Java2D avec gestion de la typologie des voies (`highway`, `lanes`, `width`) et exclusion des dessertes mineures privées.
    *   **Documentation Javadoc exhaustive en français (ADR-014) :** Intégration systématique des commentaires Javadoc sur toutes les classes, interfaces, records, constructeurs et méthodes (y compris les classes de tests).

*   **Validation & Métriques :**
    *   **Test d'intégration étalon `Sprint2IntegrationTest` sur Territoire CA01 :**
        *   Temps d'exécution : **~329 ms** (critère $\le 2000\text{ ms}$) ;
        *   Taux de concordance pixel avec l'image témoin Python `maps/road.png` : **99,9850%** (seuil exigé $\ge 99,5\%$) ;
        *   Préservation intégrale des axes secondaires sans déformation géométrique.
    *   **Tests unitaires :** 15/15 tests du package `com.sam102022.photoshop.v2.road` avec 100% de succès.

### Vendredi 02 octobre 2026 : Finalisation et Validation du Sprint 1 V2

*   **Fonctionnalités et modules implémentés :**
    *   Modèles géométriques immuables : `GeoCoordinate`, `PixelPoint`, `MapContext`, `TerritoryGeometry`.
    *   Projection Web Mercator conforme EPSG:3857 (`WebMercatorProjection`).
    *   Parseur JSON autonome de territoire (`JsonTerritoryLoader`).
    *   Rasteriseur vectoriel de polygones avec gestion des trous et multipolygones (`PolygonRasterizer`).
    *   Contrat officiel `PolygonMask`.
    *   Validation d'intégration sur Territoire CA01 (`Sprint1IntegrationTest`) avec surface de polygone conforme (~1 184 000 px).

---

## 🔬 4. État Opérationnel à Date

*   **Suite de tests V2 (`com.sam102022.photoshop.v2.**.*Test`) :**
    *   **Nombre de tests exécutés :** 54
    *   **Succès :** 54 (100%)
    *   **Échecs :** 0
    *   **Erreurs :** 0
*   **Temps d'exécution total de la suite V2 :** 4.9 s.
