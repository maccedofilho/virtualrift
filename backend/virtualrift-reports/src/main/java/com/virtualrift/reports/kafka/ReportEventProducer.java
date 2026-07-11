package com.virtualrift.reports.kafka;

import com.virtualrift.common.events.ReportGeneratedEvent;
import com.virtualrift.common.model.TenantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.reports.model.OutboxEvent;
import com.virtualrift.reports.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReportEventProducer {

    private static final String REPORT_GENERATED_TOPIC = "report.generated";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ReportEventProducer(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishReportGenerated(UUID reportId, UUID tenantId, UUID scanId, Instant generatedAt) {
        ReportGeneratedEvent event = new ReportGeneratedEvent(
                reportId,
                new TenantId(tenantId),
                scanId,
                "JSON",
                "/api/v1/reports/" + reportId,
                generatedAt
        );

        try {
            outboxRepository.save(new OutboxEvent(
                    UUID.randomUUID(),
                    tenantId,
                    reportId,
                    REPORT_GENERATED_TOPIC,
                    reportId.toString(),
                    ReportGeneratedEvent.class.getName(),
                    objectMapper.writeValueAsString(event)
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize report event for the transactional outbox", exception);
        }
    }
}
