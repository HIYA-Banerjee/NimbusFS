package com.nimbusfs.client.service;

import com.nimbusfs.client.model.SessionContext;
import com.nimbusfs.client.network.MasterClient;
import com.nimbusfs.common.model.User;
import com.nimbusfs.common.protocol.MessageType;
import com.nimbusfs.common.protocol.Packet;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side AuthService handling user login and registration with Master.
 */
public class AuthService {

    private final MasterClient masterClient = MasterClient.get();

    public CompletableFuture<User> login(String username, String password) {
        Map<String, Object> payload = Map.of("username", username, "password", password);
        Packet packet = Packet.of(MessageType.LOGIN_REQUEST, payload);

        return masterClient.sendRequest(packet).thenApply(response -> {
            if (response.getType() == MessageType.ERROR) {
                Map<?, ?> err = response.getPayloadAs(Map.class);
                throw new RuntimeException((String) err.get("message"));
            }

            Map<?, ?> body = response.getPayloadAs(Map.class);
            String token = (String) body.get("sessionToken");
            String userId = (String) body.get("userId");
            String roleStr = (String) body.get("role");

            User user = new User(userId, username, null, User.Role.valueOf(roleStr));
            user.setSessionToken(token);

            SessionContext.get().setSession(user, token);
            return user;
        });
    }

    public CompletableFuture<Boolean> register(String username, String password) {
        Map<String, Object> payload = Map.of("username", username, "password", password);
        Packet packet = Packet.of(MessageType.REGISTER_REQUEST, payload);

        return masterClient.sendRequest(packet).thenApply(response -> {
            if (response.getType() == MessageType.ERROR) {
                Map<?, ?> err = response.getPayloadAs(Map.class);
                throw new RuntimeException((String) err.get("message"));
            }
            return true;
        });
    }

    public CompletableFuture<Void> logout() {
        Packet packet = Packet.of(MessageType.LOGOUT_REQUEST, Map.of());
        return masterClient.sendRequest(packet).thenAccept(resp -> {
            SessionContext.get().clear();
        });
    }
}
