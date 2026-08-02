package com.nimbusfs.node.server;

import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.common.util.ChecksumUtil;
import com.nimbusfs.node.storage.ChunkStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles individual chunk requests (STORE, RETRIEVE, DELETE, REPLICATE) over TCP.
 */
public class ChunkRequestHandler implements Runnable {

    private static final Logger log = LogManager.getLogger(ChunkRequestHandler.class);

    private final Socket     socket;
    private final ChunkStore chunkStore;

    public ChunkRequestHandler(Socket socket, ChunkStore chunkStore) {
        this.socket     = socket;
        this.chunkStore = chunkStore;
    }

    @Override
    public void run() {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            Packet request = Packet.readFrom(in);
            Packet response = processRequest(request);
            if (response != null) {
                response.writeTo(out);
            }
        } catch (Exception e) {
            log.error("Error handling chunk request from {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private Packet processRequest(Packet request) throws Exception {
        MessageType type = request.getType();
        Map<?, ?> payload = request.getPayloadAs(Map.class);

        return switch (type) {
            case STORE_CHUNK -> handleStoreChunk(payload);
            case RETRIEVE_CHUNK -> handleRetrieveChunk(payload);
            case DELETE_CHUNK -> handleDeleteChunk(payload);
            case REPLICATE_TO -> handleReplicateTo(payload);
            default -> Packet.of(MessageType.ERROR, Map.of("message", "Unsupported chunk message type: " + type));
        };
    }

    private Packet handleStoreChunk(Map<?, ?> payload) throws Exception {
        String chunkId = (String) payload.get("chunkId");
        String dataBase64 = (String) payload.get("data");
        byte[] data = Base64.getDecoder().decode(dataBase64);

        chunkStore.storeChunk(chunkId, data);
        log.info("Stored chunk {} ({} bytes)", chunkId, data.length);

        return Packet.of(MessageType.STORE_CHUNK_ACK, Map.of("chunkId", chunkId, "success", true));
    }

    private Packet handleRetrieveChunk(Map<?, ?> payload) throws Exception {
        String chunkId = (String) payload.get("chunkId");
        byte[] data = chunkStore.retrieveChunk(chunkId);
        String checksum = ChecksumUtil.checksum(data);
        String dataBase64 = Base64.getEncoder().encodeToString(data);

        Map<String, Object> resp = new HashMap<>();
        resp.put("chunkId", chunkId);
        resp.put("data", dataBase64);
        resp.put("checksum", checksum);

        log.info("Retrieved chunk {} ({} bytes)", chunkId, data.length);
        return Packet.of(MessageType.CHUNK_DATA, resp);
    }

    private Packet handleDeleteChunk(Map<?, ?> payload) throws Exception {
        String chunkId = (String) payload.get("chunkId");
        boolean deleted = chunkStore.deleteChunk(chunkId);

        log.info("Deleted chunk {}: {}", chunkId, deleted);
        return Packet.of(MessageType.DELETE_CHUNK_ACK, Map.of("chunkId", chunkId, "success", deleted));
    }

    private Packet handleReplicateTo(Map<?, ?> payload) throws Exception {
        String chunkId = (String) payload.get("chunkId");
        String targetHost = (String) payload.get("targetHost");
        int targetPort = ((Number) payload.get("targetPort")).intValue();

        byte[] data = chunkStore.retrieveChunk(chunkId);
        String dataBase64 = Base64.getEncoder().encodeToString(data);

        // Connect to target storage node and send STORE_CHUNK
        try (Socket targetSocket = new Socket(targetHost, targetPort);
             InputStream targetIn = targetSocket.getInputStream();
             OutputStream targetOut = targetSocket.getOutputStream()) {

            Map<String, Object> storePayload = Map.of("chunkId", chunkId, "data", dataBase64);
            Packet storePacket = Packet.of(MessageType.STORE_CHUNK, storePayload);
            storePacket.writeTo(targetOut);

            Packet ack = Packet.readFrom(targetIn);
            log.info("Replicated chunk {} to {}:{} -> ACK {}", chunkId, targetHost, targetPort, ack.getType());
        }

        return Packet.of(MessageType.REPLICATION_DONE, Map.of("chunkId", chunkId, "success", true));
    }
}
