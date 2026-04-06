package com.virtualrift.common.dto;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanResult Tests")
class ScanResultTest {

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create with all fields")
        void create_quandoTodosCamposValidos_retornaResultado() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();
            VulnerabilityFinding finding = VulnerabilityFinding.of(
                    UUID.randomUUID(), scanId, tenantId,
                    "XSS", Severity.HIGH, "Injection", "/login",
                    "evidence", Instant.now()
            );
            List<VulnerabilityFinding> findings = List.of(finding);
            Instant startedAt = Instant.now().minusSeconds(60);
            Instant completedAt = Instant.now();

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, startedAt, completedAt, null, null
            );

            assertNotNull(result);
            assertEquals(scanId, result.scanId());
            assertEquals(tenantId, result.tenantId());
            assertEquals(ScanStatus.COMPLETED, result.status());
            assertEquals(1, result.findings().size());
            assertEquals(startedAt, result.startedAt());
            assertEquals(completedAt, result.completedAt());
        }

        @Test
        @DisplayName("should throw when scanId is null")
        void create_quandoScanIdNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanResult.of(
                            null, TenantId.generate(), ScanStatus.COMPLETED,
                            List.of(), Instant.now(), Instant.now(), null, null
                    )
            );

            assertEquals("scanId cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when status is null")
        void create_quandoStatusNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanResult.of(
                            UUID.randomUUID(), TenantId.generate(), null,
                            List.of(), Instant.now(), Instant.now(), null, null
                    )
            );

            assertEquals("status cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when findings is null")
        void create_quandoFindingsNulo_lancaExcecao() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> ScanResult.of(
                            UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                            null, Instant.now(), Instant.now(), null, null
                    )
            );

            assertEquals("findings cannot be null", exception.getMessage());
        }

        @Test
        @DisplayName("should accept empty findings list")
        void create_quandoFindingsVazio_retornaResultado() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), Instant.now(), null, null
            );

            assertTrue(result.findings().isEmpty());
        }

        @Test
        @DisplayName("should auto-set completedAt for COMPLETED status")
        void create_quandoStatusCompleted_defineDataAtual() {
            Instant before = Instant.now().minusSeconds(1);

            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), null, null, null
            );

            Instant after = Instant.now().plusSeconds(1);

            assertNotNull(result.completedAt());
            assertTrue(result.completedAt().isAfter(before) || result.completedAt().equals(before));
            assertTrue(result.completedAt().isBefore(after) || result.completedAt().equals(after));
        }

        @Test
        @DisplayName("should auto-set failedAt for FAILED status")
        void create_quandoStatusFailed_defineDataAtual() {
            Instant before = Instant.now().minusSeconds(1);

            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.FAILED,
                    List.of(), Instant.now(), null, null, "Connection timeout"
            );

            Instant after = Instant.now().plusSeconds(1);

            assertNotNull(result.failedAt());
            assertTrue(result.failedAt().isAfter(before) || result.failedAt().equals(before));
            assertTrue(result.failedAt().isBefore(after) || result.failedAt().equals(after));
        }
    }

    @Nested
    @DisplayName("Finding aggregation")
    class FindingAggregation {

        @Test
        @DisplayName("should count findings by severity")
        void countBySeverity_quandoChamado_retornaContagemPorSeveridade() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c1", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c2", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "h1", Severity.HIGH, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "m1", Severity.MEDIUM, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "l1", Severity.LOW, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "i1", Severity.INFO, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(2, result.countBySeverity(Severity.CRITICAL));
            assertEquals(1, result.countBySeverity(Severity.HIGH));
            assertEquals(1, result.countBySeverity(Severity.MEDIUM));
            assertEquals(1, result.countBySeverity(Severity.LOW));
            assertEquals(1, result.countBySeverity(Severity.INFO));
        }

        @Test
        @DisplayName("should count critical findings")
        void criticalCount_quandoChamado_retornaContagemCritica() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c1", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c2", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "h1", Severity.HIGH, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(2, result.criticalCount());
        }

        @Test
        @DisplayName("should count high findings")
        void highCount_quandoChamado_retornaContagemAlta() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c1", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "h1", Severity.HIGH, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "h2", Severity.HIGH, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(2, result.highCount());
        }

        @Test
        @DisplayName("should count medium findings")
        void mediumCount_quandoChamado_retornaContagemMedia() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "m1", Severity.MEDIUM, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "m2", Severity.MEDIUM, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(2, result.mediumCount());
        }

        @Test
        @DisplayName("should count low findings")
        void lowCount_quandoChamado_retornaContagemBaixa() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "l1", Severity.LOW, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "l2", Severity.LOW, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(2, result.lowCount());
        }

        @Test
        @DisplayName("should return 0 for empty findings")
        void countBySeverity_quandoFindingsVazio_retornaZero() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), Instant.now(), null, null
            );

            assertEquals(0, result.criticalCount());
            assertEquals(0, result.highCount());
            assertEquals(0, result.mediumCount());
            assertEquals(0, result.lowCount());
        }

        @Test
        @DisplayName("should return total findings count")
        void totalFindings_quandoChamado_retornaTotal() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f1", Severity.HIGH, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f2", Severity.MEDIUM, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f3", Severity.LOW, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(3, result.totalFindings());
        }
    }

    @Nested
    @DisplayName("Risk score calculation")
    class RiskScore {

        @Test
        @DisplayName("should calculate risk score from findings")
        void riskScore_quandoChamado_calculaScore() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c1", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c2", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "h1", Severity.HIGH, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "m1", Severity.MEDIUM, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            // (2 * 100 + 1 * 75 + 1 * 50) / 5 = 65
            assertEquals(65, result.riskScore());
        }

        @Test
        @DisplayName("should return 0 for no findings")
        void riskScore_quandoSemFindings_retornaZero() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), Instant.now(), null, null
            );

            assertEquals(0, result.riskScore());
        }

        @Test
        @DisplayName("should cap score at 100")
        void riskScore_quandoMuitosFindings_retornaMaximoCem() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c1", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c2", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c3", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c4", Severity.CRITICAL, "cat", "loc", "ev", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "c5", Severity.CRITICAL, "cat", "loc", "ev", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            assertEquals(100, result.riskScore());
        }
    }

    @Nested
    @DisplayName("Duration calculation")
    class DurationCalculation {

        @Test
        @DisplayName("should calculate duration between startedAt and completedAt")
        void duration_quandoCompletado_retornaDuracao() {
            Instant startedAt = Instant.now();
            Instant completedAt = startedAt.plusSeconds(90);

            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), startedAt, completedAt, null, null
            );

            assertEquals(java.time.Duration.ofSeconds(90), result.duration());
        }

        @Test
        @DisplayName("should return null when not completed")
        void duration_quandoNaoCompletado_retornaNulo() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.RUNNING,
                    List.of(), Instant.now(), null, null, null
            );

            assertNull(result.duration());
        }

        @Test
        @DisplayName("should return null when startedAt is null")
        void duration_quandoStartedAtNulo_retornaNulo() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), null, Instant.now(), null, null
            );

            assertNull(result.duration());
        }
    }

    @Nested
    @DisplayName("With masked findings")
    class WithMaskedFindings {

        @Test
        @DisplayName("should mask secrets in all findings")
        void withMaskedFindings_quandoContemSegredos_mascaraTodos() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f1", Severity.HIGH, "cat", "loc", "password=secret", Instant.now()),
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f2", Severity.HIGH, "cat", "loc", "api_key=key123", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            ScanResult masked = result.withMaskedFindings();

            assertTrue(masked.findings().get(0).evidence().contains("****"));
            assertTrue(masked.findings().get(1).evidence().contains("****"));
        }

        @Test
        @DisplayName("should return new instance with masked findings")
        void withMaskedFindings_quandoChamado_naoModificaOriginal() {
            UUID scanId = UUID.randomUUID();
            TenantId tenantId = TenantId.generate();

            List<VulnerabilityFinding> findings = List.of(
                    VulnerabilityFinding.of(UUID.randomUUID(), scanId, tenantId, "f1", Severity.HIGH, "cat", "loc", "password=secret", Instant.now())
            );

            ScanResult result = ScanResult.of(
                    scanId, tenantId, ScanStatus.COMPLETED,
                    findings, Instant.now(), Instant.now(), null, null
            );

            result.withMaskedFindings();

            assertEquals("password=secret", result.findings().get(0).evidence());
        }
    }

    @Nested
    @DisplayName("Success indicator")
    class SuccessIndicator {

        @Test
        @DisplayName("should return true when COMPLETED")
        void isSuccessful_quandoCompleted_retornaTrue() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), Instant.now(), null, null
            );

            assertTrue(result.isSuccessful());
        }

        @Test
        @DisplayName("should return false when FAILED")
        void isSuccessful_quandoFailed_retornaFalse() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.FAILED,
                    List.of(), Instant.now(), null, null, "Error"
            );

            assertFalse(result.isSuccessful());
        }

        @Test
        @DisplayName("should return false when CANCELLED")
        void isSuccessful_quandoCancelled_retornaFalse() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.CANCELLED,
                    List.of(), Instant.now(), null, null, null
            );

            assertFalse(result.isSuccessful());
        }

        @Test
        @DisplayName("should return false when RUNNING")
        void isSuccessful_quandoRunning_retornaFalse() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.RUNNING,
                    List.of(), Instant.now(), null, null, null
            );

            assertFalse(result.isSuccessful());
        }

        @Test
        @DisplayName("should return false when PENDING")
        void isSuccessful_quandoPending_retornaFalse() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.PENDING,
                    List.of(), null, null, null, null
            );

            assertFalse(result.isSuccessful());
        }
    }

    @Nested
    @DisplayName("Error message")
    class ErrorMessage {

        @Test
        @DisplayName("should return error message when failed")
        void errorMessage_quandoFalhou_retornaMensagem() {
            String errorMsg = "Connection timeout after 30 seconds";
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.FAILED,
                    List.of(), Instant.now(), null, null, errorMsg
            );

            assertEquals(errorMsg, result.errorMessage());
        }

        @Test
        @DisplayName("should return null when not failed")
        void errorMessage_quandoNaoFalhou_retornaNulo() {
            ScanResult result = ScanResult.of(
                    UUID.randomUUID(), TenantId.generate(), ScanStatus.COMPLETED,
                    List.of(), Instant.now(), Instant.now(), null, null
            );

            assertNull(result.errorMessage());
        }
    }
}
