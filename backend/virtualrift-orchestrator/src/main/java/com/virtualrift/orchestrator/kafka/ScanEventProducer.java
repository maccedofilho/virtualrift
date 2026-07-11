package com.virtualrift.orchestrator.kafka;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.TenantId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.orchestrator.model.OutboxEvent;
import com.virtualrift.orchestrator.repository.OutboxEventRepository;
import com.virtualrift.orchestrator.service.OutboxPayloadCipher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class ScanEventProducer {

    private static final String SCAN_REQUESTED_TOPIC = "scan.requested";

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final OutboxPayloadCipher payloadCipher;

    public ScanEventProducer(OutboxEventRepository outboxRepository,
                             ObjectMapper objectMapper,
                             OutboxPayloadCipher payloadCipher) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.payloadCipher = payloadCipher;
    }

    public void publishScanRequested(UUID scanId, TenantId tenantId, String target, String scanType,
                                     Integer depth, Integer timeout,
                                     Map<String, String> headers,
                                     Map<String, String> cookies) {
        ScanRequestedEvent event = new ScanRequestedEvent(
                scanId,
                tenantId,
                target,
                scanType,
                depth,
                timeout,
                headers,
                cookies,
                Instant.now()
        );

        UUID outboxId = UUID.randomUUID();
        try {
            String payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(new OutboxEvent(
                    outboxId,
                    tenantId.value(),
                    scanId,
                    SCAN_REQUESTED_TOPIC,
                    scanId.toString(),
                    ScanRequestedEvent.class.getName(),
                    payloadCipher.encrypt(payload, outboxId.toString())
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize scan event for the transactional outbox", exception);
        }
    }
}
