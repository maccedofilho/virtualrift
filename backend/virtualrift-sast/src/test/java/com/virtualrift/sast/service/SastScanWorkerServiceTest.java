package com.virtualrift.sast.service;

import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.sast.config.SastProperties;
import com.virtualrift.sast.engine.SastAnalyzer;
import com.virtualrift.sast.kafka.SastScanEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("SastScanWorkerService Tests")
class SastScanWorkerServiceTest {

    @Mock
    private SastScanEventPublisher publisher;

    @TempDir
    private Path tempDir;

    private SastScanWorkerService service;

    @BeforeEach
    void setUp() {
        SastProperties properties = new SastProperties();
        properties.setWorkspaceRoot(tempDir.resolve("workspace"));
        SastTargetResolver targetResolver = new SastTargetResolver(properties, new TestGitClient());
        service = new SastScanWorkerService(new SastAnalyzer(), targetResolver, publisher);
    }

    @Test
    @DisplayName("should analyze SAST target and publish completed event")
    void process_quandoScanSastValido_publicaEventoCompleted() throws IOException {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        Path source = tempDir.resolve("Example.java");
        Files.writeString(source, """
                class Example {
                    void run() {
                        String password = "SuperSecret123";
                    }
                }
                """);
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, source.toString(), ScanType.SAST.name());

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());
        verify(publisher, never()).publishFailed(org.mockito.ArgumentMatchers.any());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(scanId, completed.scanId());
        assertEquals(tenantId, completed.tenantId());
        assertEquals(1, completed.totalFindings());
        assertEquals(50, completed.riskScore());
        assertEquals(scanId, completed.findings().get(0).scanId());
        assertEquals(tenantId, completed.findings().get(0).tenantId());
        assertEquals(Severity.CRITICAL, completed.findings().get(0).severity());
        assertTrue(completed.findings().get(0).evidence().contains("password=****"));
        assertTrue(completed.completedAt().isAfter(completed.startedAt()) || completed.completedAt().equals(completed.startedAt()));
    }

    @Test
    @DisplayName("should clone repository target and publish completed event")
    void process_quandoTargetRepositorio_publicaEventoCompleted() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, "https://github.com/acme/vulnerable.git", ScanType.SAST.name());

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(scanId, completed.scanId());
        assertEquals(tenantId, completed.tenantId());
        assertEquals(1, completed.totalFindings());
        assertTrue(completed.findings().get(0).location().contains("RepoExample.java"));
    }

    @Test
    @DisplayName("should ignore non SAST scan requested events")
    void process_quandoScanNaoSast_ignoraEvento() {
        ScanRequestedEvent event = requestedEvent(UUID.randomUUID(), TenantId.generate(), "/missing", ScanType.WEB.name());

        service.process(event);

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("should publish failed event when SAST target does not exist")
    void process_quandoTargetNaoExiste_publicaEventoFailed() {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, tempDir.resolve("missing").toString(), ScanType.SAST.name());

        service.process(event);

        ArgumentCaptor<ScanFailedEvent> captor = ArgumentCaptor.forClass(ScanFailedEvent.class);
        verify(publisher).publishFailed(captor.capture());
        verify(publisher, never()).publishCompleted(org.mockito.ArgumentMatchers.any());

        ScanFailedEvent failed = captor.getValue();
        assertEquals(scanId, failed.scanId());
        assertEquals(tenantId, failed.tenantId());
        assertEquals("SAST_INVALID_TARGET", failed.errorCode());
        assertTrue(failed.errorMessage().contains("does not exist"));
    }

    @Test
    @DisplayName("should publish zero findings when SAST target has safe code")
    void process_quandoCodigoSeguro_publicaCompletedSemFindings() throws IOException {
        UUID scanId = UUID.randomUUID();
        TenantId tenantId = TenantId.generate();
        Path source = tempDir.resolve("Safe.java");
        Files.writeString(source, """
                class Safe {
                    String normalize(String input) {
                        return input == null ? "" : input.trim();
                    }
                }
                """);
        ScanRequestedEvent event = requestedEvent(scanId, tenantId, tempDir.toString(), ScanType.SAST.name());

        service.process(event);

        ArgumentCaptor<ScanCompletedEvent> captor = ArgumentCaptor.forClass(ScanCompletedEvent.class);
        verify(publisher).publishCompleted(captor.capture());

        ScanCompletedEvent completed = captor.getValue();
        assertEquals(0, completed.totalFindings());
        assertEquals(0, completed.riskScore());
        assertTrue(completed.findings().isEmpty());
    }

    private ScanRequestedEvent requestedEvent(UUID scanId, TenantId tenantId, String target, String scanType) {
        return new ScanRequestedEvent(scanId, tenantId, target, scanType, 1, 300, Instant.now());
    }

    private static class TestGitClient implements GitClient {

        @Override
        public void cloneRepository(URI repositoryUri, Path destination, Duration timeout) {
            try {
                Files.createDirectories(destination);
                Files.writeString(destination.resolve("RepoExample.java"), """
                        class RepoExample {
                            void run() {
                                String password = "SuperSecret123";
                            }
                        }
                        """);
            } catch (IOException e) {
                throw new IllegalArgumentException("test clone failed", e);
            }
        }
    }
}
