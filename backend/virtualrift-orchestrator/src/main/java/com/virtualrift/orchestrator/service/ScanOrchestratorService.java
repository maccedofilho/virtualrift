package com.virtualrift.orchestrator.service;

import com.virtualrift.common.model.Severity;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanFindingResponse;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.dto.ScanResultResponse;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.exception.ScanQuotaExceededException;
import com.virtualrift.orchestrator.kafka.ScanEventProducer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanFinding;
import com.virtualrift.orchestrator.repository.ScanFindingRepository;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.tenant.client.TenantClient;
import com.virtualrift.tenant.model.TenantQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ScanOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestratorService.class);

    private final ScanRepository scanRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final ScanEventProducer eventProducer;
    private final TenantClient tenantClient;

    public ScanOrchestratorService(ScanRepository scanRepository,
                                  ScanFindingRepository scanFindingRepository,
                                  ScanEventProducer eventProducer,
                                  TenantClient tenantClient) {
        this.scanRepository = scanRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.eventProducer = eventProducer;
        this.tenantClient = tenantClient;
    }

    @Transactional
    public ScanResponse createScan(CreateScanRequest request, UUID tenantId, UUID userId) {
        TenantQuota quota = tenantClient.getQuota(tenantId);
        Plan plan = tenantClient.getPlan(tenantId);

        validateScanTypeAllowed(request.scanType(), plan);
        validateDailyQuota(tenantId);
        validateConcurrentScans(tenantId, quota);

        Scan scan = new Scan(
                UUID.randomUUID(),
                tenantId,
                userId,
                request.target(),
                request.scanType(),
                request.depth(),
                request.timeout(),
                com.virtualrift.common.model.ScanStatus.PENDING
        );
        scan = scanRepository.save(scan);

        eventProducer.publishScanRequested(
                scan.getId(),
                new TenantId(tenantId),
                scan.getTarget(),
                scan.getScanType().name(),
                scan.getDepth(),
                scan.getTimeout()
        );

        return toResponse(scan);
    }

    public ScanResponse getScan(UUID scanId, UUID tenantId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan not found: " + scanId));

        if (!scan.getTenantId().equals(tenantId)) {
            throw new ScanNotFoundException("Scan not found: " + scanId);
        }

        return toResponse(scan);
    }

    public ScanResponse getScanByTenantAndId(UUID tenantId, UUID scanId) {
        return getScan(scanId, tenantId);
    }

    public ScanResponse getStatus(UUID scanId, UUID tenantId) {
        return getScan(scanId, tenantId);
    }

    public List<ScanFindingResponse> getFindings(UUID scanId, UUID tenantId) {
        getScan(scanId, tenantId);
        return scanFindingRepository.findByTenantIdAndScanIdOrderByDetectedAtDesc(tenantId, scanId).stream()
                .map(this::toFindingResponse)
                .toList();
    }

    public ScanResultResponse getResult(UUID scanId, UUID tenantId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan not found: " + scanId));

        if (!scan.getTenantId().equals(tenantId)) {
            throw new ScanNotFoundException("Scan not found: " + scanId);
        }

        List<ScanFindingResponse> findings = scanFindingRepository
                .findByTenantIdAndScanIdOrderByDetectedAtDesc(tenantId, scanId).stream()
                .map(this::toFindingResponse)
                .toList();

        return new ScanResultResponse(
                scan.getId(),
                scan.getTenantId(),
                scan.getStatus(),
                findings.size(),
                countBySeverity(findings, Severity.CRITICAL),
                countBySeverity(findings, Severity.HIGH),
                countBySeverity(findings, Severity.MEDIUM),
                countBySeverity(findings, Severity.LOW),
                countBySeverity(findings, Severity.INFO),
                calculateRiskScore(findings),
                scan.getErrorMessage(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                findings
        );
    }

    private void validateScanTypeAllowed(ScanType scanType, Plan plan) {
        if (!isScanTypeAllowed(scanType, plan)) {
            throw new ScanQuotaExceededException(
                    "Scan type " + scanType + " is not allowed for plan " + plan
            );
        }
    }

    private boolean isScanTypeAllowed(ScanType scanType, Plan plan) {
        return switch (plan) {
            case TRIAL -> scanType == ScanType.WEB;
            case STARTER -> scanType == ScanType.WEB || scanType == ScanType.API;
            case PROFESSIONAL -> scanType != ScanType.SAST;
            case ENTERPRISE -> true;
        };
    }

    private void validateDailyQuota(UUID tenantId) {
        long todayCount = scanRepository.countByTenantIdSince(tenantId, Instant.now().minus(Duration.ofDays(1)));
        int dailyLimit = tenantClient.getQuota(tenantId).getMaxScansPerDay();

        if (dailyLimit > 0 && todayCount >= dailyLimit) {
            throw new ScanQuotaExceededException("Daily scan quota exceeded");
        }
    }

    private void validateConcurrentScans(UUID tenantId, TenantQuota quota) {
        long runningCount = scanRepository.countByTenantIdAndStatus(
                tenantId, com.virtualrift.common.model.ScanStatus.RUNNING
        );

        if (runningCount >= quota.getMaxConcurrentScans()) {
            throw new ScanQuotaExceededException("Maximum concurrent scans limit reached");
        }
    }

    private ScanResponse toResponse(Scan scan) {
        return new ScanResponse(
                scan.getId(),
                scan.getTenantId(),
                scan.getUserId(),
                scan.getTarget(),
                scan.getScanType(),
                scan.getStatus(),
                scan.getDepth(),
                scan.getTimeout(),
                scan.getErrorMessage(),
                scan.getCreatedAt(),
                scan.getStartedAt(),
                scan.getCompletedAt()
        );
    }

    private ScanFindingResponse toFindingResponse(ScanFinding finding) {
        return new ScanFindingResponse(
                finding.getId(),
                finding.getScanId(),
                finding.getTenantId(),
                finding.getTitle(),
                finding.getSeverity(),
                finding.getCategory(),
                finding.getLocation(),
                finding.getEvidence(),
                finding.getDetectedAt()
        );
    }

    private int countBySeverity(List<ScanFindingResponse> findings, Severity severity) {
        return (int) findings.stream()
                .filter(finding -> finding.severity() == severity)
                .count();
    }

    private int calculateRiskScore(List<ScanFindingResponse> findings) {
        if (findings.isEmpty()) {
            return 0;
        }

        int score = findings.stream()
                .mapToInt(finding -> finding.severity().score())
                .sum();

        int normalized = Math.min(100, score / 5);
        if (countBySeverity(findings, Severity.CRITICAL) > 0 && normalized < 50) {
            return 50;
        }
        return normalized;
    }
}
