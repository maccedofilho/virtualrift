package com.virtualrift.orchestrator.kafka;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanFinding;
import com.virtualrift.orchestrator.repository.ScanFindingRepository;
import com.virtualrift.orchestrator.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScanEventConsumer.class);

    private final ScanRepository scanRepository;
    private final ScanFindingRepository scanFindingRepository;

    public ScanEventConsumer(ScanRepository scanRepository, ScanFindingRepository scanFindingRepository) {
        this.scanRepository = scanRepository;
        this.scanFindingRepository = scanFindingRepository;
    }

    @KafkaListener(
            topics = "scan.completed",
            groupId = "virtualrift-orchestrator",
            containerFactory = "scanCompletedKafkaListenerContainerFactory"
    )
    @Transactional
    public void onScanCompleted(ScanCompletedEvent event) {
        log.info("Received scan.completed event for scanId: {}", event.scanId());

        Scan scan = scanRepository.findById(event.scanId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + event.scanId()));
        validateEventTenant(scan, event.tenantId().value());

        scan.setStatus(ScanStatus.COMPLETED);
        scan.setStartedAt(event.startedAt());
        scan.setCompletedAt(event.completedAt());

        scanFindingRepository.deleteByTenantIdAndScanId(scan.getTenantId(), scan.getId());
        scanFindingRepository.saveAll(toFindings(scan, event.findings()));

        scanRepository.save(scan);
        log.info("Scan {} marked as COMPLETED", event.scanId());
    }

    @KafkaListener(
            topics = "scan.failed",
            groupId = "virtualrift-orchestrator",
            containerFactory = "scanFailedKafkaListenerContainerFactory"
    )
    @Transactional
    public void onScanFailed(ScanFailedEvent event) {
        log.info("Received scan.failed event for scanId: {}", event.scanId());

        Scan scan = scanRepository.findById(event.scanId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + event.scanId()));
        validateEventTenant(scan, event.tenantId().value());

        scan.setStatus(ScanStatus.FAILED);
        scan.setErrorMessage(event.errorMessage());
        scan.setCompletedAt(event.failedAt());

        scanRepository.save(scan);
        log.info("Scan {} marked as FAILED", event.scanId());
    }

    private void validateEventTenant(Scan scan, UUID eventTenantId) {
        if (!scan.getTenantId().equals(eventTenantId)) {
            throw new IllegalArgumentException("Scan tenant does not match event tenant");
        }
    }

    private List<ScanFinding> toFindings(Scan scan, List<VulnerabilityFinding> findings) {
        return findings.stream()
                .map(VulnerabilityFinding::withMaskedEvidence)
                .map(finding -> new ScanFinding(
                        finding.id(),
                        scan.getId(),
                        scan.getTenantId(),
                        finding.title(),
                        finding.severity(),
                        finding.category(),
                        finding.location(),
                        finding.evidence(),
                        finding.detectedAt()
                ))
                .toList();
    }
}
