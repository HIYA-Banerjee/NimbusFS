package com.nimbusfs.master.server;

import com.nimbusfs.common.model.*;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import com.nimbusfs.master.auth.AuthService;
import com.nimbusfs.master.config.MasterConfig;
import com.nimbusfs.master.metadata.MetadataStore;
import com.nimbusfs.master.registry.NodeRegistry;
import com.nimbusfs.master.replication.ReplicationManager;
import com.nimbusfs.common.exception.AuthException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.Socket;
import java.time.Instant;
import java.util.*;

/**
 * Handles all incoming connections — both desktop clients and storage nodes.
 *
 * Protocol:
 *  - First packet determines connection type:
 *    NODE_REGISTER → node connection (delegates to node-handling logic)
 *    All others    → client connection (user-facing operations)
 *
 * Each instance runs on a dedicated thread from the master's thread pool.
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LogManager.getLogger(ClientHandler.class);

    private final Socket             socket;
    private final MetadataStore      metadataStore;
    private final NodeRegistry       nodeRegistry;
    private final ReplicationManager replicationManager;
    private final AuthService        authService;
    private final MasterConfig       config;

    // Session state (set after successful login)
    private User currentUser;

    public ClientHandler(Socket socket, MetadataStore metadataStore, NodeRegistry nodeRegistry,
                         ReplicationManager replicationManager, AuthService authService,
                         MasterConfig config) {
        this.socket             = socket;
        this.metadataStore      = metadataStore;
        this.nodeRegistry       = nodeRegistry;
        this.replicationManager = replicationManager;
        this.authService        = authService;
        this.config             = config;
    }

    // ─── Main dispatch loop ────────────────────────────────────────────────────

    @Override
    public void run() {
        String remote = socket.getRemoteSocketAddress().toString();
        log.debug("New connection from {}", remote);

        try {
            socket.setSoTimeout(300_000); // 5 minute idle timeout
            InputStream  in  = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            while (!socket.isClosed()) {
                Packet request;
                try {
                    request = Packet.readFrom(in);
                } catch (IOException e) {
                    break; // client disconnected
                }

                Packet response = handlePacket(request, out);
                if (response != null) {
                    response.writeTo(out);
                }
            }
        } catch (Exception e) {
            log.debug("Connection closed: {} — {}", remote, e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private Packet handlePacket(Packet request, OutputStream out) {
        try {
            return switch (request.getType()) {
                // ── Auth ──────────────────────────────────────────────────
                case LOGIN_REQUEST    -> handleLogin(request);
                case REGISTER_REQUEST -> handleRegister(request);
                case LOGOUT_REQUEST   -> handleLogout();

                // ── Node Registration ─────────────────────────────────────
                case NODE_REGISTER    -> handleNodeRegister(request);
                case HEARTBEAT        -> handleHeartbeat(request);

                // ── File operations ───────────────────────────────────────
                case UPLOAD_REQUEST   -> handleUploadRequest(request);
                case UPLOAD_COMPLETE  -> handleUploadComplete(request);
                case DOWNLOAD_REQUEST -> handleDownloadRequest(request);
                case DOWNLOAD_COMPLETE -> handleDownloadComplete(request);
                case DELETE_REQUEST   -> handleDeleteRequest(request);
                case RENAME_REQUEST   -> handleRenameRequest(request);
                case LIST_FILES_REQUEST -> handleListFiles(request);
                case FILE_DETAILS_REQUEST -> handleFileDetails(request);
                case CHUNK_CONFIRMED  -> handleChunkConfirmed(request);

                // ── Node Monitor ──────────────────────────────────────────
                case NODE_STATUS_REQUEST -> handleNodeStatusRequest();

                // ── Admin / Analytics ─────────────────────────────────────
                case ADMIN_STATS_REQUEST   -> handleAdminStats();
                case ANALYTICS_REQUEST     -> handleAnalytics(request);
                case ACTIVITY_LOG_REQUEST  -> handleActivityLog(request);
                case USER_LIST_REQUEST     -> handleUserList();

                // ── System ────────────────────────────────────────────────
                case PING -> Packet.empty(MessageType.PONG);

                default -> errorPacket(5000, "Unknown message type: " + request.getType());
            };
        } catch (AuthException ae) {
            return errorPacket(ae.getErrorCode(), ae.getMessage());
        } catch (Exception e) {
            log.error("Error handling packet {}: {}", request.getType(), e.getMessage(), e);
            return errorPacket(5000, "Internal server error: " + e.getMessage());
        }
    }

    // ─── Auth handlers ─────────────────────────────────────────────────────────

    private Packet handleLogin(Packet req) throws Exception {
        Map<?, ?> body = req.getPayloadAs(Map.class);
        String username = (String) body.get("username");
        String password = (String) body.get("password");

        User user = authService.login(username, password);
        currentUser = user;

        // Log activity
        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.USER_LOGIN,
            "User '" + username + "' logged in",
            System.currentTimeMillis()
        );
        event.setUserId(user.getUserId());
        metadataStore.logActivity(event);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",      true);
        response.put("sessionToken", user.getSessionToken());
        response.put("userId",       user.getUserId());
        response.put("username",     user.getUsername());
        response.put("role",         user.getRole().name());
        return Packet.of(MessageType.LOGIN_RESPONSE, response);
    }

    private Packet handleRegister(Packet req) throws Exception {
        Map<?, ?> body     = req.getPayloadAs(Map.class);
        String   username  = (String) body.get("username");
        String   password  = (String) body.get("password");

        User user = authService.register(username, password, User.Role.USER);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success",  true);
        response.put("username", user.getUsername());
        response.put("userId",   user.getUserId());
        return Packet.of(MessageType.REGISTER_RESPONSE, response);
    }

    private Packet handleLogout() throws Exception {
        if (currentUser != null) {
            ActivityEvent event = new ActivityEvent(
                ActivityEvent.EventType.USER_LOGOUT,
                "User '" + currentUser.getUsername() + "' logged out",
                System.currentTimeMillis()
            );
            event.setUserId(currentUser.getUserId());
            metadataStore.logActivity(event);
            currentUser = null;
        }
        return Packet.of(MessageType.ACK, Map.of("success", true));
    }

    // ─── Node registration ─────────────────────────────────────────────────────

    private Packet handleNodeRegister(Packet req) throws Exception {
        Map<?, ?> body = req.getPayloadAs(Map.class);
        NodeInfo node = new NodeInfo(
            (String) body.get("nodeId"),
            (String) body.get("host"),
            ((Number) body.get("port")).intValue(),
            ((Number) body.get("storageTotal")).longValue()
        );
        if (body.containsKey("displayName")) {
            node.setDisplayName((String) body.get("displayName"));
        }

        nodeRegistry.register(node);
        metadataStore.saveNode(node);

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.NODE_REGISTERED,
            "Node " + node.getDisplayName() + " registered at " + node.getHost() + ":" + node.getPort(),
            System.currentTimeMillis()
        );
        event.setNodeId(node.getNodeId());
        metadataStore.logActivity(event);

        return Packet.of(MessageType.NODE_REGISTER_ACK, Map.of("success", true, "displayName", node.getDisplayName()));
    }

    private Packet handleHeartbeat(Packet req) throws Exception {
        Map<?, ?> body     = req.getPayloadAs(Map.class);
        String   nodeId    = (String) body.get("nodeId");
        long     used      = ((Number) body.get("storageUsed")).longValue();
        long     total     = ((Number) body.get("storageTotal")).longValue();

        nodeRegistry.updateHeartbeat(nodeId, used, total);
        metadataStore.updateNodeStorage(nodeId, used, total);

        return Packet.empty(MessageType.HEARTBEAT_ACK);
    }

    // ─── File operation handlers ───────────────────────────────────────────────

    private Packet handleUploadRequest(Packet req) throws Exception {
        requireAuth();
        @SuppressWarnings("unchecked")
        Map<String, Object> body       = req.getPayloadAs(Map.class);
        String     fileName           = (String)  body.get("fileName");
        long       totalSize          = ((Number) body.get("totalSize")).longValue();
        int        chunkCount         = ((Number) body.get("chunkCount")).intValue();
        int        replicationFactor  = body.containsKey("replicationFactor") ? ((Number) body.get("replicationFactor")).intValue() : config.getDefaultReplicationFactor();

        // Generate chunk IDs
        List<String> chunkIds = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) chunkIds.add(UUID.randomUUID().toString());

        String fileId = UUID.randomUUID().toString();

        UploadPlan plan = replicationManager.planUpload(fileId, chunkCount, replicationFactor, chunkIds);

        // Create pending file metadata
        FileMetadata file = new FileMetadata(fileId, fileName, currentUser.getUserId(), totalSize);
        file.setReplicationFactor(replicationFactor);
        file.setChunkCount(chunkCount);
        file.setChunkIds(chunkIds.toArray(new String[0]));
        file.setEncrypted(body.containsKey("isEncrypted") ? (Boolean) body.get("isEncrypted") : false);
        file.setCompressed(body.containsKey("isCompressed") ? (Boolean) body.get("isCompressed") : false);
        metadataStore.saveFile(file);

        // Save chunk stubs
        for (int i = 0; i < chunkCount; i++) {
            ChunkInfo chunk = new ChunkInfo(chunkIds.get(i), fileId, i, 0, null);
            metadataStore.saveChunk(chunk);
        }

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.UPLOAD_STARTED,
            "Upload started: " + fileName + " (" + chunkCount + " chunks)",
            System.currentTimeMillis()
        );
        event.setUserId(currentUser.getUserId());
        event.setFileId(fileId);
        metadataStore.logActivity(event);

        return Packet.of(MessageType.UPLOAD_PLAN, plan);
    }

    private Packet handleChunkConfirmed(Packet req) throws Exception {
        Map<?, ?>   body    = req.getPayloadAs(Map.class);
        String      chunkId = (String) body.get("chunkId");
        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) body.get("nodeIds");

        for (String nodeId : nodeIds) {
            metadataStore.addChunkToNode(chunkId, nodeId);
        }
        return Packet.empty(MessageType.ACK);
    }

    private Packet handleUploadComplete(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body     = req.getPayloadAs(Map.class);
        String    fileId   = (String) body.get("fileId");
        String    checksum = (String) body.getOrDefault("checksum", null);

        FileMetadata file = metadataStore.getFile(fileId);
        if (file != null) {
            file.setStatus(FileMetadata.FileStatus.HEALTHY);
            file.setChecksum(checksum);
            file.setUpdatedAt(System.currentTimeMillis());
            metadataStore.saveFile(file);

            ActivityEvent event = new ActivityEvent(
                ActivityEvent.EventType.UPLOAD_COMPLETE,
                "Upload complete: " + file.getFileName(),
                System.currentTimeMillis()
            );
            event.setUserId(currentUser.getUserId());
            event.setFileId(fileId);
            metadataStore.logActivity(event);
        }

        return Packet.of(MessageType.UPLOAD_SUCCESS, Map.of("fileId", fileId, "status", "HEALTHY"));
    }

    private Packet handleDownloadRequest(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body   = req.getPayloadAs(Map.class);
        String   fileId  = (String) body.get("fileId");

        FileMetadata file = metadataStore.getFile(fileId);
        if (file == null) {
            return errorPacket(1001, "File not found: " + fileId);
        }

        List<ChunkInfo> chunks = metadataStore.getChunksForFile(fileId);
        for (ChunkInfo chunk : chunks) {
            List<String> nodeIds = metadataStore.getChunkNodes(chunk.getChunkId());
            // Provide only online nodes
            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (String nodeId : nodeIds) {
                NodeInfo node = nodeRegistry.get(nodeId);
                if (node != null && node.isOnline()) {
                    Map<String, Object> nodeMap = new LinkedHashMap<>();
                    nodeMap.put("nodeId", node.getNodeId());
                    nodeMap.put("host",   node.getHost());
                    nodeMap.put("port",   node.getPort());
                    nodeList.add(nodeMap);
                }
            }
            chunk.setNodeIds(nodeIds);
        }

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.DOWNLOAD_STARTED,
            "Download started: " + file.getFileName(),
            System.currentTimeMillis()
        );
        event.setUserId(currentUser.getUserId());
        event.setFileId(fileId);
        metadataStore.logActivity(event);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("file",   file);
        response.put("chunks", chunks);
        return Packet.of(MessageType.DOWNLOAD_PLAN, response);
    }

    private Packet handleDownloadComplete(Packet req) throws Exception {
        Map<?, ?> body   = req.getPayloadAs(Map.class);
        String   fileId  = (String) body.get("fileId");

        metadataStore.incrementDownloadCount(fileId);
        FileMetadata file = metadataStore.getFile(fileId);

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.DOWNLOAD_COMPLETE,
            "Download complete: " + (file != null ? file.getFileName() : fileId),
            System.currentTimeMillis()
        );
        if (currentUser != null) event.setUserId(currentUser.getUserId());
        event.setFileId(fileId);
        metadataStore.logActivity(event);

        return Packet.empty(MessageType.ACK);
    }

    private Packet handleDeleteRequest(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body  = req.getPayloadAs(Map.class);
        String   fileId = (String) body.get("fileId");

        FileMetadata file = metadataStore.getFile(fileId);
        if (file == null) {
            return errorPacket(1001, "File not found: " + fileId);
        }

        // Mark as deleting first
        metadataStore.updateFileStatus(fileId, FileMetadata.FileStatus.DELETING);
        String fileName = file.getFileName();

        // Get chunks and instruct nodes to delete them
        List<ChunkInfo> chunks = metadataStore.getChunksForFile(fileId);
        for (ChunkInfo chunk : chunks) {
            List<String> nodeIds = metadataStore.getChunkNodes(chunk.getChunkId());
            for (String nodeId : nodeIds) {
                NodeInfo node = nodeRegistry.get(nodeId);
                if (node != null && node.isOnline()) {
                    sendDeleteChunkCommand(node, chunk.getChunkId());
                }
            }
        }

        // Clean up metadata
        metadataStore.deleteChunksForFile(fileId);
        metadataStore.deleteFile(fileId);

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.FILE_DELETED,
            "File deleted: " + fileName,
            System.currentTimeMillis()
        );
        event.setUserId(currentUser.getUserId());
        event.setFileId(fileId);
        metadataStore.logActivity(event);

        return Packet.of(MessageType.DELETE_RESPONSE, Map.of("success", true, "fileId", fileId));
    }

    private Packet handleRenameRequest(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body    = req.getPayloadAs(Map.class);
        String   fileId   = (String) body.get("fileId");
        String   newName  = (String) body.get("newName");

        FileMetadata file = metadataStore.getFile(fileId);
        if (file == null) {
            return errorPacket(1001, "File not found: " + fileId);
        }

        metadataStore.updateFileName(fileId, newName);

        ActivityEvent event = new ActivityEvent(
            ActivityEvent.EventType.FILE_RENAMED,
            "File renamed: " + file.getFileName() + " → " + newName,
            System.currentTimeMillis()
        );
        event.setUserId(currentUser.getUserId());
        event.setFileId(fileId);
        metadataStore.logActivity(event);

        return Packet.of(MessageType.RENAME_RESPONSE, Map.of("success", true, "newName", newName));
    }

    private Packet handleListFiles(Packet req) throws Exception {
        requireAuth();
        String ownerId = currentUser.isAdmin() ? null : currentUser.getUserId();
        List<FileMetadata> files = ownerId == null
            ? metadataStore.listAllFiles()
            : metadataStore.listFiles(ownerId);
        return Packet.of(MessageType.LIST_FILES_RESPONSE, Map.of("files", files));
    }

    private Packet handleFileDetails(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body   = req.getPayloadAs(Map.class);
        String   fileId  = (String) body.get("fileId");
        FileMetadata file = metadataStore.getFile(fileId);
        if (file == null) return errorPacket(1001, "File not found: " + fileId);

        List<ChunkInfo>    chunks    = metadataStore.getChunksForFile(fileId);
        List<ActivityEvent> activity = metadataStore.getActivityForFile(fileId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("file",     file);
        response.put("chunks",   chunks);
        response.put("activity", activity);
        return Packet.of(MessageType.FILE_DETAILS_RESPONSE, response);
    }

    // ─── Node Monitor ──────────────────────────────────────────────────────────

    private Packet handleNodeStatusRequest() throws Exception {
        requireAuth();
        List<NodeInfo> nodes = nodeRegistry.getAllNodes();
        return Packet.of(MessageType.NODE_STATUS_RESPONSE, Map.of("nodes", nodes));
    }

    // ─── Admin / Analytics ─────────────────────────────────────────────────────

    private Packet handleAdminStats() throws Exception {
        requireAuth();
        requireAdmin();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers",         metadataStore.getTotalUsersCount());
        stats.put("totalFiles",         metadataStore.getTotalFilesCount());
        stats.put("totalStorageBytes",  metadataStore.getTotalStorageBytes());
        stats.put("totalNodes",         nodeRegistry.getTotalCount());
        stats.put("onlineNodes",        nodeRegistry.getOnlineCount());
        stats.put("clusterStorageBytes", nodeRegistry.getTotalStorageBytes());
        stats.put("clusterUsedBytes",   nodeRegistry.getUsedStorageBytes());
        return Packet.of(MessageType.ADMIN_STATS_RESPONSE, stats);
    }

    private Packet handleAnalytics(Packet req) throws Exception {
        requireAuth();
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("clusterUsedBytes",  nodeRegistry.getUsedStorageBytes());
        analytics.put("clusterTotalBytes", nodeRegistry.getTotalStorageBytes());
        analytics.put("nodes",             nodeRegistry.getAllNodes());
        analytics.put("recentActivity",    metadataStore.getRecentActivity(100));
        return Packet.of(MessageType.ANALYTICS_RESPONSE, analytics);
    }

    private Packet handleActivityLog(Packet req) throws Exception {
        requireAuth();
        Map<?, ?> body  = req.getPayloadAs(Map.class);
        int       limit = body.containsKey("limit") ? ((Number) body.get("limit")).intValue() : 50;
        List<ActivityEvent> events = metadataStore.getRecentActivity(limit);
        return Packet.of(MessageType.ACTIVITY_LOG_RESPONSE, Map.of("events", events));
    }

    private Packet handleUserList() throws Exception {
        requireAdmin();
        List<User> users = metadataStore.getAllUsers();
        // Sanitize: remove password hashes before sending
        users.forEach(u -> u.setPasswordHash(null));
        return Packet.of(MessageType.USER_LIST_RESPONSE, Map.of("users", users));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private void requireAuth() throws AuthException {
        if (currentUser == null) {
            throw new AuthException("Not authenticated", AuthException.CODE_AUTH_FAILED);
        }
    }

    private void requireAdmin() throws AuthException {
        requireAuth();
        if (!currentUser.isAdmin()) {
            throw new AuthException("Admin privileges required", AuthException.CODE_PERMISSION_DENIED);
        }
    }

    private Packet errorPacket(int code, String message) {
        return Packet.of(MessageType.ERROR, Map.of("code", code, "message", message));
    }

    private void sendDeleteChunkCommand(NodeInfo node, String chunkId) {
        try (Socket s = new Socket(node.getHost(), node.getPort())) {
            s.setSoTimeout(5000);
            Packet cmd = Packet.of(MessageType.DELETE_CHUNK, Map.of("chunkId", chunkId));
            cmd.writeTo(s.getOutputStream());
        } catch (Exception e) {
            log.warn("Failed to send delete-chunk command to {}: {}", node.getDisplayName(), e.getMessage());
        }
    }
}
