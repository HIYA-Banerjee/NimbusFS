package com.nimbusfs.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/** User account DTO — serialized over the wire and persisted to SQLite. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {

    public enum Role { USER, ADMIN }

    private String role_string; // stored as String in JSON

    private String userId;
    private String username;
    private String passwordHash;   // BCrypt hash — never sent to client
    private Role   role;
    private String sessionToken;   // UUID — assigned on login
    private long   createdAt;
    private long   lastLogin;

    // ─── Constructors ──────────────────────────────────────────────────────────

    public User() {}

    public User(String userId, String username, String passwordHash, Role role) {
        this.userId       = userId;
        this.username     = username;
        this.passwordHash = passwordHash;
        this.role         = role;
        this.createdAt    = Instant.now().toEpochMilli();
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    // ─── Getters & Setters ─────────────────────────────────────────────────────

    public String getUserId()                       { return userId; }
    public void setUserId(String userId)            { this.userId = userId; }

    public String getUsername()                     { return username; }
    public void setUsername(String username)        { this.username = username; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String ph)          { this.passwordHash = ph; }

    public Role getRole()                           { return role; }
    public void setRole(Role role)                  { this.role = role; }

    public String getSessionToken()                 { return sessionToken; }
    public void setSessionToken(String token)       { this.sessionToken = token; }

    public long getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(long createdAt)        { this.createdAt = createdAt; }

    public long getLastLogin()                      { return lastLogin; }
    public void setLastLogin(long lastLogin)        { this.lastLogin = lastLogin; }
}
