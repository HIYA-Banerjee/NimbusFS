package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * The upload plan returned by the Master Server to the client.
 * Tells the client exactly which nodes to send each chunk to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadPlan {

    private String                fileId;
    private List<ChunkAssignment> assignments;

    public UploadPlan() {}

    public UploadPlan(String fileId, List<ChunkAssignment> assignments) {
        this.fileId      = fileId;
        this.assignments = assignments;
    }

    // ─── Inner class: chunk → node mapping ────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChunkAssignment {
        private String         chunkId;
        private int            chunkIndex;
        private List<NodeInfo> targetNodes;   // nodes to store this chunk on

        public ChunkAssignment() {}

        public ChunkAssignment(String chunkId, int chunkIndex, List<NodeInfo> targetNodes) {
            this.chunkId     = chunkId;
            this.chunkIndex  = chunkIndex;
            this.targetNodes = targetNodes;
        }

        public String getChunkId()                          { return chunkId; }
        public void setChunkId(String chunkId)              { this.chunkId = chunkId; }

        public int getChunkIndex()                          { return chunkIndex; }
        public void setChunkIndex(int chunkIndex)           { this.chunkIndex = chunkIndex; }

        public List<NodeInfo> getTargetNodes()              { return targetNodes; }
        public void setTargetNodes(List<NodeInfo> nodes)    { this.targetNodes = nodes; }
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getFileId()                               { return fileId; }
    public void setFileId(String fileId)                    { this.fileId = fileId; }

    public List<ChunkAssignment> getAssignments()           { return assignments; }
    public void setAssignments(List<ChunkAssignment> a)     { this.assignments = a; }
}
