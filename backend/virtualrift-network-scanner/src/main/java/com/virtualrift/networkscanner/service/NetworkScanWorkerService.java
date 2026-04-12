package com.virtualrift.networkscanner.service;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.networkscanner.engine.NetworkScanEngine;
import com.virtualrift.networkscanner.kafka.NetworkScanEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class NetworkScanWorkerService {

    private static final String ERROR_CODE_INVALID_TARGET = "NETWORK_INVALID_TARGET";
    private static final String ERROR_CODE_PROCESSING_FAILED = "NETWORK_PROCESSING_FAILED";

    private final NetworkScanEngine engine;
    private final NetworkScanEventPublisher publisher;

    public NetworkScanWorkerService(NetworkScanEngine engine, NetworkScanEventPublisher publisher) {
        this.engine = engine;
        this.publisher = publisher;
    }

    public void process(ScanRequestedEvent event) {
        if (!isNetworkScan(event)) {
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

    private boolean isNetworkScan(ScanRequestedEvent event) {
        return event != null && ScanType.NETWORK.name().equalsIgnoreCase(event.scanType());
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
