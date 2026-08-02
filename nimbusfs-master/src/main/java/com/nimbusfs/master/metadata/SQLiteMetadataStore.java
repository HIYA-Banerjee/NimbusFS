package com.nimbusfs.master.metadata;

import com.nimbusfs.common.model.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed implementation of MetadataStore.
 *
 * Uses JDBC with WAL mode for concurrent reads and a single writer.
 * All operations use PreparedStatements to prevent SQL injection.
 * Schema is created automatically on first run via DatabaseMigration.
 */
public class SQLiteMetadataStore implements MetadataStore {

    private static final Logger log = LogManager.getLogger(SQLiteMetadataStore.class);

    private final String  dbPath;
    private Connection    conn;

    public SQLiteMetadataStore(String dbPath) {
        this.dbPath = dbPath;
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void initialize() throws Exception {
        // Ensure parent directories exist
        Path parent = Paths.get(dbPath).getParent();
        if (parent != null) Files.createDirectories(parent);

        String url = "jdbc:sqlite:" + dbPath;
        conn = DriverManager.getConnection(url);

        // Performance pragmas
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA cache_size=10000");
            st.execute("PRAGMA foreign_keys=ON");
        }

        DatabaseMigration.migrate(conn);
        log.info("SQLite MetadataStore initialized at: {}", dbPath);
    }

