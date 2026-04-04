package com.virtualrift.auth.exception;

public class UserSuspendedException extends RuntimeException {

    public UserSuspendedException(String message) {
        super(message);
    }

    public UserSuspendedException() {
        super("User account is suspended");
    }
}
