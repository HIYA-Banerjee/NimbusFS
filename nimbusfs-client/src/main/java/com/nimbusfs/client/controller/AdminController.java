package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.AnalyticsService;
import com.nimbusfs.common.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.Map;

public class AdminController {

    @FXML private Label totalUsersLabel;
    @FXML private Label totalFilesLabel;
    @FXML private Label totalStorageLabel;
    @FXML private Label totalNodesLabel;

    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> userIdCol;
    @FXML private TableColumn<User, String> usernameCol;
    @FXML private TableColumn<User, String> roleCol;
    @FXML private TableColumn<User, Long> createdCol;

    private final AnalyticsService analyticsService = new AnalyticsService();
    private final ObservableList<User> userList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        userIdCol.setCellValueFactory(new PropertyValueFactory<>("userId"));
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        createdCol.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
        userTable.setItems(userList);

        analyticsService.getAdminStats().thenAccept(stats -> {
            Platform.runLater(() -> updateStats(stats));
        });
    }

    private void updateStats(Map<String, Object> stats) {
        if (stats.containsKey("totalUsers")) totalUsersLabel.setText(String.valueOf(stats.get("totalUsers")));
        if (stats.containsKey("totalFiles")) totalFilesLabel.setText(String.valueOf(stats.get("totalFiles")));
        if (stats.containsKey("totalNodes")) totalNodesLabel.setText(String.valueOf(stats.get("totalNodes")));
        if (stats.containsKey("totalStorageBytes")) {
            long bytes = ((Number) stats.get("totalStorageBytes")).longValue();
            totalStorageLabel.setText(String.format("%.1f GB", bytes / 1_073_741_824.0));
        }
    }
}
