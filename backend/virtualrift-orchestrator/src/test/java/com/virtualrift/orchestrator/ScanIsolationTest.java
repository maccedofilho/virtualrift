package com.virtualrift.orchestrator;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.kafka.ScanEventProducer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import com.virtualrift.tenant.client.TenantClient;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Scan Isolation Tests")
class ScanIsolationTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanEventProducer eventProducer;

    @Mock
    private TenantClient tenantClient;

    private ScanOrchestratorService scanOrchestratorService;

    @BeforeEach
    void setUp() {
        scanOrchestratorService = new ScanOrchestratorService(scanRepository, eventProducer, tenantClient);
    }

    private Scan scan(UUID scanId, UUID tenantId, UUID userId) {
        return new Scan(scanId, tenantId, userId, "https://example.com", ScanType.WEB, 3, 300, ScanStatus.RUNNING);
    }

    @Nested
    @DisplayName("Tenant ownership")
    class TenantOwnership {

        @Test
        @DisplayName("should return scan only for owning tenant")
        void getScan_quandoTenantCorreto_retornaResponse() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan(scanId, tenantId, userId)));

            ScanResponse response = scanOrchestratorService.getScan(scanId, tenantId);

            assertEquals(scanId, response.id());
            assertEquals(tenantId, response.tenantId());
        }

        @Test
        @DisplayName("should reject access to another tenant scan")
        void getScan_quandoTenantDiferente_lancaScanNotFoundException() {
            UUID scanId = UUID.randomUUID();
            UUID ownerTenantId = UUID.randomUUID();
            UUID requesterTenantId = UUID.randomUUID();

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan(scanId, ownerTenantId, UUID.randomUUID())));

            assertThrows(ScanNotFoundException.class, () -> scanOrchestratorService.getScan(scanId, requesterTenantId));
        }
    }

    @Nested
    @DisplayName("Tenant-aware lookup")
    class TenantAwareLookup {

        @Test
        @DisplayName("should preserve tenant filtering when using getScanByTenantAndId")
        void getScanByTenantAndId_quandoTenantCorreto_retornaMesmoScan() {
            UUID scanId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan(scanId, tenantId, userId)));

            ScanResponse response = scanOrchestratorService.getScanByTenantAndId(tenantId, scanId);

            assertEquals(scanId, response.id());
            assertEquals(tenantId, response.tenantId());
        }

        @Test
        @DisplayName("should preserve tenant filtering when reading scan status")
        void getStatus_quandoTenantDiferente_lancaScanNotFoundException() {
            UUID scanId = UUID.randomUUID();
            UUID ownerTenantId = UUID.randomUUID();
            UUID requesterTenantId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            when(scanRepository.findById(scanId)).thenReturn(Optional.of(scan(scanId, ownerTenantId, userId)));

            assertThrows(ScanNotFoundException.class, () -> scanOrchestratorService.getStatus(scanId, requesterTenantId));
        }
    }
}
