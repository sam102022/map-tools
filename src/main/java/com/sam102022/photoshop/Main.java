package com.sam102022.photoshop;

import com.sam102022.photoshop.cli.CliRunner;
import com.sam102022.photoshop.gui.MainWindow;

import javax.swing.SwingUtilities;

/**
 * Point d'entrée de l'application : aiguillage automatique entre le mode CLI et le mode GUI.
 */
public class Main {

    public static void main(String[] args) {
        boolean launchGui = args.length == 0 || hasGuiOption(args);

        if (launchGui) {
            SwingUtilities.invokeLater(() -> {
                MainWindow window = new MainWindow();
                window.setVisible(true);
            });
        } else {
            int exitCode = CliRunner.run(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        }
    }

    private static boolean hasGuiOption(String[] args) {
        for (String arg : args) {
            if ("--gui".equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
