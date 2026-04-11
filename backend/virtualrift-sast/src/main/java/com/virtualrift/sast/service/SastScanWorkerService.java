package com.virtualrift.sast.service;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.sast.engine.SastAnalyzer;
import com.virtualrift.sast.kafka.SastScanEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
public class SastScanWorkerService {

    private static final String ERROR_CODE_INVALID_TARGET = "SAST_INVALID_TARGET";
    private static final String ERROR_CODE_PROCESSING_FAILED = "SAST_PROCESSING_FAILED";

    private final SastAnalyzer analyzer;
    private final SastScanEventPublisher publisher;

    public SastScanWorkerService(SastAnalyzer analyzer, SastScanEventPublisher publisher) {
        this.analyzer = analyzer;
        this.publisher = publisher;
    }

    public void process(ScanRequestedEvent event) {
        if (!isSastScan(event)) {
            return;
        }

        Instant startedAt = Instant.now();

        try {
            Path targetPath = resolveTarget(event.target());
            List<VulnerabilityFinding> findings = analyze(targetPath).stream()
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

    private boolean isSastScan(ScanRequestedEvent event) {
        return event != null && ScanType.SAST.name().equalsIgnoreCase(event.scanType());
    }

    private Path resolveTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("SAST target cannot be blank");
        }

        Path path = Path.of(target).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("SAST target does not exist: " + target);
        }
        if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
            throw new IllegalArgumentException("SAST target must be a file or directory: " + target);
        }
        return path;
    }

    private List<VulnerabilityFinding> analyze(Path targetPath) {
        if (Files.isDirectory(targetPath)) {
            return analyzer.analyzeDirectory(targetPath);
        }
        return analyzer.analyzeFile(targetPath);
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
