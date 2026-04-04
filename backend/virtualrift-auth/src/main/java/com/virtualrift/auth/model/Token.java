package com.virtualrift.auth.model;

public record Token(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {
    public Token {
        if (expiresAt == null) {
            expiresAt = Instant.now().plusSeconds(15 * 60);
        }
    }
}
