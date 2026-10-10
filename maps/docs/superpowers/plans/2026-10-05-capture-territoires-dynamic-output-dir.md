# Dossier Dynamique lors du Changement de Territoire (`capture_territoires.js`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adapter dynamiquement le dossier de sortie des captures d'écran et des métadonnées dans `capture_territoires.js` lorsque l'utilisateur modifie la sélection du territoire dans l'interface pendant la pause interactive.

**Architecture:** Définition d'un utilitaire `getTerritoryDir` pour normaliser le chemin du dossier cible. Ajout d'un écouteur d'événement `change` sur `#territories` dans `waitForUserOnScreenValidation` pour mettre à jour en direct le libellé du bandeau flottant, et transmission du territoire effectif (`territoryText`, `territoryValue`) au clic sur le bouton de capture. La boucle principale Node.js résout alors le répertoire cible effectif, le crée si nécessaire, et y dépose les 5 captures et métadonnées JSON.

**Tech Stack:** Node.js, Playwright, Vanilla JavaScript (DOM in-browser evaluation).

---

### Task 1: Extraction de l'utilitaire `getTerritoryDir`

**Files:**
- Modify: `capture_territoires.js:5-15`

- [x] **Step 1: Déclarer la fonction `getTerritoryDir` en tête de fichier**

Ajouter la fonction `getTerritoryDir` après la constante `OUTPUT_DIR` :

```javascript
/**
 * Nettoie et génère le chemin absolu du dossier de destination pour un territoire donné.
 *
 * @param {string} territoryText Libellé du territoire (ex: "Territoire CA01")
 * @returns {string} Chemin absolu du répertoire de sortie
 */
function getTerritoryDir(territoryText) {
    let cleanName = territoryText.replace(/[/\\?%*:|"<>]/g, '_');
    cleanName = cleanName.replace(/[/n°]/g, '').trim();
    return path.join(OUTPUT_DIR, cleanName);
}
```

- [x] **Step 2: Vérifier la syntaxe du fichier avec Node.js**

Run: `node -c capture_territoires.js`  
Expected: Pas d'erreur (exit code 0)

---

### Task 2: Détection du changement de sélection dans `waitForUserOnScreenValidation`

**Files:**
- Modify: `capture_territoires.js:200-280`

- [x] **Step 1: Modifier `waitForUserOnScreenValidation` pour écouter `#territories` et renvoyer le territoire sélectionné**

Dans `waitForUserOnScreenValidation`, identifier le conteneur du titre du bandeau flottant avec l'identifiant `id="pw-territory-title"`, attacher un écouteur `'change'` sur `#territories` pour actualiser le titre en direct, et enrichir l'objet `window.__pw_action` lors du clic sur le bouton "📸 Lancer les 5 captures" :

```javascript
    /**
     * Affiche une barre d'action flottante sur la page et attend la validation utilisateur à l'écran.
     * Permet d'ajuster le zoom et le cadrage à la souris avant de déclencher les 5 captures.
     */
    async function waitForUserOnScreenValidation(territoryName, currentIndex, totalCount) {
        await page.evaluate(({name, index, total}) => {
            let bar = document.getElementById('pw-floating-bar');
            if (!bar) {
                bar = document.createElement('div');
                bar.id = 'pw-floating-bar';
                document.body.appendChild(bar);
            }

            // Ancrer la barre juste sous les contrôles de sélection, plutôt qu'en
            // bas du viewport : la fenêtre Chromium peut être moins haute que le
            // viewport Playwright, ce qui rendrait une barre basse invisible.
            const mapTop = document.getElementById('map')?.getBoundingClientRect().top;
            const controlsBottom = Math.max(
                ...['#localities', '#territories', '#zones']
                    .map(selector => document.querySelector(selector)?.getBoundingClientRect().bottom || 0)
            );
            const top = Math.max(12, Math.ceil(Math.max(controlsBottom, mapTop || 0) + 8));

            bar.style.cssText = `
                position: fixed;
                top: ${top}px;
                right: 16px;
                bottom: auto;
                left: auto;
                transform: none;
                z-index: 2147483647;
                display: flex;
                align-items: center;
                gap: 14px;
                background: rgba(20, 20, 20, 0.94);
                padding: 10px 22px;
                border-radius: 40px;
                box-shadow: 0 8px 25px rgba(0,0,0,0.6);
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Arial, sans-serif;
                border: 2px solid #28a745;
                color: #ffffff;
                user-select: none;
            `;

            bar.innerHTML = `
                <div style="display:flex; flex-direction:column; line-height: 1.2;">
                    <span id="pw-territory-title" style="font-size: 14px; font-weight: bold; color: #fff;">
                        📍 [${index}/${total}] ${name}
                    </span>
                    <span style="font-size: 11px; color: #aaa;">
                        Ajustez le zoom et le cadrage à la souris
                    </span>
                </div>
                <button id="pw-btn-capture" style="
                    background: #28a745;
                    color: #fff;
                    border: none;
                    padding: 10px 20px;
                    font-size: 14px;
                    font-weight: bold;
                    border-radius: 25px;
                    cursor: pointer;
                    box-shadow: 0 2px 6px rgba(0,0,0,0.3);
                ">📸 Lancer les 5 captures</button>
                <button id="pw-btn-skip" style="
                    background: #495057;
                    color: #fff;
                    border: none;
                    padding: 10px 16px;
                    font-size: 13px;
                    border-radius: 25px;
                    cursor: pointer;
                ">⏭️ Passer</button>
                <button id="pw-btn-stop" style="
                    background: #dc3545;
                    color: #fff;
                    border: none;
                    padding: 10px 16px;
                    font-size: 13px;
                    border-radius: 25px;
                    cursor: pointer;
                ">⏹️ Arrêter</button>
            `;

            window.__pw_action = null;

            // Écoute des changements de sélection manuelle dans la liste des territoires
            const territorySelect = document.getElementById('territories');
            const onTerritoryChange = () => {
                const titleSpan = document.getElementById('pw-territory-title');
                if (titleSpan && territorySelect && territorySelect.selectedIndex >= 0) {
                    const selectedText = territorySelect.options[territorySelect.selectedIndex]?.text?.trim();
                    if (selectedText) {
                        titleSpan.textContent = `📍 [${index}/${total}] ${selectedText}`;
                    }
                }
            };
            territorySelect?.addEventListener('change', onTerritoryChange);

            const btnCapture = document.getElementById('pw-btn-capture');
            btnCapture.onclick = () => {
                btnCapture.innerText = '⏳ Captures en cours...';
                btnCapture.style.background = '#e0a800';
                const selectedText = territorySelect && territorySelect.selectedIndex >= 0
                    ? territorySelect.options[territorySelect.selectedIndex]?.text?.trim()
                    : name;
                const selectedValue = territorySelect?.value ?? '';
                window.__pw_action = {
                    action: 'capture',
                    territoryText: selectedText || name,
                    territoryValue: selectedValue
                };
            };

            document.getElementById('pw-btn-skip').onclick = () => {
                window.__pw_action = { action: 'skip' };
            };

            document.getElementById('pw-btn-stop').onclick = () => {
                window.__pw_action = { action: 'stop' };
            };
        }, {name: territoryName, index: currentIndex, total: totalCount});

        // Attente indéfinie du clic de l'utilisateur sur l'un des boutons
        const actionHandle = await page.waitForFunction(() => window.__pw_action, null, {timeout: 0});
        const action = await actionHandle.jsonValue();

        // Masquer la barre flottante pour qu'elle ne figure sur aucune capture
        await page.evaluate(() => {
            const bar = document.getElementById('pw-floating-bar');
            if (bar) bar.style.display = 'none';
        });

        return action;
    }
```

