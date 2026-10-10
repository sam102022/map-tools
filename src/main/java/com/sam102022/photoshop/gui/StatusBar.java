package com.sam102022.photoshop.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * Barre d'état inférieure affichant les messages d'avancement et de diagnostic de l'application.
 */
public class StatusBar extends JPanel {
    private final JLabel statusLabel = new JLabel("Prêt");

    /**
     * Initialise la barre d'état avec une bordure gravée et un alignement à gauche.
     */
    public StatusBar() {
        setLayout(new FlowLayout(FlowLayout.LEFT));
        setBorder(BorderFactory.createEtchedBorder());
        add(statusLabel);
    }

    /**
     * Définit le message textuel d'état à afficher.
     *
     * @param message Message d'état (ex: "Prêt", "Détourage en cours...", "Erreur").
     */
    public void setMessage(String message) {
        statusLabel.setText(message);
    }

    /**
     * Récupère le message textuel d'état actuellement affiché.
     *
     * @return Message d'état textuel.
     */
    public String getMessage() {
        return statusLabel.getText();
    }
}
