package com.virtualrift.auth.dto;

import com.virtualrift.auth.model.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AccountProfileResponse(
        UUID id,
        String email,
        UUID tenantId,
        UserStatus status,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {
}
