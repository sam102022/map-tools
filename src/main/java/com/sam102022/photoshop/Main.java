package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.V2CliRunner;

/**
 * Point d'entrée de l'application : détourage en ligne de commande (pipeline V2, territoires et zones).
 */
public class Main {

    /**
     * Constructeur privé : classe utilitaire non instanciable.
     */
    private Main() {
        // Point d'entrée uniquement.
    }

    /**
     * Lance le détourage en ligne de commande ; sans argument, l'aide est affichée.
     *
     * @param args Arguments de la ligne de commande.
     */
    public static void main(String[] args) {
        int exitCode = V2CliRunner.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
