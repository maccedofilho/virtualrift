package com.virtualrift.common.runtime;

import java.util.Locale;

public enum RuntimeEnvironment {
    LOCAL,
    DEVELOPMENT,
    STAGING,
    PRODUCTION;

    public static RuntimeEnvironment fromValue(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "local" -> LOCAL;
            case "development", "dev" -> DEVELOPMENT;
            case "staging", "stage" -> STAGING;
            case "production", "prod" -> PRODUCTION;
            default -> throw new IllegalArgumentException("Unsupported virtualrift runtime environment: " + value);
        };
    }

    public boolean isLocal() {
        return this == LOCAL;
    }

    public String configValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
