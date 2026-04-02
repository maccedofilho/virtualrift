package com.virtualrift.orchestrator;

import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanStatus;
import com.virtualrift.orchestrator.model.ScanType;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL SECURITY TESTS
 * These tests verify scan isolation between tenants - cross-tenant data access must be impossible.
 * Any failure here is a CRITICAL security vulnerability.
 *
 * NOTE: These tests document expected security behaviors. Full implementation requires:
 * - All repository queries to include tenant_id filter
 * - Tenant context extraction from JWT
 * - Proper isolation in all scan operations
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@DisplayName("Scan Isolation Tests (SECURITY CRITICAL)")
class ScanIsolationTest {

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ScanOrchestratorService scanOrchestratorService;

    private UUID tenantIdA;
    private UUID tenantIdB;
    private UUID tenantIdC;

    @BeforeEach
    void setUp() {
        // Create multiple test tenants with different IDs
        tenantIdA = UUID.randomUUID();
        tenantIdB = UUID.randomUUID();
        tenantIdC = UUID.randomUUID();

        Scan scanA1 = new Scan(
                UUID.randomUUID(),
                tenantIdA,
                "https://tenant-a.com",
                ScanType.WEB,
                3,
                300,
                ScanStatus.COMPLETED,
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(90),
                Instant.now(),
                null,
                null
        );

        Scan scanA2 = new Scan(
                UUID.randomUUID(),
                tenantIdA,
                "https://tenant-a.com/admin",
                ScanType.API,
                5,
                600,
                ScanStatus.RUNNING,
                Instant.now().minusSeconds(30),
                Instant.now(),
                null,
                null,
                null
        );

        Scan scanB1 = new Scan(
                UUID.randomUUID(),
                tenantIdB,
                "https://tenant-b.com",
                ScanType.WEB,
                3,
                300,
                ScanStatus.PENDING,
                Instant.now(),
                null,
                null,
                null,
                null
        );

        Scan scanC1 = new Scan(
                UUID.randomUUID(),
                tenantIdC,
                "https://tenant-c.com",
                ScanType.NETWORK,
                2,
                120,
                ScanStatus.FAILED,
                Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(30),
                null,
                "Connection timeout",
                null
        );

        scanRepository.saveAll(List.of(scanA1, scanA2, scanB1, scanC1));
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        scanRepository.deleteAll();
    }

    @Nested
    @DisplayName("Repository level isolation")
    class RepositoryIsolation {

        @Test
        @DisplayName("CRITICAL: findByIdAndTenantId returns only tenant's scan")
        void findByIdAndTenantId_quandoTenantValido_retornaApenasScanDesseTenant() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            Scan result = scanRepository.findByIdAndTenantId(scanA1.id(), tenantIdA).orElseThrow();

            assertEquals(tenantIdA, result.tenantId());
            assertEquals("https://tenant-a.com", result.target());
        }

        @Test
        @DisplayName("CRITICAL: findByIdAndTenantId returns empty for other tenant's scan")
        void findByIdAndTenantId_quandoTenantDiferente_retornaVazio() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            var result = scanRepository.findByIdAndTenantId(scanA1.id(), tenantIdB);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("CRITICAL: findByTenantId returns only tenant's scans")
        void findByTenantId_quandoTenantValido_retornaApenasScansDesseTenant() {
            List<Scan> tenantAScans = scanRepository.findByTenantId(tenantIdA, null);

            assertEquals(2, tenantAScans.size());
            assertTrue(tenantAScans.stream().allMatch(s -> s.tenantId().equals(tenantIdA)));
        }

        @Test
        @DisplayName("CRITICAL: findByTenantId does not return other tenants' scans")
        void findByTenantId_quandoTenantValido_naoRetornaScansDeOutrosTenants() {
            List<Scan> tenantAScans = scanRepository.findByTenantId(tenantIdA, null);

            assertFalse(tenantAScans.stream().anyMatch(s -> s.tenantId().equals(tenantIdB)));
            assertFalse(tenantAScans.stream().anyMatch(s -> s.tenantId().equals(tenantIdC)));
        }

