package com.virtualrift.common.model;

public enum Severity {
    INFO(0),
    LOW(25),
    MEDIUM(50),
    HIGH(75),
    CRITICAL(100);

    private final int score;

    Severity(int score) {
        this.score = score;
    }

    public int score() {
        return score;
    }

    public boolean isCriticalOrHigher() {
        return this == CRITICAL;
    }

    public boolean isHighOrHigher() {
        return this == HIGH || this == CRITICAL;
    }

    public static Severity fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("severity cannot be null");
        }
        try {
            return Severity.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid severity: " + value, e);
        }
    }
}
