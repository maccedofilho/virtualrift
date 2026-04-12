package com.virtualrift.networkscanner.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.networkscanner.service.NetworkScanWorkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("NetworkScanEventConsumer Tests")
class NetworkScanEventConsumerTest {

    @Test
    @DisplayName("should delegate requested event to worker service")
    void onScanRequested_quandoEventoRecebido_delegaParaWorker() {
        NetworkScanWorkerService workerService = mock(NetworkScanWorkerService.class);
        NetworkScanEventConsumer consumer = new NetworkScanEventConsumer(workerService);
        ScanRequestedEvent event = new ScanRequestedEvent(
                UUID.randomUUID(),
                TenantId.generate(),
                "example.com:443",
                ScanType.NETWORK.name(),
                1,
                30,
                Instant.now()
        );

        consumer.onScanRequested(event);

        verify(workerService).process(event);
    }
}
