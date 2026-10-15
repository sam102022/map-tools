# GEMINI.md — Instructions et Mandats du Projet Photoshop (Map Snapping)

Bienvenue dans le workspace du projet **Photoshop Map Snapping** (`com.sam102022.photoshop`) ! 
Ce fichier contient les directives architecturales, les choix techniques majeurs et les standards d'ingénierie que tout agent CLI ou développeur doit respecter rigoureusement.

---

## 🏗️ 1. Architecture & Décisions Structurantes (ADRs)

L'ensemble des choix d'architecture et des standards techniques est consigné sous forme d'**Architecture Decision Records (ADR)** au format Nygard dans le dossier [`docs/adr/`](docs/adr/README.md) :

*   **[ADR-001](docs/adr/ADR-001-socle-100-pourcent-java-standard-sans-dependances-natives.md) :** Socle 100% Java standard sans dépendances natives (Java 21, Java2D, Swing).
*   **[ADR-002](docs/adr/ADR-002-modele-de-masques-hybrides-binarymask-et-coveragemask.md) :** Modèle de masques matriciels hybrides (`BinaryMask` & `CoverageMask`).
*   **[ADR-003](docs/adr/ADR-003-double-mode-d-execution-cli-et-gui-swing.md) :** Double mode d'exécution CLI headless et GUI Swing interactive.
*   **[ADR-004](docs/adr/ADR-004-immutabilite-de-la-configuration-via-records-snappingconfig.md) :** Immutabilité de la configuration via les Java Records (`SnappingConfig`).
*   **[ADR-005](docs/adr/ADR-005-recalage-par-propagation-geodesique-et-barrieres-routieres.md) :** Recalage par propagation géodésique et barrières routières.
*   **[ADR-006](docs/adr/ADR-006-le-projet-est-documente-avant-d-etre-developpe.md) :** Le projet est documenté avant d'être développé (Spec -> Plan -> Code).
*   **[ADR-007](docs/adr/ADR-007-developpement-par-sprints.md) :** Développement par sprints thématiques incrémentaux.
*   **[ADR-008](docs/adr/ADR-008-une-seule-responsabilite-par-classe.md) :** Une seule responsabilité par classe (Single Responsibility Principle - SRP).
*   **[ADR-009](docs/adr/ADR-009-les-logs-suivent-une-strategie-strictement-hierarchisee.md) :** Logs en français suivant une hiérarchie stricte (INFO, DEBUG, ERROR).
*   **[ADR-010](docs/adr/ADR-010-suivi-et-trace-journaliere-de-l-evolution.md) :** Suivi et trace journalière de l'évolution du projet (`JOURNAL.md`).
*   **[ADR-011](docs/adr/ADR-011-limitation-complexite-cognitive.md) :** Limitation stricte de la complexité cognitive à 15 par méthode.
*   **[ADR-012](docs/adr/ADR-012-usage-des-imports-explicites-et-syntaxe-simplifiee.md) :** Usage des imports explicites et proscription des noms pleinement qualifiés (FQCN).
*   **[ADR-013](docs/adr/ADR-013-standardisation-timezone-localdate-now.md) :** Standardisation du fuseau horaire (`LocalDate.now(ZoneId.systemDefault())`).
*   **[ADR-014](docs/adr/ADR-014-javadoc-obligatoire-classes-methodes.md) :** Documentation Javadoc exhaustive en français obligatoire sur l'ensemble des types et méthodes.

---

## 🛠️ 2. Standards de Codage & Conventions

*   **Modèles de Domaine :** Utilisation systématique des **Java Records** pour garantir l'immuabilité et la thread-safety (ADR-004).
*   **Documentation Javadoc Systématique Obligatoire (FR) :** Pour garantir la maintenabilité à long terme, chaque classe, record, interface ou énumération, ainsi que chaque méthode (publique, protégée ou privée) doit être obligatoirement précédée d'un bloc de documentation Javadoc complet rédigé en français, décrivant son rôle, ses paramètres (`@param`), ses retours (`@return`) et ses exceptions propagées (ADR-014).
*   **Imports Explicites :** Proscription des FQCN dans le code au profit d'imports explicites en haut de fichier (ADR-012).
*   **Complexité Cognitive :** Plafonnée à 15 par méthode avec découpage en sous-méthodes privées descriptives (ADR-011).

---

## 🧪 3. Standards de Validation (Tests)

*   **Qualité :** Aucune modification de code n'est considérée comme valide sans que la suite complète de validation (`mvn test`) ne soit exécutée et n'affiche un taux de réussite de 100%.
