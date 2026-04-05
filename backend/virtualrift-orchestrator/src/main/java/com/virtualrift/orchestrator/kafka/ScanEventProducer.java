package com.virtualrift.orchestrator.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ScanEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ScanEventProducer.class);
    private static final String SCAN_REQUESTED_TOPIC = "scan.requested";

    private final KafkaTemplate<String, ScanRequestedEvent> kafkaTemplate;

    public ScanEventProducer(KafkaTemplate<String, ScanRequestedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishScanRequested(UUID scanId, TenantId tenantId, String target, String scanType,
                                     Integer depth, Integer timeout) {
        ScanRequestedEvent event = new ScanRequestedEvent(
                scanId,
                tenantId,
                target,
                scanType,
                depth,
                timeout,
                Instant.now()
        );

        kafkaTemplate.send(SCAN_REQUESTED_TOPIC, scanId.toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Published scan.requested event for scanId: {}", scanId);
                    } else {
                        log.error("Failed to publish scan.requested event for scanId: {}", scanId, ex);
                    }
                });
    }
}
