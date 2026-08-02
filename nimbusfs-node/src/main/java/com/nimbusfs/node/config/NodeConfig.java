package com.nimbusfs.node.config;

import com.nimbusfs.common.util.ConfigLoader;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Configuration for a NimbusFS Storage Node.
 * Each node instance reads from its own node.properties.
 */
public class NodeConfig {

    private final ConfigLoader loader;

    private final String nodeId;
    private final String host;
    private final int    chunkPort;
    private final String masterHost;
    private final int    masterPort;
    private final String storageDirectory;
    private final long   storageLimitBytes;
    private final String displayName;
    private final boolean tlsEnabled;

    public NodeConfig() {
        this.loader = new ConfigLoader("node.properties");

        // nodeId: either configured (for persistence across restarts) or random UUID
        String configuredId = loader.getString("node.id", null);
        this.nodeId = (configuredId != null && !configuredId.isEmpty())
            ? configuredId : UUID.randomUUID().toString();

        this.host          = loader.getString("node.host", "localhost");
        this.chunkPort     = loader.getInt("node.chunk.port", 9001);
        this.masterHost    = loader.getString("master.host", "localhost");
        this.masterPort    = loader.getInt("master.port", 9000);
        this.displayName   = loader.getString("node.display.name", "");
        this.tlsEnabled    = loader.getBoolean("node.tls.enabled", false);

        String defaultStorage = Paths.get(System.getProperty("user.home"),
            ".nimbusfs", "node-" + chunkPort).toString();
        this.storageDirectory = loader.getString("node.storage.dir", defaultStorage);

        // Default: 10 GB storage limit per node
        this.storageLimitBytes = loader.getLong("node.storage.limit.bytes", 10L * 1024 * 1024 * 1024);
    }

    public String getNodeId()            { return nodeId; }
    public String getHost()              { return host; }
    public int    getChunkPort()         { return chunkPort; }
    public String getMasterHost()        { return masterHost; }
    public int    getMasterPort()        { return masterPort; }
    public String getStorageDirectory()  { return storageDirectory; }
    public long   getStorageLimitBytes() { return storageLimitBytes; }
    public String getDisplayName()       { return displayName; }
    public boolean isTlsEnabled()        { return tlsEnabled; }
}
