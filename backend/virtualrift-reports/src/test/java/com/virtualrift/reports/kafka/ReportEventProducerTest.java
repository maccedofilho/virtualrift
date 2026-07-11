package com.virtualrift.reports.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.events.ReportGeneratedEvent;
import com.virtualrift.reports.model.OutboxEvent;
import com.virtualrift.reports.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportEventProducer Tests")
class ReportEventProducerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    private ReportEventProducer producer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new ReportEventProducer(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("should persist report generated event in the transactional outbox")
    void publishReportGenerated_quandoChamado_persisteOutbox() throws Exception {
        UUID reportId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-04-17T01:00:00Z");
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

        producer.publishReportGenerated(reportId, tenantId, scanId, generatedAt);

        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent stored = eventCaptor.getValue();
        assertEquals(tenantId, stored.getTenantId());
        assertEquals(reportId, stored.getAggregateId());
        assertEquals("report.generated", stored.getTopic());
        assertEquals(reportId.toString(), stored.getEventKey());

        ReportGeneratedEvent event = objectMapper.readValue(stored.getPayload(), ReportGeneratedEvent.class);
        assertEquals(reportId, event.reportId());
        assertEquals(tenantId, event.tenantId().value());
        assertEquals(scanId, event.scanId());
        assertEquals("JSON", event.format());
        assertEquals("/api/v1/reports/" + reportId, event.storageUrl());
        assertEquals(generatedAt, event.generatedAt());
    }
}
