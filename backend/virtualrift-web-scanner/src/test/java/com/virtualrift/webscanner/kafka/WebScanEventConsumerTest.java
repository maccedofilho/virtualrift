package com.virtualrift.webscanner.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.webscanner.service.WebScanWorkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebScanEventConsumer Tests")
class WebScanEventConsumerTest {

    @Mock
    private WebScanWorkerService workerService;

    @Test
    @DisplayName("should delegate scan requested event to worker service")
    void onScanRequested_quandoEventoRecebido_delegaParaWorker() {
        WebScanEventConsumer consumer = new WebScanEventConsumer(workerService);
        ScanRequestedEvent event = new ScanRequestedEvent(
                UUID.randomUUID(),
                TenantId.generate(),
                "https://example.com",
                ScanType.WEB.name(),
                1,
                300,
                Instant.now()
        );

        consumer.onScanRequested(event);

        verify(workerService).process(event);
    }
}
