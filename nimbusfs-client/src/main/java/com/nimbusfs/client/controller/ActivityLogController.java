package com.nimbusfs.client.controller;

import com.nimbusfs.client.service.AnalyticsService;
import com.nimbusfs.common.model.ActivityEvent;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ActivityLogController {

    @FXML private ListView<String> logListView;
    private final AnalyticsService analyticsService = new AnalyticsService();
    private final ObservableList<String> logItems = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        logListView.setItems(logItems);
        handleRefresh();
    }

    @FXML
    private void handleRefresh() {
        analyticsService.getActivityLogs(50).thenAccept(events -> {
            Platform.runLater(() -> {
                logItems.clear();
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                for (ActivityEvent event : events) {
                    String timeStr = sdf.format(new Date(event.getTimestamp()));
                    logItems.add(String.format("[%s]  %-20s  %s", timeStr, event.getEventLabel(), event.getDescription()));
                }
            });
        });
    }
}
