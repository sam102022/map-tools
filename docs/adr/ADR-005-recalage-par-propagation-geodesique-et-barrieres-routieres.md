# ADR-005 - Recalage par propagation géodésique et barrières routières

## Statut

✅ Acceptée

## Contexte

Dans le problème du détourage de cartes Google Maps, l'utilisateur fournit une capture cartographique et un calque vert grossier marquant la zone d'intérêt. Ce masque vert initial présente des contours irréguliers, dessinés à main levée ou générés de façon approximative.

Le réseau routier (autoroutes oranges, nationales jaunes, voies urbaines blanches bordées de gris) offre des frontières naturelles idéales pour délimiter les zones géographiques. Cependant, une carte Google Maps regorge d'éléments parasites pouvant perturber la détection :
- Réseaux hydrographiques (fleuves, côtes).
- Bâti urbain dense, toitures et ombrages.
- Étiquettes textuelles de noms de rues et de villes.
- Espaces verts et parcs présentant des dominantes de couleur verte.

Si le recalage du masque était effectué de manière globale ou non contrainte, deux dérives majeures surviendraient :
1. **La fuite géométrique :** Le contour pourrait s'aimanter sur des axes autoroutiers majeurs situés loin de la zone voulue, déformant arbitrairement le périmètre.
2. **Le blocage local :** Les intersections, ronds-points ou interruptions textuelles sur les routes créeraient des brèches ou des pointes discontinues.

## Décision

Il est formellement décidé de fonder le recalage du masque sur une **stratégie de propagation géodésique contrainte par barrières routières locales** :

```
[ Masque Vert Grossier ]
         │
         ▼ (Érosion morphologique)
  [ Noyau Intérieur Sûr ]
         │
         ▼ (Propagation BFS contrainte ≤ maxSnappingDistance)
   [ Front d'Onde ] ──── Stop sur ───► [ Barrières Routières Détectées ]
         │
         ▼ (Préservation si pas de route)
[ Masque Affiné Recalé ]
```

1. **Isolement de la Région d'Intérêt (ROI) :** Le masque vert extrait par `GreenMaskExtractor` définit la zone d'influence stricte. Aucune route candidate détectée en dehors de la zone d'influence immédiate du masque vert n'est prise en compte.
2. **Érosion du masque pour définir le noyau dur :** Une érosion morphologique (`MorphologyOps.erode`) est appliquée pour extraire la zone centrale certaine et éliminer les débordements initiaux du tracé grossier.
3. **Détection des axes comme barrières infranchissables :** Les pixels identifiés par `RoadDetector` agissent comme des barrières géodésiques d'arrêt.
4. **Expansion géodésique par parcours en largeur (BFS) :** Le noyau intérieur est étendu pas-à-pas. La propagation s'interrompt dès qu'un pixel de route candidate est rencontré, ou dès que la distance de parcours atteint le rayon maximal configuré (`maxSnappingDistance`).
5. **Stratégie de repli automatique (*Fallback*) :**
   * Si aucun candidat routier n'est détecté dans le voisinage d'une section de contour, le tracé initial du masque vert (lissé) est strictement conservé.
   * Si la géométrie finale calculée est dégénérée ou vide, le moteur effectue un repli sécurisé sur le masque d'origine nettoyé.

## Conséquences

*   **Positives :**
    *   Fidélité au tracé utilisateur : le détourage ne s'évade jamais vers des axes extérieurs non concernés.
    *   Alignement net et naturel sur les bords de routes sans nécessiter de vectorisation manuelle fastidieuse.
    *   Insensibilité aux labels de texte et aux textures intérieures grâce au rôle stabilisateur du noyau érodé.
*   **Neutres / Contraintes :**
    *   La qualité de l'alignement dépend du rayon `maxSnappingDistance` : une valeur trop faible ne rejoindra pas la route, une valeur excessive pourrait franchir des voies parallèles étroites.
