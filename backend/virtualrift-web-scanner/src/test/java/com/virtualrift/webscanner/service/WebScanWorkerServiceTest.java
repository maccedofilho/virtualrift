package com.virtualrift.webscanner.service;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.webscanner.engine.WebScanEngine;
import com.virtualrift.webscanner.kafka.WebScanEventPublisher;
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
@DisplayName("WebScanWorkerService Tests")
class WebScanWorkerServiceTest {

    @Mock
    private WebScanEngine engine;

    @Mock
    private WebScanEventPublisher publisher;

    private WebScanWorkerService service;

    @BeforeEach
    void setUp() {
        service = new WebScanWorkerService(engine, publisher);
    }

    @Test
    @DisplayName("should scan WEB target and publish completed event")
    void process_quandoScanWebValido_publicaEventoCompleted() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://example.com", ScanType.WEB.name());
        when(engine.scan("https://example.com")).thenReturn(List.of(
                finding("Reflected XSS", Severity.HIGH, "XSS", "Payload: token=abcdefghijklmnopqrstuvwxyz")
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
        assertEquals(scanId, completed.findings().get(0).scanId());
        assertEquals(tenantId, completed.findings().get(0).tenantId());
        assertEquals(Severity.HIGH, completed.findings().get(0).severity());
        assertTrue(completed.findings().get(0).evidence().contains("token=****"));
        assertTrue(completed.completedAt().isAfter(completed.startedAt()) || completed.completedAt().equals(completed.startedAt()));
    }

    @Test
    @DisplayName("should sort findings by severity")
    void process_quandoMultiplosFindings_ordenaPorSeveridade() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://example.com", ScanType.WEB.name());
        when(engine.scan("https://example.com")).thenReturn(List.of(
                finding("Low finding", Severity.LOW, "INFO", "low"),
                finding("Critical finding", Severity.CRITICAL, "XSS", "critical")
        ));

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(2, completed.totalFindings());
        assertEquals(50, completed.riskScore());
        assertEquals(Severity.CRITICAL, completed.findings().get(0).severity());
    }

    @Test
    @DisplayName("should ignore non WEB scan requested events")
    void process_quandoScanNaoWeb_ignoraEvento() {
        ScanRequestedEvent event = requestedEvent(UUID.randomUUID(), TenantId.generate(), "https://example.com", ScanType.SAST.name());

        service.process(event);

        verifyNoInteractions(engine);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should publish failed event for invalid target")
    void process_quandoTargetInvalido_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "http://localhost:8080", ScanType.WEB.name());
        when(engine.scan("http://localhost:8080")).thenThrow(new IllegalArgumentException("SSRF protection"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());
        verify(publisher, never()).publishCompleted(org.mockito.ArgumentMatchers.any());

        ScanFailedEvent failed = captor.getValue();
        assertEquals(scanId, failed.scanId());
        assertEquals(tenantId, failed.tenantId());
        assertEquals("WEB_INVALID_TARGET", failed.errorCode());
        assertTrue(failed.errorMessage().contains("SSRF"));
    }

    @Test
    @DisplayName("should publish failed event for processing error")
    void process_quandoErroRuntime_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://example.com", ScanType.WEB.name());
        when(engine.scan("https://example.com")).thenThrow(new IllegalStateException("scanner failed"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());

        assertEquals("WEB_PROCESSING_FAILED", captor.getValue().errorCode());
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
                "https://example.com",
                evidence,
                Instant.now()
        );
    }
}
