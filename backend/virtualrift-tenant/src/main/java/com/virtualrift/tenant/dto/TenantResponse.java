package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
        UUID id,
        String name,
        String slug,
        Plan plan,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
