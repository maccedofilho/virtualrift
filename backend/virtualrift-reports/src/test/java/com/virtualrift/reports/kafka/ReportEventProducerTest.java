package com.virtualrift.reports.kafka;

import com.virtualrift.common.events.ReportGeneratedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportEventProducer Tests")
class ReportEventProducerTest {

    @Mock
    private KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate;

    private ReportEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new ReportEventProducer(kafkaTemplate);
    }

    @Test
    @DisplayName("should publish report generated event")
    void publishReportGenerated_quandoChamado_publicaEvento() {
        UUID reportId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        Instant generatedAt = Instant.parse("2026-04-17T01:00:00Z");
        ArgumentCaptor<ReportGeneratedEvent> eventCaptor = ArgumentCaptor.forClass(ReportGeneratedEvent.class);

        when(kafkaTemplate.send(eq("report.generated"), eq(reportId.toString()), any(ReportGeneratedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        producer.publishReportGenerated(reportId, tenantId, scanId, generatedAt);

        verify(kafkaTemplate).send(eq("report.generated"), eq(reportId.toString()), eventCaptor.capture());
        ReportGeneratedEvent event = eventCaptor.getValue();
        assertEquals(reportId, event.reportId());
        assertEquals(tenantId, event.tenantId().value());
        assertEquals(scanId, event.scanId());
        assertEquals("JSON", event.format());
        assertEquals("/api/v1/reports/" + reportId, event.storageUrl());
        assertEquals(generatedAt, event.generatedAt());
    }

}
