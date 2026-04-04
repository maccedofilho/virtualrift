package com.virtualrift.auth.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TokenClaims(
        UUID userId,
        UUID tenantId,
        Set<String> roles,
        Instant expiration,
        Instant issuedAt
) {
    public TokenClaims {
        if (issuedAt == null) {
            issuedAt = Instant.now();
        }
        if (expiration == null) {
            expiration = Instant.now().plusSeconds(15 * 60);
        }
    }
}
