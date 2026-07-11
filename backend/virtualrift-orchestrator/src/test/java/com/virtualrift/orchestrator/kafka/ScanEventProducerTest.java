package com.virtualrift.orchestrator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.model.OutboxEvent;
import com.virtualrift.orchestrator.repository.OutboxEventRepository;
import com.virtualrift.orchestrator.service.OutboxPayloadCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanEventProducer Tests")
class ScanEventProducerTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private OutboxPayloadCipher payloadCipher;

    private ObjectMapper objectMapper;
    private ScanEventProducer producer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        producer = new ScanEventProducer(outboxRepository, objectMapper, payloadCipher);
    }

    @Test
    @DisplayName("should persist encrypted scan request in the transactional outbox")
    void publishScanRequested_quandoChamado_persistePayloadCriptografado() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> associatedDataCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(payloadCipher.encrypt(payloadCaptor.capture(), associatedDataCaptor.capture()))
                .thenReturn("encrypted-payload");

        producer.publishScanRequested(
                scanId,
                new TenantId(tenantId),
                "https://example.com",
                "WEB",
                3,
                300,
                Map.of("Authorization", "Bearer secret"),
                Map.of("session", "secret")
        );

        verify(outboxRepository).save(eventCaptor.capture());
        OutboxEvent stored = eventCaptor.getValue();
        assertEquals(tenantId, stored.getTenantId());
        assertEquals(scanId, stored.getAggregateId());
        assertEquals("scan.requested", stored.getTopic());
        assertEquals("encrypted-payload", stored.getPayloadCiphertext());
        assertEquals(stored.getId().toString(), associatedDataCaptor.getValue());

        ScanRequestedEvent payload = objectMapper.readValue(payloadCaptor.getValue(), ScanRequestedEvent.class);
        assertEquals(scanId, payload.scanId());
        assertEquals(tenantId, payload.tenantId().value());
        assertEquals("Bearer secret", payload.headers().get("Authorization"));
        assertEquals("secret", payload.cookies().get("session"));
    }
}
