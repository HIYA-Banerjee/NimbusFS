package com.nimbusfs.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ChecksumUtilTest {

    @Test
    public void testChecksumConsistency() {
        byte[] data = "NimbusFS Checksum Test Data".getBytes();
        String checksum1 = ChecksumUtil.checksum(data);
        String checksum2 = ChecksumUtil.checksum(data);

        assertEquals(64, checksum1.length());
        assertEquals(checksum1, checksum2);
        assertTrue(ChecksumUtil.verify(data, checksum1));
    }
}
