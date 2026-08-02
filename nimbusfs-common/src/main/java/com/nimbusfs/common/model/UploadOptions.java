package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Options chosen by the user before uploading a file. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadOptions {

    private int     replicationFactor = 3;
    private boolean encrypt           = true;
    private boolean compress          = true;
    private int     chunkSizeMB       = 16;       // chunk size in MB

    public UploadOptions() {}

    public UploadOptions(int replicationFactor, boolean encrypt, boolean compress) {
        this.replicationFactor = replicationFactor;
        this.encrypt           = encrypt;
        this.compress          = compress;
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public int getReplicationFactor()                       { return replicationFactor; }
    public void setReplicationFactor(int replicationFactor) { this.replicationFactor = replicationFactor; }

    public boolean isEncrypt()                              { return encrypt; }
    public void setEncrypt(boolean encrypt)                 { this.encrypt = encrypt; }

    public boolean isCompress()                             { return compress; }
    public void setCompress(boolean compress)               { this.compress = compress; }

    public int getChunkSizeMB()                             { return chunkSizeMB; }
    public void setChunkSizeMB(int chunkSizeMB)             { this.chunkSizeMB = chunkSizeMB; }

    /** Chunk size in bytes (derived from chunkSizeMB). */
    public long getChunkSizeBytes()                         { return (long) chunkSizeMB * 1024 * 1024; }
}
