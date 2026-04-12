package com.virtualrift.apiscanner.service;

import com.virtualrift.apiscanner.engine.ApiScanEngine;
import com.virtualrift.apiscanner.kafka.ApiScanEventPublisher;
import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
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
@DisplayName("ApiScanWorkerService Tests")
class ApiScanWorkerServiceTest {

    @Mock
    private ApiScanEngine engine;

    @Mock
    private ApiScanEventPublisher publisher;

    private ApiScanWorkerService service;

    @BeforeEach
    void setUp() {
        service = new ApiScanWorkerService(engine, publisher);
    }

    @Test
    @DisplayName("should scan API target and publish completed event")
    void process_quandoScanApiValido_publicaEventoCompleted() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://api.example.com/users", ScanType.API.name());
        when(engine.scan("https://api.example.com/users")).thenReturn(List.of(
                finding("Excessive Data Exposure - PASSWORD", Severity.HIGH, "API_SECURITY", "token=abcdefghijklmnopqrstuvwxyz")
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
    }

    @Test
    @DisplayName("should sort findings by severity")
    void process_quandoMultiplosFindings_ordenaPorSeveridade() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://api.example.com/users", ScanType.API.name());
        when(engine.scan("https://api.example.com/users")).thenReturn(List.of(
                finding("Missing Rate Limiting", Severity.MEDIUM, "API_SECURITY", "medium"),
                finding("SQL Injection", Severity.CRITICAL, "API_SECURITY", "critical")
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
    @DisplayName("should ignore non API scan requested events")
    void process_quandoScanNaoApi_ignoraEvento() {
        ScanRequestedEvent event = requestedEvent(UUID.randomUUID(), TenantId.generate(), "https://api.example.com", ScanType.WEB.name());

        service.process(event);

        verifyNoInteractions(engine);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should publish failed event for invalid target")
    void process_quandoTargetInvalido_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "http://localhost:8080", ScanType.API.name());
        when(engine.scan("http://localhost:8080")).thenThrow(new IllegalArgumentException("SSRF protection"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());
        verify(publisher, never()).publishCompleted(org.mockito.ArgumentMatchers.any());

        ScanFailedEvent failed = captor.getValue();
        assertEquals(scanId, failed.scanId());
        assertEquals(tenantId, failed.tenantId());
        assertEquals("API_INVALID_TARGET", failed.errorCode());
        assertTrue(failed.errorMessage().contains("SSRF"));
    }

    @Test
    @DisplayName("should publish failed event for processing error")
    void process_quandoErroRuntime_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://api.example.com", ScanType.API.name());
        when(engine.scan("https://api.example.com")).thenThrow(new IllegalStateException("scanner failed"));

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());

        assertEquals("API_PROCESSING_FAILED", captor.getValue().errorCode());
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
                "https://api.example.com/users",
                evidence,
                Instant.now()
        );
    }
}
