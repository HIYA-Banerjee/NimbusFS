package com.nimbusfs.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 checksum utilities for file and byte-array integrity verification.
 *
 * Checksums are used to:
 *  - Verify chunk integrity after network transfer
 *  - Verify whole-file integrity after reassembly
 *  - Detect silent data corruption on storage nodes
 */
public final class ChecksumUtil {

    private static final String ALGORITHM  = "SHA-256";
    private static final int    BUFFER_SIZE = 65536; // 64 KB read buffer

    private ChecksumUtil() {}

    /**
     * Computes the SHA-256 checksum of the given byte array.
     *
     * @return hex-encoded checksum string (64 lowercase hex chars)
     */
    public static String checksum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            md.update(data);
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Computes the SHA-256 checksum of a file by streaming it in 64 KB blocks.
     * Does NOT load the entire file into memory.
     *
     * @param file the file to hash
     * @return hex-encoded checksum string
     * @throws IOException if the file cannot be read
     */
    public static String checksum(File file) throws IOException {
        try {
            MessageDigest md  = MessageDigest.getInstance(ALGORITHM);
            byte[]        buf = new byte[BUFFER_SIZE];
            try (DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md)) {
                while (dis.read(buf) != -1) { /* consuming stream updates the digest */ }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies that the checksum of {@code data} matches the expected value.
     *
     * @param data     raw bytes to verify
     * @param expected expected hex checksum string
     * @return true if the checksums match
     */
    public static boolean verify(byte[] data, String expected) {
        return expected != null && expected.equalsIgnoreCase(checksum(data));
    }

    /**
     * Converts a raw byte array to a lowercase hex string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
