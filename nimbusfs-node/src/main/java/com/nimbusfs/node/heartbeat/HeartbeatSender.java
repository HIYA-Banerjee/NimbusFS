package com.nimbusfs.node.heartbeat;

import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.node.config.NodeConfig;
import com.nimbusfs.node.network.MasterConnection;
import com.nimbusfs.node.storage.DiskManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically sends HEARTBEAT packets to Master Server.
 */
public class HeartbeatSender {

    private static final Logger log = LogManager.getLogger(HeartbeatSender.class);

    private final NodeConfig          config;
    private final MasterConnection    masterConnection;
    private final DiskManager         diskManager;
    private final ScheduledExecutorService scheduler;

    public HeartbeatSender(NodeConfig config, MasterConnection masterConnection, DiskManager diskManager) {
        this.config           = config;
        this.masterConnection = masterConnection;
        this.diskManager      = diskManager;
        this.scheduler        = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "node-heartbeat");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 5, 5, TimeUnit.SECONDS);
        log.info("HeartbeatSender started (5s interval)");
    }

    public void stop() {
        scheduler.shutdownNow();
        log.info("HeartbeatSender stopped.");
    }

    private void sendHeartbeat() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeId", config.getNodeId());
            payload.put("storageUsed", diskManager.getUsedSpace());
            payload.put("storageTotal", config.getStorageLimitBytes());

            Packet packet = Packet.of(MessageType.HEARTBEAT, payload);
            masterConnection.sendRequest(packet);
            log.trace("Heartbeat sent to Master.");
        } catch (Exception e) {
            log.warn("Failed to send heartbeat to Master: {}", e.getMessage());
        }
    }
}
