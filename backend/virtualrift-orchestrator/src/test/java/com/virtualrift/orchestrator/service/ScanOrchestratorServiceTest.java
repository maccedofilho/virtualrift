package com.virtualrift.orchestrator.service;

import com.virtualrift.common.events.ScanRequestedEvent;
import com.virtualrift.common.events.ScanCompletedEvent;
import com.virtualrift.common.events.ScanFailedEvent;
import com.virtualrift.common.exception.SecurityException;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.orchestrator.dto.ScanRequest;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.exception.QuotaExceededException;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.exception.InvalidScanStatusTransitionException;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.repository.ScanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanOrchestratorService Tests")
class ScanOrchestratorServiceTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private TenantQuotaClient quotaClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ReportGenerationService reportGenerationService;

    private ScanOrchestratorService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String TARGET_URL = "https://example.com";
    private static final ScanType SCAN_TYPE = ScanType.WEB;

    @BeforeEach
    void setUp() {
        service = new ScanOrchestratorService(scanRepository, quotaClient, kafkaTemplate, reportGenerationService);
    }

    @Nested
    @DisplayName("Request scan")
    class RequestScan {

        @Test
        @DisplayName("should create scan and publish event when request is valid")
        void requestScan_quandoValido_criaScanEPublicaEvento() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, 3, 300);
            when(quotaClient.canStartScan(TENANT_ID)).thenReturn(true);
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanResponse response = service.requestScan(request, TENANT_ID);

            assertNotNull(response.scanId());
            assertEquals(ScanStatus.PENDING, response.status());
            verify(scanRepository).save(any(Scan.class));

            ArgumentCaptor<ScanRequestedEvent> eventCaptor = ArgumentCaptor.forClass(ScanRequestedEvent.class);
            verify(kafkaTemplate).send(eq("scan.requested"), eventCaptor.capture());

            ScanRequestedEvent event = eventCaptor.getValue();
            assertEquals(TARGET_URL, event.target());
            assertEquals(SCAN_TYPE.name(), event.scanType());
            assertEquals(3, event.depth());
            assertEquals(300, event.timeout());
        }

        @Test
        @DisplayName("should throw QuotaExceededException when daily limit exceeded")
        void requestScan_quandoQuotaDiariaExcedida_lancaExcecao() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, null, null);
            when(quotaClient.canStartScan(TENANT_ID)).thenReturn(false);

            assertThrows(QuotaExceededException.class, () -> service.requestScan(request, TENANT_ID));
            verify(scanRepository, never()).save(any());
            verify(kafkaTemplate, never()).send(anyString(), any());
        }

        @Test
        @DisplayName("should increment concurrent count after creating scan")
        void requestScan_quandoCriado_incrementaContadorConcorrente() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, null, null);
            when(quotaClient.canStartScan(TENANT_ID)).thenReturn(true);
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.requestScan(request, TENANT_ID);

            verify(quotaClient).incrementScanCount(TENANT_ID);
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is AWS metadata endpoint (SSRF protection)")
        void requestScan_quandoTargetAwsMetadata_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://169.254.169.254/latest/api/token", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
            verify(quotaClient, never()).canStartScan(any());
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is localhost (SSRF protection)")
        void requestScan_quandoTargetLocalhost_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://localhost:8080", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is internal IP (SSRF protection)")
        void requestScan_quandoTargetIpInterno_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://192.168.1.1", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is 127.0.0.1 (SSRF protection)")
        void requestScan_quandoTarget127_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://127.0.0.1:8080", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is 0.0.0.0 (SSRF protection)")
        void requestScan_quandoTarget0_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://0.0.0.0:8080", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is 10.0.0.0/8 (private network)")
        void requestScan_quandoTargetPrivate10_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://10.0.0.1:8080", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when target is 172.16.0.0/12 (private network)")
        void requestScan_quandoTargetPrivate172_lancaSecurityException() {
            ScanRequest request = new ScanRequest("http://172.16.0.1:8080", SCAN_TYPE, null, null);

            assertThrows(SecurityException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should validate scan type is allowed for tenant plan")
        void requestScan_quandoTipoNaoPermitido_lancaExcecao() {
            ScanRequest request = new ScanRequest(TARGET_URL, ScanType.SAST, null, null);
            when(quotaClient.canStartScan(TENANT_ID)).thenReturn(true);
            when(quotaClient.getAllowedScanTypes(TENANT_ID)).thenReturn(Set.of(ScanType.WEB, ScanType.API));

            assertThrows(IllegalArgumentException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("should reject invalid URL format")
        void requestScan_quandoUrlInvalida_lancaExcecao() {
            ScanRequest request = new ScanRequest("not-a-valid-url", SCAN_TYPE, null, null);

            assertThrows(IllegalArgumentException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("should reject blank target URL")
        void requestScan_quandoTargetVazio_lancaExcecao(String invalidTarget) {
            assertThrows(IllegalArgumentException.class, () ->
                    new ScanRequest(invalidTarget, SCAN_TYPE, null, null));
        }

        @Test
        @DisplayName("should reject negative depth")
        void requestScan_quandoDepthNegativo_lancaExcecao() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, -1, null);

            assertThrows(IllegalArgumentException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("should reject negative timeout")
        void requestScan_quandoTimeoutNegativo_lancaExcecao() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, null, -1);

            assertThrows(IllegalArgumentException.class, () -> service.requestScan(request, TENANT_ID));
        }

        @Test
        @DisplayName("should reject timeout above maximum")
        void requestScan_quandoTimeoutAcimaDoMaximo_lancaExcecao() {
            ScanRequest request = new ScanRequest(TARGET_URL, SCAN_TYPE, null, 3601);

            assertThrows(IllegalArgumentException.class, () -> service.requestScan(request, TENANT_ID));
        }
    }

    @Nested
    @DisplayName("Update scan status")
    class UpdateScanStatus {

        private UUID scanId;
        private Scan pendingScan;
        private Scan runningScan;
        private Scan completedScan;

        @BeforeEach
        void setUp() {
            scanId = UUID.randomUUID();
            pendingScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.PENDING, Instant.now(),
                    null, null, null, null);
            runningScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);
            completedScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.COMPLETED, Instant.now().minusSeconds(120),
                    Instant.now().minusSeconds(90), Instant.now(), null, null);
        }

        @Test
        @DisplayName("should update status when transition is valid")
        void updateStatus_quandoTransicaoValida_atualizaStatus() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(pendingScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(pendingScan);

            service.updateStatus(scanId, TENANT_ID, ScanStatus.RUNNING);

            ArgumentCaptor<Scan> scanCaptor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(scanCaptor.capture());
            assertEquals(ScanStatus.RUNNING, scanCaptor.getValue().status());
        }

        @Test
        @DisplayName("CRITICAL: should throw when transition is invalid")
        void updateStatus_quandoTransicaoInvalida_lancaExcecao() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(completedScan));

            assertThrows(InvalidScanStatusTransitionException.class,
                    () -> service.updateStatus(scanId, TENANT_ID, ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("should throw when scan does not exist")
        void updateStatus_quandoScanNaoExiste_lancaExcecao() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class,
                    () -> service.updateStatus(scanId, TENANT_ID, ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("CRITICAL: should throw when tenant does not own scan")
        void updateStatus_quandoTenantDiferente_lancaExcecao() {
            UUID otherTenantId = UUID.randomUUID();
            when(scanRepository.findByIdAndTenantId(scanId, otherTenantId)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class,
                    () -> service.updateStatus(scanId, otherTenantId, ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("CRITICAL: should throw for INVALID transition from COMPLETED")
        void updateStatus_quandoCompletedParaRunning_lancaExcecao() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(completedScan));

            assertThrows(InvalidScanStatusTransitionException.class,
                    () -> service.updateStatus(scanId, TENANT_ID, ScanStatus.RUNNING));
        }

        @Test
        @DisplayName("CRITICAL: should throw for INVALID transition from FAILED")
        void updateStatus_quandoFailedParaRunning_lancaExcecao() {
            Scan failedScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.FAILED, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, "Error", null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(failedScan));

            assertThrows(InvalidScanStatusTransitionException.class,
                    () -> service.updateStatus(scanId, TENANT_ID, ScanStatus.RUNNING));
        }
    }

    @Nested
    @DisplayName("Get scan")
    class GetScan {

        private UUID scanId;
        private Scan scan;

        @BeforeEach
        void setUp() {
            scanId = UUID.randomUUID();
            scan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);
        }

        @Test
        @DisplayName("should return scan when exists")
        void getScan_quandoExiste_retornaScan() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(scan));

            Scan result = service.getScan(scanId, TENANT_ID);

            assertNotNull(result);
            assertEquals(scanId, result.id());
            assertEquals(TENANT_ID, result.tenantId());
        }

        @Test
        @DisplayName("should throw when scan does not exist")
        void getScan_quandoNaoExiste_lancaExcecao() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class, () -> service.getScan(scanId, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when accessing other tenant scan")
        void getScan_quandoScanOutroTenant_lancaExcecao() {
            UUID otherTenantId = UUID.randomUUID();
            when(scanRepository.findByIdAndTenantId(scanId, otherTenantId)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class, () -> service.getScan(scanId, otherTenantId));
        }
    }

    @Nested
    @DisplayName("Handle scan completion event")
    class HandleScanCompletion {

        private UUID scanId;
        private Scan runningScan;
        private List<VulnerabilityFinding> findings;

        @BeforeEach
        void setUp() {
            scanId = UUID.randomUUID();
            runningScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);

            findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, TenantId.from(TENANT_ID),
                            "XSS in search", Severity.HIGH, "Injection", "/search",
                            "<script>alert(1)</script>", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, TenantId.from(TENANT_ID),
                            "SQL Injection", Severity.CRITICAL, "Injection", "/api/users",
                            "id=1' OR '1'='1", Instant.now())
            );
        }

        @Test
        @DisplayName("should update status to COMPLETED and save findings")
        void handleScanCompleted_quandoRecebido_atualizaStatusESalvaFindings() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            ScanCompletedEvent event = new ScanCompletedEvent(scanId, TENANT_ID, findings,
                    2, 175, Instant.now().minusSeconds(60), Instant.now());

            service.handleScanCompleted(event);

            ArgumentCaptor<Scan> scanCaptor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(scanCaptor.capture());
            assertEquals(ScanStatus.COMPLETED, scanCaptor.getValue().status());
            assertEquals(2, scanCaptor.getValue().findings().size());
        }

        @Test
        @DisplayName("should decrement concurrent count")
        void handleScanCompleted_quandoRecebido_decrementaContadorConcorrente() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            ScanCompletedEvent event = new ScanCompletedEvent(scanId, TENANT_ID, findings,
                    0, 0, Instant.now(), Instant.now());

            service.handleScanCompleted(event);

            verify(quotaClient).decrementConcurrentCount(TENANT_ID);
        }

        @Test
        @DisplayName("should trigger report generation for enterprise tenants")
        void handleScanCompleted_quandoTenantEnterprise_geraRelatorio() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);
            when(quotaClient.isEnterprisePlan(TENANT_ID)).thenReturn(true);

            ScanCompletedEvent event = new ScanCompletedEvent(scanId, TENANT_ID, findings,
                    2, 175, Instant.now().minusSeconds(60), Instant.now());

            service.handleScanCompleted(event);

            verify(reportGenerationService).generateReport(scanId, TENANT_ID);
        }

        @Test
        @DisplayName("should not trigger report generation for non-enterprise tenants")
        void handleScanCompleted_quandoTenantNaoEnterprise_naoGeraRelatorio() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);
            when(quotaClient.isEnterprisePlan(TENANT_ID)).thenReturn(false);

            ScanCompletedEvent event = new ScanCompletedEvent(scanId, TENANT_ID, findings,
                    2, 175, Instant.now().minusSeconds(60), Instant.now());

            service.handleScanCompleted(event);

            verify(reportGenerationService, never()).generateReport(any(), any());
        }
    }

    @Nested
    @DisplayName("Handle scan failure event")
    class HandleScanFailure {

        private UUID scanId;
        private Scan runningScan;

        @BeforeEach
        void setUp() {
            scanId = UUID.randomUUID();
            runningScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);
        }

        @Test
        @DisplayName("should update status to FAILED and save error message")
        void handleScanFailed_quandoRecebido_atualizaStatusESalvaErro() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            String errorMessage = "Target unreachable after 30 seconds";
            ScanFailedEvent event = new ScanFailedEvent(scanId, TENANT_ID,
                    errorMessage, "CONNECTION_ERROR", Instant.now());

            service.handleScanFailed(event);

            ArgumentCaptor<Scan> scanCaptor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(scanCaptor.capture());
            assertEquals(ScanStatus.FAILED, scanCaptor.getValue().status());
            assertEquals(errorMessage, scanCaptor.getValue().errorMessage());
        }

        @Test
        @DisplayName("should decrement concurrent count")
        void handleScanFailed_quandoRecebido_decrementaContadorConcorrente() {
            when(scanRepository.findById(scanId)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            ScanFailedEvent event = new ScanFailedEvent(scanId, TENANT_ID,
                    "Error", "ERROR", Instant.now());

            service.handleScanFailed(event);

            verify(quotaClient).decrementConcurrentCount(TENANT_ID);
        }
    }

    @Nested
    @DisplayName("Cancel scan")
    class CancelScan {

        private UUID scanId;

        @Test
        @DisplayName("should cancel RUNNING scan and publish cancel event")
        void cancelScan_quandoRunning_cancelaScanEPublicaEvento() {
            Scan runningScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            service.cancelScan(scanId, TENANT_ID);

            ArgumentCaptor<Scan> scanCaptor = ArgumentCaptor.forClass(Scan.class);
            verify(scanRepository).save(scanCaptor.capture());
            assertEquals(ScanStatus.CANCELLED, scanCaptor.getValue().status());
            verify(kafkaTemplate).send(eq("scan.cancel"), any());
        }

        @Test
        @DisplayName("CRITICAL: should throw when scan is COMPLETED")
        void cancelScan_quandoCompleted_lancaExcecao() {
            Scan completedScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.COMPLETED, Instant.now().minusSeconds(120),
                    Instant.now().minusSeconds(60), Instant.now(), null, null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(completedScan));

            assertThrows(InvalidScanStatusTransitionException.class, () -> service.cancelScan(scanId, TENANT_ID));
            verify(scanRepository, never()).save(any());
        }

        @Test
        @DisplayName("CRITICAL: should throw when scan is already CANCELLED")
        void cancelScan_quandoJaCancelado_lancaExcecao() {
            Scan cancelledScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.CANCELLED, Instant.now().minusSeconds(30),
                    null, null, null, null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(cancelledScan));

            assertThrows(InvalidScanStatusTransitionException.class, () -> service.cancelScan(scanId, TENANT_ID));
        }

        @Test
        @DisplayName("CRITICAL: should throw when scan is FAILED")
        void cancelScan_quandoFailed_lancaExcecao() {
            Scan failedScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.FAILED, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, "Error", null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(failedScan));

            assertThrows(InvalidScanStatusTransitionException.class, () -> service.cancelScan(scanId, TENANT_ID));
        }

        @Test
        @DisplayName("should decrement concurrent count when cancelling RUNNING scan")
        void cancelScan_quandoRunning_decrementaContador() {
            Scan runningScan = new Scan(scanId, TENANT_ID, TARGET_URL, SCAN_TYPE,
                    3, 300, ScanStatus.RUNNING, Instant.now().minusSeconds(60),
                    Instant.now().minusSeconds(30), null, null, null);

            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.of(runningScan));
            when(scanRepository.save(any(Scan.class))).thenReturn(runningScan);

            service.cancelScan(scanId, TENANT_ID);

            verify(quotaClient).decrementConcurrentCount(TENANT_ID);
        }

        @Test
        @DisplayName("should throw when scan does not exist")
        void cancelScan_quandoScanNaoExiste_lancaExcecao() {
            when(scanRepository.findByIdAndTenantId(scanId, TENANT_ID)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class, () -> service.cancelScan(scanId, TENANT_ID));
        }
    }

    @Nested
    @DisplayName("List scans by tenant")
    class ListScans {

        @Test
        @DisplayName("should return tenant scans")
        void listScans_quandoTenantTemScans_retornaLista() {
            List<Scan> scans = List.of(
                    new Scan(UUID.randomUUID(), TENANT_ID, TARGET_URL, SCAN_TYPE,
                            3, 300, ScanStatus.COMPLETED, Instant.now().minusSeconds(120),
                            Instant.now().minusSeconds(90), Instant.now(), null, null),
                    new Scan(UUID.randomUUID(), TENANT_ID, "https://other.com", ScanType.API,
                            5, 600, ScanStatus.RUNNING, Instant.now().minusSeconds(30),
                            Instant.now(), null, null, null)
            );

            when(scanRepository.findByTenantId(eq(TENANT_ID), any())).thenReturn(scans);

            List<Scan> result = service.listScans(TENANT_ID, 0, 20);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("should apply pagination correctly")
        void listScans_quandoPaginacao_respeitaLimites() {
            when(scanRepository.findByTenantId(eq(TENANT_ID), any())).thenReturn(List.of());

            service.listScans(TENANT_ID, 2, 50);

            var pageableCaptor = ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(scanRepository).findByTenantId(eq(TENANT_ID), pageableCaptor.capture());

            var captured = pageableCaptor.getValue();
            assertEquals(2, captured.getPageNumber());
            assertEquals(50, captured.getPageSize());
        }
    }
}
