package com.virtualrift.networkscanner.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.networkscanner.service.NetworkScanWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class NetworkScanEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NetworkScanEventConsumer.class);

    private final NetworkScanWorkerService workerService;

    public NetworkScanEventConsumer(NetworkScanWorkerService workerService) {
        this.workerService = workerService;
    }

    @KafkaListener(
            topics = "scan.requested",
            groupId = "virtualrift-network-scanner",
            containerFactory = "scanRequestedKafkaListenerContainerFactory"
    )
    public void onScanRequested(ScanRequestedEvent event) {
        if (event != null) {
            log.info("Received scan.requested event for NETWORK worker scanId: {}", event.scanId());
        }
        workerService.process(event);
    }
}
