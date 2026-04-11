package com.virtualrift.sast.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.sast.service.SastScanWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class SastScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SastScanEventConsumer.class);

    private final SastScanWorkerService workerService;

    public SastScanEventConsumer(SastScanWorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(
            topics = "scan.requested",
            groupId = "virtualrift-sast",
            containerFactory = "scanRequestedKafkaListenerContainerFactory"
    )
    public void onScanRequested(ScanRequestedEvent event) {
        if (event != null) {
            log.info("Received scan.requested event for SAST worker scanId: {}", event.scanId());
        }
        workerService.process(event);
    }
}
