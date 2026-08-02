package com.nimbusfs.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Main JavaFX Application class for NimbusFS Desktop Client.
 */
public class NimbusApp extends Application {

    private static Stage primaryStage;
    private static String currentTheme = "/styles/theme-dark.css";

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        primaryStage.setTitle("NimbusFS — Distributed File System Desktop Client");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        showLoginView();
        primaryStage.show();
    }

    public static void showLoginView() {
        loadScene("/view/LoginView.fxml", 500, 600);
    }

    public static void showDashboardView() {
        loadScene("/view/DashboardView.fxml", 1100, 700);
    }

    public static void loadScene(String fxmlPath, double width, double height) {
        try {
            FXMLLoader loader = new FXMLLoader(NimbusApp.class.getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, width, height);
            applyTheme(scene);
            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void applyTheme(Scene scene) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(NimbusApp.class.getResource(currentTheme).toExternalForm());
    }

    public static void setTheme(String themePath) {
        currentTheme = themePath;
        if (primaryStage != null && primaryStage.getScene() != null) {
            applyTheme(primaryStage.getScene());
        }
    }

    public static String getCurrentTheme() {
        return currentTheme;
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
