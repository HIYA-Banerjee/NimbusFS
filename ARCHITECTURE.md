# 🏗 NimbusFS Architecture & System Design Document

This document details the architectural design, protocol specifications, persistence schemas, and fault-tolerance mechanisms of **NimbusFS**.

---

## 🗺 System Topology Overview

NimbusFS follows a **Master/Worker (Controller/Storage Node)** distributed topology:

```
                           ┌───────────────────────────┐
                           │   NimbusFS JavaFX Client  │
                           │   (Desktop GUI App)       │
                           └─────────────┬─────────────┘
                                         │
                         Control Plane   │  TCP / TLS 1.3
                         (Metadata/Auth) │
                                         ▼
                           ┌───────────────────────────┐
                           │      Master Server        │
                           │ ┌───────────────────────┐ │
                           │ │  SQLite Metadata DB   │ │
                           │ └───────────────────────┘ │
                           └─────────────┬─────────────┘
                                         │
                               Heartbeat │ Monitoring / Replication
                                         ▼
         ┌───────────────────────────────┼───────────────────────────────┐
         │                               │                               │
         ▼                               ▼                               ▼
┌─────────────────┐             ┌─────────────────┐             ┌─────────────────┐
│ Storage Node 1  │             │ Storage Node 2  │             │ Storage Node 3  │
│ (Port 9001)     │             │ (Port 9002)     │             │ (Port 9003)     │
│ ┌─────────────┐ │  Replicate  │ ┌─────────────┐ │  Replicate  │ ┌─────────────┐ │
│ │ Chunk Store │ ├────────────►│ │ Chunk Store │ ├────────────►│ │ Chunk Store │ │
│ └─────────────┘ │             │ └─────────────┘ │             │ └─────────────┘ │
└─────────────────┘             └─────────────────┘             └─────────────────┘
```

---

## 📡 Custom Binary TCP Protocol Specification

All inter-node and client communications use a custom framed binary protocol over TCP (with optional TLS 1.3 transport security).

### Wire Packet Layout
```
+-------------------+-------------------+-----------------------------------+
| Field             | Size (Bytes)      | Description                       |
+-------------------+-------------------+-----------------------------------+
| Payload Length    | 4 (int, big-endian| Total size of JSON payload        |
| Message Type Code | 1 (byte)          | Opcode (0x01 = PING, 0x10 = LOGIN)|
| Payload           | N (UTF-8 bytes)   | JSON encoded payload object       |
+-------------------+-------------------+-----------------------------------+
```

### Core Message Opcodes (`MessageType.java`)

| Hex Code | Symbol | Plane | Description |
|---|---|---|---|
| `0x01` | `PING` | Health | Node heartbeat check |
| `0x02` | `PONG` | Health | Node heartbeat response |
| `0x10` | `LOGIN_REQUEST` | Auth | Client authentication |
| `0x11` | `LOGIN_RESPONSE` | Auth | Returns session token & user role |
| `0x20` | `UPLOAD_INIT_REQUEST` | Data | Requests chunk assignment plan for a file |
| `0x21` | `UPLOAD_INIT_RESPONSE` | Data | Returns assigned storage node endpoints per chunk |
| `0x22` | `STORE_CHUNK` | Data | Direct client-to-node chunk byte transfer |
| `0x23` | `STORE_CHUNK_ACK` | Data | Node confirmation of stored chunk |
| `0x24` | `CHUNK_CONFIRMED` | Data | Client reports successful chunk placement to Master |
| `0x25` | `UPLOAD_COMPLETE` | Data | Finalizes file state to `HEALTHY` in SQLite |
| `0x30` | `DOWNLOAD_INIT_REQUEST` | Data | Requests chunk location metadata for a file |
| `0x31` | `DOWNLOAD_INIT_RESPONSE` | Data | Returns node addresses for each chunk |
| `0x32` | `RETRIEVE_CHUNK` | Data | Client requests chunk payload from storage node |
| `0x33` | `CHUNK_DATA` | Data | Storage node returns chunk payload bytes |
| `0x40` | `REGISTER_NODE` | Cluster | Storage node announces itself to Master |
| `0x41` | `HEARTBEAT` | Cluster | Storage node periodic health & storage stats report |
| `0x50` | `REPLICATE_CHUNK` | Failover | Master instructs node A to copy chunk to node B |

---

## 🗄 Metadata Storage Schema (SQLite WAL Mode)

The Master Server persists all metadata in SQLite located at `~/.nimbusfs/metadata.db`.

### `files` Table
```sql
CREATE TABLE IF NOT EXISTS files (
    file_id            TEXT PRIMARY KEY,
    file_name          TEXT NOT NULL,
    owner_id           TEXT NOT NULL,
    size_bytes         INTEGER NOT NULL,
    checksum           TEXT NOT NULL,
    replication_factor INTEGER DEFAULT 3,
    status             TEXT NOT NULL, -- UPLOADING, HEALTHY, RECOVERING, DEGRADED, DELETING
    created_at         INTEGER NOT NULL,
    updated_at         INTEGER NOT NULL,
    download_count     INTEGER DEFAULT 0,
    is_encrypted       INTEGER DEFAULT 1,
    is_compressed      INTEGER DEFAULT 1,
    chunk_count        INTEGER NOT NULL
);
```

