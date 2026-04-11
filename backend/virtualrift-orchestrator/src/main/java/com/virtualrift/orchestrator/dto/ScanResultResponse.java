package com.virtualrift.orchestrator.dto;

import com.virtualrift.common.model.ScanStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanResultResponse(
        UUID scanId,
        UUID tenantId,
        ScanStatus status,
        int totalFindings,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        int infoCount,
        int riskScore,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        List<ScanFindingResponse> findings
) {
}
