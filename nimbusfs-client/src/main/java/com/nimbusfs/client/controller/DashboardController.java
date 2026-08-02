package com.nimbusfs.client.controller;

import com.nimbusfs.client.NimbusApp;
import com.nimbusfs.client.model.FileTableModel;
import com.nimbusfs.client.model.SessionContext;
import com.nimbusfs.client.service.AuthService;
import com.nimbusfs.client.service.FileService;
import com.nimbusfs.client.service.NodeService;
import com.nimbusfs.common.model.FileMetadata;
import com.nimbusfs.common.model.UploadOptions;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DashboardController {

    @FXML private Label userInfoLabel;
    @FXML private Label storageUsedLabel;
    @FXML private Label nodesOnlineLabel;
    @FXML private Button adminBtn;

    @FXML private TableView<FileTableModel> fileTable;
    @FXML private Label dropZoneLabel;
    @FXML private TableColumn<FileTableModel, String> nameCol;
    @FXML private TableColumn<FileTableModel, String> sizeCol;
    @FXML private TableColumn<FileTableModel, Integer> replicasCol;
    @FXML private TableColumn<FileTableModel, String> statusCol;
    @FXML private TableColumn<FileTableModel, String> ownerCol;
    @FXML private TableColumn<FileTableModel, String> checksumCol;
    @FXML private TableColumn<FileTableModel, String> dateCol;
    @FXML private TextField searchField;

    private final FileService fileService = new FileService();
    private final NodeService nodeService = new NodeService();
    private final AuthService authService = new AuthService();
    private final ObservableList<FileTableModel> fileList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        if (SessionContext.get().getCurrentUser() != null) {
            userInfoLabel.setText("Logged in as: " + SessionContext.get().getCurrentUser().getUsername() +
                    " (" + SessionContext.get().getCurrentUser().getRole() + ")");
            adminBtn.setVisible(SessionContext.get().getCurrentUser().isAdmin());
        }

        nameCol.setCellValueFactory(cell -> cell.getValue().fileNameProperty());
        sizeCol.setCellValueFactory(cell -> cell.getValue().formattedSizeProperty());
        replicasCol.setCellValueFactory(cell -> cell.getValue().replicasProperty().asObject());
        statusCol.setCellValueFactory(cell -> cell.getValue().statusProperty());
        ownerCol.setCellValueFactory(cell -> cell.getValue().ownerProperty());
        checksumCol.setCellValueFactory(cell -> cell.getValue().checksumProperty());
        dateCol.setCellValueFactory(cell -> cell.getValue().createdDateProperty());

        fileTable.setItems(fileList);

        // ── Drag & Drop File Upload Support ────────────────────────────────
        // Attach handlers to the fileTable so any position over it works
        fileTable.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
                // Show glowing drop-zone overlay
                if (dropZoneLabel != null) dropZoneLabel.setVisible(true);
                fileTable.setOpacity(0.55);
            }
            event.consume();
        });

        fileTable.setOnDragExited(event -> {
            // Hide overlay when cursor leaves
            if (dropZoneLabel != null) dropZoneLabel.setVisible(false);
            fileTable.setOpacity(1.0);
            event.consume();
        });

        fileTable.setOnDragDropped(event -> {
            if (dropZoneLabel != null) dropZoneLabel.setVisible(false);
            fileTable.setOpacity(1.0);
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                java.util.List<File> droppedFiles = db.getFiles();
                for (File file : droppedFiles) {
                    if (file.isFile()) {
                        UploadOptions options = new UploadOptions(3, true, true);
                        Platform.runLater(() -> userInfoLabel.setText("⬆ Uploading " + file.getName() + " via drag & drop..."));
                        fileService.uploadFile(file, options, progress ->
                            Platform.runLater(() -> userInfoLabel.setText(
                                String.format("⬆ Uploading %s — %.0f%%", file.getName(), progress * 100)))
                        ).thenRun(() ->
                            Platform.runLater(() -> {
                                userInfoLabel.setText("✅ Uploaded " + file.getName() + " successfully!");
                                handleRefresh();
                            })
                        ).exceptionally(ex -> {
                            Platform.runLater(() -> showAlert("Upload Error", ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage()));
                            return null;
                        });
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
        fileService.listFiles().thenAccept(files -> {
            Platform.runLater(() -> {
                fileList.clear();
                for (FileMetadata meta : files) {
                    fileList.add(new FileTableModel(meta));
                }
            });
        });

        nodeService.getNodeStatuses().thenAccept(nodes -> {
            Platform.runLater(() -> {
                long online = nodes.stream().filter(n -> n.isOnline()).count();
                nodesOnlineLabel.setText(online + " / " + nodes.size());
            });
        });
    }

    @FXML
    private void handleUpload() {
        try {
            FXMLLoader loader = new FXMLLoader(NimbusApp.class.getResource("/view/UploadView.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Upload File — NimbusFS");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(NimbusApp.getPrimaryStage());
            Scene scene = new Scene(root);
            NimbusApp.applyTheme(scene);
            stage.setScene(scene);
            stage.showAndWait();

            handleRefresh();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDownload() {
        FileTableModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a file to download.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName(selected.getFileName());
        File dest = fileChooser.showSaveDialog(NimbusApp.getPrimaryStage());

        if (dest != null) {
            fileService.downloadFile(selected.getFileId(), dest.toPath(), progress -> {
                // Progress callback
            }).thenRun(() -> {
                Platform.runLater(() -> showAlert("Success", "File downloaded successfully to " + dest.getAbsolutePath()));
            }).exceptionally(ex -> {
                Platform.runLater(() -> showAlert("Error", ex.getMessage()));
                return null;
            });
        }
    }

    @FXML
    private void handleDelete() {
        FileTableModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a file to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete " + selected.getFileName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                fileService.deleteFile(selected.getFileId()).thenRun(() -> {
                    Platform.runLater(this::handleRefresh);
                });
            }
        });
    }

    @FXML
    private void handleRename() {
        FileTableModel selected = fileTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a file to rename.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(selected.getFileName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText("Rename " + selected.getFileName());
        dialog.setContentText("New Name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                fileService.renameFile(selected.getFileId(), newName.trim()).thenRun(() -> {
                    Platform.runLater(this::handleRefresh);
                });
            }
        });
    }

    @FXML
    private void showFiles() { handleRefresh(); }

    @FXML
    private void showNodes() { loadSubView("/view/NodeMonitorView.fxml"); }

    @FXML
    private void showGraph() { loadSubView("/view/NetworkGraphView.fxml"); }

    @FXML
    private void showAnalytics() { loadSubView("/view/AnalyticsView.fxml"); }

    @FXML
    private void showLogs() { loadSubView("/view/ActivityLogView.fxml"); }

    @FXML
    private void showAdmin() { loadSubView("/view/AdminView.fxml"); }

    @FXML
    private void showSettings() { loadSubView("/view/SettingsView.fxml"); }

    @FXML
    private void handleLogout() {
        authService.logout().thenRun(() -> {
            Platform.runLater(NimbusApp::showLoginView);
        });
    }

    private void loadSubView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(NimbusApp.class.getResource(fxmlPath));
            Parent view = loader.load();
            Stage stage = new Stage();
            stage.setTitle("NimbusFS Subview");
            stage.initModality(Modality.NONE);
            stage.initOwner(NimbusApp.getPrimaryStage());
            Scene scene = new Scene(view, 900, 600);
            NimbusApp.applyTheme(scene);
            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
