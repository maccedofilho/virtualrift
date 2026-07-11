package com.virtualrift.reports.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.common.events.ReportGeneratedEvent;
import com.virtualrift.reports.config.OutboxProperties;
import com.virtualrift.reports.model.OutboxEvent;
import com.virtualrift.reports.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class ReportOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReportOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate;
    private final OutboxProperties properties;

    public ReportOutboxPublisher(OutboxEventRepository outboxRepository,
                                 ObjectMapper objectMapper,
                                 KafkaTemplate<String, ReportGeneratedEvent> kafkaTemplate,
                                 OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        Instant now = Instant.now();
        for (OutboxEvent event : outboxRepository.lockPending(now, properties.getBatchSize())) {
            try {
                ReportGeneratedEvent payload = deserialize(event);
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), payload)
                        .get(properties.getSendTimeoutSeconds(), TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                log.info("Published outbox event {} for report {}", event.getId(), event.getAggregateId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                event.markFailed(Instant.now(), exception);
                return;
            } catch (Exception exception) {
                event.markFailed(Instant.now(), exception);
                log.warn("Outbox publish failed for event {}; retry {} scheduled",
                        event.getId(), event.getAttempts());
                return;
            }
        }
    }

    private ReportGeneratedEvent deserialize(OutboxEvent event) throws JsonProcessingException {
        if (!ReportGeneratedEvent.class.getName().equals(event.getEventType())) {
            throw new IllegalStateException("Unsupported reports outbox event type: " + event.getEventType());
        }
        return objectMapper.readValue(event.getPayload(), ReportGeneratedEvent.class);
    }
}
