package com.virtualrift.tenant.service;

import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.tenant.exception.TenantAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.InvalidTenantStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantService Tests")
class TenantServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantQuotaService quotaService;

    private TenantService tenantService;

    @BeforeEach
    void setUp() {
        tenantService = new TenantService(tenantRepository, quotaService);
    }

    @Nested
    @DisplayName("Create tenant")
    class CreateTenant {

        @Test
        @DisplayName("should create tenant when data is valid")
        void createTenant_quandoDadosValidos_criaTenant() {
            String name = "Acme Corp";
            String slug = "acme-corp";
            Plan plan = Plan.PROFESSIONAL;

            when(tenantRepository.existsBySlug(slug)).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                return new Tenant(UUID.randomUUID(), t.name(), t.slug(), t.plan(), t.status(), t.quota(), t.createdAt());
            });

            Tenant result = tenantService.create(name, slug, plan);

            assertNotNull(result);
            assertEquals(name, result.name());
            assertEquals(slug, result.slug());
            assertEquals(plan, result.plan());
            assertEquals(TenantStatus.PENDING_VERIFICATION, result.status());
            verify(tenantRepository).save(any(Tenant.class));
            verify(quotaService).initializeQuotas(any(UUID.class), eq(plan));
        }

        @Test
        @DisplayName("should throw when slug already exists")
        void createTenant_quandoSlugDuplicado_lancaTenantAlreadyExistsException() {
            String name = "Acme Corp";
            String slug = "acme-corp";
            Plan plan = Plan.PROFESSIONAL;

            when(tenantRepository.existsBySlug(slug)).thenReturn(true);

            assertThrows(TenantAlreadyExistsException.class, () -> tenantService.create(name, slug, plan));
            verify(tenantRepository, never()).save(any(Tenant.class));
        }

        @Test
        @DisplayName("should generate ID for new tenant")
        void createTenant_quandoCriado_geraId() {
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                return new Tenant(UUID.randomUUID(), t.name(), t.slug(), t.plan(), t.status(), t.quota(), t.createdAt());
            });

            Tenant result = tenantService.create("Acme", "acme", Plan.FREE);

            assertNotNull(result.id());
        }

        @Test
        @DisplayName("should set status to PENDING_VERIFICATION by default")
        void createTenant_quandoCriado_defineStatusPendingVerification() {
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                return new Tenant(UUID.randomUUID(), t.name(), t.slug(), t.plan(), t.status(), t.quota(), t.createdAt());
            });

            Tenant result = tenantService.create("Acme", "acme", Plan.FREE);

            assertEquals(TenantStatus.PENDING_VERIFICATION, result.status());
        }

        @Test
        @DisplayName("should initialize quotas based on plan")
        void createTenant_quandoCriado_inicializaQuotasDoPlano() {
            when(tenantRepository.existsBySlug("acme")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                return new Tenant(UUID.randomUUID(), t.name(), t.slug(), t.plan(), t.status(), t.quota(), t.createdAt());
            });

            tenantService.create("Acme", "acme", Plan.ENTERPRISE);

            verify(quotaService).initializeQuotas(any(UUID.class), eq(Plan.ENTERPRISE));
        }

        @Test
        @DisplayName("should normalize slug to lowercase and kebab-case")
        void createTenant_quandoSlugComMaiusculas_normalizaSlug() {
            when(tenantRepository.existsBySlug("acme-corp-2024")).thenReturn(false);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
                Tenant t = invocation.getArgument(0);
                return new Tenant(UUID.randomUUID(), t.name(), t.slug(), t.plan(), t.status(), t.quota(), t.createdAt());
            });

            Tenant result = tenantService.create("Acme", "Acme-Corp_2024", Plan.PROFESSIONAL);

            assertEquals("acme-corp-2024", result.slug());
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("should throw when name is blank")
        void createTenant_quandoNomeVazio_lancaExcecao(String invalidName) {
            assertThrows(IllegalArgumentException.class, () -> tenantService.create(invalidName, "slug", Plan.FREE));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        @DisplayName("should throw when slug is blank")
        void createTenant_quandoSlugVazio_lancaExcecao(String invalidSlug) {
            assertThrows(IllegalArgumentException.class, () -> tenantService.create("name", invalidSlug, Plan.FREE));
        }

        @Test
        @DisplayName("should throw when plan is null")
        void createTenant_quandoPlanoNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> tenantService.create("name", "slug", null));
        }
    }

    @Nested
    @DisplayName("Get tenant")
    class GetTenant {

        @Test
        @DisplayName("should return tenant when ID exists")
        void getTenantById_quandoExiste_retornaTenant() {
            UUID tenantId = UUID.randomUUID();
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            Optional<Tenant> result = tenantService.getById(tenantId);

            assertTrue(result.isPresent());
            assertEquals(tenantId, result.get().id());
        }

        @Test
        @DisplayName("should return empty when ID does not exist")
        void getTenantById_quandoNaoExiste_retornaEmpty() {
            when(tenantRepository.findById(any())).thenReturn(Optional.empty());

            Optional<Tenant> result = tenantService.getById(UUID.randomUUID());

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should throw when ID is null")
        void getTenantById_quandoIdNulo_lancaExcecao() {
            assertThrows(IllegalArgumentException.class, () -> tenantService.getById(null));
        }

        @Test
        @DisplayName("should return tenant when slug exists")
        void getTenantBySlug_quandoExiste_retornaTenant() {
            String slug = "acme-corp";
            Tenant tenant = new Tenant(UUID.randomUUID(), "Acme", slug, Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findBySlug(slug)).thenReturn(Optional.of(tenant));

            Optional<Tenant> result = tenantService.getBySlug(slug);

            assertTrue(result.isPresent());
            assertEquals(slug, result.get().slug());
        }

        @Test
        @DisplayName("should return empty when slug does not exist")
        void getTenantBySlug_quandoNaoExiste_retornaEmpty() {
            when(tenantRepository.findBySlug(any())).thenReturn(Optional.empty());

            Optional<Tenant> result = tenantService.getBySlug("non-existent");

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Update plan")
    class UpdatePlan {

        private UUID tenantId;
        private Tenant tenant;

        @BeforeEach
        void setUp() {
            tenantId = UUID.randomUUID();
            tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());
        }

        @Test
        @DisplayName("should update plan when valid")
        void updatePlan_quandoValido_atualizaPlano() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            tenantService.updatePlan(tenantId, Plan.PROFESSIONAL);

            verify(tenantRepository).save(argThat(t -> t.plan() == Plan.PROFESSIONAL));
            verify(quotaService).updateQuotasForPlan(tenantId, Plan.PROFESSIONAL);
        }

        @Test
        @DisplayName("should update quotas when plan changes")
        void updatePlan_quandoPlanoMuda_atualizaQuotas() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            tenantService.updatePlan(tenantId, Plan.ENTERPRISE);

            verify(quotaService).updateQuotasForPlan(tenantId, Plan.ENTERPRISE);
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void updatePlan_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> tenantService.updatePlan(tenantId, Plan.PROFESSIONAL));
        }

        @Test
        @DisplayName("should throw when new plan is same as current")
        void updatePlan_quandoMesmoPlano_lancaExcecao() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(IllegalArgumentException.class, () -> tenantService.updatePlan(tenantId, Plan.FREE));
        }
    }

    @Nested
    @DisplayName("Activate tenant")
    class ActivateTenant {

        private UUID tenantId;

        @Test
        @DisplayName("should activate suspended tenant")
        void activateTenant_quandoSuspended_reativaTenant() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.SUSPENDED, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            tenantService.activate(tenantId);

            verify(tenantRepository).save(argThat(t -> t.status() == TenantStatus.ACTIVE));
        }

        @Test
        @DisplayName("should keep active tenant active")
        void activateTenant_quandoAtivo_mantemAtivo() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            tenantService.activate(tenantId);

            verify(tenantRepository).save(argThat(t -> t.status() == TenantStatus.ACTIVE));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void activateTenant_quandoNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(any())).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> tenantService.activate(UUID.randomUUID()));
        }

        @Test
        @DisplayName("should not activate pending verification tenant")
        void activateTenant_quandoPendingVerification_lancaExcecao() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.PENDING_VERIFICATION, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(InvalidTenantStatusException.class, () -> tenantService.activate(tenantId));
        }
    }

    @Nested
    @DisplayName("Suspend tenant")
    class SuspendTenant {

        private UUID tenantId;

        @Test
        @DisplayName("should suspend active tenant")
        void suspendTenant_quandoAtivo_suspendeTenant() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
            when(quotaService.getRunningScanCount(tenantId)).thenReturn(0);

            tenantService.suspend(tenantId);

            verify(tenantRepository).save(argThat(t -> t.status() == TenantStatus.SUSPENDED));
        }

        @Test
        @DisplayName("should keep suspended tenant suspended")
        void suspendTenant_quandoSuspended_mantemSuspenso() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.SUSPENDED, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            tenantService.suspend(tenantId);

            verify(tenantRepository).save(argThat(t -> t.status() == TenantStatus.SUSPENDED));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void suspendTenant_quandoNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(any())).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> tenantService.suspend(UUID.randomUUID()));
        }

        @Test
        @DisplayName("should cancel all running scans when suspended")
        void suspendTenant_quandoSuspenso_cancelaScansEmAndamento() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);
            when(quotaService.getRunningScanCount(tenantId)).thenReturn(3);

            tenantService.suspend(tenantId);

            verify(quotaService).cancelAllRunningScans(tenantId);
        }
    }

    @Nested
    @DisplayName("Delete tenant")
    class DeleteTenant {

        private UUID tenantId;

        @Test
        @DisplayName("should delete tenant when no scans")
        void deleteTenant_quandoSemScans_deletaTenant() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaService.getRunningScanCount(tenantId)).thenReturn(0);
            when(quotaService.getTotalScanCount(tenantId)).thenReturn(0);

            tenantService.delete(tenantId);

            verify(tenantRepository).deleteById(tenantId);
        }

        @Test
        @DisplayName("should throw when tenant has scans")
        void deleteTenant_quandoTemScans_lancaExcecao() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(quotaService.getTotalScanCount(tenantId)).thenReturn(5);

            assertThrows(IllegalStateException.class, () -> tenantService.delete(tenantId));
            verify(tenantRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void deleteTenant_quandoNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(any())).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> tenantService.delete(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Plan quotas")
    class PlanQuotas {

        @ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(Plan.class)
        @DisplayName("should have defined quotas for each plan")
        void planQuotas_quandoPlanoDefinido_retornaQuotas(Plan plan) {
            assertNotNull(plan.getDailyScanLimit());
            assertNotNull(plan.getConcurrentScanLimit());
        }

        @Test
        @DisplayName("FREE plan should have lowest quotas")
        void planQuotas_free_quotasMenores() {
            assertTrue(Plan.FREE.getDailyScanLimit() < Plan.PROFESSIONAL.getDailyScanLimit());
            assertTrue(Plan.FREE.getConcurrentScanLimit() < Plan.PROFESSIONAL.getConcurrentScanLimit());
        }

        @Test
        @DisplayName("PROFESSIONAL plan should have medium quotas")
        void planQuotas_professional_quotasMedias() {
            assertTrue(Plan.PROFESSIONAL.getDailyScanLimit() < Plan.ENTERPRISE.getDailyScanLimit() || Plan.ENTERPRISE.getDailyScanLimit() == -1);
            assertTrue(Plan.PROFESSIONAL.getConcurrentScanLimit() < Plan.ENTERPRISE.getConcurrentScanLimit() || Plan.ENTERPRISE.getConcurrentScanLimit() == -1);
        }

        @Test
        @DisplayName("ENTERPRISE plan should have unlimited quotas")
        void planQuotas_enterprise_ilimitado() {
            assertEquals(-1, Plan.ENTERPRISE.getDailyScanLimit());
            assertEquals(-1, Plan.ENTERPRISE.getConcurrentScanLimit());
        }
    }
}
