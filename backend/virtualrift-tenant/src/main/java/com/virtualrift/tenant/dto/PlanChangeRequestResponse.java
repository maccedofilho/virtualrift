package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.PlanChangeRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record PlanChangeRequestResponse(
        UUID id,
        UUID tenantId,
        UUID requestedByUserId,
        Plan currentPlan,
        Plan requestedPlan,
        PlanChangeRequestStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
