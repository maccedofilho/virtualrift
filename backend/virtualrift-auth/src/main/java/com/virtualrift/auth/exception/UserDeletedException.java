package com.virtualrift.auth.exception;

public class UserDeletedException extends RuntimeException {

    public UserDeletedException(String message) {
        super(message);
    }

    public UserDeletedException() {
        super("User account is deleted");
    }
}
