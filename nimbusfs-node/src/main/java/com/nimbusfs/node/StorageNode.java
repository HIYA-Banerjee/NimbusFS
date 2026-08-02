package com.nimbusfs.node;

import com.nimbusfs.node.config.NodeConfig;
import com.nimbusfs.node.heartbeat.HeartbeatSender;
import com.nimbusfs.node.network.MasterConnection;
import com.nimbusfs.node.server.ChunkServer;
import com.nimbusfs.node.storage.ChunkStore;
import com.nimbusfs.node.storage.DiskManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * NimbusFS Storage Node — entry point.
 *
 * Responsibilities:
 *  - Connects to the Master Server and registers itself
 *  - Starts a local TCP server to handle chunk STORE/RETRIEVE/DELETE requests
 *  - Sends periodic heartbeats with storage statistics to the master
 *  - Stores chunk data on the local filesystem
 *
 * Usage:
 *   java -jar nimbusfs-node.jar
 *   java -Dnimbusfs.config=/path/to/node.properties -jar nimbusfs-node.jar
 *
 * Multiple instances can run on the same machine with different port/storage configs.
 */
public class StorageNode {

    private static final Logger log = LogManager.getLogger(StorageNode.class);

    private final NodeConfig       config;
    private final ChunkStore       chunkStore;
    private final DiskManager      diskManager;
    private final MasterConnection masterConnection;
    private final ChunkServer      chunkServer;
    private final HeartbeatSender  heartbeatSender;

    public StorageNode() throws Exception {
        this.config           = new NodeConfig();
        this.chunkStore       = new ChunkStore(config.getStorageDirectory());
        this.diskManager      = new DiskManager(config.getStorageDirectory());
        this.masterConnection = new MasterConnection(config);
        this.chunkServer      = new ChunkServer(config, chunkStore);
        this.heartbeatSender  = new HeartbeatSender(config, masterConnection, diskManager);
    }

    public void start() throws Exception {
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║         NimbusFS Storage Node v1.0           ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Node ID       : {}  ║", config.getNodeId().substring(0, 8) + "...");
        log.info("║  Chunk port    : {}                       ║", config.getChunkPort());
        log.info("║  Storage dir   : {}      ║", config.getStorageDirectory());
        log.info("║  Master        : {}:{}               ║", config.getMasterHost(), config.getMasterPort());
        log.info("╚══════════════════════════════════════════════╝");

        // Initialize chunk store
        chunkStore.initialize();

        // Start chunk server first so we're ready before registration
        chunkServer.start();

        // Register with master
        masterConnection.connect();
        masterConnection.register();

        // Start heartbeat (after registration succeeds)
        heartbeatSender.start();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "node-shutdown"));

        log.info("Storage Node is online and ready.");
    }

    public void stop() {
        log.info("Shutting down storage node...");
        heartbeatSender.stop();
        chunkServer.stop();
        masterConnection.disconnect();
        log.info("Storage node stopped.");
    }

    public static void main(String[] args) {
        try {
            StorageNode node = new StorageNode();
            node.start();
            // Keep main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            LogManager.getLogger(StorageNode.class).fatal("Failed to start Storage Node", e);
            System.exit(1);
        }
    }
}
