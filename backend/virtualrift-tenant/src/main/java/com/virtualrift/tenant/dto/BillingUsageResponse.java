package com.virtualrift.tenant.dto;

public record BillingUsageResponse(
        long scanTargetsUsed,
        Integer scanTargetsRemaining
) {
}
