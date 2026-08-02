package com.nimbusfs.common.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PacketTest {

    @Test
    public void testPacketSerializeDeserialize() throws Exception {
        Map<String, Object> payload = Map.of("username", "testuser", "action", "LOGIN");
        Packet original = Packet.of(MessageType.LOGIN_REQUEST, payload);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.writeTo(out);

        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        Packet read = Packet.readFrom(in);

        assertEquals(MessageType.LOGIN_REQUEST, read.getType());
        Map<?, ?> readPayload = read.getPayloadAs(Map.class);
        assertEquals("testuser", readPayload.get("username"));
    }
}