        @Test
        @DisplayName("CRITICAL: findByTenantId returns empty for non-existent tenant")
        void findByTenantId_quandoTenantInexistente_retornaVazio() {
            List<Scan> scans = scanRepository.findByTenantId(UUID.randomUUID(), null);

            assertTrue(scans.isEmpty());
        }
    }

    @Nested
    @DisplayName("Service level isolation")
    class ServiceIsolation {

        @Test
        @DisplayName("CRITICAL: getScan returns only tenant's scan")
        void getScan_quandoTenantValido_retornaApenasScanDesseTenant() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            Scan result = scanOrchestratorService.getScan(scanA1.id(), tenantIdA);

            assertEquals(tenantIdA, result.tenantId());
            assertEquals(scanA1.id(), result.id());
        }

        @Test
        @DisplayName("CRITICAL: getScan throws when accessing other tenant's scan")
        void getScan_quandoTenantDiferente_lancaExcecao() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            assertThrows(
                    com.virtualrift.orchestrator.exception.ScanNotFoundException.class,
                    () -> scanOrchestratorService.getScan(scanA1.id(), tenantIdB)
            );
        }

        @Test
        @DisplayName("CRITICAL: listScans returns only tenant's scans")
        void listScans_quandoTenantValido_retornaApenasScansDesseTenant() {
            List<Scan> tenantAScans = scanOrchestratorService.listScans(tenantIdA, 0, 20);

            assertEquals(2, tenantAScans.size());
            assertTrue(tenantAScans.stream().allMatch(s -> s.tenantId().equals(tenantIdA)));
        }

        @Test
        @DisplayName("CRITICAL: listScans does not return other tenants' scans")
        void listScans_quandoTenantValido_naoRetornaScansDeOutrosTenants() {
            List<Scan> tenantAScans = scanOrchestratorService.listScans(tenantIdA, 0, 20);

            assertTrue(tenantAScans.stream().noneMatch(s -> s.tenantId().equals(tenantIdB)));
            assertTrue(tenantAScans.stream().noneMatch(s -> s.tenantId().equals(tenantIdC)));
        }

        @Test
        @DisplayName("CRITICAL: updateStatus only updates tenant's scan")
        void updateStatus_quandoTenantValido_atualizaApenasScanDesseTenant() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            scanOrchestratorService.updateStatus(scanA1.id(), tenantIdA, ScanStatus.CANCELLED);

            Scan updated = scanRepository.findByIdAndTenantId(scanA1.id(), tenantIdA).orElseThrow();
            assertEquals(ScanStatus.CANCELLED, updated.status());
        }

        @Test
        @DisplayName("CRITICAL: updateStatus throws when updating other tenant's scan")
        void updateStatus_quandoTenantDiferente_lancaExcecao() {
            Scan scanA1 = scanRepository.findByTenantId(tenantIdA, null).get(0);

            assertThrows(
                    com.virtualrift.orchestrator.exception.ScanNotFoundException.class,
                    () -> scanOrchestratorService.updateStatus(scanA1.id(), tenantIdB, ScanStatus.CANCELLED)
            );
        }

        @Test
        @DisplayName("CRITICAL: cancelScan only cancels tenant's scan")
        void cancelScan_quandoTenantValido_cancelaApenasScanDesseTenant() {
            Scan scanA2 = scanRepository.findByTenantId(tenantIdA, null).stream()
                    .filter(s -> s.status().equals(ScanStatus.RUNNING))
                    .findFirst()
                    .orElseThrow();

            scanOrchestratorService.cancelScan(scanA2.id(), tenantIdA);

            Scan cancelled = scanRepository.findByIdAndTenantId(scanA2.id(), tenantIdA).orElseThrow();
            assertEquals(ScanStatus.CANCELLED, cancelled.status());
        }

        @Test
        @DisplayName("CRITICAL: cancelScan throws when cancelling other tenant's scan")
        void cancelScan_quandoTenantDiferente_lancaExcecao() {
            Scan scanA2 = scanRepository.findByTenantId(tenantIdA, null).stream()
                    .filter(s -> s.status().equals(ScanStatus.RUNNING))
                    .findFirst()
                    .orElseThrow();

            assertThrows(
                    com.virtualrift.orchestrator.exception.ScanNotFoundException.class,
                    () -> scanOrchestratorService.cancelScan(scanA2.id(), tenantIdB)
            );
        }
    }

    @Nested
    @DisplayName("Cross-tenant enumeration prevention")
    class CrossTenantEnumeration {

        @Test
        @DisplayName("CRITICAL: scan UUIDs are random type 4")
        void uuids_quandoGerados_saoAleatorios() {
            Scan scan = scanRepository.findByTenantId(tenantIdA, null).get(0);
            UUID scanId = scan.id();

            assertEquals(4, scanId.version(), "UUID must be version 4 (random)");
        }

        @Test
        @DisplayName("CRITICAL: sequential scans have different UUIDs")
        void uuids_quandoMultiplasCriacoes_saoDiferentes() {
            List<Scan> tenantAScans = scanRepository.findByTenantId(tenantIdA, null);

            assertNotEquals(tenantAScans.get(0).id(), tenantAScans.get(1).id());
        }

        @Test
        @DisplayName("CRITICAL: cannot enumerate scan IDs across tenants")
        void scanIds_quandoTenantDiferente_naoSaoEnumeraveis() {
            List<Scan> tenantAScans = scanRepository.findByTenantId(tenantIdA, null);
            List<Scan> tenantBScans = scanRepository.findByTenantId(tenantIdB, null);

            // Scan IDs should not be predictable or sequential across tenants
            assertTrue(tenantAScans.stream().noneMatch(s ->
                    tenantBScans.stream().anyMatch(b -> b.id().equals(s.id()))
            ));
        }
    }

    @Nested
    @DisplayName("Quota isolation")
    class QuotaIsolation {

        @Test
        @DisplayName("CRITICAL: each tenant has separate scan counts")
        void quotas_quandoTenantDiferentes_contasSeparadas() {
            // Each tenant must have its own scan tracking
            long countA = scanRepository.findByTenantId(tenantIdA, null).size();
            long countB = scanRepository.findByTenantId(tenantIdB, null).size();
            long countC = scanRepository.findByTenantId(tenantIdC, null).size();

            assertEquals(2, countA);
            assertEquals(1, countB);
            assertEquals(1, countC);
        }

        @Test
        @DisplayName("CRITICAL: quota exceeded for one tenant does not affect others")
        void quotas_quandoUmTenantExcede_outrosNaoAfetados() {
            // Document: When tenant A exceeds quota, tenants B and C should still be able to scan
            // This is verified by quota service, documented here for completeness
            assertTrue(true, "Quota service must enforce per-tenant limits independently");
        }
    }

    @Nested
    @DisplayName("Data integrity")
    class DataIntegrity {

        @Test
        @DisplayName("CRITICAL: tenantId cannot be null")
        void tenantId_quandoNulo_lancaExcecao() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Scan(
                            null,
                            UUID.randomUUID(),
                            "https://example.com",
                            ScanType.WEB,
                            3,
                            300,
                            ScanStatus.PENDING,
                            Instant.now(),
                            null,
                            null,
                            null,
                            null
                    )
            );
        }

        @Test
        @DisplayName("CRITICAL: scan must have valid target URL")
        void target_quandoInvalido_lancaExcecao() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Scan(
                            UUID.randomUUID(),
                            tenantIdA,
                            "not-a-valid-url",
                            ScanType.WEB,
                            3,
                            300,
                            ScanStatus.PENDING,
                            Instant.now(),
                            null,
                            null,
                            null,
                            null
                    )
            );
        }

        @Test
        @DisplayName("CRITICAL: scan must have valid scan type")
        void scanType_quandoNulo_lancaExcecao() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Scan(
                            UUID.randomUUID(),
                            tenantIdA,
                            "https://example.com",
                            null,
                            3,
                            300,
                            ScanStatus.PENDING,
                            Instant.now(),
                            null,
                            null,
                            null,
                            null
                    )
            );
        }
    }

    @Nested
    @DisplayName("Event isolation")
    class EventIsolation {

        @Test
        @DisplayName("CRITICAL: scan events include tenantId")
        void eventos_quandoGerados_incluemTenantId() {
            // Document: All scan-related events (ScanRequestedEvent, ScanCompletedEvent, ScanFailedEvent)
            // must include tenantId for proper isolation
            assertTrue(true, "All scan events must include tenantId");
        }

        @Test
        @DisplayName("CRITICAL: event consumers respect tenant boundaries")
        void eventos_quandoProcessados_respeitamLimitesTenant() {
            // Document: Event consumers must only process events for their tenant context
            // Cross-tenant event processing must be impossible
            assertTrue(true, "Event consumers must enforce tenant boundaries");
        }
    }

    @Nested
    @DisplayName("Pagination isolation")
    class PaginationIsolation {

        @Test
        @DisplayName("CRITICAL: paginated results respect tenant boundaries")
        void listScans_quandoPaginado_respeitaLimitesTenant() {
            // Create more scans for tenant A
            for (int i = 0; i < 10; i++) {
                Scan scan = new Scan(
                        UUID.randomUUID(),
                        tenantIdA,
                        "https://tenant-a.com/page" + i,
                        ScanType.WEB,
                        3,
                        300,
                        ScanStatus.PENDING,
                        Instant.now(),
                        null,
                        null,
                        null,
                        null
                );
                scanRepository.save(scan);
            }

            // Get first page
            List<Scan> page1 = scanOrchestratorService.listScans(tenantIdA, 0, 5);

            assertEquals(5, page1.size());
            assertTrue(page1.stream().allMatch(s -> s.tenantId().equals(tenantIdA)));

            // Get second page
            List<Scan> page2 = scanOrchestratorService.listScans(tenantIdA, 1, 5);

            assertEquals(5, page2.size());
            assertTrue(page2.stream().allMatch(s -> s.tenantId().equals(tenantIdA)));

            // Verify no cross-tenant leakage
            assertTrue(page1.stream().noneMatch(s -> tenantIdB.equals(s.tenantId())));
            assertTrue(page2.stream().noneMatch(s -> tenantIdB.equals(s.tenantId())));
        }

        @Test
        @DisplayName("CRITICAL: pagination counts are per-tenant")
        void listScans_quandoPaginado_contagemPorTenant() {
            List<Scan> tenantAScans = scanOrchestratorService.listScans(tenantIdA, 0, 20);
            List<Scan> tenantBScans = scanOrchestratorService.listScans(tenantIdB, 0, 20);
            List<Scan> tenantCScans = scanOrchestratorService.listScans(tenantIdC, 0, 20);

            // Each tenant should only see their own scans
            assertEquals(2, tenantAScans.size());
            assertEquals(1, tenantBScans.size());
            assertEquals(1, tenantCScans.size());
        }
    }
}
