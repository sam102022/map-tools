const {chromium} = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE_URL = 'http://localhost/maps/google/';
const OUTPUT_DIR = path.join(__dirname, 'captures_maps');

// Sélecteur du conteneur de la carte Google Maps
const MAP_SELECTOR = '#map';

(async () => {
    console.log('-> Lancement du navigateur Chromium...');
    const browser = await chromium.launch({
        headless: false, // Navigateur visible pour permettre l'ajustement du zoom
        slowMo: 50
    });

    const context = await browser.newContext({
        viewport: {width: 1920, height: 1080}
    });
    const page = await context.newPage();

    console.log(`-> Connexion à ${BASE_URL}...`);
    await page.goto(BASE_URL, {waitUntil: 'networkidle'});

    // Masquage des ascenseurs visuels et contrôles parasites Google Maps
    // await page.addStyleTag({
    //     content: `
    //         ::-webkit-scrollbar { display: none !important; }
    //         * { scrollbar-width: none !important; }
    //         .gmnoprint, .gm-style-cc { display: none !important; }
    //     `
    // });

    // 1. Sélection de la localité
    const localitiesSelect = page.locator('#localities');
    await localitiesSelect.waitFor({state: 'visible'});

    const localitiesOptions = await localitiesSelect.locator('option').evaluateAll(opts =>
        opts.map(opt => ({
            value: opt.value,
            text: opt.text.trim()
        })).filter(opt => opt.value && opt.value !== '' && !opt.text.toLowerCase().includes('sélection'))
    );

    if (localitiesOptions.length > 0) {
        const locality = localitiesOptions[0]; // Première localité par défaut (ex: Carquefou)
        console.log(`-> Localité sélectionnée : ${locality.text}`);
        await localitiesSelect.selectOption(locality.value);
        await page.waitForTimeout(1000);
    }
    const sizesWithSelect = page.locator('#sizesWith');
    const sizesHeightSelect = page.locator('#sizesHeight');

    // 2. Récupération des territoires disponibles
    const territorySelect = page.locator('#territories');
    await territorySelect.waitFor({state: 'visible'});

    let options = await territorySelect.locator('option').evaluateAll(opts =>
        opts.map(opt => ({
            value: opt.value,
            text: opt.text.trim()
        })).filter(opt => opt.value && opt.value !== '' && !opt.text.toLowerCase().includes('sélection'))
    );

    const requestedTerritory = process.env.CAPTURE_TERRITORY?.trim();
    if (requestedTerritory) {
        options = options.filter(option => option.value === requestedTerritory);
        if (options.length === 0) {
            await browser.close();
            throw new Error(`Territoire ${requestedTerritory} absent de la liste de la localité sélectionnée.`);
        }
        console.log(`-> Capture ciblée sur le territoire ${requestedTerritory}.`);
    }

    console.log(`-> ${options.length} territoires détectés.`);

    /**
     * Active ou désactive une case à cocher par son identifiant DOM.
     */
    async function setCheckbox(id, shouldCheck) {
        const checkbox = page.locator('#' + id);
        if (await checkbox.count() > 0) {
            const isChecked = await checkbox.isChecked();
            if (shouldCheck && !isChecked) {
                const camera = await readMapCamera();
                await checkbox.check();
                await restoreMapCamera(camera);
            } else if (!shouldCheck && isChecked) {
                const camera = await readMapCamera();
                await checkbox.uncheck();
                await restoreMapCamera(camera);
            }
        }
    }

    async function readMapCamera() {
        return page.evaluate(() => {
            if (typeof map === 'undefined') return null;
            const center = map.getCenter();
            return center ? {
                lat: center.lat(),
                lng: center.lng(),
                zoom: map.getZoom(),
                heading: map.getHeading() || 0,
                tilt: map.getTilt() || 0
            } : null;
        });
    }

    async function restoreMapCamera(camera) {
        if (!camera) return;
        await page.evaluate(view => {
            if (typeof map !== 'undefined') {
                map.setOptions({
                    center: { lat: view.lat, lng: view.lng },
                    zoom: view.zoom,
                    heading: view.heading,
                    tilt: view.tilt
                });
            }
        }, camera);
        await page.waitForTimeout(200);
    }

    /**
     * Applique un style cartographique Google Maps via son identifiant API ou via le bouton du panneau.
     */
    async function setMapStyle(styleTypeId, labelText) {
        // 1. Définition directe via l'API Google Maps
        const appliedDirectly = await page.evaluate(type => {
            // `map` est une liaison globale `let`, pas une propriété de `window`.
            // Passer par l'API évite de cliquer sur les contrôles visibles de la page.
            if (typeof map !== 'undefined' && map.setMapTypeId) {
                map.setMapTypeId(type);
                return true;
            }
            return false;
        }, styleTypeId);

        if (appliedDirectly) {
            return;
        }

        // 2. Clic alternatif sur le bouton de l'interface si présent
        const styleBtn = page.locator(`button:has-text("${labelText}")`)
            .or(page.locator(`label:has-text("${labelText}")`))
            .or(page.getByRole('button', {name: new RegExp(labelText, 'i')}));

        if (await styleBtn.count() > 0) {
            try {
                await styleBtn.first().click({timeout: 500});
            } catch {
                // Style déjà actif
            }
        }
    }

    /**
     * Capture l'intégralité du conteneur de la carte en tenant compte des ascenseurs
     * (équivalent automatique de Screengrab sans coupure ni ascenseurs visibles).
     */
    async function takeFullMapScreenshot(outputPath, metadataForTerritory) {
        const mapElement = page.locator(MAP_SELECTOR);
        await mapElement.waitFor({state: 'visible'});

        // Mesurer la taille totale réelle de la carte (y compris ce qui déborde dans les ascenseurs)
        // const dims = await mapElement.evaluate(el => ({
        //     width: Math.max(el.scrollWidth, el.offsetWidth, el.clientWidth),
        //     height: Math.max(el.scrollHeight, el.offsetHeight, el.clientHeight)
        // }));

        // Ajuster la fenêtre du navigateur pour englober la totalité de la carte
        // await page.setViewportSize({
        //     width: Math.max(dims.width + 50, 1920),
        //     height: Math.max(dims.height + 50, 1080)
        // });

        // Ne pas déclencher l'événement resize : il peut recadrer la carte alors
        // que l'utilisateur vient de régler son zoom ou son cadrage.
        // Laisser le temps aux tuiles et tracés de se dessiner sans toucher à la caméra.
        await page.waitForTimeout(2400);

        // Sauvegarder les coordonnées du territoire et l'état de la caméra Google
        // exactement au même moment que la capture. Le JSON porte le même nom que
        // l'image afin que RoadSnapper puisse retrouver facilement les métadonnées.
        if (metadataForTerritory) {
            const metadata = await page.evaluate(() => {
                if (typeof map === 'undefined' || typeof placemarks === 'undefined') {
                    throw new TypeError('La carte Google ou les données KML ne sont pas disponibles.');
                }

                const territorySelect = document.getElementById('territories');
                const territoryNumber = territorySelect?.value ?? '';
                const territory = placemarks.find(placemark => {
                    const values = placemark?.vars?.val;
                    return values
                        && String(values.NUMBER) === String(territoryNumber)
                        && (!values.ZONE || String(values.ZONE) === '00');
                });

                if (!territory) {
                    throw new Error(`Impossible de trouver le territoire sélectionné (${territoryNumber}).`);
                }

                // GeoXML3 expose les coordonnées KML sous forme d'objets {lat, lng}.
                // Conserver les anneaux intérieurs permet aussi de représenter d'éventuels trous.
                const polygons = (territory.Polygon || []).map(polygon => ({
                    outerRings: (polygon.outerBoundaryIs || []).map(boundary =>
                        (boundary.coordinates || []).map(point => ({lat: point.lat, lng: point.lng}))
                    ),
                    innerRings: (polygon.innerBoundaryIs || []).map(boundary =>
                        (boundary.coordinates || []).map(point => ({lat: point.lat, lng: point.lng}))
                    )
                }));

                const bounds = map.getBounds();
                const center = map.getCenter();
                const mapElement = document.getElementById('map');
                const mapRect = mapElement.getBoundingClientRect();

                const toLatLng = point => point ? {lat: point.lat(), lng: point.lng()} : null;

                return {
                    schemaVersion: 1,
                    capturedAt: new Date().toISOString(),
                    territory: {
                        number: String(territoryNumber),
                        title: territory.vars.val.TITLE || '',
                        locality: territory.vars.val.name || '',
                        polygons
                    },
                    map: {
                        bounds: bounds ? {
                            southWest: toLatLng(bounds.getSouthWest()),
                            northEast: toLatLng(bounds.getNorthEast())
                        } : null,
                        center: toLatLng(center),
                        zoom: map.getZoom(),
                        heading: map.getHeading() ?? 0,
                        tilt: map.getTilt() ?? 0,
                        mapTypeId: map.getMapTypeId(),
                        container: {
                            width: mapRect.width,
                            height: mapRect.height,
                            pixelWidth: Math.round(mapRect.width * window.devicePixelRatio),
                            pixelHeight: Math.round(mapRect.height * window.devicePixelRatio),
                            devicePixelRatio: window.devicePixelRatio
                        }
                    }
                }
            });
            const metadataPath = outputPath.replace(/\.png$/i, '.json');
            fs.writeFileSync(metadataPath, JSON.stringify(metadata, null, 2), 'utf8');
            console.log(`      Métadonnées : ${metadataPath}`);
        }

        // Capturer uniquement le conteneur de la carte (aucun ascenseur visible)
        await mapElement.screenshot({path: outputPath});
    }

    /**
     * Affiche une barre d'action flottante sur la page et attend la validation utilisateur à l'écran.
     * Permet d'ajuster le zoom et le cadrage à la souris avant de déclencher les 4 captures.
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
                    <span style="font-size: 14px; font-weight: bold; color: #fff;">
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

            const btnCapture = document.getElementById('pw-btn-capture');
            btnCapture.onclick = () => {
                btnCapture.innerText = '⏳ Captures en cours...';
                btnCapture.style.background = '#e0a800';
                window.__pw_action = 'capture';
            };

            document.getElementById('pw-btn-skip').onclick = () => {
                window.__pw_action = 'skip';
            };

            document.getElementById('pw-btn-stop').onclick = () => {
                window.__pw_action = 'stop';
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

    // 3. Boucle interactive sur chaque territoire
    for (let i = 0; i < options.length; i++) {
        const territory = options[i];
        let cleanName = territory.text.replace(/[/\\?%*:|"<>]/g, '_');
        cleanName = cleanName.replace(/[/n°]/g, '');
        const territoryDir = path.join(OUTPUT_DIR, cleanName);

        if (!fs.existsSync(territoryDir)) {
            fs.mkdirSync(territoryDir, {recursive: true});
        }

        console.log(`\n[${i + 1}/${options.length}] Territoire : ${territory.text}`);

        // Sélection du territoire dans la liste déroulante
        await territorySelect.selectOption(territory.value);
        await page.waitForTimeout(1500); // Laisse le temps initial à Google Maps de se centrer

        // Pause interactive : L'utilisateur règle le zoom et le cadrage à l'écran
        const userAction = await waitForUserOnScreenValidation(territory.text, i + 1, options.length);

        if (userAction === 'stop') {
            console.log('   ⏹️ Arrêt demandé par l\'utilisateur.');
            break;
        }

        if (userAction === 'skip') {
            console.log('   ⏭️ Territoire ignoré par l\'utilisateur.');
            continue;
        }

        // --- CAPTURE 1 : Style par défaut ("Plan") + "Afficher territoires" + "Afficher zones" ---
        console.log('   1. Capture : Plan (avec territoires)');
        //await setCheckbox('displayTerritories', true);
        //await setCheckbox('displayZones', false);
        //await setMapStyle('roadmap', 'Plan');
        await takeFullMapScreenshot(path.join(territoryDir, '01_plan_avec_territoires.png'), true);

        console.log('   2. Capture : Plan (avec zones)');
        await setCheckbox('displayTerritories', false);
        await setCheckbox('displayZones', true);
        await setMapStyle('roadmap', 'Plan');
        await takeFullMapScreenshot(path.join(territoryDir, '02_plan_avec_zones.png'), false);

        // Décocher "Afficher territoires" et "Afficher zones" pour les 3 captures suivantes
        await setCheckbox('displayTerritories', false);
        await setCheckbox('displayZones', false);

        // --- CAPTURE 3 : "Style contrasté" ---
        console.log('   3. Capture : Style contrasté');
        await setMapStyle('styled_map1', 'Style contrasté');
        await takeFullMapScreenshot(path.join(territoryDir, '03_style_contraste.png'), false);

        // --- CAPTURE 4 : "Style contrasté sans routes" ---
        console.log('   4. Capture : Style contrasté sans routes');
        await setMapStyle('styled_map2', 'Style contrasté sans routes');
        await takeFullMapScreenshot(path.join(territoryDir, '04_style_contraste_sans_routes.png'), false);

        // --- CAPTURE 5 : "Style contrasté sans rien" ---
        console.log('   5. Capture : Style contrasté sans rien');
        await setMapStyle('styled_map3', 'Style contrasté sans rien');
        await takeFullMapScreenshot(path.join(territoryDir, '05_style_contraste_sans_rien.png'), false);

        console.log('   Point de départ : Plan (avec territoires)');
        await setCheckbox('displayTerritories', true);
        await setCheckbox('displayZones', false);
        await setMapStyle('roadmap', 'Plan');

        // Initialiser les sélecteurs de taille à 100% pour la prochaine itération
        await sizesWithSelect.selectOption('100');
        await sizesHeightSelect.selectOption('100');
    }

    console.log(`\nTerminé ! Toutes les captures sont enregistrées dans : ${path.resolve(OUTPUT_DIR)}`);
    await browser.close();
})();
