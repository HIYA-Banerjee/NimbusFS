package com.nimbusfs.common.protocol;

import com.nimbusfs.common.util.JsonUtil;
import java.io.*;
import java.nio.ByteBuffer;

/**
 * Wire-format packet for the NimbusFS TCP protocol.
 *
 * Layout:
 *   [4 bytes: payload length (big-endian int)]
 *   [1 byte:  MessageType code]
 *   [N bytes: JSON payload (UTF-8)]
 *
 * This class is thread-safe for reading and writing to separate streams.
 */
public class Packet {

    /** Protocol version — included in every packet for forward compatibility. */
    public static final byte PROTOCOL_VERSION = 0x01;

    private final MessageType type;
    private final byte[]      payload;

    // ─── Construction ──────────────────────────────────────────────────────────

    public Packet(MessageType type, byte[] payload) {
        this.type    = type;
        this.payload = payload == null ? new byte[0] : payload;
    }

    /**
     * Convenience constructor: serializes any object to JSON payload.
     */
    public static Packet of(MessageType type, Object payloadObject) {
        try {
            byte[] json = JsonUtil.get().writeValueAsBytes(payloadObject);
            return new Packet(type, json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize packet payload for type " + type, e);
        }
    }

    /** Create a packet with an empty payload (e.g., ACK, PING). */
    public static Packet empty(MessageType type) {
        return new Packet(type, new byte[0]);
    }

    // ─── Serialization ─────────────────────────────────────────────────────────

    /**
     * Writes this packet to the given OutputStream.
     * Format: [4B length][1B type][payload]
     */
    public void writeTo(OutputStream out) throws IOException {
        // Header: 4 bytes length + 1 byte type
        ByteBuffer header = ByteBuffer.allocate(5);
        header.putInt(payload.length); // big-endian
        header.put(type.toByte());

        out.write(header.array());
        if (payload.length > 0) {
            out.write(payload);
        }
        out.flush();
    }

    /**
     * Reads a single packet from the given InputStream.
     * Blocks until the full packet has been received.
     */
    public static Packet readFrom(InputStream in) throws IOException {
        // Read 5-byte header
        byte[] header = readExact(in, 5);

        ByteBuffer buf    = ByteBuffer.wrap(header);
        int         length = buf.getInt();         // bytes 0-3
        byte        typeByte = buf.get();          // byte  4

        if (length < 0 || length > 256 * 1024 * 1024) { // 256 MB max
            throw new IOException("Invalid packet length: " + length);
        }

        MessageType type    = MessageType.fromByte(typeByte);
        byte[]      payload = length > 0 ? readExact(in, length) : new byte[0];

        return new Packet(type, payload);
    }

    /**
     * Reads exactly {@code n} bytes from the stream, blocking as needed.
     */
    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] buf    = new byte[n];
        int    offset = 0;
        while (offset < n) {
            int read = in.read(buf, offset, n - offset);
            if (read == -1) {
                throw new EOFException("Stream closed after reading " + offset + " of " + n + " bytes");
            }
            offset += read;
        }
        return buf;
    }

    // ─── Payload Deserialization ───────────────────────────────────────────────

    /**
     * Deserialize the JSON payload into the given class.
     */
    public <T> T getPayloadAs(Class<T> clazz) {
        try {
            return JsonUtil.get().readValue(payload, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize payload for type " + type, e);
        }
    }

    /** Return the raw payload bytes. */
    public byte[] getRawPayload() {
        return payload;
    }

    /** Return the payload as a UTF-8 string (for debugging). */
    public String getPayloadAsString() {
        return new String(payload, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Accessors ─────────────────────────────────────────────────────────────

    public MessageType getType() {
        return type;
    }

    public int getPayloadLength() {
        return payload.length;
    }

    @Override
    public String toString() {
        return "Packet{type=" + type + ", payloadLength=" + payload.length + "}";
    }
}
