package com.virtualrift.sast.kafka;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class SastScanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SastScanEventPublisher.class);
    private static final String SCAN_COMPLETED_TOPIC = "scan.completed";
    private static final String SCAN_FAILED_TOPIC = "scan.failed";

    private final KafkaTemplate<String, ScanCompletedEvent> completedKafkaTemplate;
    private final KafkaTemplate<String, ScanFailedEvent> failedKafkaTemplate;

    public SastScanEventPublisher(KafkaTemplate<String, ScanCompletedEvent> completedKafkaTemplate,
                                  KafkaTemplate<String, ScanFailedEvent> failedKafkaTemplate) {
        this.completedKafkaTemplate = completedKafkaTemplate;
        this.failedKafkaTemplate = failedKafkaTemplate;
    }

    public void publishCompleted(ScanCompletedEvent event) {
        completedKafkaTemplate.send(SCAN_COMPLETED_TOPIC, event.scanId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Published scan.completed event for SAST scanId: {}", event.scanId());
                    } else {
                        log.error("Failed to publish scan.completed event for SAST scanId: {}", event.scanId(), ex);
                    }
                });
    }

    public void publishFailed(ScanFailedEvent event) {
        failedKafkaTemplate.send(SCAN_FAILED_TOPIC, event.scanId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Published scan.failed event for SAST scanId: {}", event.scanId());
                    } else {
                        log.error("Failed to publish scan.failed event for SAST scanId: {}", event.scanId(), ex);
                    }
                });
    }
}
