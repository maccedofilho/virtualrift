package com.virtualrift.tenant.dto;

import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantStatus;

import java.util.UUID;

public record BillingSummaryResponse(
        UUID tenantId,
        String tenantName,
        String tenantSlug,
        TenantStatus tenantStatus,
        Plan currentPlan,
        TenantQuotaResponse quota,
        BillingUsageResponse usage,
        PlanChangeRequestResponse pendingPlanChangeRequest
) {
}
