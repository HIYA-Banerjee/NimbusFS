<div align="center">

# ⚡ NimbusFS — Distributed File System

### *Production-Grade, Self-Healing, High-Throughput Distributed Storage Engine Built in Core Java*

[![Build Status](https://github.com/HIYA-Banerjee/NimbusFS/workflows/NimbusFS%20CI/badge.svg)](https://github.com/HIYA-Banerjee/NimbusFS/actions)
[![Java Version](https://img.shields.io/badge/Java-17%20%7C%2021-orange.svg?style=flat-square&logo=openjdk)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/GUI-JavaFX%2021-blue.svg?style=flat-square&logo=java)](https://openjfx.io/)
[![Security](https://img.shields.io/badge/Security-AES--256--GCM%20%7C%20TLS%201.3-brightgreen.svg?style=flat-square&logo=keybase)](#-security--cryptography-architecture)
[![Architecture](https://img.shields.io/badge/Architecture-Master--Worker-purple.svg?style=flat-square)](#-system-architecture--sequence-flows)
[![License](https://img.shields.io/badge/License-MIT-green.svg?style=flat-square)](LICENSE)

[Architecture](#-system-architecture--sequence-flows) • [Engineering Highlights](#-engineering-deep-dive--faang-design-patterns) • [Features](#-core-features) • [Tech Stack](#-technology-stack) • [Quick Start](#-quick-start-guide) • [Vision & Scale Roadmap](#-vision--future-scale-roadmap)

---
</div>

## 📌 Executive Summary

**NimbusFS** is a highly scalable, fault-tolerant distributed object & file storage engine designed from first principles. Drawing architectural inspiration from **Google File System (GFS)** and **HDFS**, NimbusFS decouples control metadata operations from high-throughput payload streaming to eliminate central bottlenecks. 

It provides an end-to-end distributed system implementation featuring **custom binary TCP protocol framing**, **2-level directory-sharded storage nodes**, **automated self-healing chunk re-replication upon node failure**, client-side **AES-256-GCM authenticated payload encryption**, **TLS 1.3 transport security**, and a real-time **JavaFX Desktop Management Console**.

---

## 🏛 System Architecture & Sequence Flows

NimbusFS operates on a **decoupled Control-Plane vs Data-Plane design**:
- **Control Plane**: Master Server manages file namespace, block locations, user auth, and cluster heartbeats.
- **Data Plane**: Storage Nodes stream 16 MB chunks directly to/from clients with zero Master payload proxies.

### 🗺 High-Level System Topology

```
                               ┌───────────────────────────────────┐
                               │   NimbusFS Desktop Client (GUI)   │
                               │   (JavaFX 21 + Non-Blocking TCP)  │
                               └─────────────────┬─────────────────┘
                                                 │
                                 Control Plane   │  TLS 1.3 / Custom TCP
                                 (Metadata/Auth) │  Framed Binary Protocol
                                                 ▼
                               ┌───────────────────────────────────┐
                               │       Master Metadata Server      │
                               │ ┌───────────────────────────────┐ │
                               │ │  SQLite Metadata Store (WAL)  │ │
                               │ └───────────────────────────────┘ │
                               └─────────────────┬─────────────────┘
                                                 │
                                       Heartbeat │ Node Placement & Re-Replication
                                         (5s)    │ Engine
                                                 ▼
         ┌───────────────────────────────────────┼───────────────────────────────────────┐
         │                                       │                                       │
         ▼                                       ▼                                       ▼
┌──────────────────┐                    ┌──────────────────┐                    ┌──────────────────┐
│  Storage Node 1  │                    │  Storage Node 2  │                    │  Storage Node 3  │
│   (Port 9001)    │    Replication     │   (Port 9002)    │    Replication     │   (Port 9003)    │
│ ┌──────────────┐ │   (Direct Stream)  │ ┌──────────────┐ │   (Direct Stream)  │ ┌──────────────┐ │
│ │  ChunkStore  ├─┼───────────────────►│ │  ChunkStore  ├─┼───────────────────►│ │  ChunkStore  │ │
│ │ (256 Shards) │ │                    │ │ (256 Shards) │ │                    │ │ (256 Shards) │ │
│ └──────────────┘ │                    │ └──────────────┘ │                    │ └──────────────┘ │
└──────────────────┘                    └──────────────────┘                    └──────────────────┘
```

---

### 🔄 End-to-End Distributed Workflows

#### 1. Zero-Master Bottleneck File Upload Pipeline

```
Client App                   Master Server              Storage Node A           Storage Node B
    │                              │                           │                        │
    │── 1. UPLOAD_INIT_REQ ───────►│                           │                        │
    │   (filename, size, RF=2)     │ ── Partition 16MB Chunks  │                        │
    │                              │ ── Select Best Disk Nodes │                        │
    │◄── 2. UPLOAD_INIT_RESP ──────│                           │                        │
    │   (Chunk Assignments)        │                           │                        │
    │                              │                           │                        │
    │── 3. STORE_CHUNK (Chunk 0 Payload) ─────────────────────►│                        │
    │◄── 4. STORE_CHUNK_ACK ───────────────────────────────────│                        │
    │                                                          │                        │
    │── 5. STORE_CHUNK (Chunk 0 Payload) ──────────────────────────────────────────────►│
    │◄── 6. STORE_CHUNK_ACK ────────────────────────────────────────────────────────────│
    │                              │                           │                        │
    │── 7. CHUNK_CONFIRMED ───────►│ (Persist chunk_nodes)     │                        │
    │── 8. UPLOAD_COMPLETE ───────►│ (Set FileState=HEALTHY)   │                        │
    │◄── 9. ACK ───────────────────│                           │                        │
```

#### 2. Automated Self-Healing & Failure Recovery Protocol

```
Master Monitor               Storage Node 1 (DEAD)     Storage Node 2 (SURVIVOR)  Storage Node 3 (NEW)
      │                                │                           │                        │
      │── 1. Heartbeat Check ─────────X│                           │                        │
      │   (Timeout after 15s)          │                           │                        │
      │                                │                           │                        │
      │ ── Mark Node 1 as OFFLINE      │                           │                        │
      │ ── Identify Degraded Chunks    │                           │                        │
      │ ── Schedule Re-Replication     │                           │                        │
      │                                │                           │                        │
      │── 2. REPLICATE_CHUNK (Chunk X) ───────────────────────────►│                        │
      │                                                            │── 3. Stream Chunk X ──►│
      │                                                            │                        │
      │◄── 4. REPLICATION_COMPLETE ─────────────────────────────────────────────────────────│
      │                                │                           │                        │
      │ ── Update Metadata Registry    │                           │                        │
      │ ── Restore File to HEALTHY     │                           │                        │
```

---

## 🔬 Engineering Deep-Dive & FAANG Design Patterns

### 1. Decoupled Architecture with Direct Data Streaming
- **Design Choice**: The Master node handles *only* metadata queries and storage allocation plans. Actual payload bytes never touch the Master process.
- **Impact**: Master CPU/RAM usage stays bounded at $O(1)$ regardless of file size, enabling the cluster to scale linearly by simply attaching new Storage Nodes.

### 2. Custom Binary Framed TCP Protocol over TLS 1.3
- **Design Choice**: Custom binary wire format `[4B length][1B opcode][N-byte JSON payload]`. Built with `NimbusSocketFactory` capable of zero-downtime switching between raw TCP and TLS 1.3 transport security with self-signed certificate generation (`SSLUtil`).
- **Impact**: Avoids HTTP/1.1 header overhead, minimizing per-packet serialization latency.

### 3. O(1) Sharded Local Storage Engine
- **Design Choice**: Storage Nodes store chunk payloads using 2-level hash directory sharding (`/storage_dir/ab/cd/chunk_uuid.dat`).
- **Impact**: Prevents OS filesystem inode degradation that occurs when thousands of files inhabit a single directory.

### 4. Robust Heartbeat & Dynamic Re-Replication Engine
- **Design Choice**: Nodes transmit background heartbeats every 5 seconds. If a node misses heartbeats for 15 seconds, Master flags it as `OFFLINE`, computes under-replicated chunks, and issues async `REPLICATE_CHUNK` commands to healthy nodes.
- **Impact**: Guarantees High Availability ($N$-fold redundancy) and dynamic recovery without human intervention.

### 5. Client-Side Cryptographic Envelope & Integrity
- **Design Choice**: Files are encrypted on the client side using **AES-256-GCM** (Galois/Counter Mode) with random 12-byte IVs and compressed via **GZip** prior to transport. Both original and per-chunk **SHA-256 checksums** are validated upon download.
- **Impact**: Zero-trust data confidentiality — even compromised Storage Nodes cannot inspect or tamper with stored file contents.

---

## 🛠 Technology Stack

| Domain | Technology | Purpose |
|---|---|---|
| **Core Platform** | Java 17 / Java 21 | Modern Java features (Sealed types, Records, Pattern matching, Switch expressions) |
| **GUI & Visualization** | JavaFX 21, CSS3, Canvas API | Modern dark/light interface, real-time animated network topology graph |
| **Networking** | Java Socket, NIO, Custom Protocol | Non-blocking framed binary TCP socket client/server engine |
| **Security & Crypto** | AES-256-GCM, TLS 1.3, BCrypt, SHA-256 | End-to-end payload encryption, transport security, authenticated sessions |
| **Persistence** | SQLite 3 (WAL Mode), JDBC | High-concurrency metadata transactions with Write-Ahead Logging |
| **Multithreading** | `ExecutorService`, `CompletableFuture` | Thread pool concurrency for high throughput client/server worker threads |
| **Serialization** | Jackson Databind 2.16 | Fast JSON payload encoding/decoding |
| **DevOps & CI/CD** | GitHub Actions, Docker, Docker Compose | Automated build matrix (JDK 17+21), CodeQL security audit, container orchestration |

---

## ✨ Core Features

| Feature | Technical Implementation |
|---|---|
| **🖥 Desktop GUI Management Console** | 9 JavaFX views: File Explorer, Node Health Grid, Real-Time Animated Canvas Graph, 4 Analytics Charts, Audit Activity Log, Admin Panel, Settings. |
| **📂 Drag-and-Drop File Upload** | Direct drag-and-drop onto the JavaFX `TableView` with visual dashed drop-zone overlay, real-time upload progress updates, and automatic chunk distribution. |
| **🛡 Zero-Trust Encryption** | Client-side AES-256-GCM payload encryption + TLS 1.3 transport-layer socket encryption with `~/.nimbusfs/nimbus.jks` PKCS12 keystore. |
| **🔄 Self-Healing Cluster** | Background background daemon thread monitors node health every 5s; triggers automatic chunk replication on node drop. |
| **📊 Real-Time Analytics** | Interactive JavaFX charts tracking storage breakdown, replication health, bandwidth throughput, and live node stats. |
| **⚡ Fast Multi-Module Build** | Standalone shaded fat-JAR generation via `build.ps1` and Maven Shade plugin. |

---

## 🚀 Quick Start Guide

### Prerequisites
- **JDK 17** or **JDK 21** installed
- **Maven 3.8+** installed

### 1. Build Project
```bash
# Windows PowerShell
powershell -ExecutionPolicy Bypass -File .\build.ps1

# Linux / macOS
mvn clean package
```

### 2. Launch Local Cluster

#### Option A: One-Click Cluster Launcher (Recommended)
Spawns 1 Master Server and 3 Storage Nodes in a single terminal:
```bash
java -cp nimbusfs-master/target/nimbusfs-master-1.0.0-shaded.jar com.nimbusfs.master.Launcher
```

#### Option B: Docker Compose
```bash
docker compose up --build
```

#### Option C: Launch JavaFX Desktop Client
```bash
java -jar nimbusfs-client/target/nimbusfs-client-1.0.0-shaded.jar
```

**Default Admin Credentials:**
- **Username**: `admin`
- **Password**: `nimbus123`

---

## 🔮 Vision & Future Scale Roadmap

To evolve NimbusFS toward a multi-datacenter 100M+ RPS hyper-scale storage network:

```
[ NimbusFS Current ] ──► [ Phase 1: Raft Consensus ] ──► [ Phase 2: Erasure Coding ] ──► [ Phase 3: S3 Gateway ]
 (Single Master)         (Multi-Master Active/Active)     (8+4 Reed-Solomon Code)      (REST Object Storage API)
```

1. **Raft Consensus Protocol for Master High Availability**: Replace single SQLite Master with a multi-node Master cluster utilizing **Raft Consensus** for synchronized metadata state machine replication.
2. **Erasure Coding ($8+4$ Reed-Solomon)**: Replace traditional $N$-way chunk replication with Reed-Solomon Erasure Coding to reduce raw disk overhead from $300\%$ to $150\%$ while surviving up to 4 simultaneous node failures.
3. **AWS S3 Compatible REST Gateway**: Expose an HTTP/2 S3-compliant REST API layer (`GetObject`, `PutObject`, `MultipartUpload`) to allow standard AWS SDKs to use NimbusFS as a drop-in storage backend.
4. **Multi-Region Cross-Data-Center Asynchronous Replication**: Geographically distributed replication streams over TLS 1.3 with configurable eventual consistency models.

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for complete details.
