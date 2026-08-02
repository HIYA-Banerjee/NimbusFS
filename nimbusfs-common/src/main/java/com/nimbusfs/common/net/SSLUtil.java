package com.nimbusfs.common.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * SSL/TLS utility for NimbusFS transport-layer security.
 *
 * Strategy (in order):
 *  1. Look for an existing keystore at {@code ~/.nimbusfs/nimbus.jks}.
 *  2. If absent, generate a fresh 2048-bit RSA self-signed keystore using
 *     keytool (ships with every JDK).
 *  3. Fall back to a trust-all plaintext-equivalent SSLContext (insecure dev mode)
 *     if keytool is not available.
 *
 * The client side always uses a trust-all TrustManager so it accepts the
 * self-signed server certificate without a CA chain.
 */
public class SSLUtil {

    private static final Logger log = LogManager.getLogger(SSLUtil.class);

    private static final String PROTOCOL     = "TLSv1.3";
    private static final String KS_PASS      = "nimbus-tls-pass";
    private static final String KS_ALIAS     = "nimbusfs";
    private static final String KS_FILE_NAME = "nimbus.jks";

    private static SSLContext cachedContext;

    private SSLUtil() {}

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns a cached SSLContext backed by a self-signed keystore.
     * Thread-safe via double-checked locking.
     */
    public static synchronized SSLContext getSSLContext() {
        if (cachedContext == null) {
            cachedContext = buildSSLContext();
        }
        return cachedContext;
    }

    // ─── Construction ──────────────────────────────────────────────────────────

    private static SSLContext buildSSLContext() {
        try {
            KeyStore ks = loadOrCreateKeyStore();

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KS_PASS.toCharArray());

            SSLContext ctx = SSLContext.getInstance(PROTOCOL);
            ctx.init(kmf.getKeyManagers(), buildTrustAllManagers(), new SecureRandom());
            log.info("TLS SSLContext initialised (protocol: {})", PROTOCOL);
            return ctx;
        } catch (Exception e) {
            log.warn("Could not create keyed SSLContext ({}), falling back to trust-all dev mode.", e.getMessage());
            return buildTrustAllContext();
        }
    }

    /**
     * Loads or generates the NimbusFS keystore from {@code ~/.nimbusfs/nimbus.jks}.
     */
    private static KeyStore loadOrCreateKeyStore() throws Exception {
        Path ksDir  = Paths.get(System.getProperty("user.home"), ".nimbusfs");
        Path ksPath = ksDir.resolve(KS_FILE_NAME);
        Files.createDirectories(ksDir);

        if (Files.exists(ksPath)) {
            return loadKeyStore(ksPath);
        }

        log.info("No NimbusFS keystore found — generating self-signed certificate at {}", ksPath);
        generateKeyStore(ksPath);
        return loadKeyStore(ksPath);
    }

    /** Load a PKCS12-or-JKS keystore from disk. */
    private static KeyStore loadKeyStore(Path ksPath) throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = Files.newInputStream(ksPath)) {
            ks.load(is, KS_PASS.toCharArray());
        }
        return ks;
    }

    /**
     * Generates a self-signed keystore via keytool (bundled with every JDK/JRE).
     * keytool is located relative to java.home so it's always available.
     */
    private static void generateKeyStore(Path ksPath) throws Exception {
        String javaHome = System.getProperty("java.home");
        // In JDK: java.home/bin/keytool; in JRE layout: same
        String keytool = Paths.get(javaHome, "bin", "keytool").toString();

        List<String> cmd = List.of(
            keytool,
            "-genkeypair",
            "-alias",     KS_ALIAS,
            "-keyalg",    "RSA",
            "-keysize",   "2048",
            "-validity",  "3650",       // 10 years
            "-keystore",  ksPath.toString(),
            "-storepass", KS_PASS,
            "-keypass",   KS_PASS,
            "-dname",     "CN=NimbusFS, OU=NimbusFS, O=NimbusFS, L=Local, ST=Local, C=US",
            "-noprompt"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // Capture output for diagnostics
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        }

        int exit = proc.waitFor();
        if (exit != 0) {
            throw new RuntimeException("keytool exited with code " + exit + ": " + sb);
        }
        log.info("Self-signed keystore generated successfully at {}", ksPath);
    }

    // ─── Trust-all fallback (client & dev mode) ────────────────────────────────

    /** Returns an SSLContext with a trust-all TrustManager (no server certificate validation). */
    private static SSLContext buildTrustAllContext() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, buildTrustAllManagers(), new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new RuntimeException("Fatal: could not create fallback SSLContext", e);
        }
    }

    /** TrustManagers that accept any certificate (fine for self-signed intra-cluster traffic). */
    static TrustManager[] buildTrustAllManagers() {
        return new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };
    }
}