### `chunks` Table
```sql
CREATE TABLE IF NOT EXISTS chunks (
    chunk_id    TEXT PRIMARY KEY,
    file_id     TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    size_bytes  INTEGER NOT NULL,
    checksum    TEXT NOT NULL,
    status      TEXT NOT NULL, -- PENDING, STORED, MISSING, CORRUPTED
    FOREIGN KEY(file_id) REFERENCES files(file_id) ON DELETE CASCADE
);
```

### `chunk_nodes` Mapping Table
```sql
CREATE TABLE IF NOT EXISTS chunk_nodes (
    chunk_id  TEXT NOT NULL,
    node_id   TEXT NOT NULL,
    stored_at INTEGER NOT NULL,
    PRIMARY KEY(chunk_id, node_id),
    FOREIGN KEY(chunk_id) REFERENCES chunks(chunk_id) ON DELETE CASCADE
);
```

### `nodes` Table
```sql
CREATE TABLE IF NOT EXISTS nodes (
    node_id        TEXT PRIMARY KEY,
    host           TEXT NOT NULL,
    port           INTEGER NOT NULL,
    status         TEXT NOT NULL, -- ONLINE, OFFLINE, DEGRADED
    storage_used   INTEGER DEFAULT 0,
    storage_total  INTEGER DEFAULT 0,
    last_heartbeat INTEGER NOT NULL,
    registered_at  INTEGER NOT NULL,
    display_name   TEXT
);
```

---

## 🔄 End-to-End Sequence Workflows

### 1. File Upload Sequence (Client → Master → Storage Nodes)

```
Client                    Master Server             Node 1              Node 2
  │                             │                      │                   │
  │── 1. UPLOAD_INIT_REQUEST ──►│                      │                   │
  │   (fileName, size, RF=2)    │ ── Calculate Chunks  │                   │
  │                             │ ── Pick Best Nodes   │                   │
  │◄─ 2. UPLOAD_INIT_RESPONSE ──│                      │                   │
  │   (assignments per chunk)   │                      │                   │
  │                             │                      │                   │
  │── 3. STORE_CHUNK (Chunk 0) ───────────────────────►│                   │
  │◄─ 4. STORE_CHUNK_ACK ──────────────────────────────│                   │
  │                             │                      │                   │
  │── 5. STORE_CHUNK (Chunk 0) ───────────────────────────────────────────►│
  │◄─ 6. STORE_CHUNK_ACK ──────────────────────────────────────────────────│
  │                             │                      │                   │
  │── 7. CHUNK_CONFIRMED ──────►│ (Save chunk_nodes)   │                   │
  │── 8. UPLOAD_COMPLETE ──────►│ (Set status=HEALTHY) │                   │
  │◄─ 9. ACK ───────────────────│                      │                   │
```

---

### 2. Failure Detection & Automated Self-Healing Sequence

```
Master Server (HeartbeatMonitor)                 Node 1 (Crashed)          Node 2 (Healthy)          Node 3 (Healthy)
  │                                                     │                         │                         │
  │── Heartbeat Check ─────────────────────────────────X│                         │                         │
  │   (No response for 15s)                             │                         │                         │
  │                                                     │                         │                         │
  │ ── Mark Node 1 as OFFLINE                           │                         │                         │
  │ ── Query affected chunks                            │                         │                         │
  │ ── Find chunks with replicas < RF                   │                         │                         │
  │                                                     │                         │                         │
  │── REPLICATE_CHUNK (chunk_A → Node 3) ────────────────────────────────────────►│                         │
  │                                                     │                         │── Stream chunk_A ──────►│
  │                                                     │                         │                         │
  │◄─ REPLICATION_COMPLETE ─────────────────────────────────────────────────────────────────────────────────│
  │                                                     │                         │                         │
  │ ── Update chunk_nodes DB                            │                         │                         │
  │ ── File Status restored to HEALTHY                  │                         │                         │
```

---

## 🛡 Security Architecture

1. **At-Rest Protection**: Every file chunk is encrypted client-side using **AES-256-GCM** (Galois/Counter Mode) with random 12-byte IVs before leaving memory.
2. **In-Transit Protection**: Transport security via **TLS 1.3** (`SSLUtil` self-signed PKCS12/JKS certificate generation).
3. **Authentication**: User passwords hashed using **BCrypt** with salt factor 12. Session tokens are 128-bit cryptographically secure UUIDs.
4. **Integrity Validation**: Original file bytes and individual chunk bytes are verified via **SHA-256** checksums upon retrieval.
