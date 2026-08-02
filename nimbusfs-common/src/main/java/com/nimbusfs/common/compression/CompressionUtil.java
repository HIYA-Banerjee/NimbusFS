package com.nimbusfs.common.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZip compression/decompression utilities.
 *
 * Used to reduce chunk size before storage and transmission.
 * Compression is applied BEFORE encryption in the upload pipeline.
 */
public final class CompressionUtil {

    private static final int BUFFER_SIZE = 8192;

    private CompressionUtil() {}

    /**
     * Compresses the given byte array using GZip.
     *
     * @param data raw bytes to compress
     * @return GZip-compressed bytes
     * @throws IOException if compression fails
     */
    public static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * Decompresses GZip-compressed bytes.
     *
     * @param compressed GZip-compressed input
     * @return original uncompressed bytes
     * @throws IOException if decompression fails or data is not valid GZip
     */
    public static byte[] decompress(byte[] compressed) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int    len;
            while ((len = gzip.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        }
        return bos.toByteArray();
    }

    /**
     * Returns the compression ratio as a percentage reduction.
     * e.g. original=1000, compressed=600 → 40.0 (40% smaller)
     */
    public static double compressionRatio(int originalSize, int compressedSize) {
        if (originalSize == 0) return 0;
        return (1.0 - (double) compressedSize / originalSize) * 100.0;
    }
}
