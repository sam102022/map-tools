const fs = require('fs');
const path = require('path');

const OVERPASS_ENDPOINT = process.env.OVERPASS_URL || 'https://overpass-api.de/api/interpreter';
const EXCLUDED_HIGHWAY_CLASSES = [
    'footway', 'path', 'cycleway', 'steps', 'pedestrian', 'track',
    'construction', 'proposed', 'bridleway', 'corridor', 'elevator'
];

function usage() {
    console.error('Usage: node fetch_osm_roads.js <capture.json|territory-number> [output.json]');
    console.error('Example: node fetch_osm_roads.js CA01');
}

function findMetadataForTerritoryNumber(territoryNumber) {
    const capturesRoot = path.join(__dirname, 'captures_maps');
    const matches = [];

    function visit(directory) {
        if (!fs.existsSync(directory)) return;
        for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
            const entryPath = path.join(directory, entry.name);
            if (entry.isDirectory()) {
                visit(entryPath);
            } else if (entry.isFile()
                    && entry.name.toLowerCase() === '01_plan_avec_territoires.json') {
                try {
                    const metadata = JSON.parse(fs.readFileSync(entryPath, 'utf8'));
                    if (String(metadata?.territory?.number) === String(territoryNumber)) {
                        matches.push(entryPath);
                    }
                } catch {
                    // Ignorer les autres fichiers JSON du dossier de captures.
                }
            }
        }
    }

    visit(capturesRoot);
    if (matches.length > 1) {
        throw new Error(`Plusieurs fichiers JSON correspondent au territoire ${territoryNumber}. Passe le chemin du fichier voulu.`);
    }
    return matches[0] || null;
}

function resolveMetadataPath(input) {
    const suppliedPath = path.resolve(input);
    if (fs.existsSync(suppliedPath) && fs.statSync(suppliedPath).isFile()) {
        return suppliedPath;
    }

    // Un identifiant ASCII évite les problèmes d'encodage de chemins contenant °, accents, etc.
    if (/^[A-Za-z0-9_-]+$/.test(input)) {
        const matchedPath = findMetadataForTerritoryNumber(input);
        if (matchedPath) return matchedPath;
    }

    throw new Error(`Fichier ou numéro de territoire introuvable : ${input}`);
}

function readCaptureMetadata(metadataPath) {
    const metadata = JSON.parse(fs.readFileSync(metadataPath, 'utf8'));
    const bounds = metadata?.map?.bounds;
    const sw = bounds?.southWest;
    const ne = bounds?.northEast;

    if (![sw?.lat, sw?.lng, ne?.lat, ne?.lng].every(Number.isFinite)) {
        throw new Error('Le JSON doit contenir map.bounds.southWest et map.bounds.northEast.');
    }
    if (sw.lat >= ne.lat || sw.lng >= ne.lng) {
        throw new Error('Les limites géographiques du JSON sont incohérentes.');
    }

    return metadata;
}

function buildQuery(metadata) {
    const { southWest: sw, northEast: ne } = metadata.map.bounds;
    const excluded = EXCLUDED_HIGHWAY_CLASSES.join('|');
    return `[out:json][timeout:40];\n`
        + `way["highway"]["highway"!~"^(${excluded})$"]`
        + `(${sw.lat},${sw.lng},${ne.lat},${ne.lng});\n`
        + 'out tags geom;';
}

async function requestOverpass(query) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 50_000);

    try {
        const response = await fetch(OVERPASS_ENDPOINT, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'User-Agent': 'RoadSnapper/1.0 (local map processing)'
            },
            body: new URLSearchParams({ data: query }),
            signal: controller.signal
        });

        const responseText = await response.text();
        if (!response.ok) {
            throw new Error(`Overpass a répondu HTTP ${response.status}: ${responseText.slice(0, 500)}`);
        }

        let payload;
        try {
            payload = JSON.parse(responseText);
        } catch {
            throw new Error(`Réponse Overpass illisible : ${responseText.slice(0, 500)}`);
        }

        if (!Array.isArray(payload.elements)) {
            throw new Error('La réponse Overpass ne contient pas de liste elements.');
        }
        return payload;
    } finally {
        clearTimeout(timeout);
    }
}

async function main() {
    const metadataPath = process.argv[2];
    if (!metadataPath) {
        usage();
        process.exitCode = 2;
        return;
    }

    const resolvedMetadataPath = resolveMetadataPath(metadataPath);
    const outputPath = path.resolve(process.argv[3]
        || path.join(path.dirname(resolvedMetadataPath), 'osm_roads.json'));

    console.log(`-> Lecture du cadrage : ${resolvedMetadataPath}`);
    const metadata = readCaptureMetadata(resolvedMetadataPath);
    const query = buildQuery(metadata);

    console.log('-> Interrogation d’OpenStreetMap Overpass pour les routes de la capture...');
    const payload = await requestOverpass(query);
    const roads = payload.elements
        .filter(element => element.type === 'way'
            && typeof element.tags?.highway === 'string'
            && Array.isArray(element.geometry))
        .map(element => ({
            id: element.id,
            tags: element.tags,
            geometry: element.geometry.map(point => ({ lat: point.lat, lon: point.lon }))
        }));

    const result = {
        schemaVersion: 1,
        source: 'OpenStreetMap Overpass API',
        attribution: '© OpenStreetMap contributors',
        fetchedAt: new Date().toISOString(),
        captureMetadata: path.basename(resolvedMetadataPath),
        extent: metadata.map.bounds,
        map: metadata.map,
        territory: metadata.territory,
        osmBaseTimestamp: payload.osm3s?.timestamp_osm_base || null,
        roadCount: roads.length,
        roads
    };

    fs.mkdirSync(path.dirname(outputPath), { recursive: true });
    fs.writeFileSync(outputPath, JSON.stringify(result, null, 2), 'utf8');
    console.log(`   ${roads.length} tronçons OSM enregistrés dans : ${outputPath}`);
    console.log('   Attribution à conserver : © OpenStreetMap contributors');
}

main().catch(error => {
    console.error(`Échec de récupération des routes OSM : ${error.message}`);
    process.exitCode = 1;
});
