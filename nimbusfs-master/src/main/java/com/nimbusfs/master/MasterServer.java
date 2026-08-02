package com.nimbusfs.master;

import com.nimbusfs.master.auth.AuthService;
import com.nimbusfs.master.config.MasterConfig;
import com.nimbusfs.master.metadata.SQLiteMetadataStore;
import com.nimbusfs.master.registry.HeartbeatMonitor;
import com.nimbusfs.master.registry.NodeRegistry;
import com.nimbusfs.master.replication.ReplicationManager;
import com.nimbusfs.master.server.ClientHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NimbusFS Master Server — entry point.
 *
 * Responsibilities:
 *  - Listens on a single TCP port for all connections
 *  - Routes connections to ClientHandler (for desktop clients) or NodeHandler (for storage nodes)
 *  - Manages the MetadataStore, NodeRegistry, ReplicationManager, and AuthService lifecycles
 *  - Starts the HeartbeatMonitor background task
 *
 * Usage:
 *   java -jar nimbusfs-master.jar
 *   java -Dnimbusfs.config=/path/to/master.properties -jar nimbusfs-master.jar
 */
public class MasterServer {

    private static final Logger log = LogManager.getLogger(MasterServer.class);

    // ─── Core components ───────────────────────────────────────────────────────

    private final MasterConfig         config;
    private final SQLiteMetadataStore  metadataStore;
    private final NodeRegistry         nodeRegistry;
    private final ReplicationManager   replicationManager;
    private final AuthService          authService;
    private final HeartbeatMonitor     heartbeatMonitor;

    // ─── Threading ─────────────────────────────────────────────────────────────

    private final ExecutorService clientPool;
    private final ExecutorService nodePool;

    // ─── State ─────────────────────────────────────────────────────────────────

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket        serverSocket;

    // ─── Constructor ───────────────────────────────────────────────────────────

    public MasterServer() throws Exception {
        this.config             = new MasterConfig();
        this.metadataStore      = new SQLiteMetadataStore(config.getDatabasePath());
        this.nodeRegistry       = new NodeRegistry();
        this.replicationManager = new ReplicationManager(metadataStore, nodeRegistry);
        this.authService        = new AuthService(metadataStore);
        this.heartbeatMonitor   = new HeartbeatMonitor(nodeRegistry, replicationManager, config);

        int cpus = Runtime.getRuntime().availableProcessors();
        this.clientPool = Executors.newFixedThreadPool(cpus * 4);
        this.nodePool   = Executors.newFixedThreadPool(cpus * 2);
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() throws Exception {
        metadataStore.initialize();
        authService.ensureAdminExists();

        serverSocket = com.nimbusfs.common.net.NimbusSocketFactory.createServerSocket(config.getPort(), config.isTlsEnabled());
        serverSocket.setReuseAddress(true);
        running.set(true);

        heartbeatMonitor.start();

        log.info("╔══════════════════════════════════════════════╗");
        log.info("║         NimbusFS Master Server v1.0          ║");
        log.info("╠══════════════════════════════════════════════╣");
        log.info("║  Listening on port : {}                    ║", config.getPort());
        log.info("║  Database          : {}  ║", config.getDatabasePath());
        log.info("║  Default RF        : {}                       ║", config.getDefaultReplicationFactor());
        log.info("║  Chunk size        : {} MB                  ║", config.getChunkSizeMB());
        log.info("╚══════════════════════════════════════════════╝");

        // Register shutdown hook for graceful stop
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "shutdown-hook"));

        acceptConnections();
    }

    private void acceptConnections() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                // Peek at connection identifier: nodes send a special registration handshake
                // We use a lightweight protocol: first byte identifies connection type
                // NODE_REGISTER (0x30) → NodeHandler; everything else → ClientHandler
                dispatchConnection(socket);
            } catch (IOException e) {
                if (running.get()) {
                    log.error("Error accepting connection: {}", e.getMessage());
                }
            }
        }
    }

    private void dispatchConnection(Socket socket) {
        // We let the handlers identify themselves via the first packet they send.
        // Use a unified ClientHandler that internally delegates to NodeHandler logic
        // when it receives a NODE_REGISTER message.
        clientPool.submit(new ClientHandler(socket, metadataStore, nodeRegistry,
                                            replicationManager, authService, config));
    }

    public void stop() {
        log.info("Shutting down Master Server...");
        running.set(false);
        heartbeatMonitor.stop();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing server socket: {}", e.getMessage());
        }
        shutdownPool(clientPool, "client-pool");
        shutdownPool(nodePool, "node-pool");
        try { metadataStore.close(); } catch (Exception e) { /* ignore */ }
        log.info("Master Server stopped.");
    }

    private void shutdownPool(ExecutorService pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                log.warn("Force-stopped thread pool: {}", name);
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ─── Accessors (for testing) ───────────────────────────────────────────────

    public NodeRegistry getNodeRegistry()         { return nodeRegistry; }
    public SQLiteMetadataStore getMetadataStore() { return metadataStore; }
    public MasterConfig getConfig()               { return config; }
    public boolean isRunning()                    { return running.get(); }

    // ─── Main entry point ──────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            MasterServer server = new MasterServer();
            server.start();
        } catch (Exception e) {
            LogManager.getLogger(MasterServer.class).fatal("Failed to start Master Server", e);
            System.exit(1);
        }
    }
}
