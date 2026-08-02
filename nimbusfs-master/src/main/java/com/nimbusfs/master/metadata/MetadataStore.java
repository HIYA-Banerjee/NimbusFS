package com.nimbusfs.master.metadata;

import com.nimbusfs.common.model.*;

import java.util.List;

/**
 * Interface for the NimbusFS metadata store.
 * Implementations: SQLiteMetadataStore (production), InMemoryMetadataStore (testing).
 */
public interface MetadataStore {

    void initialize() throws Exception;
    void close() throws Exception;

    // ─── File operations ───────────────────────────────────────────────────────

    void             saveFile(FileMetadata file)                    throws Exception;
    FileMetadata     getFile(String fileId)                         throws Exception;
    FileMetadata     getFileByName(String fileName, String ownerId) throws Exception;
    List<FileMetadata> listFiles(String ownerId)                    throws Exception;
    List<FileMetadata> listAllFiles()                               throws Exception;
    void             updateFileStatus(String fileId, FileMetadata.FileStatus status) throws Exception;
    void             updateFileName(String fileId, String newName)  throws Exception;
    void             deleteFile(String fileId)                      throws Exception;
    void             incrementDownloadCount(String fileId)          throws Exception;
    long             getTotalFilesCount()                           throws Exception;
    long             getTotalStorageBytes()                         throws Exception;

    // ─── Chunk operations ──────────────────────────────────────────────────────

    void         saveChunk(ChunkInfo chunk)                           throws Exception;
    ChunkInfo    getChunk(String chunkId)                             throws Exception;
    List<ChunkInfo> getChunksForFile(String fileId)                   throws Exception;
    List<String> getChunkNodes(String chunkId)                        throws Exception;
    void         addChunkToNode(String chunkId, String nodeId)        throws Exception;
    void         removeChunkFromNode(String chunkId, String nodeId)   throws Exception;
    List<String> getChunksOnNode(String nodeId)                       throws Exception;
    void         deleteChunksForFile(String fileId)                   throws Exception;

    // ─── Node operations ───────────────────────────────────────────────────────

    void         saveNode(NodeInfo node)                               throws Exception;
    NodeInfo     getNode(String nodeId)                                throws Exception;
    List<NodeInfo> getAllNodes()                                        throws Exception;
    void         updateNodeStatus(String nodeId, NodeInfo.NodeStatus status) throws Exception;
    void         updateNodeStorage(String nodeId, long used, long total)     throws Exception;

    // ─── User operations ───────────────────────────────────────────────────────

    void       saveUser(User user)                    throws Exception;
    User       getUserById(String userId)             throws Exception;
    User       getUserByUsername(String username)     throws Exception;
    void       updateSessionToken(String userId, String token, long lastLogin) throws Exception;
    List<User> getAllUsers()                          throws Exception;
    long       getTotalUsersCount()                  throws Exception;

    // ─── Activity log ──────────────────────────────────────────────────────────

    void               logActivity(ActivityEvent event)             throws Exception;
    List<ActivityEvent> getRecentActivity(int limit)                throws Exception;
    List<ActivityEvent> getActivityForUser(String userId, int limit) throws Exception;
    List<ActivityEvent> getActivityForFile(String fileId)           throws Exception;
}
