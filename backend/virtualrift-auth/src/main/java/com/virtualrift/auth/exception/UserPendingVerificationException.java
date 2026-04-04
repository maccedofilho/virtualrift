package com.virtualrift.auth.exception;

public class UserPendingVerificationException extends RuntimeException {

    public UserPendingVerificationException(String message) {
        super(message);
    }

    public UserPendingVerificationException() {
        super("User account is pending verification");
    }
}
