package com.virtualrift.sast.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.sast.service.SastScanWorkerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SastScanEventConsumer Tests")
class SastScanEventConsumerTest {

    @Mock
    private SastScanWorkerService workerService;

    @Test
    @DisplayName("should delegate scan requested event to worker service")
    void onScanRequested_quandoEventoRecebido_delegaParaWorker() {
        SastScanEventConsumer consumer = new SastScanEventConsumer(workerService);
        ScanRequestedEvent event = new ScanRequestedEvent(
                UUID.randomUUID(),
                TenantId.generate(),
                "/tmp/project",
                ScanType.SAST.name(),
                1,
                300,
                Instant.now()
        );

        consumer.onScanRequested(event);

        verify(workerService).process(event);
    }
}
