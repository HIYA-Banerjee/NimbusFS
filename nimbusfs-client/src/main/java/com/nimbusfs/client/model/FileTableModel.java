package com.nimbusfs.client.model;

import com.nimbusfs.common.model.FileMetadata;
import javafx.beans.property.*;

/**
 * JavaFX Observable model for file table views.
 */
public class FileTableModel {

    private final StringProperty fileId;
    private final StringProperty fileName;
    private final StringProperty formattedSize;
    private final IntegerProperty replicas;
    private final StringProperty status;
    private final StringProperty owner;
    private final StringProperty checksum;
    private final StringProperty createdDate;
    private final FileMetadata originalMetadata;

    public FileTableModel(FileMetadata meta) {
        this.originalMetadata = meta;
        this.fileId = new SimpleStringProperty(meta.getFileId());
        this.fileName = new SimpleStringProperty(meta.getFileName());
        this.formattedSize = new SimpleStringProperty(meta.getFormattedSize());
        this.replicas = new SimpleIntegerProperty(meta.getReplicationFactor());
        this.status = new SimpleStringProperty(meta.getStatusLabel());
        this.owner = new SimpleStringProperty(meta.getOwnerName() != null ? meta.getOwnerName() : meta.getOwnerId());
        this.checksum = new SimpleStringProperty(meta.getShortChecksum());
        this.createdDate = new SimpleStringProperty(com.nimbusfs.client.util.FormatUtil.formatTimestamp(meta.getCreatedAt()));
    }

    public StringProperty fileIdProperty() { return fileId; }
    public StringProperty fileNameProperty() { return fileName; }
    public StringProperty formattedSizeProperty() { return formattedSize; }
    public IntegerProperty replicasProperty() { return replicas; }
    public StringProperty statusProperty() { return status; }
    public StringProperty ownerProperty() { return owner; }
    public StringProperty checksumProperty() { return checksum; }
    public StringProperty createdDateProperty() { return createdDate; }

    public String getFileId() { return fileId.get(); }
    public String getFileName() { return fileName.get(); }
    public FileMetadata getOriginalMetadata() { return originalMetadata; }
}
