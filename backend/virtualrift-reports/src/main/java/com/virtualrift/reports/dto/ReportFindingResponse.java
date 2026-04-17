package com.virtualrift.reports.dto;

import com.virtualrift.common.model.Severity;

import java.time.Instant;
import java.util.UUID;

public record ReportFindingResponse(
        UUID id,
        String title,
        Severity severity,
        String category,
        String location,
        String evidence,
        Instant detectedAt
) {
}
