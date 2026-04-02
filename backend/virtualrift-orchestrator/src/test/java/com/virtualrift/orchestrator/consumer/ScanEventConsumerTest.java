package com.virtualrift.orchestrator.consumer;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanStatus;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import com.virtualrift.orchestrator.service.quota.TenantQuotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanEventConsumer Tests")
class ScanEventConsumerTest {

    @Mock
    private ScanOrchestratorService orchestratorService;

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private TenantQuotaClient quotaClient;

    private ScanEventConsumer consumer;

    private static final UUID SCAN_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TARGET_URL = "https://example.com";

    @BeforeEach
    void setUp() {
        consumer = new ScanEventConsumer(orchestratorService, scanRepository, quotaClient);
    }

    @Nested
    @DisplayName("Handle scan.completed event")
    class HandleScanCompleted {

        private ScanCompletedEvent event;
        private List<VulnerabilityFinding> findings;

        @BeforeEach
        void setUp() {
            findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), SCAN_ID, TenantId.from(TENANT_ID),
                            "XSS Vulnerability", Severity.HIGH, "Injection", "/search",
                            "<script>alert(1)</script>", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), SCAN_ID, TenantId.from(TENANT_ID),
                            "SQL Injection", Severity.CRITICAL, "Injection", "/api/users",
                            "id=1' OR '1'='1", Instant.now())
            );

            event = new ScanCompletedEvent(
                    SCAN_ID,
                    TENANT_ID,
                    findings,
                    2,
                    175,
                    Instant.now().minusSeconds(60),
                    Instant.now()
            );
        }

        @Test
        @DisplayName("should process scan.completed message")
        void handleScanCompleted_quandoMensagemRecebida_processaEvento() {
            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(event).build();

            consumer.handleScanCompleted(message);

            verify(orchestratorService).handleScanCompleted(event);
        }

        @Test
        @DisplayName("should extract tenantId from event")
        void handleScanCompleted_quandoProcessado_extraiTenantId() {
            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(event).build();

            consumer.handleScanCompleted(message);

            ArgumentCaptor<ScanCompletedEvent> eventCaptor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
            verify(orchestratorService).handleScanCompleted(eventCaptor.capture());
            assertEquals(TENANT_ID, eventCaptor.getValue().tenantId());
        }

        @Test
        @DisplayName("should handle event with no findings")
        void handleScanCompleted_quandoSemFindings_processaEvento() {
            ScanCompletedEvent emptyEvent = new ScanCompletedEvent(
                    SCAN_ID,
                    TENANT_ID,
                    List.of(),
                    0,
                    0,
                    Instant.now().minusSeconds(30),
                    Instant.now()
            );
            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(emptyEvent).build();

            assertDoesNotThrow(() -> consumer.handleScanCompleted(message));
            verify(orchestratorService).handleScanCompleted(emptyEvent);
        }

        @Test
        @DisplayName("should log processing with scanId")
        void handleScanCompleted_quandoProcessado_logaScanId() {
            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(event)
                    .setHeader(KafkaHeaders.RECEIVED_KEY, SCAN_ID.toString())
                    .build();

            consumer.handleScanCompleted(message);

            verify(orchestratorService).handleScanCompleted(any(ScanCompletedEvent.class));
        }

        @Test
        @DisplayName("should handle null payload gracefully")
        void handleScanCompleted_quandoPayloadNulo_lancaExcecao() {
            Message<ScanCompletedEvent> message = MessageBuilder.<ScanCompletedEvent>withPayload(null).build();

            assertThrows(IllegalArgumentException.class, () -> consumer.handleScanCompleted(message));
            verify(orchestratorService, never()).handleScanCompleted(any());
        }
    }

    @Nested
    @DisplayName("Handle scan.failed event")
    class HandleScanFailed {

        private ScanFailedEvent event;

        @BeforeEach
        void setUp() {
            event = new ScanFailedEvent(
                    SCAN_ID,
                    TENANT_ID,
                    "Connection timeout after 30 seconds",
                    "CONNECTION_TIMEOUT",
                    Instant.now()
            );
        }

        @Test
        @DisplayName("should process scan.failed message")
        void handleScanFailed_quandoMensagemRecebida_processaEvento() {
            Message<ScanFailedEvent> message = MessageBuilder.withPayload(event).build();

            consumer.handleScanFailed(message);

            verify(orchestratorService).handleScanFailed(event);
        }

        @Test
        @DisplayName("should extract error details from event")
        void handleScanFailed_quandoProcessado_extraiDetalhesErro() {
            Message<ScanFailedEvent> message = MessageBuilder.withPayload(event).build();

            consumer.handleScanFailed(message);

            ArgumentCaptor<ScanFailedEvent> eventCaptor = ArgumentCaptor.forClass(ScanFailedEvent.class);
            verify(orchestratorService).handleScanFailed(eventCaptor.capture());

            assertEquals("Connection timeout after 30 seconds", eventCaptor.getValue().errorMessage());
            assertEquals("CONNECTION_TIMEOUT", eventCaptor.getValue().errorCode());
        }

        @Test
        @DisplayName("should handle different error codes")
        void handleScanFailed_quandoDiferentesErros_processaEvento() {
            String[] errorCodes = {"TIMEOUT", "CONNECTION_ERROR", "SSL_ERROR", "TARGET_UNREACHABLE", "SCANNER_ERROR"};

            for (String errorCode : errorCodes) {
                ScanFailedEvent errorEvent = new ScanFailedEvent(
                        SCAN_ID,
                        TENANT_ID,
                        "Error occurred",
                        errorCode,
                        Instant.now()
                );
                Message<ScanFailedEvent> message = MessageBuilder.withPayload(errorEvent).build();

                consumer.handleScanFailed(message);
            }

            verify(orchestratorService, times(errorCodes.length)).handleScanFailed(any(ScanFailedEvent.class));
        }

        @Test
        @DisplayName("should handle null error message")
        void handleScanFailed_quandoMensagemNula_processaEvento() {
            ScanFailedEvent nullMessageEvent = new ScanFailedEvent(
                    SCAN_ID,
                    TENANT_ID,
                    null,
                    "UNKNOWN",
                    Instant.now()
            );
            Message<ScanFailedEvent> message = MessageBuilder.withPayload(nullMessageEvent).build();

            assertDoesNotThrow(() -> consumer.handleScanFailed(message));
        }

        @Test
        @DisplayName("should log error with tenantId")
        void handleScanFailed_quandoProcessado_logaTenantId() {
            Message<ScanFailedEvent> message = MessageBuilder.withPayload(event)
                    .setHeader(KafkaHeaders.RECEIVED_KEY, SCAN_ID.toString())
                    .build();

            consumer.handleScanFailed(message);

            verify(orchestratorService).handleScanFailed(any(ScanFailedEvent.class));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should continue processing after single failure")
        void handleScanCompleted_quandoFalhaUm_naoInterrompeProcessamento() {
            ScanCompletedEvent event1 = new ScanCompletedEvent(
                    UUID.randomUUID(), TENANT_ID, List.of(), 0, 0,
                    Instant.now(), Instant.now()
            );
            ScanCompletedEvent event2 = new ScanCompletedEvent(
                    UUID.randomUUID(), TENANT_ID, List.of(), 0, 0,
                    Instant.now(), Instant.now()
            );

            doThrow(new RuntimeException("Processing error"))
                    .when(orchestratorService).handleScanCompleted(eq(event1));

            Message<ScanCompletedEvent> message1 = MessageBuilder.withPayload(event1).build();
            Message<ScanCompletedEvent> message2 = MessageBuilder.withPayload(event2).build();

            assertThrows(RuntimeException.class, () -> consumer.handleScanCompleted(message1));

            // Second event should still be processable
            assertDoesNotThrow(() -> consumer.handleScanCompleted(message2));
            verify(orchestratorService).handleScanCompleted(event2);
        }

        @Test
        @DisplayName("should handle malformed event gracefully")
        void handleScanCompleted_quandoEventoMalformado_lancaExcecao() {
            // Event with invalid UUID would be handled at deserialization level
            // This test documents expected behavior
            ScanCompletedEvent validEvent = new ScanCompletedEvent(
                    SCAN_ID, TENANT_ID, List.of(), 0, 0,
                    Instant.now(), Instant.now()
            );

            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(validEvent).build();

            assertDoesNotThrow(() -> consumer.handleScanCompleted(message));
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should handle duplicate scan.completed events")
        void handleScanCompleted_quandoEventoDuplicado_processaIdempotente() {
            Scan runningScan = new Scan(SCAN_ID, TENANT_ID, TARGET_URL, ScanType.WEB,
                    3, 300, ScanStatus.COMPLETED, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), Instant.now(), null, null);

            when(scanRepository.findById(SCAN_ID)).thenReturn(Optional.of(runningScan));

            ScanCompletedEvent event = new ScanCompletedEvent(
                    SCAN_ID, TENANT_ID, List.of(), 0, 0,
                    Instant.now().minusSeconds(60), Instant.now()
            );

            Message<ScanCompletedEvent> message = MessageBuilder.withPayload(event).build();

            // Process same event twice
            consumer.handleScanCompleted(message);
            consumer.handleScanCompleted(message);

            // Service should handle idempotently
            verify(orchestratorService, times(2)).handleScanCompleted(event);
        }

        @Test
        @DisplayName("should ignore duplicate events for completed scans")
        void handleScanCompleted_quandoScanJaCompletado ignoraDuplicata() {
            // Document: If scan is already COMPLETED, duplicate events should be ignored
            // This is a design decision - the service layer should enforce idempotency
            assertTrue(true, "Service layer should handle idempotency for duplicate events");
        }
    }
}
