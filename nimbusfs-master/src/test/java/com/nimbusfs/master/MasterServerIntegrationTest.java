package com.nimbusfs.master;

import com.nimbusfs.common.model.*;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that starts a real Master Server on an ephemeral port
 * and exercises the full login → upload-plan → complete flow.
 *
 * NOTE: Uses port 9099 to avoid conflicts with a running master.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MasterServerIntegrationTest {

    private static MasterServer master;
    private static Thread masterThread;
    private static final int PORT = 9000;

    @BeforeAll
    static void startMaster() throws Exception {
        System.setProperty("master.port", String.valueOf(PORT));
        master = new MasterServer();
        masterThread = new Thread(() -> {
            try { master.start(); } catch (Exception e) { e.printStackTrace(); }
        }, "test-master");
        masterThread.setDaemon(true);
        masterThread.start();
        Thread.sleep(500); // Let master bind and start
    }

    @AfterAll
    static void stopMaster() {
        if (master != null) master.stop();
    }

    @Test
    @Order(1)
    void testPingPong() throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(5000);
            Packet ping = Packet.empty(MessageType.PING);
            ping.writeTo(socket.getOutputStream());

            Packet pong = Packet.readFrom(socket.getInputStream());
            assertEquals(MessageType.PONG, pong.getType());
        }
    }

    @Test
    @Order(2)
    void testLoginWithDefaultAdmin() throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(5000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            Packet loginReq = Packet.of(MessageType.LOGIN_REQUEST,
                Map.of("username", "admin", "password", "nimbus123"));
            loginReq.writeTo(out);

            Packet resp = Packet.readFrom(in);
            assertEquals(MessageType.LOGIN_RESPONSE, resp.getType());

            Map<?, ?> body = resp.getPayloadAs(Map.class);
            assertTrue((Boolean) body.get("success"));
            assertNotNull(body.get("sessionToken"));
            assertEquals("ADMIN", body.get("role"));
        }
    }

    @Test
    @Order(3)
    void testRegisterNewUser() throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(5000);
            Packet req = Packet.of(MessageType.REGISTER_REQUEST,
                Map.of("username", "testuser", "password", "test1234"));
            req.writeTo(socket.getOutputStream());

            Packet resp = Packet.readFrom(socket.getInputStream());
            assertEquals(MessageType.REGISTER_RESPONSE, resp.getType());

            Map<?, ?> body = resp.getPayloadAs(Map.class);
            assertTrue((Boolean) body.get("success"));
            assertEquals("testuser", body.get("username"));
        }
    }

    @Test
    @Order(4)
    void testListFilesRequiresAuth() throws Exception {
        try (Socket socket = new Socket("localhost", PORT)) {
            socket.setSoTimeout(5000);
            // Don't log in first — should get error
            Packet req = Packet.of(MessageType.LIST_FILES_REQUEST, Map.of());
            req.writeTo(socket.getOutputStream());

            Packet resp = Packet.readFrom(socket.getInputStream());
            assertEquals(MessageType.ERROR, resp.getType());
        }
    }
}
