package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Represents a storage node in the NimbusFS cluster.
 * Maintained in the NodeRegistry on the Master Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeInfo {

    public enum NodeStatus {
        ONLINE, OFFLINE, DEGRADED
    }

    private String     nodeId;
    private String     host;
    private int        port;
    private NodeStatus status;
    private long       storageUsed;    // bytes currently used
    private long       storageTotal;   // total capacity in bytes
    private long       lastHeartbeat;  // epoch millis
    private long       registeredAt;   // epoch millis
    private String     displayName;    // e.g. "Node 1"

    // ─── Constructors ──────────────────────────────────────────────────────────

    public NodeInfo() {}

    public NodeInfo(String nodeId, String host, int port, long storageTotal) {
        this.nodeId        = nodeId;
        this.host          = host;
        this.port          = port;
        this.storageTotal  = storageTotal;
        this.status        = NodeStatus.ONLINE;
        this.registeredAt  = Instant.now().toEpochMilli();
        this.lastHeartbeat = this.registeredAt;
    }

    // ─── Computed helpers ──────────────────────────────────────────────────────

    /** Storage usage as a 0.0 – 1.0 fraction. */
    public double getStorageUsageFraction() {
        return storageTotal > 0 ? (double) storageUsed / storageTotal : 0.0;
    }

    /** Storage usage as a 0–100 integer percentage. */
    public int getStorageUsagePercent() {
        return (int) Math.round(getStorageUsageFraction() * 100);
    }

    public boolean isOnline() {
        return status == NodeStatus.ONLINE;
    }

    public String getFormattedStorageUsed() {
        return formatBytes(storageUsed);
    }

    public String getFormattedStorageTotal() {
        return formatBytes(storageTotal);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1_099_511_627_776L) return String.format("%.1f TB", bytes / 1_099_511_627_776.0);
        if (bytes >= 1_073_741_824L)     return String.format("%.1f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)         return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.1f KB", bytes / 1_024.0);
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getNodeId()                         { return nodeId; }
    public void setNodeId(String nodeId)              { this.nodeId = nodeId; }

    public String getHost()                           { return host; }
    public void setHost(String host)                  { this.host = host; }

    public int getPort()                              { return port; }
    public void setPort(int port)                     { this.port = port; }

    public NodeStatus getStatus()                     { return status; }
    public void setStatus(NodeStatus status)          { this.status = status; }

    public long getStorageUsed()                      { return storageUsed; }
    public void setStorageUsed(long storageUsed)      { this.storageUsed = storageUsed; }

    public long getStorageTotal()                     { return storageTotal; }
    public void setStorageTotal(long storageTotal)    { this.storageTotal = storageTotal; }

    public long getLastHeartbeat()                    { return lastHeartbeat; }
    public void setLastHeartbeat(long lastHeartbeat)  { this.lastHeartbeat = lastHeartbeat; }

    public long getRegisteredAt()                     { return registeredAt; }
    public void setRegisteredAt(long registeredAt)    { this.registeredAt = registeredAt; }

    public String getDisplayName()                    { return displayName; }
    public void setDisplayName(String displayName)    { this.displayName = displayName; }
}
