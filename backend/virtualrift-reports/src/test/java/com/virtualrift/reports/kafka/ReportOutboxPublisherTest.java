package com.virtualrift.reports.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.events.ReportGeneratedEvent;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.reports.config.OutboxProperties;
import com.virtualrift.reports.model.OutboxEvent;
import com.virtualrift.reports.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
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
@DisplayName("ReportOutboxPublisher Tests")
class ReportOutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate;

    private ObjectMapper objectMapper;
    private ReportOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxProperties properties = new OutboxProperties();
        properties.setBatchSize(10);
        properties.setSendTimeoutSeconds(1);
        publisher = new ReportOutboxPublisher(outboxRepository, objectMapper, kafkaTemplate, properties);
    }

    @Test
    @DisplayName("should mark event as published only after Kafka confirms delivery")
    void publishPending_quandoKafkaConfirma_marcaPublicado() throws Exception {
        ReportGeneratedEvent payload = payload();
        OutboxEvent event = event(payload);
        when(outboxRepository.lockPending(any(Instant.class), eq(10))).thenReturn(List.of(event));
        when(kafkaTemplate.send("report.generated", event.getEventKey(), payload))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        assertNotNull(event.getPublishedAt());
        assertEquals(0, event.getAttempts());
        verify(kafkaTemplate).send("report.generated", event.getEventKey(), payload);
    }

    @Test
    @DisplayName("should retain event and schedule retry when Kafka fails")
    void publishPending_quandoKafkaFalha_reagendaEvento() throws Exception {
        OutboxEvent event = event(payload());
        OutboxEvent nextEvent = event(payload());
        when(outboxRepository.lockPending(any(Instant.class), eq(10))).thenReturn(List.of(event, nextEvent));
        when(kafkaTemplate.send(anyString(), anyString(), any(ReportGeneratedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        publisher.publishPending();

        assertNull(event.getPublishedAt());
        assertEquals(1, event.getAttempts());
        assertNotNull(event.getAvailableAt());
        assertTrue(event.getLastError().contains("Kafka unavailable"));
        assertEquals(0, nextEvent.getAttempts());
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any(ReportGeneratedEvent.class));
    }

    private ReportGeneratedEvent payload() {
        UUID reportId = UUID.randomUUID();
        return new ReportGeneratedEvent(
                reportId,
                new TenantId(UUID.randomUUID()),
                UUID.randomUUID(),
                "JSON",
                "/api/v1/reports/" + reportId,
                Instant.parse("2026-07-11T12:00:00Z")
        );
    }

    private OutboxEvent event(ReportGeneratedEvent payload) throws Exception {
        return new OutboxEvent(
                UUID.randomUUID(),
                payload.tenantId().value(),
                payload.reportId(),
                "report.generated",
                payload.reportId().toString(),
                ReportGeneratedEvent.class.getName(),
                objectMapper.writeValueAsString(payload)
        );
    }
}
