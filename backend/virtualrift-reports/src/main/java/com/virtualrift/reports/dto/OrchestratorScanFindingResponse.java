package com.virtualrift.reports.dto;

import com.virtualrift.common.model.Severity;

import java.time.Instant;
import java.util.UUID;

public record OrchestratorScanFindingResponse(
        UUID id,
        UUID scanId,
        UUID tenantId,
        String title,
        Severity severity,
        String category,
        String location,
        String evidence,
        Instant detectedAt
) {
}
