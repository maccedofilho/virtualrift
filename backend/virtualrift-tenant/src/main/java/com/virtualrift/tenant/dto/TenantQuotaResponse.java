package com.virtualrift.tenant.dto;

public record TenantQuotaResponse(
        int maxScansPerDay,
        int maxConcurrentScans,
        int maxScanTargets,
        int reportRetentionDays,
        boolean sastEnabled
) {
}
