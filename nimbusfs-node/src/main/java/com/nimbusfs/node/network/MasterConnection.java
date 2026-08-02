package com.nimbusfs.node.network;

import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.node.config.NodeConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles communication from Storage Node to Master Server.
 */
public class MasterConnection {

    private static final Logger log = LogManager.getLogger(MasterConnection.class);

    private final NodeConfig config;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public MasterConnection(NodeConfig config) {
        this.config = config;
    }

    public synchronized void connect() throws IOException {
        if (socket != null && !socket.isClosed()) return;

        log.info("Connecting to Master at {}:{}...", config.getMasterHost(), config.getMasterPort());
        socket = com.nimbusfs.common.net.NimbusSocketFactory.createClientSocket(config.getMasterHost(), config.getMasterPort(), config.isTlsEnabled());
        in  = socket.getInputStream();
        out = socket.getOutputStream();
        log.info("Connected to Master.");
    }

    public synchronized void register() throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("nodeId", config.getNodeId());
        payload.put("host", config.getHost());
        payload.put("port", config.getChunkPort());
        payload.put("storageTotal", config.getStorageLimitBytes());
        if (!config.getDisplayName().isEmpty()) {
            payload.put("displayName", config.getDisplayName());
        }

        Packet regPacket = Packet.of(MessageType.NODE_REGISTER, payload);
        regPacket.writeTo(out);

        Packet ack = Packet.readFrom(in);
        log.info("Node registration ACK received from Master: {}", ack.getType());
    }

    public synchronized Packet sendRequest(Packet request) throws IOException {
        connect();
        request.writeTo(out);
        return Packet.readFrom(in);
    }

    public synchronized void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        log.info("Disconnected from Master.");
    }
}
