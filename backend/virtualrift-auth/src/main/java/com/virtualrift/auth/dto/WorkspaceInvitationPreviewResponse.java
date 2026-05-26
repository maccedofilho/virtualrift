package com.virtualrift.auth.dto;

import com.virtualrift.tenant.model.Plan;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WorkspaceInvitationPreviewResponse(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        Plan plan,
        String email,
        Set<String> roles,
        Instant expiresAt
) {
}
