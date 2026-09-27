package com.sam102022.photoshop.core.detection;

import com.sam102022.photoshop.core.model.BinaryMask;
import java.awt.image.BufferedImage;

/**
 * Détecteur des pixels candidats appartenant au réseau routier Google Maps.
 * <p>
 * Ce composant identifie les axes routiers principaux (autoroutes, voies rapides, nationales,
 * départementales majeures) à partir de leur signature chromatique distinctive (jaune, orange, rouge).
 * Les petites rues blanches sont volontairement exclues à ce stade pour éviter les fausses détections
 * sur les fonds cartographiques clairs et les zones urbaines denses.
 * </p>
 */
public class RoadCandidateDetector {

    /**
     * Analyse l'image cartographique pour extraire un masque binaire des pixels candidats routiers.
     * Chaque pixel est évalué selon sa concordance avec les signatures chromatiques routières.
     *
     * @param image Image source de la carte (format standard RVB ou ARGB).
     * @return Masque binaire de même dimension où chaque pixel actif représente un candidat routier.
     * @throws IllegalArgumentException si l'image fournie est null.
     */
    public BinaryMask detect(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("L'image de la carte ne peut pas être null.");
        }
        BinaryMask candidates = new BinaryMask(image.getWidth(), image.getHeight());
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                if (roadScore((rgb >>> 16) & 0xff, (rgb >>> 8) & 0xff, rgb & 0xff) > 0) {
                    candidates.set(x, y, true);
                }
            }
        }
        return candidates;
    }

    /**
     * Calcule un score de confiance chromatique pour un triplet RVB donné.
     * <p>
     * Le score est évalué comme suit :
     * <ul>
     *   <li>{@code 1.0} : Axes routiers majeurs et autoroutes (tons orange / rouge prononcés).</li>
     *   <li>{@code 0.75} : Routes principales secondaires (tons jaunes caractéristiques).</li>
     *   <li>{@code 0.0} : Hors réseau routier identifié ou rue blanche/neutre exclue.</li>
     * </ul>
     * </p>
     *
     * @param r Composante rouge du pixel [0..255].
     * @param g Composante verte du pixel [0..255].
     * @param b Composante bleue du pixel [0..255].
     * @return Score de confiance dans l'intervalle [0.0, 1.0].
     */
    public double roadScore(int r, int g, int b) {
        if (r >= 210 && g >= 130 && g <= 220 && b >= 50 && b <= 165 && r - g >= 15) {
            return 1.0;
        }
        if (r >= 220 && g >= 190 && b >= 100 && b <= 190
                && Math.abs(r - g) <= 35 && r - b >= 40) {
            return 0.75;
        }
        return 0.0;
    }
}
