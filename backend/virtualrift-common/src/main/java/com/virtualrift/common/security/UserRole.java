package com.virtualrift.common.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum UserRole {
    OWNER,
    ANALYST,
    READER;

    public static Optional<UserRole> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(role -> role.name().equals(normalized))
                .findFirst();
    }
}
