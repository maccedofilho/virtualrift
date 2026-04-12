package com.virtualrift.apiscanner.service;

import com.virtualrift.apiscanner.engine.ApiScanEngine;
import com.virtualrift.apiscanner.kafka.ApiScanEventPublisher;
import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ApiScanWorkerService {

    private static final String ERROR_CODE_INVALID_TARGET = "API_INVALID_TARGET";
    private static final String ERROR_CODE_PROCESSING_FAILED = "API_PROCESSING_FAILED";

    private final ApiScanEngine engine;
    private final ApiScanEventPublisher publisher;

    public ApiScanWorkerService(ApiScanEngine engine, ApiScanEventPublisher publisher) {
        this.engine = engine;
        this.publisher = publisher;
    }

    public void process(ScanRequestedEvent event) {
        if (!isApiScan(event)) {
            return;
        }

        Instant startedAt = Instant.now();

        try {
            List<VulnerabilityFinding> findings = engine.scan(event.target()).stream()
                    .map(finding -> remapFinding(event, finding))
                    .sorted(VulnerabilityFinding.bySeverity())
                    .toList();

            ScanCompletedEvent completedEvent = new ScanCompletedEvent(
                    event.scanId(),
                    event.tenantId(),
                    findings,
                    findings.size(),
                    calculateRiskScore(findings),
                    startedAt,
                    Instant.now()
            );
            publisher.publishCompleted(completedEvent);
        } catch (IllegalArgumentException e) {
            publishFailed(event, e.getMessage(), ERROR_CODE_INVALID_TARGET);
        } catch (RuntimeException e) {
            publishFailed(event, e.getMessage(), ERROR_CODE_PROCESSING_FAILED);
        }
    }

    private boolean isApiScan(ScanRequestedEvent event) {
        return event != null && ScanType.API.name().equalsIgnoreCase(event.scanType());
    }

    private VulnerabilityFinding remapFinding(ScanRequestedEvent event, VulnerabilityFinding finding) {
        return VulnerabilityFinding.of(
                finding.id(),
                event.scanId(),
                event.tenantId(),
                finding.title(),
                finding.severity(),
                finding.category(),
                finding.location(),
                finding.evidence(),
                finding.detectedAt()
        ).withMaskedEvidence();
    }

    private int calculateRiskScore(List<VulnerabilityFinding> findings) {
        if (findings.isEmpty()) {
            return 0;
        }

        int score = findings.stream()
                .mapToInt(finding -> finding.severity().score())
                .sum();

        int normalized = Math.min(100, score / 5);
        boolean hasCritical = findings.stream()
                .anyMatch(finding -> finding.severity() == Severity.CRITICAL);
        if (hasCritical && normalized < 50) {
            return 50;
        }
        return normalized;
    }

    private void publishFailed(ScanRequestedEvent event, String message, String code) {
        ScanFailedEvent failedEvent = new ScanFailedEvent(
                event.scanId(),
                event.tenantId(),
                message,
                code,
                Instant.now()
        );
        publisher.publishFailed(failedEvent);
    }
}
