package com.virtualrift.common.exception;

public class SecurityException extends RuntimeException {

    private final String errorCode;

    public SecurityException(String message) {
        super(message);
        this.errorCode = "SECURITY_VIOLATION";
    }

    public SecurityException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public SecurityException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SECURITY_VIOLATION";
    }

    public String errorCode() {
        return errorCode;
    }
}
