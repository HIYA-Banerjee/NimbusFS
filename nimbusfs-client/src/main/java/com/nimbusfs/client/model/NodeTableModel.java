package com.nimbusfs.client.model;

import com.nimbusfs.common.model.NodeInfo;
import javafx.beans.property.*;

/**
 * JavaFX Observable model for node table views in Admin and Monitor panels.
 */
public class NodeTableModel {

    private final StringProperty  nodeId;
    private final StringProperty  displayName;
    private final StringProperty  address;
    private final StringProperty  status;
    private final StringProperty  storageUsed;
    private final StringProperty  storageTotal;
    private final IntegerProperty usagePercent;
    private final NodeInfo        original;

    public NodeTableModel(NodeInfo node) {
        this.original     = node;
        this.nodeId       = new SimpleStringProperty(node.getNodeId());
        this.displayName  = new SimpleStringProperty(
            node.getDisplayName() != null ? node.getDisplayName() : node.getNodeId().substring(0, 8));
        this.address      = new SimpleStringProperty(node.getHost() + ":" + node.getPort());
        this.status       = new SimpleStringProperty(node.isOnline() ? "Online" : "Offline");
        this.storageUsed  = new SimpleStringProperty(node.getFormattedStorageUsed());
        this.storageTotal = new SimpleStringProperty(node.getFormattedStorageTotal());
        this.usagePercent = new SimpleIntegerProperty(node.getStorageUsagePercent());
    }

    public StringProperty  nodeIdProperty()       { return nodeId; }
    public StringProperty  displayNameProperty()  { return displayName; }
    public StringProperty  addressProperty()      { return address; }
    public StringProperty  statusProperty()       { return status; }
    public StringProperty  storageUsedProperty()  { return storageUsed; }
    public StringProperty  storageTotalProperty() { return storageTotal; }
    public IntegerProperty usagePercentProperty() { return usagePercent; }

    public String   getNodeId()      { return nodeId.get(); }
    public NodeInfo getOriginal()    { return original; }
}
