package com.nimbusfs.common.exception;

/** Base checked exception for all NimbusFS errors. */
public class NimbusException extends Exception {

    private final int errorCode;

    public NimbusException(String message) {
        super(message);
        this.errorCode = 5000;
    }

    public NimbusException(String message, int errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public NimbusException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = 5000;
    }

    public NimbusException(String message, int errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
