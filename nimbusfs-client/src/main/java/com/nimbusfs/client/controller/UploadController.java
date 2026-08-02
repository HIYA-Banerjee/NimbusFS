package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.FileService;
import com.nimbusfs.common.model.UploadOptions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class UploadController {

    @FXML private Label selectedFileLabel;
    @FXML private RadioButton rf3Radio;
    @FXML private RadioButton rf2Radio;
    @FXML private CheckBox encryptCheck;
    @FXML private CheckBox compressCheck;
    @FXML private Button uploadBtn;
    @FXML private VBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;

    private File selectedFile;
    private final FileService fileService = new FileService();

    @FXML
    private void handleChooseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Upload");
        selectedFile = fileChooser.showOpenDialog(uploadBtn.getScene().getWindow());

        if (selectedFile != null) {
            selectedFileLabel.setText(selectedFile.getName() + " (" + (selectedFile.length() / 1024) + " KB)");
            uploadBtn.setDisable(false);
        }
    }

    @FXML
    private void handleStartUpload() {
        if (selectedFile == null) return;

        int rf = rf3Radio.isSelected() ? 3 : 2;
        UploadOptions options = new UploadOptions(rf, encryptCheck.isSelected(), compressCheck.isSelected());

        progressContainer.setVisible(true);
        uploadBtn.setDisable(true);

        fileService.uploadFile(selectedFile, options, progress -> {
            Platform.runLater(() -> {
                progressBar.setProgress(progress);
                progressLabel.setText(String.format("Uploading... %.0f%%", progress * 100));
            });
        }).thenRun(() -> {
            Platform.runLater(this::closeWindow);
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                progressLabel.setText("Upload Failed: " + ex.getMessage());
                uploadBtn.setDisable(false);
            });
            return null;
        });
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        Stage stage = (Stage) uploadBtn.getScene().getWindow();
        stage.close();
    }
}
