# Changelog

Toutes les modifications notables apportées à ce projet sont documentées dans ce fichier.
Le format est basé sur [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

## [Non publié] - 2026-10-08

### Corrigé
- `ZoneInteriorBuilder` : un îlot pris pour un giratoire n'est plus retiré de la zone s'il se trouve à plus de 90 % dans le tracé de la zone (`MAX_POLYGON_COVERAGE`) ; supprime l'encoche autour de l'îlot d'arrêt de bus de la zone rouge (CA02). Seuls les giratoires traversés par le tracé sont exclus.
- **Concordance V2 avec l'étalon Python `snap_cells_prototype_v7.py`** (audit comparatif Java ↔ Python sur Territoire CA01) :
  - `AlgebraicEllipseFitter` : l'orientation `theta` était décalée de π/2 dès que le grand axe était incliné à moins de 45° de l'horizontale (résidus jusqu'à ~70 px sur des ellipses parfaites). Formule corrigée : `phi = 0.5·atan2(2b, a−c) + π/2`, sans correction supplémentaire selon le signe de `a − c`.
  - `HermiteSplineConnector` / `ContourSmoothingEngine` : le test de recouvrement du disque d'un giratoire (≥ 20 %) est désormais évalué sur le masque consolidé rempli (`Mfill` de l'étalon) et non plus sur les seules cellules retenues (`T`), qui excluent la chaussée de l'anneau. La sonde extérieure de l'arc reste sur `T`. Nouvelle surcharge `integrateRoundaboutsWithTracking(contour, roundabouts, territoryMask, consolidatedFilledMask, zr)` ; l'ancienne signature est conservée.
  - `StraightSegmentSmoother` / `RobustLqrSmoother` : un contour sans coin ou avec un seul coin n'était pas lissé (segment réduit à un point). Le segment couvre désormais la boucle complète (`n + 1` indices), comme `arange(a, a + N + 1) % N` dans l'étalon.

### Modifié
- CLI : `--json` devient facultatif pour les territoires ; à défaut, `01_plan_avec_territoires.json` est pris dans le dossier de la carte (message d'erreur explicite s'il est absent). Seul `--map` reste obligatoire.
- Mode zones : polygone à grands segments plus tolérant par défaut (5 px au lieu de 1 px, `V2ZoneCliRunner.DEFAULT_ZONE_POLYGON_TOLERANCE`, `--polygon-tolerance` reste prioritaire) et découpe exacte de la capture « Routes seules » limitée aux abords des giratoires (`RoadExactCoverage`, marge 15 px) : les bords le long des routes sont rectilignes malgré les débouchés de rues et entrées charretières (zone rouge : rue de la Fonderie, route de Paris).
- CLI : `--mode zone` lance désormais le détourage des zones ; sans `--zones`, le plan `02_plan_avec_zones.png` est pris dans le dossier de la carte (`--json` facultatif). L'ancien mode « zone » du pipeline territoire produisait un `clipped.png` unique, sans rapport avec les zones tracées.
- `HermiteSplineConnector` : un arc extérieur de giratoire dont la sonde atteint 0,3 est rejeté (pas de substitution plutôt qu'un arc intérieur).
- `CellSelectionPolicy.DEFAULT_MIN_PARTIAL_THICKNESS` : 20 → 25 px.
- `EuclideanDistanceTransform` : passe verticale en balayage par lignes et passe horizontale parallélisée par blocs (résultat identique, ~40 % plus rapide).
- **Étalonnage sur le détourage de référence CA02** (`maps/captures_maps/Territoire CA02/expected_territory.jpg`) : seuil d'inclusion des cellules `hi` 0,60 → 0,80 (`V2Config.defaultConfig()`) et demi-largeur minimale des chaussées de boulevard incluses 8 → 6 px (`BoulevardPocketFiller.DEFAULT_BOULEVARD_HALF_WIDTH`, pour inclure la seconde chaussée plus étroite). IoU avec la référence : 0,961 (matin) → 0,974 → 0,983. CA01 inchangé. Nouveau test `ExpectedTerritoryCA02Test` (IoU ≥ 0,978).
- `AlphaRefinementConfig` / `AlphaRefiner` : le bouclier d'exemption des giratoires (`RoundaboutExemptionModulator`) est désormais **désactivé par défaut** (nouveau composant `roundaboutShieldEnabled`, constructeur à 5 paramètres conservé avec `false`). Il conservait le fond blanc pris entre l'arc d'ellipse, les raccords d'Hermite et la chaussée (jusqu'à 3 rayons normés autour de chaque giratoire substitué). L'îlot central reste protégé par son inclusion dans la zone autorisée. Note : dans l'étalon Python v7, la formule `factor = 1 - zw*(1-factor)` appliquait de fait l'affinage *uniquement* autour des giratoires — la sortie Python ne présentait donc pas ce défaut.
- `V2CliRunner` : la source du masque routier par défaut est désormais la **détection colorimétrique** sur la carte (`RoadDetectorStyle` + `RoadMaskCleaner`), conformément à l'étalon Python. La rasterisation OpenStreetMap devient optionnelle via `--road-source osm` ou `--osm-roads <chemin>`. `--road <png>` reste prioritaire. Suppression du repli implicite sur `maps/road.png`.
- `Sprint6IntegrationTest` / `Sprint7IntegrationTest` : seuils recalibrés sur l'étalon Python (7 ronds-points substitués sur la fixture CA01 au lieu de 1 ; 4 à 16 coins, les 5 sommets du polygone étant situés sur des giratoires).

### Ajouté
- `TraceAlignedOutline` (v2.zone) : bord des zones rectifié en grands segments parallèles au tracé de l'utilisateur. Chaque côté du tracé simplifié (tolérance 2,5 px) est reporté vers l'intérieur de sa distance médiane au masque de zone (bord de chaussée) ; les renfoncements de moins de 40 px (débouchés de rues, entrées de parking, raccords de contre-allée) sont comblés, les giratoires exclus restent exclus. L'accrochage au bord de chaussée n'est plus appliqué aux zones (il recréait les décrochements) ; la découpe exacte autour des giratoires est conservée. Test : `TraceAlignedOutlineTest`.
- `RoadExactCoverage` (v2.zone) : en mode zones avec la capture « Routes seules », l'opacité au voisinage (6 px) des routes frontières est lue directement dans la capture (`1 - part de chaussée`) au lieu du contour vectoriel lissé : le détourage suit exactement l'anneau des giratoires et les raccords de rues. Débouchés de rues intérieures pleins, fragments isolés retirés, trous de moins de 200 px comblés. Test : `RoadExactCoverageTest`.
- **Capture Google « Routes seules » (`06_routes_seules.png`) comme source des routes** :
  - `GoogleRoadsImage` (v2.road) : niveau de gris = part de chaussée de chaque pixel ; fermeture 3x3 (efface les marquages sombres de 2 px au plus), ouverture 3x3 (efface sentiers et traits de moins de 3 px) ; voies rapides (teinte de la carte) retirées avant nettoyage, le pipeline les traitant à part. Fournit le masque routier (seuil 0,5) et le champ de bord sub-pixel (`RoadEdgeField(GoogleRoadsImage, CropWindow)`).
  - `RoadEdgeSnapper.snap(..., googleRoads)`, `V2Pipeline.execute(..., googleRoads)`, `V2ZonePipeline.execute(..., googleRoads)` (surcharges ; `null` = comportement précédent).
  - CLI (territoires et zones) : la capture voisine de la carte est utilisée automatiquement ; `--google-roads <png>` pour un autre chemin, `--no-google-roads` pour revenir à la détection par la couleur ; `--road`, `--road-source`, `--osm-roads` restent prioritaires pour le masque. Classe `GoogleRoadsResolver`.
  - `BoulevardPocketFiller` : un terre-plein n'est comblé que s'il est bordé pour moitié au moins par les chaussées du boulevard (`MIN_MEDIAN_ENCLOSURE`), ce qui écarte les îlots de carrefour situés au-delà.
  - `capture_territoires.js` : le champ de recherche et tous les conteneurs de contrôles sont masqués pendant la capture « Routes seules ».
  - Tests : `GoogleRoadsImageTest`, `GoogleRoadsResolverTest`, `ExpectedTerritoryCA02Test.testIouWithExpectedTerritoryGoogleRoads`.
- `maps/capture_territoires.js` : 6e capture `06_routes_seules.png` (style « Routes seules » : routes blanches sur fond noir, futur masque routier). Le style est retrouvé par le libellé de son bouton (« Routes seules » ou « Style routes ») ; les contrôles Google superposés (barre des styles, zoom, logo, mentions) sont masqués pendant cette capture uniquement, sans déplacer la carte.
- **Mode zones : détourage sur le bord intérieur des routes** (plan `02_plan_avec_zones.png`, une zone par couleur de trait : 1 rouge, 2 verte, 3 mauve, 4 orange, 5 cyan, 6 noir) :
  - Nouveau paquet `v2.zone` : `ZoneColor` (couleurs de référence mesurées sur les tracés de CA02, tolérance RVB 60, chaque pixel attribué à la zone la plus proche), `ZoneExtractor` (traits longs ≥ 150 px, interruptions sous les pictogrammes refermées par épaississement progressif jusqu'à 12 px, une partie par région enclose ≥ 20 000 px), `ZoneInteriorBuilder` (cellules retenues + rues intérieures comblées par fermeture de rayon 12 px ; routes frontières et giratoires frontaliers exclus), `DiskMorphology`, `MaskComponents`.
  - `V2ZonePipeline` : vote, masque de zone, lissage sans substitution de giratoires, accrochage au bord **intérieur** des chaussées, polygone à grands segments, affinage et rendu SS=4 ; une zone en plusieurs parties est détourée partie par partie puis réunie.
  - `RoadEdgeSnapper.snap(..., innerEdge)` et `ContourSmoothingEngine.process(..., substituteRoundabouts)` (surcharges ; signatures existantes inchangées).
  - CLI : `--zones <plan>` (`--json` facultatif) et `--zone <liste>` ; sorties `zoneN_<couleur>_mask.png` et `zoneN_<couleur>_rendu.png`. Classe `V2ZoneCliRunner`.
  - Tests : `ZoneColorTest`, `ZoneExtractorTest`, `ZoneInteriorBuilderTest`, `V2ZoneCliRunnerTest`, `ZonesCA02IntegrationTest`.
- **CA02 — giratoires frontaliers et bord nord-est** (retour utilisateur sur la référence `expected_territory.jpg`) :
  - `BoundaryRoundaboutIncluder` (v2.expansion) : un giratoire traversé par le polygone (couverture du disque ≥ 30 %, territoire < 90 %) est inclus en entier ; le contour suit son bord extérieur.
  - `PolygonLateralBand` (v2.expansion) : bandes latérales par côté du polygone, jusqu'au bord opposé de la route longée (profondeur médiane, interruptions ≤ 12 px) ; remplace la dilatation isotrope du polygone dans `BoulevardPocketFiller` (nouvelle surcharge `fill(..., polygonMask, polygonOutline, pocketRadius)`).
  - `RoadDetectorStyle.detectMotorways` : détection de la teinte autoroute (A811), utilisée uniquement comme route longée.
  - Tests : `PolygonLateralBandTest`, `RoadDetectorStyleTest.testMotorwayColourDetectedSeparately` ; `ExpectedTerritoryCA02Test` relevé à IoU ≥ 0,985 (mesuré 0,9887).
- **Boulevards, bretelles et boucles (Territoire CA02)** :
  - `BoulevardPocketFiller` (v2.expansion) : comble les poches (bretelles, îlots, débouchés) situées à moins de 25 px d'une chaussée large (demi-largeur ≥ 8 px) par fermeture morphologique de (territoire ∪ boulevard), le boulevard servant d'appui sans être ajouté ; les grands îlots non retenus (> 20 000 px) ne sont jamais absorbés. Dilatation/érosion par transformée de distance exacte (O(N)). Le contour longe désormais le bord du boulevard au lieu de contourner les bretelles. `V2Config.boulevardPocketRadius` (défaut 25, 0 = désactivé), option CLI `--pocket-radius`.
  - Boulevard longé inclus jusqu'à son bord opposé (convention des routes frontières, choix utilisateur) : chaussées larges à moins de 45 px du territoire et en contact sur au moins 80 px, puis comblement du terre-plein central ; retrait des amorces étroites de rues et bretelles débordant au-delà (ouverture de rayon 10 px limitée aux chaussées étroites hors cellules retenues).
  - `RoundaboutDetector` : rejet des îlots non circulaires (circularité 4πA/P² < 0,78 ; giratoires réels ≈ 0,9, îlot triangulaire de carrefour ≈ 0,6) qui produisaient de faux giratoires et des boucles.
  - `LocalLoopRemover` (v2.edge) : suppression des boucles locales où le contour se recoupe (après l'accrochage au bord des routes).
  - Tests : `BoulevardPocketFillerTest`, `LocalLoopRemoverTest`, `RoundaboutDetectorTest.testIslandCircularitySeparatesTriangleFromDisk`.
- **Détourage en polygone à grands segments (« lasso polygonal »)** :
  - `PolygonalSimplifier` (v2.edge) : réduction du contour accroché par Douglas-Peucker sur boucle fermée (tolérance 1 px), réajustement de chaque segment par une droite des moindres carrés totaux et sommets placés à l'intersection des droites voisines (garde-fous : droites quasi parallèles, sommet éloigné du tracé). Coins réindexés. Sur CA01 : 5 081 points → 88 sommets.
  - `EdgeBandGuard` (v2.edge) : le facteur d'affinage (issu d'un masque binaire flouté) est forcé à 1 dans une bande de 2 px autour du polygone, pour ne pas réintroduire l'escalier des pixels sur les grands segments ; il reste appliqué au-delà (fuites intérieures).
  - `V2Config.polygonTolerance` (défaut 1.0 ; 0 = contour lissé point par point, constructeur à 25 paramètres conservé) ; option CLI `--polygon-tolerance <px>`.
  - Tests : `PolygonalSimplifierTest` (carré bruité → 4 sommets exacts, cercle facetté sous tolérance, désactivation), `EdgeBandGuardTest`.
- **Accrochage du contour sur le bord vectoriel des routes** (nouveau paquet `v2.edge`) :
  - `RoadEdgeField` : champ continu de « routéité » `clamp((B − R) / 27, 0, 1)` extrait de l'anti-crénelage de la carte ; son iso-ligne 0,5 restitue le bord de chaussée au sub-pixel.
  - `RoadEdgeSnapper` : pour chaque point du contour lissé, recherche le long de la normale extérieure (±8 px, pas 0,25 px) du passage chaussée → fond le plus proche, précédé d'au moins 3 px de chaussée ; régularisation des décalages (médiane glissante ±12 points, rejet > 1 px, lissage gaussien pondéré σ = 6 points) et transition continue vers les tronçons non accrochés (traversées, débouchés de rues).
  - `RoadEdgeSnapConfig` : hyperparamètres immuables (`defaultConfig()`, `disabled()`).
  - Intégration dans `V2Pipeline` (étape 5 bis, entre le lissage et l'affinage) ; `V2Config.roadEdgeSnapEnabled` (défaut `true`, constructeur à 24 paramètres conservé) ; option CLI `--no-road-snap`.
  - Sur CA01 : 95,8 % du contour accroché au bord des routes ; bords rectilignes et réguliers, chaussée conservée sur toute sa largeur.
  - Tests : `RoadEdgeSnapperTest` (accrochage sub-pixel sur route synthétique anti-crénelée, désactivation, absence de route).
- `PartialSliverFilter` (vote) : filtre des lamelles fines issues des cellules partielles. Lorsque le polygone d'intention déborde de quelques pixels au-delà d'une route frontière, les îlots voisins (couverture 5 à 7 %) produisaient des lamelles de 10 à 15 px conservées dans `T` ; leur basculement autour du seuil `lo = 0.05` créait des crans sur le contour (CA01, route de Carquefou). Les composantes cellule partielle ∩ polygone d'épaisseur maximale < `minPartialThickness` (défaut 20 px) sont désormais retirées de `T` et des masques partiels. Paramètre exposé dans `CellSelectionPolicy`, `V2Config` et en CLI (`--min-partial-thickness`, `0` = comportement de l'étalon Python v7). Écart volontaire vis-à-vis de l'étalon.
- Tests : `AlphaRefinerTest.testDefaultConfigDoesNotShieldRoundaboutSurroundings` (et `testRefinementWithRoundabout` passé en bouclier explicite), `PartialSliverFilterTest`, `PartialCellResolverTest.testResolveRemovesThinPartialSliver` ; `TopologicalVoteEngineTest.testExecuteDefaultPolicy` adapté (lamelle de 1 px retirée par défaut, 40 px avec filtre désactivé).
- Tests de non-régression : `AlgebraicEllipseFitterTest.testFitOrientationAllQuadrants`, `HermiteSplineConnectorTest.testDiskOverlapUsesConsolidatedFilledMask`, `StraightSegmentSmootherTest.testClosedContourWithZeroOrOneCornerIsSmoothed`, `V2CliRunnerTest.testDefaultRoadSourceIsStyleDetection`, `V2CliRunnerTest.testRejectsUnknownRoadSource`.

## [Non publié] - 2026-10-05

### Ajouté
- **Sprint 9 (V2) - Rendu Sub-Pixel Supersampling (SS=4), Export RGBA & CLI V2 (Parachèvement V2)** :
  - `RenderResult` : Record immuable regroupant l'ensemble des livrables graphiques finaux : `clipped` (ARGB 32-bit), `mask` (TYPE_BYTE_GRAY 8-bit), `overlay` (RGB avec contour rouge `#FF0000`) et `coverageMask` (`CoverageMask` continu $[0..255]$).
  - `V2Config` : Record immuable unifié encapsulant l'intégralité des hyperparamètres des Sprints 1 à 9, avec validation défensive des bornes et méthodes de conversion vers les sous-configurations (`CellSelectionPolicy`, `ExpansionConfig`, `ContourSmoothingConfig`, `AlphaRefinementConfig`).
  - `SupersampleRenderer` : Rastériseur vectoriel haute-fidélité par sur-échantillonnage ($SS=4$, 16 sous-pixels/px) avec décalage de demi-pixel ($+0.5\text{ px}$), réduction par Box Filter et modulation point par point par la matrice d'affinage spectral `AlphaRefinementMap`.
  - `ImageClipper` : Découpeur et assembleur des livrables graphiques avec composition alpha sans crénelage, extraction morphologique de bordure frontière et projection sur la carte source.
  - `V2Pipeline` : Orchestrateur complet de bout en bout reliant de façon fonctionnelle et modulaire les 9 étapes algorithmiques (Sprints 1 à 9).
  - `V2CliRunner` : Point d'entrée de ligne de commande dédié au pipeline V2 avec gestion des drapeaux (`--map`, `--json`, `--road`, `--out-dir`, `--output`, `--mask-out`, `--overlay-out`, `--ss`, `--mode`, `--crop-margin`, `--eps`, etc.), validation des fichiers et affichage de statistiques d'exécution en français.
  - `CliRunner` : Aiguillage automatique vers `V2CliRunner` lors de la présence de l'option `--v2`.
  - Tests unitaires et d'intégration : `RenderResultTest`, `V2ConfigTest`, `SupersampleRendererTest`, `ImageClipperTest`, `V2PipelineTest`, `V2CliRunnerTest`.
  - Test d'intégration pivot CA01 `Sprint9IntegrationTest` validant l'ensemble de la chaîne de bout en bout avec concordance IoU de 98.6938% vs `CA01_mask_v7.png` et temps de calcul de 1574 ms (budget $\le 2500\text{ ms}$).

- **Sprint 8 (V2) - Affinage Spectral de la Couverture Alpha & Anti-Aliasing Réel** :
  - `AllowedRegionBuilder` : Construction du masque binaire de l'espace autorisé $M_{\text{allowed}} = M_{\text{fill}} \lor R_c \lor \text{îlots ronds-points}$ et comblement sélectif des cavités intérieures compactes de chaussée ($< 2000\text{ px}$).
  - `SoftThresholdFilter` : Filtre de convolution gaussienne séparable 2D ($\sigma = 1.4\text{ px}$) avec conditions aux limites réfléchies (miroir demi-échantillon) et fonction de transfert à contraste renforcé ($FK = 2.0$) clampée dans $[0.0 .. 1.0]$, optimisé pour une exécution ultra-rapide en cache L1.
  - `RoundaboutExemptionModulator` : Modulateur d'exemption spectrale sanctuarisant les carrefours giratoires substitués par un bouclier interpolé en Smoothstep cubique $C^1$ ($3t^2 - 2t^3$) entre $FZ_0 = 2.3$ et $FZ_1 = 3.0$ en rayons normés d'ellipse.
  - `AlphaRefiner` : Façade d'orchestration de bout en bout de l'affinage spectral avec injection de dépendances et surcharge de commodité avec configuration par défaut.
  - `AlphaRefinementConfig` : Record immuable regroupant les hyperparamètres calibrés (`gaussianBlurSigma`, `contrastStiffness`, `roundaboutBufferInner`, `roundaboutBufferOuter`, `roadHoleMaxArea`).
  - `AlphaRefinementMap` : Record immuable encapsulant la matrice locale 2D `float[][] factor` sur la `CropWindow` avec copie défensive et accesseur unitaire `factorAt(x, y)`.
  - `SmoothVectorContour` & `Roundabout` : Traçabilité des ronds-points substitués et exposition de `cellId`.
  - Tests unitaires et d'intégration complets : `AllowedRegionBuilderTest`, `SoftThresholdFilterTest`, `RoundaboutExemptionModulatorTest`, `AlphaRefinementModelTest`, `AlphaRefinerTest`.
  - Test d'intégration pivot CA01 `Sprint8IntegrationTest` validant l'enchaînement des Sprints 1 à 8, la sanctuarisation totale du giratoire ($\text{factor} = 1.0f$), la neutralisation des pixels résiduels en débordement de chaussée et le respect du budget de performance (~330 ms pour un budget $\le 1000\text{ ms}$).

- **Sprint 7 (V2) - Lissage Adaptatif Multi-Échelle des Tronçons Droits (Double LQR)** :
  - `StraightSegmentSmoother` : Composant dédié (SRP) exécutant un double lissage LQR multi-échelle ($\sigma = 22.0$ et $\sigma_{wide} = 59.4$) avec évaluation d'écart géométrique local $\Delta = \|\mathbf{sm}_b - \mathbf{sm}_a\|$, seuillage de rectitude ($ST\_T = 3.0\text{ px}$) et transition continue $C^1$ par convolution boîte 1D avec réplication de bord ($k = 10$).
  - `ContourSmoothingConfig` : Enrichissement du record immuable avec les 4 hyperparamètres étalonnés sur Python V7 (`straightFactor`, `straightScale`, `straightThreshold`, `straightTransitionK`) et validation d'invariants.
  - `RobustLqrSmoother.LqrResidualResult` : Record immuable regroupant les signaux résiduels lissés `fitX` et `fitY`.
  - `RobustLqrSmoother.smoothResiduals` : Exposition de la méthode de lissage sur les résidus 2D avec factorisation de la boucle itérative conjointe.
  - `ContourSmoothingEngine` : Intégration de `StraightSegmentSmoother` dans le pipeline vectoriel sub-pixel avec support de l'injection de dépendances.
  - Tests unitaires complets : `StraightSegmentSmootherTest`, extension de `ContourDomainModelTest`, `RobustLqrSmootherTest` et `ContourSmoothingEngineTest`.
  - Test d'intégration pivot CA01 `Sprint7IntegrationTest` validant l'absorption des encoches d'icônes, la préservation des 5 coins majeurs, la régularité spatiale (5 215 points sub-pixels à pas 1.0 px) et la performance d'exécution (~230 ms pour un budget $\le 1000\text{ ms}$).
  - Documentation et traçabilité : Mise à jour de `JOURNAL.md`, `docs/SPRINTS_V2.md`, `docs/sprints_v2/sprint-7-lissage-troncons-droits.md` et plan d'exécution du Sprint 7.

## [Sprint 6] - 2026-10-04
### Ajouté
- Géométrie Sub-Pixel, Lissage Robuste LQR & Détection/Substitution des Ronds-points.

## [Sprint 5] - 2026-10-04
### Ajouté
- Reconstruction des Frontières Routières, Expansion Géodésique & `ConsolidatedMask`.

## [Sprint 4] - 2026-10-04
### Ajouté
- Moteur de Vote Topologique & Résolution des Parcelles Ouvertes.

## [Sprint 3] - 2026-10-04
### Ajouté
- Segmentation en Cellules (4-connexité) & `CellGraph`.

## [Sprint 2] - 2026-10-03
### Ajouté
- Détection Colorimétrique des Routes & Fermeture Topologique.

## [Sprint 1] - 2026-10-02
### Ajouté
- Socle Géométrique, Projection Web Mercator & Rasterisation de Polygones.
