package com.virtualrift.reports.kafka;

import com.virtualrift.common.events.ReportGeneratedEvent;
import com.virtualrift.common.model.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReportEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ReportEventProducer.class);
    private static final String REPORT_GENERATED_TOPIC = "report.generated";

    private final KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate;

    public ReportEventProducer(KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
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
            kafkaTemplate.send(REPORT_GENERATED_TOPIC, reportId.toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Published report.generated event for reportId: {}", reportId);
                        } else {
                            log.error("Failed to publish report.generated event for reportId: {}", reportId, ex);
                        }
                    });
        } catch (RuntimeException ex) {
            log.error("Failed to enqueue report.generated event for reportId: {}", reportId, ex);
        }
    }
}
