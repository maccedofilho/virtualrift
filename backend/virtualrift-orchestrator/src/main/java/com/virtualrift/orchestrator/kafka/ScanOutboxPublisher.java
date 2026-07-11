package com.virtualrift.orchestrator.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.orchestrator.config.OutboxProperties;
import com.virtualrift.orchestrator.model.OutboxEvent;
import com.virtualrift.orchestrator.repository.OutboxEventRepository;
import com.virtualrift.orchestrator.service.OutboxPayloadCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class ScanOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScanOutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final OutboxPayloadCipher payloadCipher;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, ScanRequestedEvent> kafkaTemplate;
    private final OutboxProperties properties;

    public ScanOutboxPublisher(OutboxEventRepository outboxRepository,
                               OutboxPayloadCipher payloadCipher,
                               ObjectMapper objectMapper,
                               KafkaTemplate<String, ScanRequestedEvent> kafkaTemplate,
                               OutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.payloadCipher = payloadCipher;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        Instant now = Instant.now();
        for (OutboxEvent event : outboxRepository.lockPending(now, properties.getPublisher().getBatchSize())) {
            try {
                ScanRequestedEvent payload = deserialize(event);
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), payload)
                        .get(properties.getPublisher().getSendTimeoutSeconds(), TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                log.info("Published outbox event {} for scan {}", event.getId(), event.getAggregateId());
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

    private ScanRequestedEvent deserialize(OutboxEvent event) throws JsonProcessingException {
        if (!ScanRequestedEvent.class.getName().equals(event.getEventType())) {
            throw new IllegalStateException("Unsupported orchestrator outbox event type: " + event.getEventType());
        }
        String payload = payloadCipher.decrypt(event.getPayloadCiphertext(), event.getId().toString());
        return objectMapper.readValue(payload, ScanRequestedEvent.class);
    }
}
