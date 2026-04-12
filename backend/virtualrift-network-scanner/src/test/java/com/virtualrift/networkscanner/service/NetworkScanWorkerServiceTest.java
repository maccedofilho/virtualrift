package com.virtualrift.networkscanner.service;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.networkscanner.engine.NetworkScanEngine;
import com.virtualrift.networkscanner.kafka.NetworkScanEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NetworkScanWorkerService Tests")
class NetworkScanWorkerServiceTest {

    @Mock
    private NetworkScanEngine engine;

    @Mock
    private NetworkScanEventPublisher publisher;

    private NetworkScanWorkerService service;

    @BeforeEach
    void setUp() {
        service = new NetworkScanWorkerService(engine, publisher);
    }

    @Test
    @DisplayName("should scan network target and publish completed event")
    void process_quandoScanNetworkValido_publicaEventoCompleted() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "example.com:443", ScanType.NETWORK.name());
        when(engine.scan("example.com:443")).thenReturn(List.of(
                finding("Weak TLS Protocol", Severity.HIGH, "TLS", "token=abcdefghijklmnopqrstuvwxyz")
        ));

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());
        verify(publisher, never()).publishFailed(org.mockito.ArgumentMatchers.any());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(scanId, completed.scanId());
        assertEquals(tenantId, completed.tenantId());
        assertEquals(1, completed.totalFindings());
        assertEquals(15, completed.riskScore());
        assertEquals(scanId, completed.findings().getFirst().scanId());
        assertEquals(tenantId, completed.findings().getFirst().tenantId());
        assertEquals(Severity.HIGH, completed.findings().getFirst().severity());
        assertTrue(completed.findings().getFirst().evidence().contains("token=****"));
    }

    @Test
    @DisplayName("should sort findings by severity")
    void process_quandoMultiplosFindings_ordenaPorSeveridade() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "example.com:443", ScanType.NETWORK.name());
        when(engine.scan("example.com:443")).thenReturn(List.of(
                finding("Missing HSTS Header", Severity.MEDIUM, "TLS", "medium"),
                finding("NULL Cipher Suite", Severity.CRITICAL, "TLS", "critical")
        ));

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(2, completed.totalFindings());
        assertEquals(50, completed.riskScore());
        assertEquals(Severity.CRITICAL, completed.findings().getFirst().severity());
    }

    @Test
    @DisplayName("should ignore non NETWORK scan requested events")
    void process_quandoScanNaoNetwork_ignoraEvento() {
        ScanRequestedEvent event = requestedEvent(UUID.randomUUID(), TenantId.generate(), "example.com:443", ScanType.WEB.name());

        service.process(event);

        verifyNoInteractions(engine);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should publish failed event for invalid target")
    void process_quandoTargetInvalido_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "localhost:443", ScanType.NETWORK.name());
        when(engine.scan("localhost:443")).thenThrow(new IllegalArgumentException("SSRF protection"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());
        verify(publisher, never()).publishCompleted(org.mockito.ArgumentMatchers.any());

        ScanFailedEvent failed = captor.getValue();
        assertEquals(scanId, failed.scanId());
        assertEquals(tenantId, failed.tenantId());
        assertEquals("NETWORK_INVALID_TARGET", failed.errorCode());
        assertTrue(failed.errorMessage().contains("SSRF"));
    }

    @Test
    @DisplayName("should publish failed event for processing error")
    void process_quandoErroRuntime_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "example.com:443", ScanType.NETWORK.name());
        when(engine.scan("example.com:443")).thenThrow(new IllegalStateException("scanner failed"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());

        assertEquals("NETWORK_PROCESSING_FAILED", captor.getValue().errorCode());
    }

    private ScanRequestedEvent requestedEvent(UUID scanId, TenantId tenantId, String target, String scanType) {
        return new ScanRequestedEvent(scanId, tenantId, target, scanType, 1, 300, Instant.now());
    }

    private VulnerabilityFinding finding(String title, Severity severity, String category, String evidence) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TenantId.generate(),
                title,
                severity,
                category,
                "example.com:443",
                evidence,
                Instant.now()
        );
    }
}
