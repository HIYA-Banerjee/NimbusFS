package com.nimbusfs.master.auth;

import com.nimbusfs.common.exception.AuthException;
import com.nimbusfs.common.model.User;
import com.nimbusfs.master.metadata.MetadataStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mindrot.jbcrypt.BCrypt;

import java.util.UUID;

/**
 * Handles user authentication and session management.
 *
 * - Passwords hashed using BCrypt (cost factor 12)
 * - Sessions identified by UUID tokens stored in the 'users' table
 * - Default admin account created on first run if no admin exists
 */
public class AuthService {

    private static final Logger log           = LogManager.getLogger(AuthService.class);
    private static final int    BCRYPT_COST   = 12;
    private static final String DEFAULT_ADMIN = "admin";
    private static final String DEFAULT_PASS  = "nimbus123";

    private final MetadataStore metadataStore;

    public AuthService(MetadataStore metadataStore) {
        this.metadataStore = metadataStore;
    }

    // ─── Bootstrap ─────────────────────────────────────────────────────────────

    /**
     * Creates the default admin account if no admin user exists.
     * Called on server startup.
     */
    public void ensureAdminExists() {
        try {
            User existing = metadataStore.getUserByUsername(DEFAULT_ADMIN);
            if (existing == null) {
                log.info("No admin user found. Creating default admin account: '{}'", DEFAULT_ADMIN);
                register(DEFAULT_ADMIN, DEFAULT_PASS, User.Role.ADMIN);
                log.warn("⚠ Default admin password is '{}'. Change it after first login.", DEFAULT_PASS);
            }
        } catch (Exception e) {
            log.error("Failed to ensure admin exists: {}", e.getMessage());
        }
    }

    // ─── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * @param username unique username
     * @param password plain-text password (will be hashed)
     * @param role     USER or ADMIN
     * @return the created User (without password hash in returned object)
     * @throws AuthException if username already exists or is invalid
     */
    public User register(String username, String password, User.Role role) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthException("Username cannot be empty");
        }
        if (password == null || password.length() < 6) {
            throw new AuthException("Password must be at least 6 characters");
        }

        User existing = metadataStore.getUserByUsername(username.trim());
        if (existing != null) {
            throw new AuthException("Username '" + username + "' is already taken");
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_COST));
        User   user = new User(UUID.randomUUID().toString(), username.trim(), hash, role);

        metadataStore.saveUser(user);
        log.info("User registered: {} ({})", username, role);
        return sanitize(user);
    }

    // ─── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and issues a new session token.
     *
     * @return authenticated User with a fresh session token
     * @throws AuthException if credentials are invalid
     */
    public User login(String username, String password) throws Exception {
        if (username == null || password == null) {
            throw new AuthException("Username and password are required");
        }

        User user = metadataStore.getUserByUsername(username.trim());
        if (user == null) {
            throw new AuthException("Invalid username or password", AuthException.CODE_AUTH_FAILED);
        }

        if (!BCrypt.checkpw(password, user.getPasswordHash())) {
            log.warn("Failed login attempt for user: {}", username);
            throw new AuthException("Invalid username or password", AuthException.CODE_AUTH_FAILED);
        }

        // Issue a fresh session token
        String token     = UUID.randomUUID().toString();
        long   loginTime = System.currentTimeMillis();
        metadataStore.updateSessionToken(user.getUserId(), token, loginTime);

        user.setSessionToken(token);
        user.setLastLogin(loginTime);

        log.info("User '{}' logged in successfully.", username);
        return sanitize(user);
    }

    // ─── Session validation ────────────────────────────────────────────────────

    /**
     * Validates a session token and returns the associated user.
     *
     * @throws AuthException if the token is invalid or expired
     */
    public User validateSession(String sessionToken) throws Exception {
        if (sessionToken == null || sessionToken.isEmpty()) {
            throw new AuthException("Session token is required");
        }

        // We look up by scanning — in production, a Redis-backed token store would be used
        // For this project, we use a sequential scan of users (acceptable for small user counts)
        for (User user : metadataStore.getAllUsers()) {
            if (sessionToken.equals(user.getSessionToken())) {
                return user;
            }
        }

        throw new AuthException("Invalid or expired session token", AuthException.CODE_AUTH_FAILED);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a copy of the user with the password hash cleared (safe to send to client). */
    private User sanitize(User user) {
        User safe = new User(user.getUserId(), user.getUsername(), null, user.getRole());
        safe.setSessionToken(user.getSessionToken());
        safe.setCreatedAt(user.getCreatedAt());
        safe.setLastLogin(user.getLastLogin());
        return safe;
    }
}
