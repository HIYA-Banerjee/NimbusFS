package com.nimbusfs.master.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Creates and migrates the NimbusFS SQLite schema.
 * Called once on Master Server startup.
 * Safe to run on an existing database — uses CREATE TABLE IF NOT EXISTS.
 */
public class DatabaseMigration {

    private static final Logger log = LogManager.getLogger(DatabaseMigration.class);

    private DatabaseMigration() {}

    public static void migrate(Connection conn) throws Exception {
        log.info("Running database migration...");

        try (Statement st = conn.createStatement()) {
            // ─── Users table ──────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    user_id        TEXT PRIMARY KEY,
                    username       TEXT UNIQUE NOT NULL,
                    password_hash  TEXT NOT NULL,
                    role           TEXT NOT NULL DEFAULT 'USER',
                    session_token  TEXT,
                    created_at     INTEGER NOT NULL,
                    last_login     INTEGER DEFAULT 0
                )
            """);

            // ─── Files table ──────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS files (
                    file_id            TEXT PRIMARY KEY,
                    file_name          TEXT NOT NULL,
                    owner_id           TEXT NOT NULL,
                    size_bytes         INTEGER NOT NULL DEFAULT 0,
                    checksum           TEXT,
                    replication_factor INTEGER NOT NULL DEFAULT 3,
                    status             TEXT NOT NULL DEFAULT 'UPLOADING',
                    created_at         INTEGER NOT NULL,
                    updated_at         INTEGER NOT NULL,
                    download_count     INTEGER NOT NULL DEFAULT 0,
                    is_encrypted       INTEGER NOT NULL DEFAULT 0,
                    is_compressed      INTEGER NOT NULL DEFAULT 0,
                    chunk_count        INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (owner_id) REFERENCES users(user_id)
                )
            """);

            // ─── Chunks table ─────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS chunks (
                    chunk_id     TEXT PRIMARY KEY,
                    file_id      TEXT NOT NULL,
                    chunk_index  INTEGER NOT NULL,
                    size_bytes   INTEGER NOT NULL DEFAULT 0,
                    checksum     TEXT,
                    status       TEXT NOT NULL DEFAULT 'PENDING',
                    FOREIGN KEY (file_id) REFERENCES files(file_id)
                )
            """);

            // ─── Nodes table ──────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS nodes (
                    node_id        TEXT PRIMARY KEY,
                    host           TEXT NOT NULL,
                    port           INTEGER NOT NULL,
                    status         TEXT NOT NULL DEFAULT 'ONLINE',
                    storage_used   INTEGER NOT NULL DEFAULT 0,
                    storage_total  INTEGER NOT NULL DEFAULT 0,
                    last_heartbeat INTEGER NOT NULL DEFAULT 0,
                    registered_at  INTEGER NOT NULL DEFAULT 0,
                    display_name   TEXT
                )
            """);

            // ─── Chunk-Node junction table ────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS chunk_nodes (
                    chunk_id   TEXT NOT NULL,
                    node_id    TEXT NOT NULL,
                    stored_at  INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (chunk_id, node_id),
                    FOREIGN KEY (chunk_id) REFERENCES chunks(chunk_id),
                    FOREIGN KEY (node_id)  REFERENCES nodes(node_id)
                )
            """);

            // ─── Activity log ─────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS activity_log (
                    log_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_type  TEXT NOT NULL,
                    user_id     TEXT,
                    file_id     TEXT,
                    node_id     TEXT,
                    description TEXT,
                    timestamp   INTEGER NOT NULL
                )
            """);

            // ─── Indices for common query patterns ────────────────────────────
            st.execute("CREATE INDEX IF NOT EXISTS idx_files_owner    ON files(owner_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chunks_file    ON chunks(file_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_chunk_nodes_node ON chunk_nodes(node_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_activity_time  ON activity_log(timestamp DESC)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_activity_user  ON activity_log(user_id)");
        }

        log.info("Database migration complete.");
    }
}
