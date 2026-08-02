package com.nimbusfs.node.server;

import com.nimbusfs.node.config.NodeConfig;
import com.nimbusfs.node.storage.ChunkStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP Server running on a Storage Node to listen for chunk operations from Master or Clients.
 */
public class ChunkServer {

    private static final Logger log = LogManager.getLogger(ChunkServer.class);

    private final NodeConfig      config;
    private final ChunkStore      chunkStore;
    private final ExecutorService threadPool;
    private final AtomicBoolean   running = new AtomicBoolean(false);

    private ServerSocket serverSocket;

    public ChunkServer(NodeConfig config, ChunkStore chunkStore) {
        this.config     = config;
        this.chunkStore = chunkStore;
        this.threadPool = Executors.newFixedThreadPool(16);
    }

    public void start() throws IOException {
        serverSocket = com.nimbusfs.common.net.NimbusSocketFactory.createServerSocket(config.getChunkPort(), config.isTlsEnabled());
        running.set(true);

        Thread acceptThread = new Thread(this::listen, "chunk-server-listen");
        acceptThread.setDaemon(true);
        acceptThread.start();

        log.info("ChunkServer listening on port {}", config.getChunkPort());
    }

    private void listen() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                threadPool.submit(new ChunkRequestHandler(socket, chunkStore));
            } catch (IOException e) {
                if (running.get()) {
                    log.error("ChunkServer accept error: {}", e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
        threadPool.shutdownNow();
        log.info("ChunkServer stopped.");
    }
}
