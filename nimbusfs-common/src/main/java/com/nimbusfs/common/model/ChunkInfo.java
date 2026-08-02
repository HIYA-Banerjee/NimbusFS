package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Represents a single chunk of a file in NimbusFS.
 * A file is split into equal-sized chunks (except the last),
 * each stored on multiple storage nodes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkInfo {

    public enum ChunkStatus {
        PENDING, STORED, MISSING, CORRUPTED
    }

    private String      chunkId;
    private String      fileId;
    private int         chunkIndex;    // 0-based position within the file
    private long        sizeBytes;
    private String      checksum;      // SHA-256 of chunk bytes (after encrypt/compress)
    private List<String> nodeIds;       // node IDs that hold this chunk
    private ChunkStatus  status;

    // ─── Constructors ──────────────────────────────────────────────────────────

    public ChunkInfo() {}

    public ChunkInfo(String chunkId, String fileId, int chunkIndex, long sizeBytes, String checksum) {
        this.chunkId    = chunkId;
        this.fileId     = fileId;
        this.chunkIndex = chunkIndex;
        this.sizeBytes  = sizeBytes;
        this.checksum   = checksum;
        this.status     = ChunkStatus.PENDING;
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getChunkId()                    { return chunkId; }
    public void setChunkId(String chunkId)        { this.chunkId = chunkId; }

    public String getFileId()                     { return fileId; }
    public void setFileId(String fileId)          { this.fileId = fileId; }

    public int getChunkIndex()                    { return chunkIndex; }
    public void setChunkIndex(int chunkIndex)     { this.chunkIndex = chunkIndex; }

    public long getSizeBytes()                    { return sizeBytes; }
    public void setSizeBytes(long sizeBytes)      { this.sizeBytes = sizeBytes; }

    public String getChecksum()                   { return checksum; }
    public void setChecksum(String checksum)      { this.checksum = checksum; }

    public List<String> getNodeIds()              { return nodeIds; }
    public void setNodeIds(List<String> nodeIds)  { this.nodeIds = nodeIds; }

    public ChunkStatus getStatus()                { return status; }
    public void setStatus(ChunkStatus status)     { this.status = status; }
}
