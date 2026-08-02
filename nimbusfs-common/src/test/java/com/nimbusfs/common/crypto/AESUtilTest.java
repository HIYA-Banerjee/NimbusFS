package com.nimbusfs.common.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AESUtilTest {

    @Test
    public void testEncryptDecryptRoundTrip() {
        byte[] key = AESUtil.generateKey();
        byte[] plaintext = "Hello NimbusFS Distributed File System!".getBytes();

        byte[] encrypted = AESUtil.encrypt(key, plaintext);
        assertNotNull(encrypted);
        assertNotEquals(plaintext.length, encrypted.length);

        byte[] decrypted = AESUtil.decrypt(key, encrypted);
        assertArrayEquals(plaintext, decrypted);
    }
}
