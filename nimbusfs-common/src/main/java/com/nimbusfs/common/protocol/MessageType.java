package com.nimbusfs.common.protocol;

/**
 * Defines all message type codes used in the NimbusFS TCP protocol.
 * Each type maps to a unique byte value sent in the packet header.
 */
public enum MessageType {
    // ─── Authentication ───────────────────────────────────────
    LOGIN_REQUEST(0x01),
    LOGIN_RESPONSE(0x02),
    REGISTER_REQUEST(0x03),
    REGISTER_RESPONSE(0x04),
    LOGOUT_REQUEST(0x05),

    // ─── File Operations (Client ↔ Master) ────────────────────
    UPLOAD_REQUEST(0x10),
    UPLOAD_PLAN(0x11),
    UPLOAD_COMPLETE(0x12),
    UPLOAD_SUCCESS(0x13),
    DOWNLOAD_REQUEST(0x14),
    DOWNLOAD_PLAN(0x15),
    DOWNLOAD_COMPLETE(0x16),
    DELETE_REQUEST(0x17),
    DELETE_RESPONSE(0x18),
    RENAME_REQUEST(0x19),
    RENAME_RESPONSE(0x1A),
    LIST_FILES_REQUEST(0x1B),
    LIST_FILES_RESPONSE(0x1C),
    FILE_DETAILS_REQUEST(0x1D),
    FILE_DETAILS_RESPONSE(0x1E),
    CHUNK_CONFIRMED(0x1F),

    // ─── Chunk Operations (Client ↔ Node, Node ↔ Node) ────────
    STORE_CHUNK(0x20),
    STORE_CHUNK_ACK(0x21),
    RETRIEVE_CHUNK(0x22),
    CHUNK_DATA(0x23),
    DELETE_CHUNK(0x24),
    DELETE_CHUNK_ACK(0x25),
    REPLICATE_TO(0x26),
    REPLICATION_DONE(0x27),

    // ─── Node Management (Node ↔ Master) ──────────────────────
    NODE_REGISTER(0x30),
    NODE_REGISTER_ACK(0x31),
    HEARTBEAT(0x32),
    HEARTBEAT_ACK(0x33),
    NODE_STATUS_REQUEST(0x34),
    NODE_STATUS_RESPONSE(0x35),

    // ─── Admin / Analytics ────────────────────────────────────
    ADMIN_STATS_REQUEST(0x40),
    ADMIN_STATS_RESPONSE(0x41),
    ANALYTICS_REQUEST(0x42),
    ANALYTICS_RESPONSE(0x43),
    ACTIVITY_LOG_REQUEST(0x44),
    ACTIVITY_LOG_RESPONSE(0x45),
    USER_LIST_REQUEST(0x46),
    USER_LIST_RESPONSE(0x47),

    // ─── System ───────────────────────────────────────────────
    ACK(0xF0),
    NACK(0xF1),
    ERROR(0xF2),
    PING(0xF3),
    PONG(0xF4);

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public byte toByte() {
        return (byte) code;
    }

    /**
     * Look up a MessageType by its wire-format byte code.
     * @throws IllegalArgumentException if the code is unknown
     */
    public static MessageType fromByte(byte b) {
        int value = b & 0xFF;
        for (MessageType type : values()) {
            if (type.code == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageType code: 0x" + Integer.toHexString(value));
    }
}
