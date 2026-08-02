package com.nimbusfs.master.replication;

import com.nimbusfs.common.model.*;
import com.nimbusfs.master.metadata.MetadataStore;
import com.nimbusfs.master.registry.NodeRegistry;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.common.util.JsonUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Socket;
import java.util.*;

/**
 * Manages chunk replication in NimbusFS.
 *
 * Responsibilities:
 *  1. Plan uploads — select which nodes store each chunk (called before upload)
 *  2. Recover from node failures — find orphaned chunks and re-replicate them
 */
public class ReplicationManager {

    private static final Logger log = LogManager.getLogger(ReplicationManager.class);

    private final MetadataStore metadataStore;
    private final NodeRegistry  nodeRegistry;

    public ReplicationManager(MetadataStore metadataStore, NodeRegistry nodeRegistry) {
        this.metadataStore = metadataStore;
        this.nodeRegistry  = nodeRegistry;
    }

    // ─── Upload planning ───────────────────────────────────────────────────────

    /**
     * Creates an UploadPlan for a new file.
     *
     * For each chunk, selects RF healthy nodes with the most available storage.
     * Returns a plan the client can use to distribute chunks directly to nodes.
     *
     * @param fileId           UUID of the new file
     * @param chunkCount       number of chunks the file was split into
     * @param replicationFactor number of replicas per chunk
     * @param chunkIds         ordered list of pre-generated chunk IDs
     * @return UploadPlan with chunk → node assignments
     */
    public UploadPlan planUpload(String fileId, int chunkCount, int replicationFactor, List<String> chunkIds) {
        List<UploadPlan.ChunkAssignment> assignments = new ArrayList<>();

        for (int i = 0; i < chunkCount; i++) {
            String chunkId = chunkIds.get(i);
            // Select RF nodes, excluding nodes already selected for previous replicas of THIS chunk
            List<NodeInfo> selected = nodeRegistry.selectNodes(replicationFactor, Collections.emptySet());
            assignments.add(new UploadPlan.ChunkAssignment(chunkId, i, selected));
        }

        log.info("Upload plan created for file {}: {} chunks × {} replicas = {} chunk-placements",
            fileId, chunkCount, replicationFactor, chunkCount * replicationFactor);

        return new UploadPlan(fileId, assignments);
    }

    // ─── Failure recovery ──────────────────────────────────────────────────────

    /**
     * Triggered when a storage node goes offline.
     *
     * Finds all chunks stored exclusively on the failed node and re-replicates
     * them to new healthy nodes to restore the configured replication factor.
     *
     * @param offlineNodeId UUID of the node that went offline
     */
    public void recover(String offlineNodeId) {
        NodeInfo offlineNode = nodeRegistry.get(offlineNodeId);
        String nodeName = offlineNode != null ? offlineNode.getDisplayName() : offlineNodeId;

        log.warn("Starting recovery for offline node: {}", nodeName);

        try {
            List<String> affectedChunkIds = metadataStore.getChunksOnNode(offlineNodeId);
            log.info("Found {} chunks on offline node {}.", affectedChunkIds.size(), nodeName);

            int recovered = 0;
            int failed    = 0;

            for (String chunkId : affectedChunkIds) {
                try {
                    recoverChunk(chunkId, offlineNodeId);
                    recovered++;
                } catch (Exception e) {
                    log.error("Failed to recover chunk {}: {}", chunkId, e.getMessage());
                    failed++;
                }
            }

            log.info("Recovery complete for {}: {}/{} chunks recovered, {} failed.",
                nodeName, recovered, affectedChunkIds.size(), failed);

        } catch (Exception e) {
            log.error("Recovery process failed for node {}: {}", nodeName, e.getMessage());
        }
    }

    /**
     * Re-replicates a single chunk from a healthy source to a new target node.
     */
    private void recoverChunk(String chunkId, String offlineNodeId) throws Exception {
        // Get all current nodes holding this chunk
        List<String> currentNodeIds = metadataStore.getChunkNodes(chunkId);
        currentNodeIds.remove(offlineNodeId); // exclude the offline node

        if (currentNodeIds.isEmpty()) {
            log.error("CRITICAL: No healthy replicas found for chunk {}! Data may be LOST.", chunkId);
            return;
        }

        // Find a healthy source node
        String sourceNodeId = findHealthyNode(currentNodeIds);
        if (sourceNodeId == null) {
            log.error("No healthy source node found for chunk {}. Cannot recover.", chunkId);
            return;
        }

        NodeInfo sourceNode = nodeRegistry.get(sourceNodeId);
        if (sourceNode == null) {
            log.error("Source node {} not in registry for chunk {}.", sourceNodeId, chunkId);
            return;
        }

        // Select a new target node (not already holding this chunk)
        Set<String> excludeIds = new HashSet<>(currentNodeIds);
        excludeIds.add(offlineNodeId);

        List<NodeInfo> targets = nodeRegistry.selectNodes(1, excludeIds);
        if (targets.isEmpty()) {
            log.warn("No available target nodes for chunk {}. Will retry later.", chunkId);
            return;
        }

        NodeInfo targetNode = targets.get(0);

        // Instruct the source node to replicate this chunk to the target
        log.info("Replicating chunk {} from {} to {}.", chunkId,
            sourceNode.getDisplayName(), targetNode.getDisplayName());

        sendReplicateCommand(sourceNode, chunkId, targetNode);

        // Update metadata
        metadataStore.addChunkToNode(chunkId, targetNode.getNodeId());
        metadataStore.removeChunkFromNode(chunkId, offlineNodeId);

        log.info("Chunk {} successfully recovered to {}.", chunkId, targetNode.getDisplayName());
    }

    private String findHealthyNode(List<String> nodeIds) {
        for (String nodeId : nodeIds) {
            NodeInfo node = nodeRegistry.get(nodeId);
            if (node != null && node.isOnline()) {
                return nodeId;
            }
        }
        return null;
    }

    /**
     * Opens a TCP connection to the source node and sends REPLICATE_TO command.
     * The source node then pushes the chunk directly to the target.
     */
    private void sendReplicateCommand(NodeInfo sourceNode, String chunkId, NodeInfo targetNode) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("chunkId",    chunkId);
        payload.put("targetHost", targetNode.getHost());
        payload.put("targetPort", targetNode.getPort());
        payload.put("targetNodeId", targetNode.getNodeId());

        try (Socket socket = new Socket(sourceNode.getHost(), sourceNode.getPort())) {
            socket.setSoTimeout(30_000); // 30 second timeout
            Packet cmd = Packet.of(MessageType.REPLICATE_TO, payload);
            cmd.writeTo(socket.getOutputStream());
            // Read ACK
            Packet ack = Packet.readFrom(socket.getInputStream());
            if (ack.getType() != MessageType.REPLICATION_DONE && ack.getType() != MessageType.ACK) {
                throw new IOException("Unexpected response to REPLICATE_TO: " + ack.getType());
            }
        }
    }
}
