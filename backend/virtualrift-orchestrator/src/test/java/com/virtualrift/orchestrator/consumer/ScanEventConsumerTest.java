package com.virtualrift.orchestrator.consumer;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.orchestrator.kafka.ScanEventConsumer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanFinding;
import com.virtualrift.orchestrator.repository.ScanFindingRepository;
import com.virtualrift.orchestrator.repository.ScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanEventConsumer Tests")
class ScanEventConsumerTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanFindingRepository scanFindingRepository;

    private ScanEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ScanEventConsumer(scanRepository, scanFindingRepository);
    }

    private Scan createRunningScan(UUID scanId, UUID tenantId) {
        return new Scan(scanId, tenantId, UUID.randomUUID(), "https://example.com", ScanType.WEB, 3, 300, ScanStatus.RUNNING);
    }

    private VulnerabilityFinding finding(UUID scanId, UUID tenantId, String evidence) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                scanId,
                TenantId.of(tenantId),
                "Hardcoded Password",
                Severity.CRITICAL,
                "SAST",
                "src/App.java:10",
                evidence,
                Instant.now()
        );
    }

    @Nested
    @DisplayName("Completed events")
    class CompletedEvents {

        @Test
        @DisplayName("should mark scan as completed")
        void onScanCompleted_quandoScanExiste_atualizaStatusECompletedAt() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Scan scan = createRunningScan(scanId, tenantId);
            Instant completedAt = Instant.now();
            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId, TenantId.of(tenantId), List.of(), 0, 0, Instant.now().minusSeconds(30), completedAt
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            consumer.onScanCompleted(event);

            ArgumentCaptor<Scan> captor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(captor.capture());
            verify(scanFindingRepository).deleteByTenantIdAndScanId(tenantId, scanId);
            verify(scanFindingRepository).saveAll(List.of());
            assertEquals(ScanStatus.COMPLETED, captor.getValue().getStatus());
            assertEquals(completedAt, captor.getValue().getCompletedAt());
        }

        @Test
        @SuppressWarnings("unchecked")
        @DisplayName("should persist masked findings when scan completes")
        void onScanCompleted_quandoEventoTemFindings_persisteEvidenciaMascarada() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Scan scan = createRunningScan(scanId, tenantId);
            VulnerabilityFinding finding = finding(scanId, tenantId, "password=supersecret");
            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId, TenantId.of(tenantId), List.of(finding), 1, 50, Instant.now().minusSeconds(30), Instant.now()
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            consumer.onScanCompleted(event);

            ArgumentCaptor<Iterable<ScanFinding>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(scanFindingRepository).saveAll(captor.capture());
            ScanFinding persisted = captor.getValue().iterator().next();
            assertEquals(scanId, persisted.getScanId());
            assertEquals(tenantId, persisted.getTenantId());
            assertEquals("password=****", persisted.getEvidence());
        }

        @Test
        @DisplayName("should reject completed event from another tenant")
        void onScanCompleted_quandoTenantDoEventoDiverge_lancaIllegalArgumentException() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Scan scan = createRunningScan(scanId, tenantId);
            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId, TenantId.of(UUID.randomUUID()), List.of(), 0, 0, Instant.now(), Instant.now()
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            assertThrows(IllegalArgumentException.class, () -> consumer.onScanCompleted(event));
        }

        @Test
        @DisplayName("should throw when completed event references missing scan")
        void onScanCompleted_quandoScanNaoExiste_lancaIllegalArgumentException() {
            UUID scanId = UUID.randomUUID();
            ScanCompletedEvent event = new ScanCompletedEvent(
                    scanId, TenantId.of(UUID.randomUUID()), List.of(), 0, 0, Instant.now(), Instant.now()
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> consumer.onScanCompleted(event));
        }
    }

    @Nested
    @DisplayName("Failed events")
    class FailedEvents {

        @Test
        @DisplayName("should mark scan as failed and store error")
        void onScanFailed_quandoScanExiste_atualizaStatusErroECompletedAt() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Scan scan = createRunningScan(scanId, tenantId);
            Instant failedAt = Instant.now();
            ScanFailedEvent event = new ScanFailedEvent(scanId, TenantId.of(tenantId), "timeout", "TIMEOUT", failedAt);

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            consumer.onScanFailed(event);

            ArgumentCaptor<Scan> captor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(captor.capture());
            assertEquals(ScanStatus.FAILED, captor.getValue().getStatus());
            assertEquals("timeout", captor.getValue().getErrorMessage());
            assertEquals(failedAt, captor.getValue().getCompletedAt());
        }

        @Test
        @DisplayName("should throw when failed event references missing scan")
        void onScanFailed_quandoScanNaoExiste_lancaIllegalArgumentException() {
            UUID scanId = UUID.randomUUID();
            ScanFailedEvent event = new ScanFailedEvent(
                    scanId, TenantId.of(UUID.randomUUID()), "timeout", "TIMEOUT", Instant.now()
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> consumer.onScanFailed(event));
        }

        @Test
        @DisplayName("should reject failed event from another tenant")
        void onScanFailed_quandoTenantDoEventoDiverge_lancaIllegalArgumentException() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            Scan scan = createRunningScan(scanId, tenantId);
            ScanFailedEvent event = new ScanFailedEvent(
                    scanId, TenantId.of(UUID.randomUUID()), "timeout", "TIMEOUT", Instant.now()
            );

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            assertThrows(IllegalArgumentException.class, () -> consumer.onScanFailed(event));
        }
    }
}
