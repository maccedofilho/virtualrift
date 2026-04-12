package com.virtualrift.webscanner.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.webscanner.service.WebScanWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class WebScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebScanEventConsumer.class);

    private final WebScanWorkerService workerService;

    public WebScanEventConsumer(WebScanWorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(
            topics = "scan.requested",
            groupId = "virtualrift-web-scanner",
            containerFactory = "scanRequestedKafkaListenerContainerFactory"
    )
    public void onScanRequested(ScanRequestedEvent event) {
        if (event != null) {
            log.info("Received scan.requested event for WEB worker scanId: {}", event.scanId());
        }
        workerService.process(event);
    }
}
