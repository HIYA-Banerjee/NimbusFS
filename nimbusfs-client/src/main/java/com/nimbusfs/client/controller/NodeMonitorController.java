package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.NodeService;
import com.nimbusfs.common.model.NodeInfo;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;

public class NodeMonitorController {

    @FXML private FlowPane nodeContainer;
    private final NodeService nodeService = new NodeService();

    @FXML
    public void initialize() {
        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
        nodeService.getNodeStatuses().thenAccept(nodes -> {
            Platform.runLater(() -> renderNodes(nodes));
        });
    }

    private void renderNodes(List<NodeInfo> nodes) {
        nodeContainer.getChildren().clear();

        for (NodeInfo node : nodes) {
            VBox card = new VBox(10);
            card.getStyleClass().add("card");
            card.setPrefWidth(240);
            card.setPadding(new Insets(15));

            Label title = new Label(node.getDisplayName() != null ? node.getDisplayName() : node.getNodeId().substring(0, 8));
            title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

            Label status = new Label(node.isOnline() ? "🟢 Online" : "🔴 Offline");
            status.getStyleClass().add(node.isOnline() ? "status-online" : "status-offline");

            Label storageText = new Label("Storage Used: " + node.getStorageUsagePercent() + "%");

            ProgressBar progressBar = new ProgressBar(node.getStorageUsageFraction());
            progressBar.setMaxWidth(Double.MAX_VALUE);

            Label detail = new Label(node.getHost() + ":" + node.getPort());
            detail.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

            card.getChildren().addAll(title, status, storageText, progressBar, detail);
            nodeContainer.getChildren().add(card);
        }
    }
}
