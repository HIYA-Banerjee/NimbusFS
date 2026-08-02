package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.NodeService;
import com.nimbusfs.common.model.NodeInfo;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class NetworkGraphController {

    @FXML private Canvas graphCanvas;

    private final NodeService nodeService = new NodeService();
    private List<NodeInfo> nodes = new ArrayList<>();
    private double particleOffset = 0;

    @FXML
    public void initialize() {
        nodeService.getNodeStatuses().thenAccept(fetchedNodes -> {
            Platform.runLater(() -> {
                this.nodes = fetchedNodes;
                startGraphAnimation();
            });
        });
    }

    private void startGraphAnimation() {
        GraphicsContext gc = graphCanvas.getGraphicsContext2D();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                drawTopology(gc);
                particleOffset += 2;
                if (particleOffset > 100) particleOffset = 0;
            }
        };
        timer.start();
    }

    private void drawTopology(GraphicsContext gc) {
        double width = graphCanvas.getWidth();
        double height = graphCanvas.getHeight();

        gc.clearRect(0, 0, width, height);

        double masterX = width / 2;
        double masterY = height / 2 - 50;

        // Draw Master Server
        gc.setFill(Color.web("#58a6ff"));
        gc.fillOval(masterX - 35, masterY - 35, 70, 70);
        gc.setFill(Color.WHITE);
        gc.fillText("Master Server", masterX - 35, masterY + 50);

        int count = nodes.isEmpty() ? 5 : nodes.size();
        double radius = 180;

        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            double nodeX = masterX + radius * Math.cos(angle);
            double nodeY = masterY + radius * Math.sin(angle);

            boolean isOnline = i < nodes.size() ? nodes.get(i).isOnline() : (i != 2);
            String nodeName = i < nodes.size() ? nodes.get(i).getDisplayName() : "Node " + (i + 1);

            // Draw Edge
            if (isOnline) {
                gc.setStroke(Color.web("#30363d"));
                gc.setLineWidth(2);
                gc.strokeLine(masterX, masterY, nodeX, nodeY);

                // Animated Particle Flow
                double particleRatio = (particleOffset / 100.0);
                double px = masterX + (nodeX - masterX) * particleRatio;
                double py = masterY + (nodeY - masterY) * particleRatio;
                gc.setFill(Color.web("#3fb950"));
                gc.fillOval(px - 4, py - 4, 8, 8);
            }

            // Draw Node Circle
            gc.setFill(isOnline ? Color.web("#238636") : Color.web("#da3633"));
            gc.fillOval(nodeX - 25, nodeY - 25, 50, 50);

            gc.setFill(Color.WHITE);
            gc.fillText(nodeName + "\n" + (isOnline ? "🟢 Online" : "🔴 Offline"), nodeX - 30, nodeY + 40);
        }
    }
}
