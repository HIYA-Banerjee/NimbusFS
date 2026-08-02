package com.nimbusfs.client.model;

import com.nimbusfs.common.model.User;

/**
 * Thread-safe singleton for storing current authenticated user session in the JavaFX client.
 */
public class SessionContext {

    private static final SessionContext INSTANCE = new SessionContext();

    private User currentUser;
    private String sessionToken;
    private String serverHost = "localhost";
    private int serverPort = 9000;

    private SessionContext() {}

    public static SessionContext get() {
        return INSTANCE;
    }

    public synchronized void setSession(User user, String token) {
        this.currentUser = user;
        this.sessionToken = token;
    }

    public synchronized void clear() {
        this.currentUser = null;
        this.sessionToken = null;
    }

    public synchronized User getCurrentUser() {
        return currentUser;
    }

    public synchronized String getSessionToken() {
        return sessionToken;
    }

    public synchronized boolean isLoggedIn() {
        return sessionToken != null && currentUser != null;
    }

    public synchronized String getServerHost() {
        return serverHost;
    }

    public synchronized void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public synchronized int getServerPort() {
        return serverPort;
    }

    public synchronized void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }
}
