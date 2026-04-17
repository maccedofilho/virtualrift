package com.virtualrift.reports.dto;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        UUID tenantId,
        UUID scanId,
        UUID userId,
        String target,
        ScanType scanType,
        ScanStatus status,
        int totalFindings,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        int infoCount,
        int riskScore,
        String errorMessage,
        Instant scanCreatedAt,
        Instant scanStartedAt,
        Instant scanCompletedAt,
        Instant createdAt,
        Instant generatedAt,
        List<ReportFindingResponse> findings
) {
}
