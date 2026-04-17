package com.virtualrift.reports.dto;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;

import java.time.Instant;
import java.util.UUID;

public record OrchestratorScanResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        String target,
        ScanType scanType,
        ScanStatus status,
        Integer depth,
        Integer timeout,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt
) {
}
