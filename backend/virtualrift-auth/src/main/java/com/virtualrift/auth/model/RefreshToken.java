package com.virtualrift.auth.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        String token,
        UUID userId,
        UUID tenantId,
        Instant expiration
) {
    public RefreshToken {
        if (expiration == null) {
            expiration = Instant.now().plusSeconds(7 * 24 * 60 * 60);
        }
    }
}
