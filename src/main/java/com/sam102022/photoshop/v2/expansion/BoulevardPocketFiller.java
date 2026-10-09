package com.sam102022.photoshop.v2.expansion;

import com.sam102022.photoshop.core.model.BinaryMask;
import com.sam102022.photoshop.v2.cell.Cell;
import com.sam102022.photoshop.v2.cell.CellLabelMap;
import com.sam102022.photoshop.v2.geometry.PixelPoint;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Comble les poches situées entre le territoire consolidé et un boulevard (chaussée large).
 * <p>
 * Le long d'un boulevard doublé d'une contre-allée, l'expansion géodésique s'engage dans les bretelles et
 * contourne leurs îlots : le contour fait alors des excursions. Pour que le contour longe le bord du
 * boulevard, les poches (bretelles, îlots, débouchés) situées à moins de {@code pocketRadius} pixels d'une
 * chaussée large sont ajoutées au territoire par une fermeture morphologique de (territoire ∪ boulevard),
 * le boulevard servant d'appui sans être lui-même ajouté. Les grands îlots non retenus (pâtés de maisons,
 * aire &gt; {@code maxCellArea}) ne sont jamais ajoutés.
 * </p>
 * <p>
 * Toutes les opérations morphologiques sont réalisées par transformée de distance euclidienne exacte
 * (dilatation : distance à l'ensemble ≤ r ; érosion : distance au complément &gt; r), en O(N).
 * </p>
 */
public class BoulevardPocketFiller {

    private static final Logger LOGGER = Logger.getLogger(BoulevardPocketFiller.class.getName());
    private static final int[] DX = {0, 0, -1, 1};
    private static final int[] DY = {-1, 1, 0, 0};

    /** Demi-largeur minimale (px) d'une chaussée considérée comme boulevard. */
    public static final double DEFAULT_WIDE_HALF_WIDTH = 8.0;
    /** Aire maximale (px) d'un îlot non retenu pouvant être absorbé dans une poche. */
    public static final long DEFAULT_MAX_CELL_AREA = 20_000L;
    /**
     * Demi-largeur minimale (px) d'une chaussée longée incluse jusqu'à son bord opposé. Plus faible que
     * {@link #DEFAULT_WIDE_HALF_WIDTH} pour inclure aussi la seconde chaussée, plus étroite, d'un boulevard à
     * chaussées séparées (étalonné sur le détourage de référence de CA02).
     */
    public static final double DEFAULT_BOULEVARD_HALF_WIDTH = 6.0;
    /** Distance maximale (px) au polygone d'intention d'une chaussée longée incluse. */
    public static final double DEFAULT_POLYGON_REACH = 30.0;
    /** Fraction minimale de la longueur d'un côté du polygone longée par une route pour suivre cette route. */
    public static final double DEFAULT_MIN_ALONG_FRACTION = 0.3;
    /** Part minimale du pourtour d'un terre-plein comblé bordée par les chaussées du boulevard. */
    static final double MIN_MEDIAN_ENCLOSURE = 0.5;
    /** Terre-plein central (px) toléré entre les chaussées séparées d'un boulevard. */
    static final int BOULEVARD_MEDIAN_GAP = 12;
    /** Interruption (px) tolérée dans une chaussée ordinaire (marquages au sol). */
    static final int MARKING_GAP = 2;
    /** Distance (px) en deçà de laquelle une chaussée est considérée au contact du territoire. */
    static final double CONTACT_TOLERANCE = 3.0;
    /** Distance maximale (px) au polygone d'intention d'une chaussée de boulevard longée incluse. */
    public static final double DEFAULT_WIDE_POLYGON_REACH = 60.0;
    /** Distance maximale (px) au territoire d'une chaussée de boulevard longé à inclure. */
    public static final double DEFAULT_BOULEVARD_REACH = 45.0;
    /** Contact minimal (px) entre le territoire et une chaussée large pour qu'elle soit considérée comme longée. */
    public static final int DEFAULT_MIN_CONTACT = 80;
    /** Demi-largeur (px) maximale des amorces de rues retirées au-delà d'un boulevard inclus. */
    public static final double DEFAULT_TONGUE_RADIUS = 10.0;

    private final EuclideanDistanceTransform distanceTransform;

    /**
     * Construit le combleur avec la transformée de distance standard.
     */
    public BoulevardPocketFiller() {
        this(new EuclideanDistanceTransform());
    }

    /**
     * Construit le combleur avec une transformée de distance injectée.
     *
     * @param distanceTransform Transformée de distance euclidienne exacte.
     * @throws NullPointerException si distanceTransform est null.
     */
    public BoulevardPocketFiller(EuclideanDistanceTransform distanceTransform) {
        this.distanceTransform = Objects.requireNonNull(distanceTransform, "distanceTransform ne doit pas être nul.");
    }

    /**
     * Comble les poches entre le territoire et les boulevards.
     *
     * @param consolidated Masque consolidé (repère local).
     * @param roadMask     Masque routier recadré.
     * @param retained     Masque des cellules retenues (T).
     * @param labelMap     Étiquettes des cellules.
     * @param cells        Cellules (pour leurs aires).
     * @param pocketRadius Rayon (px) de la fermeture ; 0 ou moins désactive le comblement.
     * @return Nouveau masque consolidé (ou l'original si désactivé).
     * @throws NullPointerException si un argument est null.
     */
    public ConsolidatedMask fill(ConsolidatedMask consolidated, BinaryMask roadMask, BinaryMask retained,
                                 CellLabelMap labelMap, List<Cell> cells, int pocketRadius) {
        return fill(consolidated, roadMask, retained, labelMap, cells, null, null, pocketRadius);
    }

    /**
     * Comble les poches entre le territoire et les boulevards, en limitant l'inclusion des routes longées à la
     * proximité du polygone d'intention.
     * <p>
     * Lorsque le polygone est fourni, toute route longée par le territoire (quelle que soit sa largeur : avenue,
     * autoroute, boulevard) est incluse jusqu'à son bord opposé, mais seulement sur les pixels situés dans le
     * polygone ou à moins de {@link #DEFAULT_POLYGON_REACH} px de celui-ci : une route qui se prolonge au-delà du
     * polygone n'est pas suivie.
     * </p>
     *
     * @param consolidated Masque consolidé (repère local).
     * @param roadMask     Masque routier recadré.
     * @param retained     Masque des cellules retenues (T).
     * @param labelMap     Étiquettes des cellules.
     * @param cells        Cellules (pour leurs aires).
     * @param polygonMask  Polygone d'intention recadré, ou null (aucune restriction, routes larges seulement).
     * @param polygonOutline Sommets du polygone dans le repère local, ou null (bande par dilatation isotrope).
     * @param pocketRadius Rayon (px) de la fermeture ; 0 ou moins désactive le comblement.
     * @return Nouveau masque consolidé (ou l'original si désactivé).
     * @throws NullPointerException si un argument obligatoire est null.
     */
    public ConsolidatedMask fill(ConsolidatedMask consolidated, BinaryMask roadMask, BinaryMask retained,
                                 CellLabelMap labelMap, List<Cell> cells, BinaryMask polygonMask,
                                 List<PixelPoint> polygonOutline, int pocketRadius) {
        Objects.requireNonNull(consolidated, "consolidated ne doit pas être nul.");
        Objects.requireNonNull(roadMask, "roadMask ne doit pas être nul.");
        Objects.requireNonNull(retained, "retained ne doit pas être nul.");
        Objects.requireNonNull(labelMap, "labelMap ne doit pas être nulle.");
        Objects.requireNonNull(cells, "cells ne doit pas être nulle.");
        if (pocketRadius <= 0) {
            return consolidated;
        }
        int w = consolidated.width();
        int h = consolidated.height();
        BinaryMask mask = consolidated.mask().copy();

        DistanceMap roadDepth = distanceTransform.compute(roadMask);
        BinaryMask wide = wideRoads(roadMask, roadDepth, DEFAULT_WIDE_HALF_WIDTH);
        if (wide.countActivePixels() == 0) {
            return consolidated;
        }
        boolean[] bigCell = bigUnretainedCells(cells, labelMap, retained, DEFAULT_MAX_CELL_AREA);

        // 1. Poches entre le territoire et le boulevard (bretelles, îlots, débouchés)
        BinaryMask nearWide = dilate(wide, pocketRadius);
        long pockets = fillPockets(mask, wide, nearWide, labelMap, bigCell, pocketRadius, null, null);
        // 2. Le boulevard longé est inclus jusqu'à son bord extérieur (convention : le territoire inclut ses
        //    routes frontières), puis le terre-plein central et les poches résiduelles sont comblés.
        BinaryMask boulevards = wideRoads(roadMask, roadDepth, DEFAULT_BOULEVARD_HALF_WIDTH);
        BinaryMask wideZone = polygonMask == null ? null
                : band(polygonMask, polygonOutline, boulevards, DEFAULT_WIDE_POLYGON_REACH, BOULEVARD_MEDIAN_GAP);
        long boulevard = polygonMask == null
                ? includeAdjacentBoulevard(mask, boulevards, null, DEFAULT_BOULEVARD_REACH, DEFAULT_MIN_CONTACT)
                : includeAlongPolygon(mask, roadMask, boulevards, polygonMask, polygonOutline, wideZone);
        // Le terre-plein est comblé sans dépasser le bord opposé du boulevard longé.
        long median = boulevard > 0 ? fillPockets(mask, wide, nearWide, labelMap, bigCell, pocketRadius, wideZone,
                boulevards) : 0L;
        // 3. Amorces de rues et de bretelles débordant au-delà du boulevard inclus : retirées si plus étroites
        //    que 2 x TONGUE_RADIUS (ouverture morphologique limitée aux chaussées étroites hors cellules retenues).
        long tongues = boulevard > 0 ? removeNarrowRoadTongues(mask, roadMask, wide, retained, DEFAULT_TONGUE_RADIUS) : 0L;
        LOGGER.info(() -> String.format(
                "[Boulevards] %d px de poches, %d px de chaussée de boulevard et %d px de terre-plein ajoutés ; %d px d'amorces retirés.",
                pockets, boulevard, median, tongues));
        return new ConsolidatedMask(w, h, mask, consolidated.cropWindow());
    }

    /**
     * Comble (en place) les poches entre le masque et les chaussées larges.
     *
     * @param mask         Masque modifié en place.
     * @param wide         Chaussées larges.
     * @param near         Voisinage (rayon pocketRadius) des chaussées larges.
     * @param labelMap     Étiquettes des cellules.
     * @param bigCell      Grandes cellules non retenues (jamais ajoutées).
     * @param pocketRadius Rayon de fermeture (px).
     * @param zone         Zone autorisée, ou null.
     * @param enclosure    Chaussées devant border majoritairement chaque poche comblée (terre-plein), ou null.
     * @return Nombre de pixels ajoutés.
     */
    private long fillPockets(BinaryMask mask, BinaryMask wide, BinaryMask near, CellLabelMap labelMap, boolean[] bigCell,
                             int pocketRadius, BinaryMask zone, BinaryMask enclosure) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        BinaryMask union = or(mask, wide);
        BinaryMask closed = erode(dilate(union, pocketRadius), pocketRadius);
        BinaryMask candidates = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int label = labelMap.getLabel(x, y);
                boolean forbidden = label > 0 && label < bigCell.length && bigCell[label];
                if (closed.get(x, y) && !union.get(x, y) && near.get(x, y) && !forbidden
                        && (zone == null || zone.get(x, y))) {
                    candidates.set(x, y, true);
                }
            }
        }
        if (enclosure != null) {
            keepEnclosedByWide(candidates, enclosure, MIN_MEDIAN_ENCLOSURE);
        }
        return addComponentsTouching(mask, candidates);
    }

    /**
     * Ne conserve (en place) que les composantes de candidats majoritairement bordées de chaussée large : un
     * terre-plein central est enserré entre les deux chaussées du boulevard, alors qu'un îlot de carrefour situé
     * au-delà du boulevard n'est bordé par lui que d'un côté (le reste par des rues étroites).
     *
     * @param candidates  Candidats, modifiés en place.
     * @param wide        Chaussées larges.
     * @param minFraction Part minimale du pourtour (voisins 4-connexes extérieurs) en chaussée large.
     */
    private void keepEnclosedByWide(BinaryMask candidates, BinaryMask wide, double minFraction) {
        int w = candidates.getWidth();
        int h = candidates.getHeight();
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        for (int start = 0; start < w * h; start++) {
            if (visited[start] || !candidates.get(start % w, start / w)) {
                continue;
            }
            int size = collectComponent(candidates, visited, queue, start, w, h);
            int border = 0;
            int wideBorder = 0;
            for (int k = 0; k < size; k++) {
                int x = queue[k] % w;
                int y = queue[k] / w;
                for (int i = 0; i < 4; i++) {
                    int nx = x + DX[i];
                    int ny = y + DY[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && !candidates.get(nx, ny)) {
                        border++;
                        if (wide.get(nx, ny)) {
                            wideBorder++;
                        }
                    }
                }
            }
            if (border > 0 && wideBorder < minFraction * border) {
                for (int k = 0; k < size; k++) {
                    candidates.set(queue[k] % w, queue[k] / w, false);
                }
            }
        }
    }

    /**
     * Zone de proximité du polygone : bandes latérales des côtés longés par une route si les sommets sont
     * connus, dilatation isotrope sinon.
     *
     * @param polygonMask Polygone rasterisé.
     * @param outline     Sommets, ou null.
     * @param roads       Routes candidates.
     * @param reach       Demi-largeur (px).
     * @param maxGap      Interruption maximale de la chaussée (px).
     * @return Zone de proximité.
     */
    private BinaryMask band(BinaryMask polygonMask, List<PixelPoint> outline, BinaryMask roads, double reach,
                            int maxGap) {
        return outline == null
                ? dilate(polygonMask, reach)
                : PolygonLateralBand.build(outline, polygonMask, roads, reach, DEFAULT_MIN_ALONG_FRACTION, maxGap);
    }

    /**
     * Inclut (en place) les routes longées par le territoire à proximité du polygone : boulevards jusqu'à
     * {@link #DEFAULT_WIDE_POLYGON_REACH} px du polygone, autres routes jusqu'à {@link #DEFAULT_POLYGON_REACH} px.
     *
     * @param mask        Masque modifié en place.
     * @param roadMask    Masque routier.
     * @param boulevards  Chaussées de boulevard.
     * @param polygonMask Polygone d'intention.
     * @param outline     Sommets du polygone, ou null.
     * @param wideZone    Zone des boulevards longés (bandes jusqu'à leur bord opposé).
     * @return Nombre de pixels ajoutés.
     */
    private long includeAlongPolygon(BinaryMask mask, BinaryMask roadMask, BinaryMask boulevards, BinaryMask polygonMask,
                                     List<PixelPoint> outline, BinaryMask wideZone) {
        long added = includeAdjacentBoulevard(mask, boulevards, wideZone, DEFAULT_BOULEVARD_REACH, DEFAULT_MIN_CONTACT);
        added += includeAdjacentBoulevard(mask, roadMask,
                band(polygonMask, outline, roadMask, DEFAULT_POLYGON_REACH, MARKING_GAP),
                DEFAULT_BOULEVARD_REACH, DEFAULT_MIN_CONTACT);
        return added;
    }

    /**
     * Ajoute (en place) les chaussées larges longées par le territoire : pixels de chaussée large à moins de
     * {@code reach} px du masque, regroupés en composantes 4-connexes ; seules les composantes dont le contact
     * avec le masque dépasse {@code minContact} pixels (boulevard longé) sont ajoutées, ce qui écarte les
     * amorces de routes larges qui quittent le territoire perpendiculairement.
     *
     * @param mask       Masque modifié en place.
     * @param wide       Chaussées candidates (larges, ou toutes les routes si le polygone est fourni).
     * @param zone       Zone autorisée (polygone élargi), ou null.
     * @param reach      Distance maximale au masque (px).
     * @param minContact Contact minimal (pixels adjacents au masque).
     * @return Nombre de pixels ajoutés.
     */
    private long includeAdjacentBoulevard(BinaryMask mask, BinaryMask wide, BinaryMask zone, double reach, int minContact) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        DistanceMap toMask = distanceTo(mask);
        BinaryMask near = within(mask, toMask, reach);
        BinaryMask candidates = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (wide.get(x, y) && near.get(x, y) && !mask.get(x, y) && (zone == null || zone.get(x, y))) {
                    candidates.set(x, y, true);
                }
            }
        }
        BinaryMask contactZone = within(mask, toMask, CONTACT_TOLERANCE);
        BinaryMask addedPixels = new BinaryMask(w, h);
        boolean[] visited = new boolean[w * h];
        int[] queue = new int[w * h];
        long added = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int start = y * w + x;
                if (candidates.get(x, y) && !visited[start]) {
                    int size = collectComponent(candidates, visited, queue, start, w, h);
                    if (contactCount(contactZone, queue, size, w) >= minContact) {
                        for (int k = 0; k < size; k++) {
                            mask.set(queue[k] % w, queue[k] / w, true);
                            addedPixels.set(queue[k] % w, queue[k] / w, true);
                        }
                        added += size;
                    }
                }
            }
        }
        return added > 0 ? added + bridgeGaps(mask, addedPixels, contactZone) : 0L;
    }

    /**
     * Comble (en place) l'interstice de quelques pixels laissé entre le territoire et une chaussée ajoutée
     * (liseré d'anti-crénelage non détecté comme route).
     *
     * @param mask        Masque modifié en place.
     * @param addedPixels Pixels de chaussée ajoutés.
     * @param contactZone Voisinage du territoire avant ajout.
     * @return Nombre de pixels ajoutés.
     */
    private long bridgeGaps(BinaryMask mask, BinaryMask addedPixels, BinaryMask contactZone) {
        BinaryMask nearAdded = dilate(addedPixels, CONTACT_TOLERANCE);
        long bridged = 0;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (!mask.get(x, y) && nearAdded.get(x, y) && contactZone.get(x, y)) {
                    mask.set(x, y, true);
                    bridged++;
                }
            }
        }
        return bridged;
    }

    /**
     * Retire (en place) les amorces étroites de chaussée : pixels du masque hors de son ouverture par un disque de
     * rayon {@code radius}, qui sont des chaussées étroites (ni boulevard, ni cellule retenue).
     *
     * @param mask     Masque modifié en place.
     * @param road     Masque routier.
     * @param wide     Chaussées larges.
     * @param retained Cellules retenues (T).
     * @param radius   Rayon d'ouverture (px).
     * @return Nombre de pixels retirés.
     */
    private long removeNarrowRoadTongues(BinaryMask mask, BinaryMask road, BinaryMask wide, BinaryMask retained,
                                         double radius) {
        BinaryMask opened = dilate(erode(mask, radius), radius);
        long removed = 0;
        for (int y = 0; y < mask.getHeight(); y++) {
            for (int x = 0; x < mask.getWidth(); x++) {
                if (mask.get(x, y) && !opened.get(x, y) && road.get(x, y) && !wide.get(x, y) && !retained.get(x, y)) {
                    mask.set(x, y, false);
                    removed++;
                }
            }
        }
        return removed;
    }

    /**
     * Parcourt une composante 4-connexe de candidats et la stocke dans queue.
     *
     * @param candidates Candidats.
     * @param visited    Marqueurs de visite.
     * @param queue      Tampon des indices.
     * @param start      Indice de départ.
     * @param w          Largeur.
     * @param h          Hauteur.
     * @return Taille de la composante.
     */
    private int collectComponent(BinaryMask candidates, boolean[] visited, int[] queue, int start, int w, int h) {
        int head = 0;
        int tail = 0;
        visited[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int idx = queue[head++];
            int cx = idx % w;
            int cy = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                    int n = ny * w + nx;
                    if (!visited[n] && candidates.get(nx, ny)) {
                        visited[n] = true;
                        queue[tail++] = n;
                    }
                }
            }
        }
        return tail;
    }

    /**
     * Nombre de pixels d'une composante situés au contact du territoire (à moins de
     * {@link #CONTACT_TOLERANCE} px).
     *
     * @param contactZone Voisinage du territoire.
     * @param queue       Indices de la composante.
     * @param size        Taille de la composante.
     * @param w           Largeur.
     * @return Nombre de pixels de contact.
     */
    private int contactCount(BinaryMask contactZone, int[] queue, int size, int w) {
        int contact = 0;
        for (int k = 0; k < size; k++) {
            if (contactZone.get(queue[k] % w, queue[k] / w)) {
                contact++;
            }
        }
        return contact;
    }

    /**
     * Chaussées larges : pixels de route à moins de {@code halfWidth} px d'un axe de demi-largeur &gt;= halfWidth.
     *
     * @param road      Masque routier.
     * @param halfWidth Demi-largeur minimale (px).
     * @return Masque des chaussées larges.
     */
    BinaryMask wideRoads(BinaryMask road, double halfWidth) {
        return wideRoads(road, distanceTransform.compute(road), halfWidth);
    }

    /**
     * Chaussées larges, à partir de la distance au bord de chaussée déjà calculée.
     *
     * @param road      Masque routier.
     * @param edt       Distance au bord de chaussée.
     * @param halfWidth Demi-largeur minimale (px).
     * @return Masque des chaussées larges.
     */
    private BinaryMask wideRoads(BinaryMask road, DistanceMap edt, double halfWidth) {
        BinaryMask cores = new BinaryMask(road.getWidth(), road.getHeight());
        for (int y = 0; y < road.getHeight(); y++) {
            for (int x = 0; x < road.getWidth(); x++) {
                if (edt.get(x, y) >= halfWidth) {
                    cores.set(x, y, true);
                }
            }
        }
        BinaryMask grown = dilate(cores, halfWidth);
        BinaryMask wide = new BinaryMask(road.getWidth(), road.getHeight());
        for (int y = 0; y < road.getHeight(); y++) {
            for (int x = 0; x < road.getWidth(); x++) {
                if (grown.get(x, y) && road.get(x, y)) {
                    wide.set(x, y, true);
                }
            }
        }
        return wide;
    }

    /**
     * Dilatation par un disque : pixels à distance euclidienne &lt;= r de l'ensemble.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble dilaté.
     */
    BinaryMask dilate(BinaryMask set, double r) {
        return within(set, distanceTo(set), r);
    }

    /**
     * Distance euclidienne de chaque pixel à l'ensemble (0 sur l'ensemble, et partout si l'ensemble est vide).
     *
     * @param set Ensemble.
     * @return Carte des distances à l'ensemble.
     */
    private DistanceMap distanceTo(BinaryMask set) {
        int w = set.getWidth();
        int h = set.getHeight();
        BinaryMask complement = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                complement.set(x, y, !set.get(x, y));
            }
        }
        return distanceTransform.compute(complement);
    }

    /**
     * Pixels à distance euclidienne &lt;= r de l'ensemble.
     *
     * @param set   Ensemble.
     * @param toSet Distance à l'ensemble ({@link #distanceTo(BinaryMask)}).
     * @param r     Rayon (px).
     * @return Ensemble dilaté.
     */
    private BinaryMask within(BinaryMask set, DistanceMap toSet, double r) {
        int w = set.getWidth();
        int h = set.getHeight();
        BinaryMask out = new BinaryMask(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float d = toSet.get(x, y);
                // d == 0 hors de l'ensemble signifie « aucun pixel de l'ensemble » (ensemble vide)
                if (set.get(x, y) || (d > 0.0f && d <= r)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Érosion par un disque : pixels à distance euclidienne &gt; r du complément.
     *
     * @param set Ensemble.
     * @param r   Rayon (px).
     * @return Ensemble érodé.
     */
    BinaryMask erode(BinaryMask set, double r) {
        DistanceMap toComplement = distanceTransform.compute(set);
        BinaryMask out = new BinaryMask(set.getWidth(), set.getHeight());
        for (int y = 0; y < set.getHeight(); y++) {
            for (int x = 0; x < set.getWidth(); x++) {
                if (set.get(x, y) && (toComplement.get(x, y) > r || toComplement.get(x, y) == 0.0f)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Union de deux masques.
     *
     * @param a Premier masque.
     * @param b Second masque.
     * @return a ∪ b.
     */
    private BinaryMask or(BinaryMask a, BinaryMask b) {
        BinaryMask out = new BinaryMask(a.getWidth(), a.getHeight());
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                if (a.get(x, y) || b.get(x, y)) {
                    out.set(x, y, true);
                }
            }
        }
        return out;
    }

    /**
     * Marque les grandes cellules non retenues (jamais absorbées).
     *
     * @param cells    Cellules.
     * @param labelMap Étiquettes.
     * @param retained Masque T.
     * @param maxArea  Aire maximale absorbable.
     * @return Tableau indexé par identifiant de cellule.
     */
    private boolean[] bigUnretainedCells(List<Cell> cells, CellLabelMap labelMap, BinaryMask retained, long maxArea) {
        boolean[] big = new boolean[labelMap.cellCount() + 1];
        for (Cell cell : cells) {
            if (cell.area() > maxArea && cell.id() < big.length) {
                big[cell.id()] = true;
            }
        }
        for (int y = 0; y < retained.getHeight(); y++) {
            for (int x = 0; x < retained.getWidth(); x++) {
                int label = labelMap.getLabel(x, y);
                if (retained.get(x, y) && label > 0 && label < big.length) {
                    big[label] = false;
                }
            }
        }
        return big;
    }

    /**
     * Ajoute au masque (en place) les composantes 4-connexes de candidats qui touchent le masque.
     *
     * @param mask       Masque modifié en place.
     * @param candidates Pixels candidats.
     * @return Nombre de pixels ajoutés.
     */
    private long addComponentsTouching(BinaryMask mask, BinaryMask candidates) {
        int w = mask.getWidth();
        int h = mask.getHeight();
        int[] queue = new int[w * h];
        int tail = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (candidates.get(x, y) && touches(mask, x, y, w, h)) {
                    candidates.set(x, y, false);
                    mask.set(x, y, true);
                    queue[tail++] = y * w + x;
                }
            }
        }
        int head = 0;
        while (head < tail) {
            int idx = queue[head++];
            int cx = idx % w;
            int cy = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = cx + DX[i];
                int ny = cy + DY[i];
                if (nx >= 0 && nx < w && ny >= 0 && ny < h && candidates.get(nx, ny)) {
                    candidates.set(nx, ny, false);
                    mask.set(nx, ny, true);
                    queue[tail++] = ny * w + nx;
                }
            }
        }
        return tail;
    }

    /**
     * Indique si un pixel a un voisin 4-connexe dans le masque.
     *
     * @param mask Masque.
     * @param x    Abscisse.
     * @param y    Ordonnée.
     * @param w    Largeur.
     * @param h    Hauteur.
     * @return true si un voisin appartient au masque.
     */
    private boolean touches(BinaryMask mask, int x, int y, int w, int h) {
        for (int i = 0; i < 4; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            if (nx >= 0 && nx < w && ny >= 0 && ny < h && mask.get(nx, ny)) {
                return true;
            }
        }
        return false;
    }
}
