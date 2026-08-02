package com.nimbusfs.common.exception;

/** Thrown when authentication or authorization fails. */
public class AuthException extends NimbusException {

    public static final int CODE_AUTH_FAILED      = 1000;
    public static final int CODE_PERMISSION_DENIED = 1006;

    public AuthException(String message) {
        super(message, CODE_AUTH_FAILED);
    }

    public AuthException(String message, int errorCode) {
        super(message, errorCode);
    }
}
