package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.AnalyticsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.*;

import java.util.Map;

public class AnalyticsController {

    @FXML private PieChart storagePieChart;
    @FXML private LineChart<String, Number> growthLineChart;
    @FXML private BarChart<String, Number> nodeBarChart;
    @FXML private AreaChart<String, Number> trafficAreaChart;

    private final AnalyticsService analyticsService = new AnalyticsService();

    @FXML
    public void initialize() {
        analyticsService.getAnalyticsData().thenAccept(data -> {
            Platform.runLater(() -> renderCharts(data));
        });
    }

    private void renderCharts(Map<String, Object> data) {
        // 1. Pie Chart
        storagePieChart.getData().clear();
        storagePieChart.getData().add(new PieChart.Data("Used Storage (4.2 TB)", 4.2));
        storagePieChart.getData().add(new PieChart.Data("Free Storage (5.8 TB)", 5.8));

        // 2. Line Chart
        growthLineChart.getData().clear();
        XYChart.Series<String, Number> growthSeries = new XYChart.Series<>();
        growthSeries.setName("Storage Growth (TB)");
        growthSeries.getData().add(new XYChart.Data<>("Jan", 1.2));
        growthSeries.getData().add(new XYChart.Data<>("Feb", 1.8));
        growthSeries.getData().add(new XYChart.Data<>("Mar", 2.5));
        growthSeries.getData().add(new XYChart.Data<>("Apr", 3.1));
        growthSeries.getData().add(new XYChart.Data<>("May", 4.2));
        growthLineChart.getData().add(growthSeries);

        // 3. Bar Chart
        nodeBarChart.getData().clear();
        XYChart.Series<String, Number> nodeSeries = new XYChart.Series<>();
        nodeSeries.setName("Utilization %");
        nodeSeries.getData().add(new XYChart.Data<>("Node 1", 63));
        nodeSeries.getData().add(new XYChart.Data<>("Node 2", 42));
        nodeSeries.getData().add(new XYChart.Data<>("Node 3", 0));
        nodeSeries.getData().add(new XYChart.Data<>("Node 4", 80));
        nodeSeries.getData().add(new XYChart.Data<>("Node 5", 31));
        nodeBarChart.getData().add(nodeSeries);

        // 4. Area Chart
        trafficAreaChart.getData().clear();
        XYChart.Series<String, Number> uploadSeries = new XYChart.Series<>();
        uploadSeries.setName("Upload Traffic (MB/s)");
        uploadSeries.getData().add(new XYChart.Data<>("10:00", 45));
        uploadSeries.getData().add(new XYChart.Data<>("10:05", 120));
        uploadSeries.getData().add(new XYChart.Data<>("10:10", 85));
        uploadSeries.getData().add(new XYChart.Data<>("10:15", 210));

        XYChart.Series<String, Number> downloadSeries = new XYChart.Series<>();
        downloadSeries.setName("Download Traffic (MB/s)");
        downloadSeries.getData().add(new XYChart.Data<>("10:00", 30));
        downloadSeries.getData().add(new XYChart.Data<>("10:05", 70));
        downloadSeries.getData().add(new XYChart.Data<>("10:10", 150));
        downloadSeries.getData().add(new XYChart.Data<>("10:15", 90));

        trafficAreaChart.getData().addAll(uploadSeries, downloadSeries);
    }
}
