package com.nimbusfs.master.registry;

import com.nimbusfs.master.config.MasterConfig;
import com.nimbusfs.master.replication.ReplicationManager;
import com.nimbusfs.common.model.NodeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled background task that monitors storage node heartbeats.
 *
 * Runs every {@code heartbeatIntervalSeconds} seconds.
 * Marks a node OFFLINE if its last heartbeat is older than
 * {@code heartbeatTimeoutSeconds} (default: 15s = 3 missed heartbeats at 5s interval).
 *
 * When a node goes offline, triggers ReplicationManager.recover() to
 * redistribute chunks that were stored exclusively on that node.
 */
public class HeartbeatMonitor {

    private static final Logger log = LogManager.getLogger(HeartbeatMonitor.class);

    private final NodeRegistry          nodeRegistry;
    private final ReplicationManager    replicationManager;
    private final MasterConfig          config;
    private final ScheduledExecutorService scheduler;

    public HeartbeatMonitor(NodeRegistry nodeRegistry,
                            ReplicationManager replicationManager,
                            MasterConfig config) {
        this.nodeRegistry       = nodeRegistry;
        this.replicationManager = replicationManager;
        this.config             = config;
        this.scheduler          = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        int interval = config.getHeartbeatIntervalSeconds();
        scheduler.scheduleAtFixedRate(this::checkHeartbeats, interval, interval, TimeUnit.SECONDS);
        log.info("HeartbeatMonitor started. Checking every {}s, timeout at {}s.",
            interval, config.getHeartbeatTimeoutSeconds());
    }

    public void stop() {
        scheduler.shutdownNow();
        log.info("HeartbeatMonitor stopped.");
    }

    /**
     * Main check loop — called every heartbeatIntervalSeconds.
     * Checks each registered node's last heartbeat timestamp.
     */
    private void checkHeartbeats() {
        long now          = System.currentTimeMillis();
        long timeoutMs    = (long) config.getHeartbeatTimeoutSeconds() * 1000;

        List<NodeInfo> allNodes = nodeRegistry.getAllNodes();

        for (NodeInfo node : allNodes) {
            if (node.getStatus() == NodeInfo.NodeStatus.OFFLINE) {
                continue; // already marked offline
            }

            long elapsed = now - node.getLastHeartbeat();

            if (elapsed > timeoutMs) {
                log.warn("Node {} missed heartbeat. Last seen {}ms ago. Marking OFFLINE.",
                    node.getDisplayName(), elapsed);

                nodeRegistry.markOffline(node.getNodeId());

                // Trigger async recovery on a separate thread to not block the monitor
                Thread recoveryThread = new Thread(
                    () -> replicationManager.recover(node.getNodeId()),
                    "recovery-" + node.getNodeId().substring(0, 8)
                );
                recoveryThread.setDaemon(true);
                recoveryThread.start();
            }
        }
    }
}
