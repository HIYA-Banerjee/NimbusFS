package com.nimbusfs.client.service;

import com.nimbusfs.client.network.MasterClient;
import com.nimbusfs.common.model.ActivityEvent;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for fetching analytics dashboard data, activity logs, and admin statistics.
 */
public class AnalyticsService {

    private final MasterClient masterClient = MasterClient.get();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<Map<String, Object>> getAnalyticsData() {
        Packet req = Packet.of(MessageType.ANALYTICS_REQUEST, Map.of());
        return masterClient.sendRequest(req).thenApply(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
            return resp.getPayloadAs(Map.class);
        });
    }

    public CompletableFuture<List<ActivityEvent>> getActivityLogs(int limit) {
        Packet req = Packet.of(MessageType.ACTIVITY_LOG_REQUEST, Map.of("limit", limit));
        return masterClient.sendRequest(req).thenApply(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
            Map<?, ?> body = resp.getPayloadAs(Map.class);
            return mapper.convertValue(body.get("events"), new TypeReference<List<ActivityEvent>>() {});
        });
    }

    public CompletableFuture<Map<String, Object>> getAdminStats() {
        Packet req = Packet.of(MessageType.ADMIN_STATS_REQUEST, Map.of());
        return masterClient.sendRequest(req).thenApply(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
            return resp.getPayloadAs(Map.class);
        });
    }
}
