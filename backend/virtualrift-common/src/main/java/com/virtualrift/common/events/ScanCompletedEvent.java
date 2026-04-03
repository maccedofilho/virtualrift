package com.virtualrift.common.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanCompletedEvent(
        UUID scanId,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        TenantId tenantId,
        List<VulnerabilityFinding> findings,
        int totalFindings,
        int riskScore,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant startedAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant completedAt
) {
    public ScanCompletedEvent {
        if (findings == null) {
            findings = List.of();
        }
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }

    public int criticalCount() {
        return (int) findings.stream()
                .filter(f -> f.severity() == Severity.CRITICAL)
                .count();
    }

    public int highCount() {
        return (int) findings.stream()
                .filter(f -> f.severity() == Severity.HIGH)
                .count();
    }
}
