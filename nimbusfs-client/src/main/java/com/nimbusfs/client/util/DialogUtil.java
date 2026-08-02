package com.nimbusfs.client.util;

import com.nimbusfs.client.NimbusApp;
import javafx.scene.control.Alert;

/**
 * Utility class for creating consistent JavaFX dialogs.
 */
public class DialogUtil {

    private DialogUtil() {}

    public static void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        NimbusApp.applyTheme(alert.getDialogPane().getScene());
        alert.showAndWait();
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        NimbusApp.applyTheme(alert.getDialogPane().getScene());
        alert.showAndWait();
    }

    public static void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        NimbusApp.applyTheme(alert.getDialogPane().getScene());
        alert.showAndWait();
    }
}
