package com.virtualrift.orchestrator.kafka;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.repository.ScanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ScanEventConsumer.class);

    private final ScanRepository scanRepository;

    public ScanEventConsumer(ScanRepository scanRepository) {
        this.scanRepository = scanRepository;
    }

    @KafkaListener(topics = "scan.completed", groupId = "virtualrift-orchestrator")
    @Transactional
    public void onScanCompleted(ScanCompletedEvent event) {
        log.info("Received scan.completed event for scanId: {}", event.scanId());

        Scan scan = scanRepository.findById(event.scanId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + event.scanId()));

        scan.setStatus(ScanStatus.COMPLETED);
        scan.setCompletedAt(event.completedAt());

        scanRepository.save(scan);
        log.info("Scan {} marked as COMPLETED", event.scanId());
    }

    @KafkaListener(topics = "scan.failed", groupId = "virtualrift-orchestrator")
    @Transactional
    public void onScanFailed(ScanFailedEvent event) {
        log.info("Received scan.failed event for scanId: {}", event.scanId());

        Scan scan = scanRepository.findById(event.scanId())
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + event.scanId()));

        scan.setStatus(ScanStatus.FAILED);
        scan.setErrorMessage(event.errorMessage());
        scan.setCompletedAt(event.failedAt());

        scanRepository.save(scan);
        log.info("Scan {} marked as FAILED", event.scanId());
    }
}
