package com.virtualrift.apiscanner.kafka;

import com.virtualrift.apiscanner.service.ApiScanWorkerService;
import com.virtualrift.common.events.ScanRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ApiScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ApiScanEventConsumer.class);

    private final ApiScanWorkerService workerService;

    public ApiScanEventConsumer(ApiScanWorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(
            topics = "scan.requested",
            groupId = "virtualrift-api-scanner",
            containerFactory = "scanRequestedKafkaListenerContainerFactory"
    )
    public void onScanRequested(ScanRequestedEvent event) {
        if (event != null) {
            log.info("Received scan.requested event for API worker scanId: {}", event.scanId());
        }
        workerService.process(event);
    }
}
