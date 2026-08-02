package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Metadata for a file stored in NimbusFS.
 * Persisted in the SQLite 'files' table on the Master Server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMetadata {

    public enum FileStatus {
        UPLOADING, HEALTHY, RECOVERING, DEGRADED, DELETING
    }

    private String     fileId;
    private String     fileName;
    private String     ownerId;
    private String     ownerName;
    private long       sizeBytes;
    private String     checksum;       // SHA-256 of original (pre-encrypt/compress) bytes
    private int        replicationFactor;
    private FileStatus status;
    private long       createdAt;      // epoch millis
    private long       updatedAt;
    private int        downloadCount;
    private boolean    encrypted;
    private boolean    compressed;
    private int        chunkCount;
    private String[]   chunkIds;

    // ─── Constructors ──────────────────────────────────────────────────────────

    public FileMetadata() {}

    public FileMetadata(String fileId, String fileName, String ownerId, long sizeBytes) {
        this.fileId    = fileId;
        this.fileName  = fileName;
        this.ownerId   = ownerId;
        this.sizeBytes = sizeBytes;
        this.status    = FileStatus.UPLOADING;
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
    }

    // ─── Computed helpers ──────────────────────────────────────────────────────

    public String getFormattedSize() {
        if (sizeBytes >= 1_073_741_824L) return String.format("%.1f GB", sizeBytes / 1_073_741_824.0);
        if (sizeBytes >= 1_048_576L)     return String.format("%.1f MB", sizeBytes / 1_048_576.0);
        if (sizeBytes >= 1_024L)         return String.format("%.1f KB", sizeBytes / 1_024.0);
        return sizeBytes + " B";
    }

    public String getStatusLabel() {
        return status == null ? "Unknown" : switch (status) {
            case UPLOADING  -> "Uploading";
            case HEALTHY    -> "Healthy";
            case RECOVERING -> "Recovering";
            case DEGRADED   -> "Degraded";
            case DELETING   -> "Deleting";
        };
    }

    public String getShortChecksum() {
        return checksum != null && checksum.length() > 8 ? checksum.substring(0, 8) + "..." : checksum;
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getFileId()                       { return fileId; }
    public void setFileId(String fileId)            { this.fileId = fileId; }

    public String getFileName()                     { return fileName; }
    public void setFileName(String fileName)        { this.fileName = fileName; }

    public String getOwnerId()                      { return ownerId; }
    public void setOwnerId(String ownerId)          { this.ownerId = ownerId; }

    public String getOwnerName()                    { return ownerName; }
    public void setOwnerName(String ownerName)      { this.ownerName = ownerName; }

    public long getSizeBytes()                      { return sizeBytes; }
    public void setSizeBytes(long sizeBytes)        { this.sizeBytes = sizeBytes; }

    public String getChecksum()                     { return checksum; }
    public void setChecksum(String checksum)        { this.checksum = checksum; }

    public int getReplicationFactor()               { return replicationFactor; }
    public void setReplicationFactor(int rf)        { this.replicationFactor = rf; }

    public FileStatus getStatus()                   { return status; }
    public void setStatus(FileStatus status)        { this.status = status; }

    public long getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(long createdAt)        { this.createdAt = createdAt; }

    public long getUpdatedAt()                      { return updatedAt; }
    public void setUpdatedAt(long updatedAt)        { this.updatedAt = updatedAt; }

    public int getDownloadCount()                   { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public boolean isEncrypted()                    { return encrypted; }
    public void setEncrypted(boolean encrypted)     { this.encrypted = encrypted; }

    public boolean isCompressed()                   { return compressed; }
    public void setCompressed(boolean compressed)   { this.compressed = compressed; }

    public int getChunkCount()                      { return chunkCount; }
    public void setChunkCount(int chunkCount)       { this.chunkCount = chunkCount; }

    public String[] getChunkIds()                   { return chunkIds; }
    public void setChunkIds(String[] chunkIds)      { this.chunkIds = chunkIds; }
}
