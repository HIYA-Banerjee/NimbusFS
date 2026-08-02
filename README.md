# ⚡ NimbusFS — Distributed File System in Java

[![Build Status](https://github.com/HIYA-Banerjee/NimbusFS/workflows/NimbusFS%20CI/badge.svg)](https://github.com/HIYA-Banerjee/NimbusFS/actions)
[![Java Version](https://img.shields.io/badge/Java-17%20%7C%2021-orange.svg)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/UI-JavaFX%2021-blue.svg)](https://openjfx.io/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![Security](https://img.shields.io/badge/Security-AES--256--GCM%20%7C%20TLS%201.3-brightgreen.svg)](#-security--integrity)

> **NimbusFS** is a fault-tolerant, high-performance distributed file system built entirely in Java 17+. It features a Master-Worker architecture, automatic chunking, configurable replication, automated failure detection & self-healing re-replication, client-side **AES-256-GCM** encryption, **TLS 1.3** transport security, and a modern **JavaFX Desktop Management Console**.

---

## 🌟 Highlights & Key Features

- **🖥 Modern JavaFX Desktop Client**: Full-featured GUI with Dark/Light mode, drag-and-drop file upload, real-time node monitoring, system metrics, network topology canvas, and activity audit log.
- **🛡 End-to-End Security**: Client-side AES-256-GCM payload encryption, TLS 1.3 transport security (`SSLUtil` with zero-config self-signed keystore), and BCrypt authentication.
- **⚡ High Performance & Scalability**: Custom binary TCP protocol (`[4B length][1B type][JSON payload]`), 2-level directory sharded storage engine to avoid OS filesystem bottlenecks.
- **🔄 Fault Tolerance & Self-Healing**: Automated heartbeat tracking (5s interval, 15s timeout). If a storage node dies, the Master automatically re-replicates missing chunk copies across remaining healthy nodes.
- **📊 Real-Time Analytics & Topology**: Visual canvas displaying node topology with particle data flows, live storage utilization progress bars, and historical activity logs.
- **🐳 Single-Command Cluster Launch**: Launch 1 Master Server and 3 Storage Nodes locally via `Launcher.java` or `docker compose up`.

---

## 📐 Architecture Overview

```
                          ┌────────────────────────────┐
                          │   NimbusFS Desktop Client  │
                          │   (JavaFX 21 + Custom CSS) │
                          └──────────────┬─────────────┘
                                         │
                          Control Plane  │  TCP / TLS 1.3
                          (Metadata/Auth)│
                                         ▼
                          ┌────────────────────────────┐
                          │       Master Server        │
                          │ ┌────────────────────────┐ │
                          │ │  SQLite Metadata Store │ │
                          │ └────────────────────────┘ │
                          └──────────────┬─────────────┘
                                         │
                               Heartbeat │ Load Balancing / Replication
                                         ▼
        ┌────────────────────────────────┼────────────────────────────────┐
        │                                │                                │
        ▼                                ▼                                ▼
┌────────────────┐              ┌────────────────┐              ┌────────────────┐
│ Storage Node 1 │              │ Storage Node 2 │              │ Storage Node 3 │
│  (Port 9001)   │  Replicate   │  (Port 9002)   │  Replicate   │  (Port 9003)   │
│ ┌────────────┐ │ ────────────►│ ┌────────────┐ │ ────────────►│ ┌────────────┐ │
│ │ ChunkStore │ │              │ │ ChunkStore │ │              │ │ ChunkStore │ │
│ └────────────┘ │              │ └────────────┘ │              │ └────────────┘ │
└────────────────┘              └────────────────┘              └────────────────┘
```

Detailed design specification available in [ARCHITECTURE.md](ARCHITECTURE.md).

---

## 🚀 Quick Start Guide

### Prerequisites
- **JDK 17** or **JDK 21** installed
- **Maven 3.8+** installed (or run via provided build scripts)

### 1. Build the Entire System
```bash
# Windows
.\build.bat

# PowerShell / Linux / macOS
powershell -ExecutionPolicy Bypass -File .\build.ps1
```
This builds all 4 modules and creates executable shaded fat-JARs in each module's `target/` directory.

---

### 2. Launching the Local Cluster

#### Option A: One-Click Cluster Launcher (Recommended)
Run Master + 3 Storage Nodes simultaneously in a single terminal:
```bash
java -cp nimbusfs-master/target/nimbusfs-master-1.0.0-shaded.jar com.nimbusfs.master.Launcher
```

#### Option B: Docker Compose
```bash
docker compose up --build
```

#### Option C: Manual Launch (Separate Terminals)
```bash
# Terminal 1: Master Server
java -jar nimbusfs-master/target/nimbusfs-master-1.0.0-shaded.jar

# Terminal 2: Storage Node 1
java -Dnode.chunk.port=9001 -Dnode.display.name="Storage-Node-1" -jar nimbusfs-node/target/nimbusfs-node-1.0.0-shaded.jar

# Terminal 3: Storage Node 2
java -Dnode.chunk.port=9002 -Dnode.display.name="Storage-Node-2" -jar nimbusfs-node/target/nimbusfs-node-1.0.0-shaded.jar

# Terminal 4: Storage Node 3
java -Dnode.chunk.port=9003 -Dnode.display.name="Storage-Node-3" -jar nimbusfs-node/target/nimbusfs-node-1.0.0-shaded.jar
```

---

### 3. Launching the JavaFX Desktop Client
```bash
java -jar nimbusfs-client/target/nimbusfs-client-1.0.0-shaded.jar
```

**Default Admin Credentials:**
- **Username:** `admin`
- **Password:** `nimbus123`

---

## 📂 Repository Structure

```
NimbusFS/
├── nimbusfs-common/         # Shared Protocol, Models, Crypto (AES-256), TLS, Compression (GZip)
├── nimbusfs-master/         # Metadata Server, SQLite WAL Store, Load Balancer, Failure Recovery
├── nimbusfs-node/           # Storage Node, 2-Level Sharded Chunk Store, Disk Manager, Heartbeats
├── nimbusfs-client/         # JavaFX 21 Desktop UI Application (9 Views & Controllers)
├── .github/workflows/       # GitHub Actions CI/CD (JDK 17+21 matrix, Security scanning, CodeQL)
├── ARCHITECTURE.md          # Comprehensive System Design Document
├── docker-compose.yml       # Docker orchestration for 1 Master + 3 Nodes
└── build.ps1 / build.bat    # Portable build scripts
```

---

## 🧪 Testing & Verification

Run unit & integration test suites across all modules:
```bash
mvn clean test
```
- **`AESUtilTest`**: Verifies AES-256-GCM encryption/decryption roundtrip.
- **`CompressionUtilTest`**: Verifies GZip byte stream compression & decompression.
- **`ChecksumUtilTest`**: Verifies SHA-256 hash generation and validation.
- **`PacketTest`**: Verifies framed binary packet encoding/decoding over TCP streams.
- **`ChunkStoreTest`**: Verifies 2-level directory sharding storage operations.
- **`MasterServerIntegrationTest`**: Verifies Master startup, PING/PONG, user auth, and request guarding.

---

## 🔒 Security & Integrity

| Layer | Mechanism | Details |
|---|---|---|
| **Data at Rest** | **AES-256-GCM** | Client-side chunk payload encryption with random 12-byte IVs |
| **Data in Transit** | **TLS 1.3** | PKCS12/JKS keystore generation via `SSLUtil` |
| **Authentication** | **BCrypt** | Salted password hashing (cost factor 12) + session tokens |
| **Integrity** | **SHA-256** | Per-file and per-chunk checksum verification upon retrieval |

---

## 📜 License

Distributed under the MIT License. See `LICENSE` for details.
