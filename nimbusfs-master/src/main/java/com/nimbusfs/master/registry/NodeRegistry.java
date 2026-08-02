package com.nimbusfs.master.registry;

import com.nimbusfs.common.model.NodeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory registry of all connected storage nodes.
 *
 * Backed by ConcurrentHashMap — safe for concurrent reads and writes
 * from heartbeat threads, client handlers, and the replication manager.
 *
 * The registry maintains the live state (RAM); the SQLite MetadataStore
 * persists node records across server restarts.
 */
public class NodeRegistry {

    private static final Logger log = LogManager.getLogger(NodeRegistry.class);

    private final ConcurrentHashMap<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private       int nodeCounter = 0;

    // ─── Registration ──────────────────────────────────────────────────────────

    public synchronized void register(NodeInfo node) {
        if (!nodes.containsKey(node.getNodeId())) {
            nodeCounter++;
            if (node.getDisplayName() == null || node.getDisplayName().isEmpty()) {
                node.setDisplayName("Node " + nodeCounter);
            }
        }
        nodes.put(node.getNodeId(), node);
        log.info("Node registered: {} [{}:{}] storage={}",
            node.getDisplayName(), node.getHost(), node.getPort(),
            node.getFormattedStorageTotal());
    }

    public void deregister(String nodeId) {
        NodeInfo removed = nodes.remove(nodeId);
        if (removed != null) {
            log.warn("Node deregistered: {} ({})", removed.getDisplayName(), nodeId);
        }
    }

    // ─── Heartbeat update ──────────────────────────────────────────────────────

    public void updateHeartbeat(String nodeId, long storageUsed, long storageTotal) {
        NodeInfo node = nodes.get(nodeId);
        if (node != null) {
            node.setLastHeartbeat(System.currentTimeMillis());
            node.setStorageUsed(storageUsed);
            node.setStorageTotal(storageTotal);
            if (node.getStatus() != NodeInfo.NodeStatus.ONLINE) {
                node.setStatus(NodeInfo.NodeStatus.ONLINE);
                log.info("Node {} came back online.", node.getDisplayName());
            }
        } else {
            log.warn("Heartbeat from unknown node: {}. Ignoring.", nodeId);
        }
    }

    public void markOffline(String nodeId) {
        NodeInfo node = nodes.get(nodeId);
        if (node != null && node.getStatus() == NodeInfo.NodeStatus.ONLINE) {
            node.setStatus(NodeInfo.NodeStatus.OFFLINE);
            log.warn("Node marked OFFLINE: {} ({})", node.getDisplayName(), nodeId);
        }
    }

    // ─── Queries ───────────────────────────────────────────────────────────────

    public NodeInfo get(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<NodeInfo> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public List<NodeInfo> getOnlineNodes() {
        return nodes.values().stream()
            .filter(n -> n.getStatus() == NodeInfo.NodeStatus.ONLINE)
            .collect(Collectors.toList());
    }

    public int getOnlineCount() {
        return (int) nodes.values().stream()
            .filter(n -> n.getStatus() == NodeInfo.NodeStatus.ONLINE)
            .count();
    }

    public int getTotalCount() {
        return nodes.size();
    }

    /**
     * Selects {@code count} healthy nodes to store a new chunk on.
     *
     * Strategy: prefer nodes with the most free space (load-balancing),
     * excluding nodes in the given exclusion list (e.g., nodes already
     * holding a replica of this chunk).
     *
     * @param count       number of nodes to select
     * @param excludeIds  node IDs to exclude from selection
     * @return list of selected NodeInfo objects
     * @throws IllegalStateException if fewer than {@code count} healthy nodes are available
     */
    public List<NodeInfo> selectNodes(int count, Set<String> excludeIds) {
        List<NodeInfo> candidates = nodes.values().stream()
            .filter(n -> n.getStatus() == NodeInfo.NodeStatus.ONLINE)
            .filter(n -> !excludeIds.contains(n.getNodeId()))
            .filter(n -> n.getStorageUsageFraction() < 0.95)  // exclude nearly-full nodes
            .sorted(Comparator.comparingLong(n -> (n.getStorageTotal() - n.getStorageUsed())))
            .collect(Collectors.toCollection(ArrayList::new));

        // Reverse so nodes with most free space come first
        Collections.reverse(candidates);

        if (candidates.size() < count) {
            throw new IllegalStateException(
                "Insufficient healthy nodes. Requested: " + count + ", available: " + candidates.size());
        }

        return candidates.subList(0, count);
    }

    public List<NodeInfo> selectNodes(int count) {
        return selectNodes(count, Collections.emptySet());
    }

    public boolean contains(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    public long getTotalStorageBytes() {
        return nodes.values().stream()
            .filter(n -> n.getStatus() == NodeInfo.NodeStatus.ONLINE)
            .mapToLong(NodeInfo::getStorageTotal)
            .sum();
    }

    public long getUsedStorageBytes() {
        return nodes.values().stream()
            .filter(n -> n.getStatus() == NodeInfo.NodeStatus.ONLINE)
            .mapToLong(NodeInfo::getStorageUsed)
            .sum();
    }
}
