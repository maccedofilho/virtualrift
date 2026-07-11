package com.virtualrift.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.config.OutboxProperties;
import com.virtualrift.orchestrator.model.OutboxEvent;
import com.virtualrift.orchestrator.repository.OutboxEventRepository;
import com.virtualrift.orchestrator.service.OutboxPayloadCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanOutboxPublisher Tests")
class ScanOutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OutboxPayloadCipher payloadCipher;

    @Mock
    private KafkaTemplate<String, ScanRequestedEvent> kafkaTemplate;

    private ObjectMapper objectMapper;
    private OutboxProperties properties;
    private ScanOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        properties = new OutboxProperties();
        properties.getPublisher().setBatchSize(10);
        properties.getPublisher().setSendTimeoutSeconds(1);
        publisher = new ScanOutboxPublisher(
                outboxRepository,
                payloadCipher,
                objectMapper,
                kafkaTemplate,
                properties
        );
    }

    @Test
    @DisplayName("should mark event as published only after Kafka confirms delivery")
    void publishPending_quandoKafkaConfirma_marcaPublicado() throws Exception {
        OutboxEvent event = event();
        ScanRequestedEvent payload = payload(event);
        when(outboxRepository.lockPending(any(Instant.class), eq(10))).thenReturn(List.of(event));
        when(payloadCipher.decrypt("encrypted-payload", event.getId().toString()))
                .thenReturn(objectMapper.writeValueAsString(payload));
        when(kafkaTemplate.send("scan.requested", event.getEventKey(), payload))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertNotNull(event.getPublishedAt());
        assertEquals(0, event.getAttempts());
        verify(kafkaTemplate).send("scan.requested", event.getEventKey(), payload);
    }

    @Test
    @DisplayName("should retain event and schedule retry when Kafka fails")
    void publishPending_quandoKafkaFalha_reagendaEvento() throws Exception {
        OutboxEvent event = event();
        ScanRequestedEvent payload = payload(event);
        OutboxEvent nextEvent = event();
        when(outboxRepository.lockPending(any(Instant.class), eq(10))).thenReturn(List.of(event, nextEvent));
        when(payloadCipher.decrypt("encrypted-payload", event.getId().toString()))
                .thenReturn(objectMapper.writeValueAsString(payload));
        when(kafkaTemplate.send(anyString(), anyString(), any(ScanRequestedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPending();

        assertNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertNotNull(event.getAvailableAt());
        assertTrue(event.getLastError().contains("Kafka unavailable"));
        assertEquals(0, nextEvent.getAttempts());
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(ScanRequestedEvent.class));
    }

    private OutboxEvent event() {
        UUID scanId = UUID.randomUUID();
        return new OutboxEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                scanId,
                "scan.requested",
                scanId.toString(),
                ScanRequestedEvent.class.getName(),
                "encrypted-payload"
        );
    }

    private ScanRequestedEvent payload(OutboxEvent event) {
        return new ScanRequestedEvent(
                event.getAggregateId(),
                new TenantId(event.getTenantId()),
                "https://example.com",
                "WEB",
                3,
                300,
                Map.of(),
                Map.of(),
                Instant.parse("2026-07-11T12:00:00Z")
        );
    }
}
