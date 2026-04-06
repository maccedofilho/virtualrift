package com.virtualrift.common.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.UUID;

public record TenantId(UUID value) {

    public static TenantId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        return new TenantId(value);
    }

    public static TenantId generate() {
        return new TenantId(UUID.randomUUID());
    }

    @JsonCreator
    public static TenantId fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        try {
            return new TenantId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID: " + value, e);
        }
    }

    @JsonValue
    @Override
    public String toString() {
        return value.toString();
    }
}
