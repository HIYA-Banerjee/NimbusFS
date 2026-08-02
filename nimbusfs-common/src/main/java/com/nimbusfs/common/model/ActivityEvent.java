package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** An entry in the NimbusFS activity log. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActivityEvent {

    public enum EventType {
        UPLOAD_STARTED, UPLOAD_COMPLETE, UPLOAD_FAILED,
        DOWNLOAD_STARTED, DOWNLOAD_COMPLETE, DOWNLOAD_FAILED,
        FILE_DELETED, FILE_RENAMED,
        REPLICATION_STARTED, REPLICA_CREATED, REPLICATION_FAILED,
        NODE_REGISTERED, NODE_ONLINE, NODE_OFFLINE,
        USER_LOGIN, USER_LOGOUT, USER_REGISTERED,
        RECOVERY_STARTED, RECOVERY_COMPLETE
    }

    private long      logId;
    private EventType eventType;
    private String    userId;
    private String    username;
    private String    fileId;
    private String    fileName;
    private String    nodeId;
    private String    nodeName;
    private String    description;
    private long      timestamp;

    public ActivityEvent() {}

    public ActivityEvent(EventType eventType, String description, long timestamp) {
        this.eventType   = eventType;
        this.description = description;
        this.timestamp   = timestamp;
    }

    /** Returns a short, human-readable label for the event type. */
    public String getEventLabel() {
        return switch (eventType) {
            case UPLOAD_STARTED       -> "Upload Started";
            case UPLOAD_COMPLETE      -> "Upload Complete";
            case UPLOAD_FAILED        -> "Upload Failed";
            case DOWNLOAD_STARTED     -> "Download Started";
            case DOWNLOAD_COMPLETE    -> "Download Complete";
            case DOWNLOAD_FAILED      -> "Download Failed";
            case FILE_DELETED         -> "File Deleted";
            case FILE_RENAMED         -> "File Renamed";
            case REPLICATION_STARTED  -> "Replication Started";
            case REPLICA_CREATED      -> "Replica Created";
            case REPLICATION_FAILED   -> "Replication Failed";
            case NODE_REGISTERED      -> "Node Registered";
            case NODE_ONLINE          -> "Node Online";
            case NODE_OFFLINE         -> "Node Offline";
            case USER_LOGIN           -> "User Login";
            case USER_LOGOUT          -> "User Logout";
            case USER_REGISTERED      -> "User Registered";
            case RECOVERY_STARTED     -> "Recovery Started";
            case RECOVERY_COMPLETE    -> "Recovery Complete";
        };
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public long getLogId()                          { return logId; }
    public void setLogId(long logId)                { this.logId = logId; }

    public EventType getEventType()                 { return eventType; }
    public void setEventType(EventType eventType)   { this.eventType = eventType; }

    public String getUserId()                       { return userId; }
    public void setUserId(String userId)            { this.userId = userId; }

    public String getUsername()                     { return username; }
    public void setUsername(String username)        { this.username = username; }

    public String getFileId()                       { return fileId; }
    public void setFileId(String fileId)            { this.fileId = fileId; }

    public String getFileName()                     { return fileName; }
    public void setFileName(String fileName)        { this.fileName = fileName; }

    public String getNodeId()                       { return nodeId; }
    public void setNodeId(String nodeId)            { this.nodeId = nodeId; }

    public String getNodeName()                     { return nodeName; }
    public void setNodeName(String nodeName)        { this.nodeName = nodeName; }

    public String getDescription()                  { return description; }
    public void setDescription(String description)  { this.description = description; }

    public long getTimestamp()                      { return timestamp; }
    public void setTimestamp(long timestamp)        { this.timestamp = timestamp; }
}