- [x] **Step 2: Vérifier la syntaxe du fichier avec Node.js**

Run: `node -c capture_territoires.js`  
Expected: Pas d'erreur (exit code 0)

---

### Task 3: Mise à jour de la boucle principale pour exploiter le dossier effectif

**Files:**
- Modify: `capture_territoires.js:280-340`

- [x] **Step 1: Mettre à jour la boucle principale d'itération**

Remplacer la création prématurée du dossier dans la boucle par la résolution dynamique post-validation :

```javascript
    // 3. Boucle interactive sur chaque territoire
    for (let i = 0; i < options.length; i++) {
        const territory = options[i];

        console.log(`\n[${i + 1}/${options.length}] Territoire : ${territory.text}`);

        // Sélection du territoire dans la liste déroulante
        await territorySelect.selectOption(territory.value);
        await page.waitForTimeout(1500); // Laisse le temps initial à Google Maps de se centrer

        // Pause interactive : L'utilisateur règle le zoom et le cadrage à l'écran
        const userAction = await waitForUserOnScreenValidation(territory.text, i + 1, options.length);

        const actionType = typeof userAction === 'string' ? userAction : userAction?.action;

        if (actionType === 'stop') {
            console.log('   ⏹️ Arrêt demandé par l\'utilisateur.');
            break;
        }

        if (actionType === 'skip') {
            console.log('   ⏭️ Territoire ignoré par l\'utilisateur.');
            continue;
        }

        // Détermination du territoire effectif (au cas où l'utilisateur a changé la sélection dans la page)
        const effectiveTerritoryText = userAction?.territoryText || territory.text;
        const territoryDir = getTerritoryDir(effectiveTerritoryText);

        if (!fs.existsSync(territoryDir)) {
            fs.mkdirSync(territoryDir, {recursive: true});
        }

        if (effectiveTerritoryText !== territory.text) {
            console.log(`   🔄 Territoire modifié manuellement : "${territory.text}" -> "${effectiveTerritoryText}"`);
        }
        console.log(`   📂 Dossier cible : ${territoryDir}`);

        // --- CAPTURE 1 : Style par défaut ("Plan") + "Afficher territoires" + "Afficher zones" ---
        console.log('   1. Capture : Plan (avec territoires)');
        await takeFullMapScreenshot(path.join(territoryDir, '01_plan_avec_territoires.png'), true);
```

- [x] **Step 2: Vérifier la syntaxe du fichier avec Node.js**

Run: `node -c capture_territoires.js`  
Expected: Pas d'erreur (exit code 0)

---

### Task 4: Validation et test de non-régression

**Files:**
- Inspect: `capture_territoires.js`

- [x] **Step 1: Test de compilation et validation globale du script**

Run: `node -c capture_territoires.js`  
Expected: Exit code 0 sans warning ni erreur de syntaxe

- [x] **Step 2: Vérification git diff des modifications**

Run: `git diff capture_territoires.js`  
Expected: Diff conforme aux 3 tâches (getTerritoryDir, écoute dynamique sur `#territories`, et calcul dynamique de `territoryDir`)
