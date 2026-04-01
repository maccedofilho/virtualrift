package com.virtualrift.tenant.service;

import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.tenant.exception.QuotaExceededException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantQuotaService Tests")
class TenantQuotaServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    private TenantQuotaService quotaService;

    private UUID tenantId;
    private Tenant activeTenant;
    private TenantQuota quota;

    @BeforeEach
    void setUp() {
        quotaService = new TenantQuotaService(tenantRepository);
        tenantId = UUID.randomUUID();
        quota = new TenantQuota(100, 5, 0, 0);
        activeTenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());
    }

    @Nested
    @DisplayName("Can start scan")
    class CanStartScan {

        @Test
        @DisplayName("should return true when below daily limit")
        void canStartScan_quandoAbaixoDoLimiteDiario_retornaTrue() {
            TenantQuota quota = new TenantQuota(100, 5, 50, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertTrue(quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should return false when at daily limit")
        void canStartScan_quandoNoLimiteDiario_retornaFalse() {
            TenantQuota quota = new TenantQuota(100, 5, 100, 0);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertFalse(quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should return true when below concurrent limit")
        void canStartScan_quandoAbaixoDoLimiteConcorrente_retornaTrue() {
            TenantQuota quota = new TenantQuota(100, 5, 0, 3);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertTrue(quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should return false when at concurrent limit")
        void canStartScan_quandoNoLimiteConcorrente_retornaFalse() {
            TenantQuota quota = new TenantQuota(100, 5, 0, 5);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertFalse(quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should return true when unlimited (-1)")
        void canStartScan_quandoIlimitado_retornaTrue() {
            TenantQuota quota = new TenantQuota(-1, -1, 999999, 999999);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.ENTERPRISE, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertTrue(quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void canStartScan_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should throw when tenant is suspended")
        void canStartScan_quandoTenantSuspenso_lancaExcecao() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.SUSPENDED, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(IllegalStateException.class, () -> quotaService.canStartScan(tenantId));
        }

        @Test
        @DisplayName("should throw when tenant is pending verification")
        void canStartScan_quandoTenantPending_lancaExcecao() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.PENDING_VERIFICATION, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(IllegalStateException.class, () -> quotaService.canStartScan(tenantId));
        }
    }

    @Nested
    @DisplayName("Increment scan count")
    class IncrementScanCount {

        @Test
        @DisplayName("should increment daily count")
        void incrementScanCount_quandoChamado_incrementaContadorDiario() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.incrementScanCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().dailyScanCount() == 11));
        }

        @Test
        @DisplayName("should increment concurrent count")
        void incrementScanCount_quandoChamado_incrementaContadorConcorrente() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.incrementScanCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().concurrentScanCount() == 3));
        }

        @Test
        @DisplayName("should throw when at daily limit")
        void incrementScanCount_quandoNoLimiteDiario_lancaQuotaExceededException() {
            TenantQuota quota = new TenantQuota(100, 5, 100, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(QuotaExceededException.class, () -> quotaService.incrementScanCount(tenantId));
        }

        @Test
        @DisplayName("should throw when at concurrent limit")
        void incrementScanCount_quandoNoLimiteConcorrente_lancaQuotaExceededException() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 5);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            assertThrows(QuotaExceededException.class, () -> quotaService.incrementScanCount(tenantId));
        }

        @Test
        @DisplayName("should not increment when unlimited")
        void incrementScanCount_quandoIlimitado_naoIncrementa() {
            TenantQuota quota = new TenantQuota(-1, -1, 0, 0);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.ENTERPRISE, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.incrementScanCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().dailyScanCount() == 0));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void incrementScanCount_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> quotaService.incrementScanCount(tenantId));
        }
    }

    @Nested
    @DisplayName("Decrement concurrent count")
    class DecrementConcurrentCount {

        @Test
        @DisplayName("should decrement concurrent count")
        void decrementConcurrentCount_quandoChamado_decrementaContador() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 3);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.decrementConcurrentCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().concurrentScanCount() == 2));
        }

        @Test
        @DisplayName("should not go below zero")
        void decrementConcurrentCount_quandoZero_mantemZero() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 0);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.decrementConcurrentCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().concurrentScanCount() == 0));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void decrementConcurrentCount_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> quotaService.decrementConcurrentCount(tenantId));
        }
    }

    @Nested
    @DisplayName("Reset daily count")
    class ResetDailyCount {

        @Test
        @DisplayName("should reset daily count to zero")
        void resetDailyCount_quandoChamado_resetaParaZero() {
            TenantQuota quota = new TenantQuota(100, 5, 95, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.resetDailyCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().dailyScanCount() == 0));
        }

        @Test
        @DisplayName("should reset all tenants")
        void resetDailyCount_quandoChamado_resetaTodosTenants() {
            TenantQuota quota1 = new TenantQuota(100, 5, 95, 2);
            Tenant tenant1 = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota1, java.time.Instant.now());

            UUID tenantId2 = UUID.randomUUID();
            TenantQuota quota2 = new TenantQuota(50, 3, 40, 1);
            Tenant tenant2 = new Tenant(tenantId2, "Beta", "beta", Plan.FREE, TenantStatus.ACTIVE, quota2, java.time.Instant.now());

            when(tenantRepository.findAll()).thenReturn(java.util.List.of(tenant1, tenant2));
            when(tenantRepository.saveAll(any())).thenReturn(java.util.List.of(tenant1, tenant2));

            quotaService.resetAllDailyCounts();

            verify(tenantRepository).saveAll(argThat(tenants -> tenants.stream().allMatch(t -> t.quota().dailyScanCount() == 0)));
        }

        @Test
        @DisplayName("should not reset concurrent count")
        void resetDailyCount_quandoChamado_naoAlteraConcorrente() {
            TenantQuota quota = new TenantQuota(100, 5, 95, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.resetDailyCount(tenantId);

            verify(tenantRepository).save(argThat(t -> t.quota().concurrentScanCount() == 2));
        }
    }

    @Nested
    @DisplayName("Get quotas")
    class GetQuotas {

        @Test
        @DisplayName("should return tenant quotas")
        void getQuotas_quandoTenantExiste_retornaQuotas() {
            TenantQuota quota = new TenantQuota(100, 5, 10, 2);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            TenantQuota result = quotaService.getQuotas(tenantId);

            assertEquals(quota, result);
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void getQuotas_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> quotaService.getQuotas(tenantId));
        }
    }

    @Nested
    @DisplayName("Update quotas")
    class UpdateQuotas {

        @Test
        @DisplayName("should update quotas")
        void updateQuotas_quandoValido_atualizaQuotas() {
            TenantQuota newQuota = new TenantQuota(200, 10, 0, 0);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, quota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.updateQuotas(tenantId, newQuota);

            verify(tenantRepository).save(argThat(t -> t.quota().equals(newQuota)));
        }

        @Test
        @DisplayName("should throw when negative values")
        void updateQuotas_quandoValoresNegativos_lancaExcecao() {
            TenantQuota invalidQuota = new TenantQuota(-1, 5, 0, 0); // Only -1 for unlimited is valid

            assertThrows(IllegalArgumentException.class, () -> quotaService.updateQuotas(tenantId, invalidQuota));
        }

        @Test
        @DisplayName("should throw when tenant does not exist")
        void updateQuotas_quandoTenantNaoExiste_lancaTenantNotFoundException() {
            TenantQuota newQuota = new TenantQuota(200, 10, 0, 0);

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            assertThrows(TenantNotFoundException.class, () -> quotaService.updateQuotas(tenantId, newQuota));
        }

        @Test
        @DisplayName("should not affect current counts")
        void updateQuotas_quandoAtualizado_naoAlteraContadoresAtuais() {
            TenantQuota newQuota = new TenantQuota(200, 10, 0, 0);
            TenantQuota currentQuota = new TenantQuota(100, 5, 50, 3);
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, currentQuota, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.updateQuotas(tenantId, newQuota);

            verify(tenantRepository).save(argThat(t ->
                    t.quota().dailyScanLimit() == 200 &&
                    t.quota().concurrentScanLimit() == 10 &&
                    t.quota().dailyScanCount() == 50 &&
                    t.quota().concurrentScanCount() == 3
            ));
        }
    }

    @Nested
    @DisplayName("Initialize quotas")
    class InitializeQuotas {

        @Test
        @DisplayName("should set free plan quotas")
        void initializeQuotas_quantoPlanoFree_defineQuotasFree() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.FREE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.initializeQuotas(tenantId, Plan.FREE);

            verify(tenantRepository).save(argThat(t ->
                    t.quota().dailyScanLimit() == 10 &&
                    t.quota().concurrentScanLimit() == 1
            ));
        }

        @Test
        @DisplayName("should set professional plan quotas")
        void initializeQuotas_quantoPlanoProfessional_defineQuotasProfessional() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.PROFESSIONAL, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.initializeQuotas(tenantId, Plan.PROFESSIONAL);

            verify(tenantRepository).save(argThat(t ->
                    t.quota().dailyScanLimit() == 100 &&
                    t.quota().concurrentScanLimit() == 5
            ));
        }

        @Test
        @DisplayName("should set enterprise plan quotas")
        void initializeQuotas_quantoPlanoEnterprise_defineQuotasEnterprise() {
            Tenant tenant = new Tenant(tenantId, "Acme", "acme", Plan.ENTERPRISE, TenantStatus.ACTIVE, null, java.time.Instant.now());

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenReturn(tenant);

            quotaService.initializeQuotas(tenantId, Plan.ENTERPRISE);

            verify(tenantRepository).save(argThat(t ->
                    t.quota().dailyScanLimit() == -1 &&
                    t.quota().concurrentScanLimit() == -1
            ));
        }
    }
}
