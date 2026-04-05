package com.virtualrift.orchestrator.service;

import com.virtualrift.common.dto.ScanRequest;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.Plan;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.exception.ScanQuotaExceededException;
import com.virtualrift.orchestrator.kafka.ScanEventProducer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.tenant.client.TenantClient;
import com.virtualrift.tenant.model.TenantQuota;
import org.slf4j.Logger;
import org.slf4jj.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ScanOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestratorService.class);

    private final ScanRepository scanRepository;
    private final ScanEventProducer eventProducer;
    private final TenantClient tenantClient;

    public ScanOrchestratorService(ScanRepository scanRepository,
                                  ScanEventProducer eventProducer,
                                  TenantClient tenantClient) {
        this.scanRepository = scanRepository;
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

    public ScanResponse getStatus(UUID scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan not found: " + scanId));

        return toResponse(scan);
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
        long todayCount = scanRepository.countByTenantIdSince(tenantId, Instant.now().minusDays(1));
        int dailyLimit = tenantClient.getQuota(tenantId).maxScansPerDay();

        if (dailyLimit > 0 && todayCount >= dailyLimit) {
            throw new ScanQuotaExceededException("Daily scan quota exceeded");
        }
    }

    private void validateConcurrentScans(UUID tenantId, TenantQuota quota) {
        long runningCount = scanRepository.countByTenantIdAndStatus(
                tenantId, com.virtualrift.common.model.ScanStatus.RUNNING
        );

        if (runningCount >= quota.maxConcurrentScans()) {
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
}
