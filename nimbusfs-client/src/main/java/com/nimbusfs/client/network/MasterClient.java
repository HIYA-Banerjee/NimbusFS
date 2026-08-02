package com.nimbusfs.client.network;

import com.nimbusfs.client.model.SessionContext;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async Master Server TCP Client for JavaFX desktop application.
 * All network calls return CompletableFuture so JavaFX UI thread stays responsive.
 */
public class MasterClient {

    private static final Logger log = LogManager.getLogger(MasterClient.class);
    private static final MasterClient INSTANCE = new MasterClient();

    private final ExecutorService executor = Executors.newCachedThreadPool();

    private MasterClient() {}

    public static MasterClient get() {
        return INSTANCE;
    }

    /**
     * Sends a packet to the Master Server asynchronously and receives the response packet.
     */
    public CompletableFuture<Packet> sendRequest(Packet request) {
        return CompletableFuture.supplyAsync(() -> {
            String host = SessionContext.get().getServerHost();
            int port = SessionContext.get().getServerPort();

            try (Socket socket = new Socket(host, port)) {
                socket.setSoTimeout(30000);
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                request.writeTo(out);
                return Packet.readFrom(in);
            } catch (Exception e) {
                log.error("MasterClient request error: {}", e.getMessage());
                throw new RuntimeException("Failed to communicate with Master Server at " + host + ":" + port, e);
            }
        }, executor);
    }
}
