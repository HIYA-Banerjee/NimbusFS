package com.nimbusfs.master.config;

import com.nimbusfs.common.util.ConfigLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the NimbusFS Master Server.
 * Reads from master.properties (classpath or config directory).
 */
public class MasterConfig {

    private final ConfigLoader loader;

    // Resolved config values
    private final int    port;
    private final String databasePath;
    private final int    defaultReplicationFactor;
    private final int    chunkSizeMB;
    private final int    heartbeatIntervalSeconds;
    private final int    heartbeatTimeoutSeconds;
    private final int    maxUploadSizeMB;
    private final boolean tlsEnabled;

    public MasterConfig() {
        this.loader = new ConfigLoader("master.properties");

        this.port                     = loader.getInt("master.port", 9000);
        this.defaultReplicationFactor = loader.getInt("master.replication.factor", 3);
        this.chunkSizeMB              = loader.getInt("master.chunk.size.mb", 16);
        this.heartbeatIntervalSeconds = loader.getInt("master.heartbeat.interval.seconds", 5);
        this.heartbeatTimeoutSeconds  = loader.getInt("master.heartbeat.timeout.seconds", 15);
        this.maxUploadSizeMB          = loader.getInt("master.max.upload.size.mb", 10240);  // 10 GB
        this.tlsEnabled               = loader.getBoolean("master.tls.enabled", false);

        // Default database in user home directory
        String defaultDb = Paths.get(System.getProperty("user.home"), ".nimbusfs", "metadata.db").toString();
        this.databasePath = loader.getString("master.database.path", defaultDb);
    }

    public int    getPort()                     { return port; }
    public String getDatabasePath()             { return databasePath; }
    public int    getDefaultReplicationFactor() { return defaultReplicationFactor; }
    public int    getChunkSizeMB()              { return chunkSizeMB; }
    public long   getChunkSizeBytes()           { return (long) chunkSizeMB * 1024 * 1024; }
    public int    getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public int    getHeartbeatTimeoutSeconds()  { return heartbeatTimeoutSeconds; }
    public int    getMaxUploadSizeMB()          { return maxUploadSizeMB; }
    public long   getMaxUploadSizeBytes()       { return (long) maxUploadSizeMB * 1024 * 1024; }
    public boolean isTlsEnabled()               { return tlsEnabled; }
}
