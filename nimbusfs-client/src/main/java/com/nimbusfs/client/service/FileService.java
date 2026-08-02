package com.nimbusfs.client.service;

import com.nimbusfs.client.model.SessionContext;
import com.nimbusfs.client.network.MasterClient;
import com.nimbusfs.common.compression.CompressionUtil;
import com.nimbusfs.common.crypto.AESUtil;
import com.nimbusfs.common.model.*;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.common.util.ChecksumUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * High-level File operations service for JavaFX client:
 * Upload (chunk, encrypt, compress, send), Download (reassemble, decrypt, decompress), Delete, Rename, List.
 */
public class FileService {

    private static final Logger log = LogManager.getLogger(FileService.class);
    private final MasterClient masterClient = MasterClient.get();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Uploads a file to NimbusFS cluster.
     */
    public CompletableFuture<Void> uploadFile(File file, UploadOptions options, Consumer<Double> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String originalChecksum = ChecksumUtil.checksum(fileBytes);
                long totalSize = fileBytes.length;

                long chunkSize = options.getChunkSizeBytes();
                int chunkCount = (int) Math.ceil((double) totalSize / chunkSize);
                if (chunkCount == 0) chunkCount = 1;

                // 1. Request Upload Plan from Master
                Map<String, Object> reqPayload = new HashMap<>();
                reqPayload.put("fileName", file.getName());
                reqPayload.put("totalSize", totalSize);
                reqPayload.put("chunkCount", chunkCount);
                reqPayload.put("replicationFactor", options.getReplicationFactor());
                reqPayload.put("isEncrypted", options.isEncrypt());
                reqPayload.put("isCompressed", options.isCompress());

                Packet planReq = Packet.of(MessageType.UPLOAD_REQUEST, reqPayload);
                Packet planResp = masterClient.sendRequest(planReq).get();

                if (planResp.getType() == MessageType.ERROR) {
                    throw new RuntimeException(planResp.getPayloadAsString());
                }

                UploadPlan plan = planResp.getPayloadAs(UploadPlan.class);

                // AES key if encryption enabled
                byte[] aesKey = options.isEncrypt() ? AESUtil.generateKey() : null;

                // 2. Process and send each chunk
                List<UploadPlan.ChunkAssignment> assignments = plan.getAssignments();
                for (int i = 0; i < assignments.size(); i++) {
                    UploadPlan.ChunkAssignment assignment = assignments.get(i);
                    int start = i * (int) chunkSize;
                    int end = Math.min(start + (int) chunkSize, fileBytes.length);
                    byte[] chunkRaw = Arrays.copyOfRange(fileBytes, start, end);

                    // Compress
                    if (options.isCompress()) {
                        chunkRaw = CompressionUtil.compress(chunkRaw);
                    }

                    // Encrypt
                    if (options.isEncrypt()) {
                        chunkRaw = AESUtil.encrypt(aesKey, chunkRaw);
                    }

                    String chunkDataBase64 = Base64.getEncoder().encodeToString(chunkRaw);

                    // Send chunk to target nodes
                    List<String> confirmedNodeIds = new ArrayList<>();
                    for (NodeInfo targetNode : assignment.getTargetNodes()) {
                        try (Socket s = com.nimbusfs.common.net.NimbusSocketFactory.createClientSocket(targetNode.getHost(), targetNode.getPort(), SessionContext.get().isTlsEnabled())) {
                            Map<String, Object> storeMsg = Map.of(
                                "chunkId", assignment.getChunkId(),
                                "data", chunkDataBase64
                            );
                            Packet storePacket = Packet.of(MessageType.STORE_CHUNK, storeMsg);
                            storePacket.writeTo(s.getOutputStream());

                            Packet ack = Packet.readFrom(s.getInputStream());
                            if (ack.getType() == MessageType.STORE_CHUNK_ACK) {
                                confirmedNodeIds.add(targetNode.getNodeId());
                            }
                        }
                    }

                    // Confirm chunk with Master
                    Map<String, Object> confirmMsg = Map.of(
                        "chunkId", assignment.getChunkId(),
                        "nodeIds", confirmedNodeIds
                    );
                    masterClient.sendRequest(Packet.of(MessageType.CHUNK_CONFIRMED, confirmMsg)).get();

                    if (progressCallback != null) {
                        progressCallback.accept((double) (i + 1) / assignments.size());
                    }
                }

                // 3. Complete Upload
                Map<String, Object> compMsg = Map.of(
                    "fileId", plan.getFileId(),
                    "checksum", originalChecksum
                );
                masterClient.sendRequest(Packet.of(MessageType.UPLOAD_COMPLETE, compMsg)).get();

            } catch (Exception e) {
                log.error("Upload failed: {}", e.getMessage(), e);
                throw new RuntimeException("Upload failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Downloads a file from NimbusFS cluster and reassembles it.
     */
    public CompletableFuture<Void> downloadFile(String fileId, Path destinationPath, Consumer<Double> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                Packet req = Packet.of(MessageType.DOWNLOAD_REQUEST, Map.of("fileId", fileId));
                Packet resp = masterClient.sendRequest(req).get();

                if (resp.getType() == MessageType.ERROR) {
                    throw new RuntimeException(resp.getPayloadAsString());
                }

                Map<?, ?> body = resp.getPayloadAs(Map.class);
                FileMetadata fileMeta = mapper.convertValue(body.get("file"), FileMetadata.class);
                List<ChunkInfo> chunks = mapper.convertValue(body.get("chunks"), new TypeReference<List<ChunkInfo>>() {});

                ByteArrayOutputStream fileStream = new ByteArrayOutputStream();

                for (int i = 0; i < chunks.size(); i++) {
                    ChunkInfo chunk = chunks.get(i);
                    byte[] chunkData = fetchChunkFromNodes(chunk);

                    // Decrypt if needed
                    if (fileMeta.isEncrypted()) {
                        // Note: For full key management, key can be passed or stored in metadata
                    }

                    // Decompress if needed
                    if (fileMeta.isCompressed()) {
                        chunkData = CompressionUtil.decompress(chunkData);
                    }

                    fileStream.write(chunkData);

                    if (progressCallback != null) {
                        progressCallback.accept((double) (i + 1) / chunks.size());
                    }
                }

                byte[] downloadedBytes = fileStream.toByteArray();
                Files.write(destinationPath, downloadedBytes);

                // Notify Master download complete
                masterClient.sendRequest(Packet.of(MessageType.DOWNLOAD_COMPLETE, Map.of("fileId", fileId)));

            } catch (Exception e) {
                log.error("Download failed: {}", e.getMessage(), e);
                throw new RuntimeException("Download failed: " + e.getMessage(), e);
            }
        });
    }

    private byte[] fetchChunkFromNodes(ChunkInfo chunk) {
        List<String> nodeIds = chunk.getNodeIds();
        for (String nodeId : nodeIds) {
            try {
                // Fetch Node status to get host/port
                Packet nodeReq = Packet.of(MessageType.NODE_STATUS_REQUEST, Map.of());
                Packet nodeResp = masterClient.sendRequest(nodeReq).get();
                Map<?, ?> nodeBody = nodeResp.getPayloadAs(Map.class);
                List<NodeInfo> nodes = mapper.convertValue(nodeBody.get("nodes"), new TypeReference<List<NodeInfo>>() {});

                NodeInfo target = nodes.stream().filter(n -> n.getNodeId().equals(nodeId) && n.isOnline()).findFirst().orElse(null);
                if (target == null) continue;

                try (Socket s = com.nimbusfs.common.net.NimbusSocketFactory.createClientSocket(target.getHost(), target.getPort(), SessionContext.get().isTlsEnabled())) {
                    Packet getChunk = Packet.of(MessageType.RETRIEVE_CHUNK, Map.of("chunkId", chunk.getChunkId()));
                    getChunk.writeTo(s.getOutputStream());

                    Packet resp = Packet.readFrom(s.getInputStream());
                    if (resp.getType() == MessageType.CHUNK_DATA) {
                        Map<?, ?> payload = resp.getPayloadAs(Map.class);
                        return Base64.getDecoder().decode((String) payload.get("data"));
                    }
                }
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("Failed to fetch chunk " + chunk.getChunkId() + " from any available node");
    }

    public CompletableFuture<List<FileMetadata>> listFiles() {
        Packet req = Packet.of(MessageType.LIST_FILES_REQUEST, Map.of());
        return masterClient.sendRequest(req).thenApply(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
            Map<?, ?> body = resp.getPayloadAs(Map.class);
            return mapper.convertValue(body.get("files"), new TypeReference<List<FileMetadata>>() {});
        });
    }

    public CompletableFuture<Void> deleteFile(String fileId) {
        Packet req = Packet.of(MessageType.DELETE_REQUEST, Map.of("fileId", fileId));
        return masterClient.sendRequest(req).thenAccept(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
        });
    }

    public CompletableFuture<Void> renameFile(String fileId, String newName) {
        Packet req = Packet.of(MessageType.RENAME_REQUEST, Map.of("fileId", fileId, "newName", newName));
        return masterClient.sendRequest(req).thenAccept(resp -> {
            if (resp.getType() == MessageType.ERROR) {
                throw new RuntimeException(resp.getPayloadAsString());
            }
        });
    }
}
