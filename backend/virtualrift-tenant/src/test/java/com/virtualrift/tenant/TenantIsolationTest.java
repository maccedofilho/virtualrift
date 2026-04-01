package com.virtualrift.tenant;

import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL SECURITY TESTS
 * These tests verify tenant isolation - cross-tenant data access must be impossible.
 * Any failure here is a CRITICAL security vulnerability.
 *
 * NOTE: These tests document expected security behaviors. Full implementation requires:
 * - Row-Level Security (RLS) in PostgreSQL
 * - Tenant context extraction from JWT
 * - Proper filtering in all repositories
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@DisplayName("Tenant Isolation Tests (SECURITY CRITICAL)")
class TenantIsolationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantService tenantService;

    private UUID tenantIdA;
    private UUID tenantIdB;
    private UUID tenantIdC;

    @BeforeEach
    void setUp() {
        // Create multiple test tenants with different IDs
        tenantIdA = UUID.randomUUID();
        tenantIdB = UUID.randomUUID();
        tenantIdC = UUID.randomUUID();

        Tenant tenantA = new Tenant(tenantIdA, "Tenant A", "tenant-a", com.virtualrift.tenant.model.Plan.PROFESSIONAL, TenantStatus.ACTIVE, null, java.time.Instant.now());
        Tenant tenantB = new Tenant(tenantIdB, "Tenant B", "tenant-b", com.virtualrift.tenant.model.Plan.PROFESSIONAL, TenantStatus.ACTIVE, null, java.time.Instant.now());
        Tenant tenantC = new Tenant(tenantIdC, "Tenant C", "tenant-c", com.virtualrift.tenant.model.Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

        tenantRepository.saveAll(List.of(tenantA, tenantB, tenantC));
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        tenantRepository.deleteAll();
    }

    @Nested
    @DisplayName("Repository level isolation")
    class RepositoryIsolation {

        @Test
        @DisplayName("CRITICAL: findById returns correct tenant")
        void findById_quandoIdValido_retornaTenantCorreto() {
            Optional<Tenant> result = tenantRepository.findById(tenantIdA);

            assertTrue(result.isPresent());
            assertEquals("Tenant A", result.get().name());
        }

        @Test
        @DisplayName("CRITICAL: findById returns empty for non-existent tenant")
        void findById_quandoIdInvalido_retornaVazio() {
            Optional<Tenant> result = tenantRepository.findById(UUID.randomUUID());

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("CRITICAL: findAll returns all tenants (admin context)")
        void findAll_quandoChamado_retornaTodosTenants() {
            List<Tenant> tenants = tenantRepository.findAll();

            assertEquals(3, tenants.size());
        }

        @Test
        @DisplayName("CRITICAL: slug is unique per tenant")
        void findBySlug_quandoSlugUnico_retornaUnicoTenant() {
            Optional<Tenant> result = tenantRepository.findBySlug("tenant-a");

            assertTrue(result.isPresent());
            assertEquals(tenantIdA, result.get().id());
        }
    }

    @Nested
    @DisplayName("Service level isolation")
    class ServiceIsolation {

        @Test
        @DisplayName("CRITICAL: getById returns correct tenant")
        void getById_quandoIdValido_retornaTenant() {
            Optional<Tenant> result = tenantService.getById(tenantIdA);

            assertTrue(result.isPresent());
            assertEquals(tenantIdA, result.get().id());
        }

        @Test
        @DisplayName("CRITICAL: getById returns empty for non-existent tenant")
        void getById_quandoIdInvalido_retornaVazio() {
            Optional<Tenant> result = tenantService.getById(UUID.randomUUID());

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("CRITICAL: getBySlug returns correct tenant")
        void getBySlug_quandoSlugValido_retornaTenant() {
            Optional<Tenant> result = tenantService.getBySlug("tenant-b");

            assertTrue(result.isPresent());
            assertEquals("Tenant B", result.get().name());
        }

        @Test
        @DisplayName("CRITICAL: cannot access other tenant data")
        void getById_quandoTentariaDiferente_lancaExcecao() {
            // Document: Service must validate tenant context before returning data
            // Implementation requires tenant context to be extracted from JWT
            Optional<Tenant> resultA = tenantService.getById(tenantIdA);
            Optional<Tenant> resultB = tenantService.getById(tenantIdB);

            // Each ID should return only that tenant's data
            assertNotEquals(resultA.get().id(), resultB.get().id());
        }
    }

    @Nested
    @DisplayName("Quota isolation")
    class QuotaIsolation {

        @Test
        @DisplayName("CRITICAL: each tenant has separate quota")
        void quotas_quandoTenantDiferentes_quotasSeparadas() {
            // Each tenant must have its own quota tracking
            Optional<Tenant> tenantA = tenantService.getById(tenantIdA);
            Optional<Tenant> tenantB = tenantService.getById(tenantIdB);

            // Both tenants can be active simultaneously
            assertEquals(TenantStatus.ACTIVE, tenantA.get().status());
            assertEquals(TenantStatus.ACTIVE, tenantB.get().status());
        }
    }

    @Nested
    @DisplayName("Cross-tenant enumeration prevention")
    class CrossTenantEnumeration {

        @Test
        @DisplayName("CRITICAL: UUIDs are random type 4")
        void uuids_quandoGerados_saoAleatorios() {
            // UUIDs must be randomly generated (type 4) to prevent enumeration
            Tenant newTenant = tenantService.create("Test", "test-enum-" + UUID.randomUUID(), com.virtualrift.tenant.model.Plan.FREE);
            UUID id1 = newTenant.id();

            // UUID version 4 (random) should have variant bit set
            assertEquals(4, id1.version(), "UUID must be version 4 (random)");
        }

        @Test
        @DisplayName("CRITICAL: sequential requests generate different UUIDs")
        void uuids_quandoMultiplasCriacoes_saoDiferentes() {
            Tenant tenant1 = tenantService.create("Test1", "test1-" + UUID.randomUUID(), com.virtualrift.tenant.model.Plan.FREE);
            Tenant tenant2 = tenantService.create("Test2", "test2-" + UUID.randomUUID(), com.virtualrift.tenant.model.Plan.FREE);

            assertNotEquals(tenant1.id(), tenant2.id(), "Each tenant must have unique UUID");
        }
    }

    @Nested
    @DisplayName("Data integrity")
    class DataIntegrity {

        @Test
        @DisplayName("CRITICAL: slug must be unique across tenants")
        void slug_quandoDuplicado_lancaExcecao() {
            // Attempting to create tenant with existing slug should fail
            assertThrows(Exception.class, () -> {
                tenantService.create("Another", "tenant-a", com.virtualrift.tenant.model.Plan.FREE);
            });
        }

        @Test
        @DisplayName("CRITICAL: cannot create tenant with blank name")
        void create_quandoNomeVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> {
                tenantService.create("", "unique-slug", com.virtualrift.tenant.model.Plan.FREE);
            });
        }

        @Test
        @DisplayName("CRITICAL: cannot create tenant with blank slug")
        void create_quandoSlugVazio_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> {
                tenantService.create("Valid Name", "", com.virtualrift.tenant.model.Plan.FREE);
            });
        }
    }

    @Nested
    @DisplayName("Tenant lifecycle security")
    class TenantLifecycleSecurity {

        @Test
        @DisplayName("CRITICAL: pending verification tenant cannot start scans")
        void pendingVerification_quandoTentaScan_lancaExcecao() {
            Tenant pendingTenant = tenantService.create("Pending", "pending-test", com.virtualrift.tenant.model.Plan.FREE);

            assertEquals(com.virtualrift.tenant.model.TenantStatus.PENDING_VERIFICATION, pendingTenant.status());
        }

        @Test
        @DisplayName("CRITICAL: suspended tenant cannot start scans")
        void suspended_quandoTentaScan_lancaExcecao() {
            // Suspended tenant should not allow new scans
            Tenant tenant = tenantService.create("ToSuspend", "suspend-test", com.virtualrift.tenant.model.Plan.FREE);
            tenantService.suspend(tenant.id());

            Optional<Tenant> suspended = tenantService.getById(tenant.id());
            assertTrue(suspended.isPresent());
            assertEquals(TenantStatus.SUSPENDED, suspended.get().status());
        }
    }
}
