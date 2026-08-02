package com.nimbusfs.common.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Socket factory providing transparent TLS 1.3 or plaintext sockets across all NimbusFS components.
 *
 * Usage:
 *   // Server side (Master, ChunkServer)
 *   ServerSocket ss = NimbusSocketFactory.createServerSocket(port, tlsEnabled);
 *
 *   // Client side (Storage Node → Master, JavaFX Client → Master / Nodes)
 *   Socket s = NimbusSocketFactory.createClientSocket(host, port, tlsEnabled);
 *
 * When tlsEnabled=true, all traffic is encrypted with TLS 1.3 using the self-signed
 * keystore generated in ~/.nimbusfs/nimbus.jks.
 * The client side uses a trust-all TrustManager so the self-signed cert is accepted
 * without a CA chain (acceptable for intra-cluster traffic).
 */
public class NimbusSocketFactory {

    private static final Logger log = LogManager.getLogger(NimbusSocketFactory.class);

    private NimbusSocketFactory() {}

    // ─── Client socket ─────────────────────────────────────────────────────────

    /**
     * Creates a connected client Socket. Uses TLS handshake if {@code useTls} is true.
     */
    public static Socket createClientSocket(String host, int port, boolean useTls) throws IOException {
        if (useTls) {
            log.trace("Opening TLS 1.3 client socket → {}:{}", host, port);
            try {
                // Clients accept the self-signed server cert via trust-all manager
                SSLContext trustAllCtx = SSLContext.getInstance("TLS");
                trustAllCtx.init(null, SSLUtil.buildTrustAllManagers(), new java.security.SecureRandom());
                SSLSocket socket = (SSLSocket) trustAllCtx.getSocketFactory().createSocket(host, port);
                socket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                socket.startHandshake();
                log.debug("TLS handshake completed with {}:{}", host, port);
                return socket;
            } catch (Exception e) {
                throw new IOException("TLS client socket setup failed: " + e.getMessage(), e);
            }
        } else {
            log.trace("Opening plain TCP socket → {}:{}", host, port);
            return new Socket(host, port);
        }
    }

    // ─── Server socket ─────────────────────────────────────────────────────────

    /**
     * Creates a bound ServerSocket. Uses TLS if {@code useTls} is true.
     */
    public static ServerSocket createServerSocket(int port, boolean useTls) throws IOException {
        if (useTls) {
            log.info("Binding TLS 1.3 ServerSocket on port {}", port);
            SSLServerSocketFactory factory = SSLUtil.getSSLContext().getServerSocketFactory();
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(port);
            serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
            serverSocket.setWantClientAuth(false);
            serverSocket.setNeedClientAuth(false);
            return serverSocket;
        } else {
            log.info("Binding plain TCP ServerSocket on port {}", port);
            return new ServerSocket(port);
        }
    }
}
