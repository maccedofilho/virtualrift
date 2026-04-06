package com.virtualrift.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantQuota Tests")
class TenantQuotaTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create with valid limits")
        void create_quandoLimitesValidos_retornaQuota() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB));

            assertNotNull(quota);
            assertEquals(100, quota.maxScansPerDay());
            assertEquals(10, quota.maxConcurrentScans());
            assertEquals(1000, quota.maxScanTargets());
            assertEquals(30, quota.reportRetentionDays());
            assertEquals(0, quota.currentScansToday());
            assertEquals(0, quota.currentConcurrentScans());
        }

        @Test
        @DisplayName("should throw when maxScansPerDay is negative")
        void create_quandoMaxScansPerDayNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(-2, 10, 1000, 30, Set.of())
            );

            assertEquals("maxScansPerDay cannot be negative (use -1 for unlimited)", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when maxConcurrentScans is negative")
        void create_quandoMaxConcurrentScansNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, -2, 1000, 30, Set.of())
            );

            assertEquals("maxConcurrentScans cannot be negative (use -1 for unlimited)", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when maxScanTargets is negative")
        void create_quandoMaxScanTargetsNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, 10, -2, 30, Set.of())
            );

            assertEquals("maxScanTargets cannot be negative (use -1 for unlimited)", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when reportRetentionDays is negative")
        void create_quandoReportRetentionDaysNegativo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, 10, 1000, -2, Set.of())
            );

            assertEquals("reportRetentionDays cannot be negative", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when allowedScanTypes is null")
        void create_quandoAllowedScanTypesNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, 10, 1000, 30, null)
            );

            assertEquals("allowedScanTypes cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should support unlimited scans (-1)")
        void create_quandoIlimitado_retornaQuotaIlimitada() {
            TenantQuota quota = TenantQuota.of(-1, -1, -1, 30, Set.of(ScanType.WEB));

            assertEquals(-1, quota.maxScansPerDay());
            assertEquals(-1, quota.maxConcurrentScans());
            assertEquals(-1, quota.maxScanTargets());
        }
    }

    @Nested
    @DisplayName("Can start scan")
    class CanStartScan {

        @Test
        @DisplayName("should return true when below daily limit")
        void canStartScan_quandoAbaixoDoLimiteDiario_retornaTrue() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(50);

            assertTrue(quota.canStartScan());
        }

        @Test
        @DisplayName("should return false when at daily limit")
        void canStartScan_quandoNoLimiteDiario_retornaFalse() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(100);

            assertFalse(quota.canStartScan());
        }

        @Test
        @DisplayName("should return true when below concurrent limit")
        void canStartScan_quandoAbaixoDoLimiteConcorrente_retornaTrue() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentConcurrentScans(5);

            assertTrue(quota.canStartScan());
        }

        @Test
        @DisplayName("should return false when at concurrent limit")
        void canStartScan_quandoNoLimiteConcorrente_retornaFalse() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentConcurrentScans(10);

            assertFalse(quota.canStartScan());
        }

        @Test
        @DisplayName("should return true when unlimited (-1)")
        void canStartScan_quandoIlimitado_retornaTrue() {
            TenantQuota quota = TenantQuota.of(-1, -1, -1, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(100000)
                    .withCurrentConcurrentScans(100000);

            assertTrue(quota.canStartScan());
        }

        @Test
        @DisplayName("should return false when both limits exceeded")
        void canStartScan_quandoAmbosLimitesExcedidos_retornaFalse() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(100)
                    .withCurrentConcurrentScans(10);

            assertFalse(quota.canStartScan());
        }
    }

    @Nested
    @DisplayName("Can use scan type")
    class CanUseScanType {

        @Test
        @DisplayName("should return true when scan type is allowed")
        void canUseScanType_quandoPermitido_retornaTrue() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB, ScanType.API));

            assertTrue(quota.canUseScanType(ScanType.WEB));
            assertTrue(quota.canUseScanType(ScanType.API));
        }

        @Test
        @DisplayName("should return false when scan type is not allowed")
        void canUseScanType_quandoNaoPermitido_retornaFalse() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB));

            assertFalse(quota.canUseScanType(ScanType.API));
            assertFalse(quota.canUseScanType(ScanType.NETWORK));
        }
    }

    @Nested
    @DisplayName("Increment scan count")
    class IncrementScanCount {

        @Test
        @DisplayName("should increment current count")
        void incrementScanCount_quandoChamado_incrementaContador() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(5);

            TenantQuota updated = quota.incrementScanCount();

            assertEquals(6, updated.currentScansToday());
        }

        @Test
        @DisplayName("should throw when already at limit")
        void incrementScanCount_quandoNoLimite_lancaExcecao() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(100);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    quota::incrementScanCount
            );

            assertTrue(exception.getMessage().contains("Daily scan limit reached"));
        }

        @Test
        @DisplayName("should not increment when unlimited")
        void incrementScanCount_quandoIlimitado_naoIncrementa() {
            TenantQuota quota = TenantQuota.of(-1, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(0);

            TenantQuota updated = quota.incrementScanCount();

            assertEquals(0, updated.currentScansToday()); // Doesn't increment when unlimited
        }
    }

    @Nested
    @DisplayName("Decrement concurrent scans")
    class DecrementConcurrentScans {

        @Test
        @DisplayName("should decrement concurrent count")
        void decrementConcurrentScans_quandoChamado_decrementaContador() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentConcurrentScans(5);

            TenantQuota updated = quota.decrementConcurrentScans();

            assertEquals(4, updated.currentConcurrentScans());
        }

        @Test
        @DisplayName("should not go below zero")
        void decrementConcurrentScans_quandoZero_retornaZero() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentConcurrentScans(0);

            TenantQuota updated = quota.decrementConcurrentScans();

            assertEquals(0, updated.currentConcurrentScans());
        }
    }

    @Nested
    @DisplayName("Reset daily count")
    class ResetDailyCount {

        @Test
        @DisplayName("should reset current count to zero")
        void resetDailyCount_quandoChamado_resetaParaZero() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(50);

            TenantQuota updated = quota.resetDailyCount();

            assertEquals(0, updated.currentScansToday());
            assertEquals(10, updated.maxConcurrentScans()); // Other fields unchanged
        }
    }

    @Nested
    @DisplayName("Plan quotas")
    class PlanQuotas {

        @Test
        @DisplayName("should return TRIAL quotas")
        void trial_quandoChamado_retornaQuotasTrial() {
            TenantQuota quota = TenantQuota.trial();

            assertEquals(10, quota.maxScansPerDay());
            assertEquals(2, quota.maxConcurrentScans());
            assertEquals(50, quota.maxScanTargets());
            assertEquals(7, quota.reportRetentionDays());
            assertEquals(Set.of(ScanType.WEB), quota.allowedScanTypes());
        }

        @Test
        @DisplayName("should return STARTER quotas")
        void starter_quandoChamado_retornaQuotasStarter() {
            TenantQuota quota = TenantQuota.starter();

            assertEquals(100, quota.maxScansPerDay());
            assertEquals(5, quota.maxConcurrentScans());
            assertEquals(500, quota.maxScanTargets());
            assertEquals(30, quota.reportRetentionDays());
            assertEquals(Set.of(ScanType.WEB, ScanType.API), quota.allowedScanTypes());
        }

        @Test
        @DisplayName("should return PROFESSIONAL quotas")
        void professional_quandoChamado_retornaQuotasProfessional() {
            TenantQuota quota = TenantQuota.professional();

            assertEquals(1000, quota.maxScansPerDay());
            assertEquals(20, quota.maxConcurrentScans());
            assertEquals(5000, quota.maxScanTargets());
            assertEquals(90, quota.reportRetentionDays());
            assertEquals(Set.of(ScanType.WEB, ScanType.API, ScanType.NETWORK), quota.allowedScanTypes());
        }

        @Test
        @DisplayName("should return ENTERPRISE quotas")
        void enterprise_quandoChamado_retornaQuotasEnterprise() {
            TenantQuota quota = TenantQuota.enterprise();

            assertEquals(-1, quota.maxScansPerDay()); // Unlimited
            assertEquals(100, quota.maxConcurrentScans());
            assertEquals(-1, quota.maxScanTargets()); // Unlimited
            assertEquals(365, quota.reportRetentionDays());
            assertEquals(Set.of(ScanType.WEB, ScanType.API, ScanType.NETWORK, ScanType.SAST), quota.allowedScanTypes());
        }
    }

    @Nested
    @DisplayName("With current concurrent scans")
    class WithCurrentConcurrentScans {

        @Test
        @DisplayName("should set concurrent scan count")
        void withCurrentConcurrentScans_quandoChamado_defineContador() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentConcurrentScans(3);

            assertEquals(3, quota.currentConcurrentScans());
        }

        @Test
        @DisplayName("should throw when exceeding max")
        void withCurrentConcurrentScans_quandoExcedeMax_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                            .withCurrentConcurrentScans(11)
            );

            assertTrue(exception.getMessage().contains("exceeds maxConcurrentScans"));
        }
    }

    @Nested
    @DisplayName("With current scans today")
    class WithCurrentScansToday {

        @Test
        @DisplayName("should set daily scan count")
        void withCurrentScansToday_quandoChamado_defineContador() {
            TenantQuota quota = TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(25);

            assertEquals(25, quota.currentScansToday());
        }

        @Test
        @DisplayName("should throw when exceeding max")
        void withCurrentScansToday_quandoExcedeMax_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> TenantQuota.of(100, 10, 1000, 30, Set.of(ScanType.WEB))
                            .withCurrentScansToday(101)
            );

            assertTrue(exception.getMessage().contains("exceeds maxScansPerDay"));
        }

        @Test
        @DisplayName("should allow any value when unlimited")
        void withCurrentScansToday_quandoIlimitado_permiteQualquerValor() {
            TenantQuota quota = TenantQuota.of(-1, 10, 1000, 30, Set.of(ScanType.WEB))
                    .withCurrentScansToday(1000000);

            assertEquals(1000000, quota.currentScansToday());
        }
    }
}
