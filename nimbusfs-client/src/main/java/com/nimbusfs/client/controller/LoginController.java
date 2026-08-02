package com.nimbusfs.client.controller;

import com.nimbusfs.client.NimbusApp;
import com.nimbusfs.client.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    @FXML private Button loginButton;

    private final AuthService authService = new AuthService();

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }

        statusLabel.setStyle("-fx-text-fill: #58a6ff;");
        statusLabel.setText("Logging in...");
        loginButton.setDisable(true);

        authService.login(username, password).thenAccept(user -> {
            Platform.runLater(NimbusApp::showDashboardView);
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #f85149;");
                statusLabel.setText(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                loginButton.setDisable(false);
            });
            return null;
        });
    }

    @FXML
    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Enter username and password to register.");
            return;
        }

        statusLabel.setStyle("-fx-text-fill: #58a6ff;");
        statusLabel.setText("Registering...");

        authService.register(username, password).thenAccept(success -> {
            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #3fb950;");
                statusLabel.setText("User registered successfully! Click Login.");
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusLabel.setStyle("-fx-text-fill: #f85149;");
                statusLabel.setText(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            });
            return null;
        });
    }
}
