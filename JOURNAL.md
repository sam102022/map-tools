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
| **Sprint 4** | Moteur de Vote Topologique & Résolution des Parcelles | Planifié | ⏳ À venir | `CellCoverageCalculator`, `CellClassifier`, `PartialCellResolver`, `CellSelection` |
| **Sprint 5** | Reconstruction des Frontières Routières & Expansion Géodésique | Planifié | ⏳ À venir | `BoundingBoxCropper`, `BoundaryRoadExtractor`, `RoadDistanceTransform`, `GeodesicRoadExpander`, `ResidualHoleResolver` |
| **Sprint 6** | Géométrie Sub-Pixel, Lissage Robuste LQR & Ronds-points | Planifié | ⏳ À venir | `SubpixelContourExtractor`, `CornerDetector`, `RobustLqrSmoother`, `RoundaboutDetector`, `HermiteSplineConnector` |
| **Sprint 7** | Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 | Planifié | ⏳ À venir | `SupersampleRenderer`, `ImageClipper`, `V2CliRunner` |

---

## 📝 3. Entrées Journalières Datées

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
    *   **Extracteur d'interfaces routières `RoadInterfaceExtractor` :** Détection d'adjacence par contact direct et propagation Voronoi BFS dans la chaussée, construction automatique du `CellGraph`.
    *   **Documentation Javadoc exhaustive en français (ADR-014) :** Intégration systématique des commentaires Javadoc sur toutes les classes et méthodes.

*   **Validation & Métriques :**
    *   **Test d'intégration pivot `Sprint3IntegrationTest` sur Territoire CA01 :**
        *   Nombre de cellules détectées : **exactement 130 cellules** (concordance déterministe à 100% avec l'étalon Python `ndi.label(~Rc)`) ;
        *   Nombre d'interfaces routières identifiées : **269 interfaces** (concordance exacte avec l'analyse d'adjacence Voronoi Python) ;
        *   Temps d'exécution du pipeline complet : **~800 ms** (critère $\le 1000\text{ ms}$).
    *   **Suite de tests V2 :** 51/51 tests réussis (100% de succès sur l'ensemble des packages V2 geometry, road et cell).

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
    *   **Nombre de tests exécutés :** 51
    *   **Succès :** 51 (100%)
    *   **Échecs :** 0
    *   **Erreurs :** 0
*   **Temps d'exécution total de la suite V2 :** 4.3 s.
