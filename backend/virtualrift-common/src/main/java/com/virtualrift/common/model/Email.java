package com.virtualrift.common.model;


public record Email(String value) {

    // basic email regex - sufficient for validation without external dependencies
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    public static Email of(String value) {
        if (value == null) {
            throw new IllegalArgumentException("email cannot be null");
        }

        String normalized = value.trim().toLowerCase();

        if (normalized.isBlank() || !normalized.matches(EMAIL_REGEX)) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }

        return new Email(normalized);
    }

    public String domain() {
        int atIndex = value.indexOf('@');
        if (atIndex >= 0 && atIndex < value.length() - 1) {
            return value.substring(atIndex + 1);
        }
        return value;
    }

    public String localPart() {
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            return value.substring(0, atIndex);
        }
        return value;
    }
}
