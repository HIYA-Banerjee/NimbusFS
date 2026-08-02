# ⚡ NimbusFS — Java Distributed File System

NimbusFS is a production-quality, completely Java-based distributed file system featuring a **JavaFX Desktop Client**, **Master Server**, **Storage Nodes**, **AES-256 Encryption**, **GZip Compression**, **File Chunking & Replication**, **Heartbeat Failover**, and **Live JavaFX Analytics**.

---

## 🖥️ Technology Stack

- **Frontend**: JavaFX 21, JavaFX CSS, JavaFX Charts (Pie, Line, Bar, Area), JavaFX Canvas
- **Networking**: Custom TCP Binary & JSON Packet Protocol (`Packet.java`)
- **Backend & Storage**: Core Java 17+, Multithreading (`ExecutorService`, `ScheduledExecutorService`), SQLite JDBC (WAL mode), Jackson JSON
- **Security**: AES-256-GCM Encryption, GZip Compression, BCrypt Password Hashing
- **Build System**: Maven Multi-Module (`nimbusfs-common`, `nimbusfs-master`, `nimbusfs-node`, `nimbusfs-client`)

---

## 🚀 Quick Start

### 1. Launch Full Cluster (Master + 3 Storage Nodes)
Run `com.nimbusfs.master.Launcher` or:
```bash
# Launch Master Server
java -jar nimbusfs-master/target/nimbusfs-master-1.0.0.jar

# Launch Storage Nodes
java -jar nimbusfs-node/target/nimbusfs-node-1.0.0.jar
```

### 2. Launch JavaFX Desktop Client
```bash
java -jar nimbusfs-client/target/nimbusfs-client-1.0.0.jar
```

---

## 🔑 Default Credentials
- **Username**: `admin`
- **Password**: `nimbus123`

---

## 📐 System Architecture

```
┌─────────────────────────────────────────────────────┐
│                JavaFX Desktop Client                │
│  (Login · Dashboard · Upload · Analytics · Admin)  │
└─────────────────┬───────────────────────────────────┘
                  │ TCP (Custom Protocol)
┌─────────────────▼───────────────────────────────────┐
│                  Master Server                      │
│  (Metadata DB · Node Registry · Replication Mgr)   │
└──────┬─────────────┬──────────────┬─────────────────┘
       │             │              │
  ┌────▼────┐   ┌────▼────┐   ┌────▼────┐
  │ Node 1  │   │ Node 2  │   │ Node 3  │
  └─────────┘   └─────────┘   └─────────┘
```
