package com.nimbusfs.client.controller;

import com.nimbusfs.client.NimbusApp;
import com.nimbusfs.client.model.SessionContext;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

public class SettingsController {

    @FXML private TextField serverIpField;
    @FXML private TextField serverPortField;
    @FXML private ComboBox<String> replicationCombo;
    @FXML private ComboBox<String> themeCombo;

    @FXML
    public void initialize() {
        serverIpField.setText(SessionContext.get().getServerHost());
        serverPortField.setText(String.valueOf(SessionContext.get().getServerPort()));

        replicationCombo.getItems().addAll("3 Copies (Default)", "2 Copies");
        replicationCombo.getSelectionModel().select(0);

        themeCombo.getItems().addAll("Dark Mode", "Light Mode");
        themeCombo.getSelectionModel().select(NimbusApp.getCurrentTheme().contains("dark") ? 0 : 1);
    }

    @FXML
    private void handleSave() {
        SessionContext.get().setServerHost(serverIpField.getText().trim());
        try {
            SessionContext.get().setServerPort(Integer.parseInt(serverPortField.getText().trim()));
        } catch (NumberFormatException ignored) {}

        if (themeCombo.getSelectionModel().getSelectedIndex() == 0) {
            NimbusApp.setTheme("/styles/theme-dark.css");
        } else {
            NimbusApp.setTheme("/styles/theme-light.css");
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully.");
        alert.showAndWait();
    }
}
