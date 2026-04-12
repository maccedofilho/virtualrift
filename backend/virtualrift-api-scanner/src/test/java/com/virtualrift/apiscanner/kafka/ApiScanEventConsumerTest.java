package com.virtualrift.apiscanner.kafka;

import com.virtualrift.apiscanner.service.ApiScanWorkerService;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("ApiScanEventConsumer Tests")
class ApiScanEventConsumerTest {

    @Test
    @DisplayName("should delegate requested event to worker service")
    void onScanRequested_quandoEventoRecebido_delegaParaWorker() {
        ApiScanWorkerService workerService = mock(ApiScanWorkerService.class);
        ApiScanEventConsumer consumer = new ApiScanEventConsumer(workerService);
        ScanRequestedEvent event = new ScanRequestedEvent(
                UUID.randomUUID(),
                TenantId.generate(),
                "https://api.example.com/users",
                ScanType.API.name(),
                1,
                30,
                Instant.now()
        );

        consumer.onScanRequested(event);

        verify(workerService).process(event);
    }
}
