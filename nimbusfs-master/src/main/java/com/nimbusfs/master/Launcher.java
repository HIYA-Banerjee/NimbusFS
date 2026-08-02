package com.nimbusfs.master;

import com.nimbusfs.node.StorageNode;

/**
 * Cluster Launcher — Bootstraps 1 Master Server and 3 Storage Nodes in separate threads
 * for instant single-click demo and testing.
 */
public class Launcher {

    public static void main(String[] args) {
        System.out.println("🚀 Starting NimbusFS Cluster (1 Master Server + 3 Storage Nodes)...");

        // 1. Start Master Server
        Thread masterThread = new Thread(() -> {
            try {
                MasterServer master = new MasterServer();
                master.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "master-cluster-thread");
        masterThread.start();

        // Wait 2 seconds for Master to initialize
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

        // 2. Start Node 1 (Port 9001)
        startNodeProcess(9001, "Node 1");

        // 3. Start Node 2 (Port 9002)
        startNodeProcess(9002, "Node 2");

        // 4. Start Node 3 (Port 9003)
        startNodeProcess(9003, "Node 3");

        System.out.println("✅ All cluster nodes launched successfully!");
    }

    private static void startNodeProcess(int port, String name) {
        Thread nodeThread = new Thread(() -> {
            try {
                System.setProperty("node.chunk.port", String.valueOf(port));
                System.setProperty("node.display.name", name);
                System.setProperty("node.storage.dir", System.getProperty("user.home") + "/.nimbusfs/" + name.toLowerCase().replace(" ", ""));
                StorageNode node = new StorageNode();
                node.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "node-" + port + "-thread");
        nodeThread.start();
    }
}
