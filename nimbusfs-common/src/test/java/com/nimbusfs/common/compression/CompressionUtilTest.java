package com.nimbusfs.common.compression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionUtilTest {

    @Test
    public void testCompressDecompressRoundTrip() throws Exception {
        byte[] original = "NimbusFS ".repeat(100).getBytes();

        byte[] compressed = CompressionUtil.compress(original);
        assertTrue(compressed.length < original.length);

        byte[] decompressed = CompressionUtil.decompress(compressed);
        assertArrayEquals(original, decompressed);
    }
}
