package com.virtualrift.tenant.dto;

import com.virtualrift.common.security.UserRole;
import com.virtualrift.tenant.model.TenantInvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantInvitationResponse(
        UUID id,
        UUID tenantId,
        String email,
        UserRole role,
        TenantInvitationStatus status,
        UUID invitedByUserId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant createdAt,
        Instant updatedAt,
        String inviteToken
) {
}
