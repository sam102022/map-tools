# Architecture Decision Records (ADR) — Photoshop Map Snapping

Ce répertoire rassemble l'ensemble des **Architecture Decision Records (ADR)** du projet d'automatisation du détourage cartographique et de recalage sur réseau routier (`com.sam102022.photoshop`).

Chaque document formalise un choix architectural ou une décision d'ingénierie structurante, en décrivant son **contexte**, la **décision** prise et ses **conséquences** (positives, négatives et neutres), selon le standard de Michael Nygard.

---

## 📑 Registre des Décisions d'Architecture

| Identifiant | Titre | Catégorie | Statut | Résumé de la Décision |
| :--- | :--- | :--- | :---: | :--- |
| [**ADR-001**](ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md) | Socle 100% Java standard sans dépendances natives | Architecture & Socle | ✅ Acceptée | Utilisation exclusive de Java 21, Java2D et Swing. Zéro dépendance native C++/OpenCV pour une portabilité totale. |
| [**ADR-002**](ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md) | Modèle de masques matriciels hybrides | Conception & Modélisation | ✅ Acceptée | Double représentation : `BinaryMask` (tableau 1D `boolean[]`) pour la performance matricielle et `CoverageMask` (`byte[]`) pour l'antialiasing sub-pixel. |
| [**ADR-003**](ADR-003-double-mode-d-execution-cli-et-gui-swing.md) | Double mode d'exécution CLI headless et GUI Swing | Architecture & Présentation | ✅ Acceptée | Moteur algorithmique pur (`RoadSnappingEngine`) invocable indifféremment en CLI batch headless ou en GUI interactive multi-panneaux. |
| [**ADR-004**](ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md) | Immutabilité de la configuration via les Java Records | Conception & Modélisation | ✅ Acceptée | Modélisation de `SnappingConfig` en Java Record immuable avec validation *fail-fast* au constructeur compact et *thread-safety* absolue. |
| [**ADR-005**](ADR-005-recalage-par-propagation-geodesique-et-barrieres-routieres.md) | Recalage par propagation géodésique et barrières routières | Algorithmique & Vision | ✅ Acceptée | Aimantation par parcours BFS contraint à partir du noyau érodé du masque vert, s'arrêtant sur les barrières routières sans dérive globale. |
| [**ADR-006**](ADR-006-le-projet-est-documente-avant-d-etre-developpe.md) | Le projet est documenté avant d'être développé | Méthodologie & Processus | ✅ Acceptée | Cycle strict Spécification (`docs/specs`) -> Plan d'implémentation (`docs/plans`) -> TDD & Code. Jamais de code sans spec préalable. |
| [**ADR-007**](ADR-007-developpement-par-sprints.md) | Développement par sprints | Méthodologie & Processus | ✅ Acceptée | Feuille de route incrémentale en 7 sprints thématiques validés successivement par des suites de tests unitaires et d'intégration. |
| [**ADR-008**](ADR-008-une-seule-responsabilite-par-classe.md) | Une seule responsabilité par classe (SRP) | Qualité & Standards de code | ✅ Acceptée | Application stricte du Single Responsibility Principle : découplage étanche entre modèles, détection, géométrie, segmentation, I/O et interfaces. |
| [**ADR-009**](ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md) | Stratégie de journalisation strictement hiérarchisée | Qualité & Observabilité | ✅ Acceptée | Logs obligatoirement rédigés en français, structurés en niveaux INFO (jalons métier/pipeline), DEBUG (détails d'étape) et ERROR (anomalies contextualisées). |
| [**ADR-010**](ADR-010-suivi-et-trace-journaliere-de-l-evolution.md) | Suivi et trace journalière de l'évolution du projet | Méthodologie & Processus | ✅ Acceptée | Tenue d'un journal de bord centralisé `JOURNAL.md` à la racine pour assurer la traçabilité des refactorings et des arbitrages quotidiens. |
| [**ADR-011**](ADR-011-limitation-complexite-cognitive.md) | Limitation de la complexité cognitive du code | Qualité & Standards de code | ✅ Acceptée | Plafond strict de complexité cognitive fixé à 15 par méthode. Découpage obligatoire en sous-méthodes privées, pures et déterministes. |
| [**ADR-012**](ADR-012-usage-des-imports-explicites-et-syntaxe-simplifiee.md) | Usage des imports explicites et écriture simplifiée | Qualité & Standards de code | ✅ Acceptée | Proscription formelle des noms pleinement qualifiés (FQCN) dans le corps du code source au profit d'imports explicites en en-tête. |
| [**ADR-013**](ADR-013-standardisation-timezone-localdate-now.md) | Standardisation du fuseau horaire pour LocalDate.now | Qualité & Standards de code | ✅ Acceptée | Interdiction de `LocalDate.now()` sans argument. Obligation d'expliciter le fuseau (`ZoneId.systemDefault()`) ou d'injecter une `Clock`. |
| [**ADR-014**](ADR-014-javadoc-obligatoire-classes-methodes.md) | Javadoc obligatoire pour chaque classe et chaque méthode | Qualité & Documentation | ✅ Acceptée | Documentation Javadoc intégrale rédigée en français obligatoire sur l'ensemble des types, records, constructeurs et méthodes du projet. |

---

## 🏛️ Structure Standard d'une Décision (Format Nygard)

Chaque ADR du projet respecte rigoureusement la structure suivante :

1. **Titre :** Numéro séquentiel et libellé explicite de la décision (ex : `ADR-001 - ...`).
2. **Statut :** État actuel de la décision (`✅ Acceptée`, `⏳ Proposée`, `🔄 Remplacée`, `❌ Rejetée`).
3. **Contexte :** Problématique technique, contraintes rencontrées et raisons motivant la réflexion.
4. **Décision :** Choix architectural formellement arrêté, avec règles d'implémentation et exemples de code si pertinent.
5. **Conséquences :** Impacts sur le projet classés en conséquences positives, négatives et contraintes neutres.
