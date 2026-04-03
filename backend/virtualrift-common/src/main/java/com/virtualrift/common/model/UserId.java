package com.virtualrift.common.model;

import java.util.UUID;

public record UserId(UUID value) {

    public static UserId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        try {
            return new UserId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
