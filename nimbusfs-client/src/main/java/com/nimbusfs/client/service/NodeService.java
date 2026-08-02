package com.nimbusfs.client.service;

import com.nimbusfs.client.network.MasterClient;
import com.nimbusfs.common.model.NodeInfo;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for fetching Storage Node statuses and network topology.
 */
public class NodeService {

    private final MasterClient masterClient = MasterClient.get();
    private final ObjectMapper mapper = new ObjectMapper();

    public CompletableFuture<List<NodeInfo>> getNodeStatuses() {
        Packet req = Packet.of(MessageType.NODE_STATUS_REQUEST, Map.of());
        return masterClient.sendRequest(req).thenApply(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
            Map<?, ?> body = resp.getPayloadAs(Map.class);
            return mapper.convertValue(body.get("nodes"), new TypeReference<List<NodeInfo>>() {});
        });
    }
}
