package com.sam102022.photoshop.core.segmentation;

import com.sam102022.photoshop.core.model.BinaryMask;

/**
 * Consolidateur des barrières infranchissables pour le découpage de zone.
 * <p>
 * Combine de manière étanche les axes routiers candidats, les démarcations sombres cartographiques
 * et l'extérieur du territoire. Applique une fermeture morphologique ainsi qu'un colmatage
 * des diagonales 8-connexes pour interdire toute fuite lors de l'inondation en 4-connectivité.
 * </p>
 */
public final class ZoneBarrierConsolidator {

    /**
     * Initialise une nouvelle instance du consolidateur de barrières.
     */
    public ZoneBarrierConsolidator() {
    }

    /**
     * Consolide les barrières infranchissables délimitant la zone.
     *
     * @param roadCandidates   Masque des axes routiers candidats (non null).
     * @param darkDemarcations Masque des démarcations sombres (peut être null).
     * @param territoryMask    Masque du territoire global (non null).
     * @return Masque binaire consolidé et étanchéifié des barrières.
     * @throws IllegalArgumentException si roadCandidates ou territoryMask est null, ou en cas de dimensions discordantes.
     */
    public BinaryMask consolidate(BinaryMask roadCandidates, BinaryMask darkDemarcations, BinaryMask territoryMask) {
        validateInputs(roadCandidates, darkDemarcations, territoryMask);

        int width = roadCandidates.getWidth();
        int height = roadCandidates.getHeight();

        BinaryMask outsideTerritory = territoryMask.not();
        BinaryMask rawBarriers = buildRawBarriers(roadCandidates, darkDemarcations, outsideTerritory);

        // Fermeture morphologique pour boucher les micro-trous
        BinaryMask closed = MorphologyOps.close(rawBarriers, 1);

        // Colmatage explicite des diagonales 8-connexes
        BinaryMask sealed = closeDiagonalPassages(closed.or(rawBarriers), width, height);

        // Préservation absolue de l'extérieur du territoire
        return sealed.or(outsideTerritory);
    }

    /**
     * Valide la présence et la cohérence dimensionnelle des masques en entrée.
     *
     * @param roadCandidates   Masque routier.
     * @param darkDemarcations Masque de démarcations.
     * @param territoryMask    Masque de territoire.
     * @throws IllegalArgumentException si les arguments sont invalides.
     */
    private static void validateInputs(BinaryMask roadCandidates, BinaryMask darkDemarcations, BinaryMask territoryMask) {
        if (roadCandidates == null) {
            throw new IllegalArgumentException("Le masque roadCandidates ne peut pas être null.");
        }
        if (territoryMask == null) {
            throw new IllegalArgumentException("Le masque territoryMask ne peut pas être null.");
        }

        int width = roadCandidates.getWidth();
        int height = roadCandidates.getHeight();

        if (territoryMask.getWidth() != width || territoryMask.getHeight() != height) {
            throw new IllegalArgumentException("Dimensions incompatibles entre roadCandidates (" + width + "x" + height
                    + ") et territoryMask (" + territoryMask.getWidth() + "x" + territoryMask.getHeight() + ").");
        }

        if (darkDemarcations != null) {
            if (darkDemarcations.getWidth() != width || darkDemarcations.getHeight() != height) {
                throw new IllegalArgumentException("Dimensions incompatibles entre roadCandidates (" + width + "x" + height
                        + ") et darkDemarcations (" + darkDemarcations.getWidth() + "x" + darkDemarcations.getHeight() + ").");
            }
        }
    }

    /**
     * Construit l'union brute des sources de barrières.
     *
     * @param roads            Masque routier.
     * @param demarcations     Masque de démarcations (optionnel).
     * @param outsideTerritory Masque représentant l'extérieur du territoire.
     * @return Union des obstacles.
     */
    private static BinaryMask buildRawBarriers(BinaryMask roads, BinaryMask demarcations, BinaryMask outsideTerritory) {
        BinaryMask raw = roads.or(outsideTerritory);
        if (demarcations != null) {
            raw = raw.or(demarcations);
        }
        return raw;
    }

    /**
     * Colmate les passages diagonaux où deux pixels de barrière se touchent par un coin (8-connexes)
     * sans pixel de barrière commun en 4-connectivité.
     *
     * @param mask   Masque des barrières à étanchéifier.
     * @param width  Largeur du masque.
     * @param height Hauteur du masque.
     * @return Nouveau masque étanchéifié contre les fuites en 4-connectivité.
     */
    private static BinaryMask closeDiagonalPassages(BinaryMask mask, int width, int height) {
        BinaryMask sealed = mask.copy();

        for (int y = 0; y < height - 1; y++) {
            for (int x = 0; x < width - 1; x++) {
                sealDiagonalQuad(sealed, mask, x, y);
            }
        }

        return sealed;
    }

    /**
     * Scelle un bloc 2x2 s'il présente un passage diagonal non bloqué.
     *
     * @param target Masque récepteur des colmatages.
     * @param source Masque source des barrières.
     * @param x      Origine X du bloc 2x2.
     * @param y      Origine Y du bloc 2x2.
     */
    private static void sealDiagonalQuad(BinaryMask target, BinaryMask source, int x, int y) {
        boolean tl = source.get(x, y);
        boolean tr = source.get(x + 1, y);
        boolean bl = source.get(x, y + 1);
        boolean br = source.get(x + 1, y + 1);

        // Diagonale descendante (haut-gauche et bas-droite)
        if (tl && br && !tr && !bl) {
            target.set(x + 1, y, true);
            target.set(x, y + 1, true);
        }

        // Diagonale montante (haut-droite et bas-gauche)
        if (tr && bl && !tl && !br) {
            target.set(x, y, true);
            target.set(x + 1, y + 1, true);
        }
    }
}
