package com.virtualrift.tenant.dto;

import com.virtualrift.common.security.UserRole;
import com.virtualrift.tenant.model.Plan;

import java.util.UUID;

public record InternalAcceptTenantInvitationResponse(
        UUID invitationId,
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        Plan plan,
        String email,
        UserRole role
) {
}
