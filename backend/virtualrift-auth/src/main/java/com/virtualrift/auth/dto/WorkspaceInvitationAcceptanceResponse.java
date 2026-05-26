package com.virtualrift.auth.dto;

import com.virtualrift.tenant.model.Plan;

import java.util.Set;
import java.util.UUID;

public record WorkspaceInvitationAcceptanceResponse(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        Plan plan,
        Set<String> roles,
        String accessToken,
        String refreshToken
) {
}
