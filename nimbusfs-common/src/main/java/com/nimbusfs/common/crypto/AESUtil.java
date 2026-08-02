package com.nimbusfs.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM authenticated encryption utility.
 *
 * Usage:
 *   byte[] key = AESUtil.generateKey();
 *   byte[] encrypted = AESUtil.encrypt(key, plaintext);
 *   byte[] decrypted = AESUtil.decrypt(key, encrypted);
 *
 * Wire format of encrypted output:
 *   [12 bytes IV][encrypted bytes (plaintext.length + 16 bytes GCM tag)]
 */
public final class AESUtil {

    private static final String ALGORITHM    = "AES";
    private static final String CIPHER       = "AES/GCM/NoPadding";
    private static final int    KEY_BITS     = 256;
    private static final int    IV_LENGTH    = 12;   // 96 bits — NIST recommended for GCM
    private static final int    TAG_LENGTH   = 128;  // bits

    private static final SecureRandom RANDOM = new SecureRandom();

    private AESUtil() {}

    /**
     * Generates a random 256-bit AES key.
     * Store this securely — it is needed to decrypt the file.
     */
    public static byte[] generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
            kg.init(KEY_BITS, RANDOM);
            return kg.generateKey().getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param keyBytes 32-byte raw key material
     * @param plaintext data to encrypt
     * @return [12-byte IV || ciphertext+GCM-tag]
     */
    public static byte[] encrypt(byte[] keyBytes, byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(keyBytes, ALGORITHM),
                        new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Prepend IV to ciphertext
            byte[] output = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv,         0, output, 0,         IV_LENGTH);
            System.arraycopy(ciphertext, 0, output, IV_LENGTH, ciphertext.length);
            return output;
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Decrypts data produced by {@link #encrypt}.
     *
     * @param keyBytes 32-byte raw key material
     * @param encryptedData [12-byte IV || ciphertext+GCM-tag]
     * @return original plaintext
     * @throws RuntimeException if decryption fails (wrong key or tampered data)
     */
    public static byte[] decrypt(byte[] keyBytes, byte[] encryptedData) {
        try {
            byte[] iv         = new byte[IV_LENGTH];
            int    ctLength   = encryptedData.length - IV_LENGTH;
            byte[] ciphertext = new byte[ctLength];

            System.arraycopy(encryptedData, 0,         iv,         0, IV_LENGTH);
            System.arraycopy(encryptedData, IV_LENGTH, ciphertext, 0, ctLength);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE,
                        new SecretKeySpec(keyBytes, ALGORITHM),
                        new GCMParameterSpec(TAG_LENGTH, iv));

            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed — wrong key or corrupted data", e);
        }
    }

    /** Encodes a key to a Base64 string for storage in the SQLite metadata. */
    public static String keyToBase64(byte[] keyBytes) {
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /** Decodes a Base64 key string back to raw bytes. */
    public static byte[] keyFromBase64(String base64Key) {
        return Base64.getDecoder().decode(base64Key);
    }
}
