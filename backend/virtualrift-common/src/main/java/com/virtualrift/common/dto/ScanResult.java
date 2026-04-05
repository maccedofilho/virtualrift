package com.virtualrift.common.dto;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ScanResult(
        UUID scanId,
        TenantId tenantId,
        ScanStatus status,
        List<VulnerabilityFinding> findings,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        String errorMessage
) {

    public static ScanResult of(UUID scanId, TenantId tenantId, ScanStatus status,
                                List<VulnerabilityFinding> findings,
                                Instant startedAt, Instant completedAt, Instant failedAt, String errorMessage) {
        if (scanId == null) {
            throw new IllegalArgumentException("scanId cannot be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }
        if (findings == null) {
            throw new IllegalArgumentException("findings cannot be null");
        }

        Instant actualCompletedAt = completedAt;
        Instant actualFailedAt = null;

        if (status == ScanStatus.COMPLETED && completedAt == null) {
            actualCompletedAt = Instant.now();
        } else if (status == ScanStatus.FAILED && failedAt == null) {
            actualFailedAt = Instant.now();
        }

        return new ScanResult(
                scanId,
                tenantId,
                status,
                List.copyOf(findings),
                startedAt,
                actualCompletedAt,
                actualFailedAt,
                errorMessage
        );
    }

    public int countBySeverity(Severity severity) {
        return (int) findings.stream()
                .filter(f -> f.severity() == severity)
                .count();
    }

    public int criticalCount() {
        return countBySeverity(Severity.CRITICAL);
    }

    public int highCount() {
        return countBySeverity(Severity.HIGH);
    }

    public int mediumCount() {
        return countBySeverity(Severity.MEDIUM);
    }

    public int lowCount() {
        return countBySeverity(Severity.LOW);
    }

    public int totalFindings() {
        return findings.size();
    }

    public int riskScore() {
        if (findings.isEmpty()) {
            return 0;
        }

        int score = findings.stream()
                .mapToInt(f -> f.severity().score())
                .sum();

        int normalized = Math.min(100, score / 5);

        if (criticalCount() > 0 && normalized < 50) {
            normalized = 50;
        }

        return Math.min(100, normalized);
    }

    public Duration duration() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt);
    }

    public ScanResult withMaskedFindings() {
        List<VulnerabilityFinding> masked = findings.stream()
                .map(VulnerabilityFinding::withMaskedEvidence)
                .toList();

        return new ScanResult(scanId, tenantId, status, masked, startedAt, completedAt, failedAt, errorMessage);
    }

    public boolean isSuccessful() {
        return status == ScanStatus.COMPLETED;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
