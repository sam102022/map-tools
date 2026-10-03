# ADR-002 - Modèle de masques matriciels hybrides (BinaryMask et CoverageMask)

## Statut

✅ Acceptée

## Contexte

Le pipeline de détourage cartographique et d'alignement géométrique doit concilier deux exigences a priori contradictoires :

1. **Performance matricielle brute :** Les étapes de détection spectrale, d'érosion, de dilatation, de fermeture morphologique et de propagation géodésique BFS manipulent des images haute résolution (ex : $1920 \times 1080$ ou supérieur, soit plusieurs millions de pixels). Ces traitements itératifs nécessitent un accès mémoire ultra-rapide et déterministe. L'utilisation de tableaux 2D d'objets (`Boolean[][]` ou `Pixel[][]`) provoquerait une dispersion mémoire catastrophique, une pression intolérable sur le Garbage Collector et de fréquents défauts de cache CPU.
2. **Qualité de découpe sub-pixel sans aliasing :** Un masque purement binaire (pixel intérieur = 1, pixel extérieur = 0) produit lors de l'export d'image détourée des bordures en marche d'escalier (crénelage ou *aliasing*) très inesthétiques, particulièrement le long des voies routières diagonales ou courbes. Pour obtenir un détourage visuel naturel et propre, chaque pixel de frontière doit porter une valeur d'opacité continue entre 0 et 255 proportionnelle à sa surface couverte.

## Décision

Il est formellement décidé de structurer la représentation spatiale autour de **deux modèles de masques spécialisés et complémentaires** :

### 1. `BinaryMask` : Masque Matriciel Binaire Dédié aux Algorithmes de Segmentation
*   **Structure interne :** Tableau unidimensionnel linéaire plat `boolean[]` de dimension `width * height`.
*   **Indexation optimisée :** Accès direct par translation spatiale : $\text{index} = y \times \text{width} + x$. Cette disposition garantit une excellente localité spatiale et temporelle dans les caches de données processeur (L1/L2) lors des balayages horizontaux.
*   **Rôle :** Représentation exclusive des étapes algorithmiques de segmentation :
    *   Masque vert extrait (`GreenMaskExtractor`).
    *   Candidats d'axes routiers (`RoadCandidateDetector`, `RoadDetector`).
    *   Opérations de morphologie mathématique (`MorphologyOps`).
    *   Zone d'aimantation recalée (`RoadSnappingEngine`).

### 2. `CoverageMask` : Masque Continu Sub-Pixel Dédié au Rendu et à la Découpe
*   **Structure interne :** Tableau unidimensionnel linéaire plat `byte[]` de taille `width * height`, où chaque octet est interprété comme un entier non signé dans $[0, 255]$ via l'opération `data[index] & 0xFF`.
*   **Rôle :** Représentation fine des niveaux de couverture et de transparence :
    *   `0` : pixel entièrement extérieur (transparent).
    *   `255` : pixel entièrement intérieur (opaque).
    *   `1..254` : couverture partielle calculée par rasterisation géométrique vectorielle Java2D avec anticrénelage (`Graphics2D` et `Path2D`).
*   **Interopérabilité :**
    *   Méthode utilitaire de binarisation `toBinaryMask(int threshold)` avec seuil paramétrable (seuil standard à 128).
    *   Méthode de conversion utilitaire `fromBinaryMask(BinaryMask mask)` transformant un état booléen en $0$ ou $255$.
    *   Formule de composition alpha exacte pour l'export :
        $$\text{finalAlpha} = \frac{\text{sourceAlpha} \times \text{effectiveCoverage} + 127}{255}$$

## Conséquences

*   **Positives :**
    *   Vitesse d'exécution optimale lors des parcours matriciels intensifs sans compromis sur la finesse graphique finale.
    *   Empreinte mémoire maîtrisée : seulement 1 octet par pixel pour `CoverageMask` et 1 booléen par pixel pour `BinaryMask`.
    *   Qualité d'exportation PNG irréprochable avec des bords doux et anti-aliasés.
*   **Neutres / Contraintes :**
    *   Nécessite une étape explicite de conversion entre l'espace binaire algorithmique et l'espace continu lors de la phase de rendu final.
