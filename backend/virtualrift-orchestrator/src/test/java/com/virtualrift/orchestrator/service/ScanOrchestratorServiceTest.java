package com.virtualrift.orchestrator.service;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.exception.ScanQuotaExceededException;
import com.virtualrift.orchestrator.kafka.ScanEventProducer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.tenant.client.TenantClient;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantQuota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanOrchestratorService Tests")
class ScanOrchestratorServiceTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanEventProducer eventProducer;

    @Mock
    private TenantClient tenantClient;

    private ScanOrchestratorService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TARGET_URL = "https://example.com";

    @BeforeEach
    void setUp() {
        service = new ScanOrchestratorService(scanRepository, eventProducer, tenantClient);
    }

    private TenantQuota createQuota(int maxScansPerDay, int maxConcurrentScans) {
        return new TenantQuota(TENANT_ID, maxScansPerDay, maxConcurrentScans, 25, 90, true);
    }

    private Scan createScan(UUID scanId, UUID tenantId, UUID userId, ScanStatus status) {
        return new Scan(scanId, tenantId, userId, TARGET_URL, ScanType.WEB, 3, 300, status);
    }

    @Nested
    @DisplayName("Create scan")
    class CreateScanFlow {

        @Test
        @DisplayName("should create scan and publish event when request is valid")
        void createScan_quandoValido_criaScanEPublicaEvento() {
            CreateScanRequest request = new CreateScanRequest(TARGET_URL, ScanType.WEB, 3, 300);

            when(tenantClient.getQuota(TENANT_ID)).thenReturn(createQuota(100, 10));
            when(tenantClient.getPlan(TENANT_ID)).thenReturn(Plan.STARTER);
            when(scanRepository.countByTenantIdSince(eq(TENANT_ID), any())).thenReturn(0L);
            when(scanRepository.countByTenantIdAndStatus(TENANT_ID, ScanStatus.RUNNING)).thenReturn(0L);
            when(scanRepository.save(any(Scan.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ScanResponse response = service.createScan(request, TENANT_ID, USER_ID);

            assertNotNull(response.id());
            assertEquals(TENANT_ID, response.tenantId());
            assertEquals(USER_ID, response.userId());
            assertEquals(ScanStatus.PENDING, response.status());
            verify(eventProducer).publishScanRequested(
                    eq(response.id()),
                    any(TenantId.class),
                    eq(TARGET_URL),
                    eq(ScanType.WEB.name()),
                    eq(3),
                    eq(300)
            );
        }

        @Test
        @DisplayName("should reject scan type not allowed by tenant plan")
        void createScan_quandoTipoNaoPermitido_lancaScanQuotaExceededException() {
            CreateScanRequest request = new CreateScanRequest(TARGET_URL, ScanType.SAST, 3, 300);

            when(tenantClient.getQuota(TENANT_ID)).thenReturn(createQuota(100, 10));
            when(tenantClient.getPlan(TENANT_ID)).thenReturn(Plan.PROFESSIONAL);

            assertThrows(ScanQuotaExceededException.class, () -> service.createScan(request, TENANT_ID, USER_ID));
            verify(scanRepository, never()).save(any(Scan.class));
        }

        @Test
        @DisplayName("should reject when daily quota is exceeded")
        void createScan_quandoQuotaDiariaExcedida_lancaScanQuotaExceededException() {
            CreateScanRequest request = new CreateScanRequest(TARGET_URL, ScanType.WEB, 3, 300);

            when(tenantClient.getQuota(TENANT_ID)).thenReturn(createQuota(1, 10));
            when(tenantClient.getPlan(TENANT_ID)).thenReturn(Plan.TRIAL);
            when(scanRepository.countByTenantIdSince(eq(TENANT_ID), any())).thenReturn(1L);

            assertThrows(ScanQuotaExceededException.class, () -> service.createScan(request, TENANT_ID, USER_ID));
        }

        @Test
        @DisplayName("should reject when concurrent quota is exceeded")
        void createScan_quandoQuotaConcorrenteExcedida_lancaScanQuotaExceededException() {
            CreateScanRequest request = new CreateScanRequest(TARGET_URL, ScanType.WEB, 3, 300);

            when(tenantClient.getQuota(TENANT_ID)).thenReturn(createQuota(100, 1));
            when(tenantClient.getPlan(TENANT_ID)).thenReturn(Plan.TRIAL);
            when(scanRepository.countByTenantIdSince(eq(TENANT_ID), any())).thenReturn(0L);
            when(scanRepository.countByTenantIdAndStatus(TENANT_ID, ScanStatus.RUNNING)).thenReturn(1L);

            assertThrows(ScanQuotaExceededException.class, () -> service.createScan(request, TENANT_ID, USER_ID));
        }
    }

    @Nested
    @DisplayName("Get scan")
    class GetScanFlow {

        @Test
        @DisplayName("should return scan when tenant owns it")
        void getScan_quandoPertenceAoTenant_retornaResponse() {
            UUID scanId = UUID.randomUUID();
            Scan scan = createScan(scanId, TENANT_ID, USER_ID, ScanStatus.RUNNING);

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            ScanResponse response = service.getScan(scanId, TENANT_ID);

            assertEquals(scanId, response.id());
            assertEquals(TENANT_ID, response.tenantId());
        }

        @Test
        @DisplayName("should throw when scan belongs to another tenant")
        void getScan_quandoPertenceAOutroTenant_lancaScanNotFoundException() {
            UUID scanId = UUID.randomUUID();
            Scan scan = createScan(scanId, UUID.randomUUID(), USER_ID, ScanStatus.RUNNING);

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            assertThrows(ScanNotFoundException.class, () -> service.getScan(scanId, TENANT_ID));
        }

        @Test
        @DisplayName("should delegate getScanByTenantAndId to tenant-aware lookup")
        void getScanByTenantAndId_quandoValido_retornaMesmoResultado() {
            UUID scanId = UUID.randomUUID();
            Scan scan = createScan(scanId, TENANT_ID, USER_ID, ScanStatus.PENDING);

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            ScanResponse response = service.getScanByTenantAndId(TENANT_ID, scanId);

            assertEquals(scanId, response.id());
            assertEquals(ScanStatus.PENDING, response.status());
        }
    }

    @Nested
    @DisplayName("Get status")
    class GetStatusFlow {

        @Test
        @DisplayName("should return current status when scan exists")
        void getStatus_quandoExiste_retornaStatus() {
            UUID scanId = UUID.randomUUID();
            Scan scan = createScan(scanId, TENANT_ID, USER_ID, ScanStatus.COMPLETED);

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan));

            ScanResponse response = service.getStatus(scanId);

            assertEquals(ScanStatus.COMPLETED, response.status());
            assertEquals(scanId, response.id());
        }

        @Test
        @DisplayName("should throw when scan is missing")
        void getStatus_quandoNaoExiste_lancaScanNotFoundException() {
            UUID scanId = UUID.randomUUID();
            when(scanRepository.findById(scanId)).thenReturn(Optional.empty());

            assertThrows(ScanNotFoundException.class, () -> service.getStatus(scanId));
        }
    }
}