    @Override
    public void close() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
            log.info("SQLite connection closed.");
        }
    }

    // ─── File operations ───────────────────────────────────────────────────────

    @Override
    public void saveFile(FileMetadata file) throws Exception {
        String sql = """
            INSERT OR REPLACE INTO files
              (file_id, file_name, owner_id, size_bytes, checksum,
               replication_factor, status, created_at, updated_at,
               download_count, is_encrypted, is_compressed, chunk_count)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, file.getFileId());
            ps.setString(2, file.getFileName());
            ps.setString(3, file.getOwnerId());
            ps.setLong(4,   file.getSizeBytes());
            ps.setString(5, file.getChecksum());
            ps.setInt(6,    file.getReplicationFactor());
            ps.setString(7, file.getStatus().name());
            ps.setLong(8,   file.getCreatedAt());
            ps.setLong(9,   file.getUpdatedAt());
            ps.setInt(10,   file.getDownloadCount());
            ps.setInt(11,   file.isEncrypted()  ? 1 : 0);
            ps.setInt(12,   file.isCompressed() ? 1 : 0);
            ps.setInt(13,   file.getChunkCount());
            ps.executeUpdate();
        }
    }

    @Override
    public FileMetadata getFile(String fileId) throws Exception {
        String sql = "SELECT * FROM files WHERE file_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFile(rs) : null;
            }
        }
    }

    @Override
    public FileMetadata getFileByName(String fileName, String ownerId) throws Exception {
        String sql = "SELECT * FROM files WHERE file_name = ? AND owner_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ps.setString(2, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFile(rs) : null;
            }
        }
    }

    @Override
    public List<FileMetadata> listFiles(String ownerId) throws Exception {
        String sql = "SELECT * FROM files WHERE owner_id = ? AND status != 'DELETING' ORDER BY created_at DESC";
        List<FileMetadata> files = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) files.add(mapFile(rs));
            }
        }
        return files;
    }

    @Override
    public List<FileMetadata> listAllFiles() throws Exception {
        String sql = "SELECT f.*, u.username as owner_name FROM files f LEFT JOIN users u ON f.owner_id = u.user_id ORDER BY f.created_at DESC";
        List<FileMetadata> files = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                FileMetadata f = mapFile(rs);
                try { f.setOwnerName(rs.getString("owner_name")); } catch (Exception ignored) {}
                files.add(f);
            }
        }
        return files;
    }

    @Override
    public void updateFileStatus(String fileId, FileMetadata.FileStatus status) throws Exception {
        String sql = "UPDATE files SET status = ?, updated_at = ? WHERE file_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2,   System.currentTimeMillis());
            ps.setString(3, fileId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateFileName(String fileId, String newName) throws Exception {
        String sql = "UPDATE files SET file_name = ?, updated_at = ? WHERE file_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setLong(2,   System.currentTimeMillis());
            ps.setString(3, fileId);
            ps.executeUpdate();
        }
    }

    @Override
    public void deleteFile(String fileId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM files WHERE file_id = ?")) {
            ps.setString(1, fileId);
            ps.executeUpdate();
        }
    }

    @Override
    public void incrementDownloadCount(String fileId) throws Exception {
        String sql = "UPDATE files SET download_count = download_count + 1 WHERE file_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            ps.executeUpdate();
        }
    }

    @Override
    public long getTotalFilesCount() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM files WHERE status != 'DELETING'")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    @Override
    public long getTotalStorageBytes() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(size_bytes) FROM files WHERE status != 'DELETING'")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ─── Chunk operations ──────────────────────────────────────────────────────

    @Override
    public void saveChunk(ChunkInfo chunk) throws Exception {
        String sql = "INSERT OR REPLACE INTO chunks (chunk_id, file_id, chunk_index, size_bytes, checksum, status) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunk.getChunkId());
            ps.setString(2, chunk.getFileId());
            ps.setInt(3,    chunk.getChunkIndex());
            ps.setLong(4,   chunk.getSizeBytes());
            ps.setString(5, chunk.getChecksum());
            ps.setString(6, chunk.getStatus().name());
            ps.executeUpdate();
        }
    }

    @Override
    public ChunkInfo getChunk(String chunkId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM chunks WHERE chunk_id = ?")) {
            ps.setString(1, chunkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapChunk(rs) : null;
            }
        }
    }

    @Override
    public List<ChunkInfo> getChunksForFile(String fileId) throws Exception {
        String sql = "SELECT * FROM chunks WHERE file_id = ? ORDER BY chunk_index";
        List<ChunkInfo> chunks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chunks.add(mapChunk(rs));
            }
        }
        return chunks;
    }

    @Override
    public List<String> getChunkNodes(String chunkId) throws Exception {
        String sql = "SELECT node_id FROM chunk_nodes WHERE chunk_id = ?";
        List<String> nodes = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunkId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) nodes.add(rs.getString("node_id"));
            }
        }
        return nodes;
    }

    @Override
    public void addChunkToNode(String chunkId, String nodeId) throws Exception {
        String sql = "INSERT OR IGNORE INTO chunk_nodes (chunk_id, node_id, stored_at) VALUES (?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunkId);
            ps.setString(2, nodeId);
            ps.setLong(3,   System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    @Override
    public void removeChunkFromNode(String chunkId, String nodeId) throws Exception {
        String sql = "DELETE FROM chunk_nodes WHERE chunk_id = ? AND node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, chunkId);
            ps.setString(2, nodeId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<String> getChunksOnNode(String nodeId) throws Exception {
        String sql = "SELECT chunk_id FROM chunk_nodes WHERE node_id = ?";
        List<String> chunks = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chunks.add(rs.getString("chunk_id"));
            }
        }
        return chunks;
    }

    @Override
    public void deleteChunksForFile(String fileId) throws Exception {
        // Remove chunk_nodes entries first (FK constraint)
        String getChunksSql = "SELECT chunk_id FROM chunks WHERE file_id = ?";
        List<String> chunkIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(getChunksSql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) chunkIds.add(rs.getString("chunk_id"));
            }
        }
        for (String chunkId : chunkIds) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chunk_nodes WHERE chunk_id = ?")) {
                ps.setString(1, chunkId);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chunks WHERE file_id = ?")) {
            ps.setString(1, fileId);
            ps.executeUpdate();
        }
    }

    // ─── Node operations ───────────────────────────────────────────────────────

    @Override
    public void saveNode(NodeInfo node) throws Exception {
        String sql = "INSERT OR REPLACE INTO nodes (node_id, host, port, status, storage_used, storage_total, last_heartbeat, registered_at, display_name) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, node.getNodeId());
            ps.setString(2, node.getHost());
            ps.setInt(3,    node.getPort());
            ps.setString(4, node.getStatus().name());
            ps.setLong(5,   node.getStorageUsed());
            ps.setLong(6,   node.getStorageTotal());
            ps.setLong(7,   node.getLastHeartbeat());
            ps.setLong(8,   node.getRegisteredAt());
            ps.setString(9, node.getDisplayName());
            ps.executeUpdate();
        }
    }

    @Override
    public NodeInfo getNode(String nodeId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM nodes WHERE node_id = ?")) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapNode(rs) : null;
            }
        }
    }

    @Override
    public List<NodeInfo> getAllNodes() throws Exception {
        List<NodeInfo> nodes = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM nodes ORDER BY registered_at")) {
            while (rs.next()) nodes.add(mapNode(rs));
        }
        return nodes;
    }

    @Override
    public void updateNodeStatus(String nodeId, NodeInfo.NodeStatus status) throws Exception {
        String sql = "UPDATE nodes SET status = ? WHERE node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, nodeId);
            ps.executeUpdate();
        }
    }

    @Override
    public void updateNodeStorage(String nodeId, long used, long total) throws Exception {
        String sql = "UPDATE nodes SET storage_used = ?, storage_total = ?, last_heartbeat = ? WHERE node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1,   used);
            ps.setLong(2,   total);
            ps.setLong(3,   System.currentTimeMillis());
            ps.setString(4, nodeId);
            ps.executeUpdate();
        }
    }

    // ─── User operations ───────────────────────────────────────────────────────

    @Override
    public void saveUser(User user) throws Exception {
        String sql = "INSERT OR REPLACE INTO users (user_id, username, password_hash, role, session_token, created_at, last_login) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getSessionToken());
            ps.setLong(6,   user.getCreatedAt());
            ps.setLong(7,   user.getLastLogin());
            ps.executeUpdate();
        }
    }

    @Override
    public User getUserById(String userId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE user_id = ?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    @Override
    public User getUserByUsername(String username) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    @Override
    public void updateSessionToken(String userId, String token, long lastLogin) throws Exception {
        String sql = "UPDATE users SET session_token = ?, last_login = ? WHERE user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setLong(2,   lastLogin);
            ps.setString(3, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public List<User> getAllUsers() throws Exception {
        List<User> users = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM users ORDER BY created_at")) {
            while (rs.next()) users.add(mapUser(rs));
        }
        return users;
    }

    @Override
    public long getTotalUsersCount() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    // ─── Activity log ──────────────────────────────────────────────────────────

    @Override
    public void logActivity(ActivityEvent event) throws Exception {
        String sql = "INSERT INTO activity_log (event_type, user_id, file_id, node_id, description, timestamp) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.getEventType().name());
            ps.setString(2, event.getUserId());
            ps.setString(3, event.getFileId());
            ps.setString(4, event.getNodeId());
            ps.setString(5, event.getDescription());
            ps.setLong(6,   event.getTimestamp());
            ps.executeUpdate();
        }
    }

    @Override
    public List<ActivityEvent> getRecentActivity(int limit) throws Exception {
        String sql = "SELECT al.*, u.username FROM activity_log al LEFT JOIN users u ON al.user_id = u.user_id ORDER BY al.timestamp DESC LIMIT ?";
        List<ActivityEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapActivity(rs));
            }
        }
        return events;
    }

    @Override
    public List<ActivityEvent> getActivityForUser(String userId, int limit) throws Exception {
        String sql = "SELECT al.*, u.username FROM activity_log al LEFT JOIN users u ON al.user_id = u.user_id WHERE al.user_id = ? ORDER BY al.timestamp DESC LIMIT ?";
        List<ActivityEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setInt(2,    limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapActivity(rs));
            }
        }
        return events;
    }

    @Override
    public List<ActivityEvent> getActivityForFile(String fileId) throws Exception {
        String sql = "SELECT al.*, u.username FROM activity_log al LEFT JOIN users u ON al.user_id = u.user_id WHERE al.file_id = ? ORDER BY al.timestamp DESC";
        List<ActivityEvent> events = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapActivity(rs));
            }
        }
        return events;
    }

    // ─── ResultSet mappers ─────────────────────────────────────────────────────

    private FileMetadata mapFile(ResultSet rs) throws SQLException {
        FileMetadata f = new FileMetadata();
        f.setFileId(rs.getString("file_id"));
        f.setFileName(rs.getString("file_name"));
        f.setOwnerId(rs.getString("owner_id"));
        f.setSizeBytes(rs.getLong("size_bytes"));
        f.setChecksum(rs.getString("checksum"));
        f.setReplicationFactor(rs.getInt("replication_factor"));
        f.setStatus(FileMetadata.FileStatus.valueOf(rs.getString("status")));
        f.setCreatedAt(rs.getLong("created_at"));
        f.setUpdatedAt(rs.getLong("updated_at"));
        f.setDownloadCount(rs.getInt("download_count"));
        f.setEncrypted(rs.getInt("is_encrypted") == 1);
        f.setCompressed(rs.getInt("is_compressed") == 1);
        f.setChunkCount(rs.getInt("chunk_count"));
        return f;
    }

    private ChunkInfo mapChunk(ResultSet rs) throws SQLException {
        ChunkInfo c = new ChunkInfo();
        c.setChunkId(rs.getString("chunk_id"));
        c.setFileId(rs.getString("file_id"));
        c.setChunkIndex(rs.getInt("chunk_index"));
        c.setSizeBytes(rs.getLong("size_bytes"));
        c.setChecksum(rs.getString("checksum"));
        c.setStatus(ChunkInfo.ChunkStatus.valueOf(rs.getString("status")));
        return c;
    }

    private NodeInfo mapNode(ResultSet rs) throws SQLException {
        NodeInfo n = new NodeInfo();
        n.setNodeId(rs.getString("node_id"));
        n.setHost(rs.getString("host"));
        n.setPort(rs.getInt("port"));
        n.setStatus(NodeInfo.NodeStatus.valueOf(rs.getString("status")));
        n.setStorageUsed(rs.getLong("storage_used"));
        n.setStorageTotal(rs.getLong("storage_total"));
        n.setLastHeartbeat(rs.getLong("last_heartbeat"));
        n.setRegisteredAt(rs.getLong("registered_at"));
        n.setDisplayName(rs.getString("display_name"));
        return n;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getString("user_id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(User.Role.valueOf(rs.getString("role")));
        u.setSessionToken(rs.getString("session_token"));
        u.setCreatedAt(rs.getLong("created_at"));
        u.setLastLogin(rs.getLong("last_login"));
        return u;
    }

    private ActivityEvent mapActivity(ResultSet rs) throws SQLException {
        ActivityEvent e = new ActivityEvent();
        e.setLogId(rs.getLong("log_id"));
        e.setEventType(ActivityEvent.EventType.valueOf(rs.getString("event_type")));
        e.setUserId(rs.getString("user_id"));
        e.setFileId(rs.getString("file_id"));
        e.setNodeId(rs.getString("node_id"));
        e.setDescription(rs.getString("description"));
        e.setTimestamp(rs.getLong("timestamp"));
        try { e.setUsername(rs.getString("username")); } catch (SQLException ignored) {}
        return e;
    }
}
