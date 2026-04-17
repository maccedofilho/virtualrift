package com.virtualrift.reports.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.Severity;
import com.virtualrift.reports.client.OrchestratorClient;
import com.virtualrift.reports.dto.OrchestratorScanFindingResponse;
import com.virtualrift.reports.dto.OrchestratorScanResponse;
import com.virtualrift.reports.dto.OrchestratorScanResultResponse;
import com.virtualrift.reports.dto.ReportFindingResponse;
import com.virtualrift.reports.dto.ReportResponse;
import com.virtualrift.reports.exception.ReportGenerationException;
import com.virtualrift.reports.exception.ReportNotFoundException;
import com.virtualrift.reports.exception.ReportNotReadyException;
import com.virtualrift.reports.kafka.ReportEventProducer;
import com.virtualrift.reports.model.Report;
import com.virtualrift.reports.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportService Tests")
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private OrchestratorClient orchestratorClient;

    @Mock
    private ReportEventProducer eventProducer;

    private ReportService service;
    private ObjectMapper objectMapper;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SCAN_ID = UUID.randomUUID();
    private static final String TARGET = "https://example.com";
    private static final Instant CREATED_AT = Instant.parse("2026-04-17T01:00:00Z");
    private static final Instant STARTED_AT = Instant.parse("2026-04-17T01:01:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-04-17T01:02:00Z");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new ReportService(reportRepository, orchestratorClient, objectMapper, eventProducer);
    }

    private OrchestratorScanResponse scan(ScanStatus status) {
        return new OrchestratorScanResponse(
                SCAN_ID,
                TENANT_ID,
                USER_ID,
                TARGET,
                ScanType.WEB,
                status,
                3,
                300,
                null,
                CREATED_AT,
                STARTED_AT,
                status.isFinal() ? COMPLETED_AT : null
        );
    }

    private OrchestratorScanResultResponse result(List<OrchestratorScanFindingResponse> findings) {
        return new OrchestratorScanResultResponse(
                SCAN_ID,
                TENANT_ID,
                ScanStatus.COMPLETED,
                findings.size(),
                countBySeverity(findings, Severity.CRITICAL),
                countBySeverity(findings, Severity.HIGH),
                countBySeverity(findings, Severity.MEDIUM),
                countBySeverity(findings, Severity.LOW),
                countBySeverity(findings, Severity.INFO),
                50,
                null,
                STARTED_AT,
                COMPLETED_AT,
                findings
        );
    }

    private int countBySeverity(List<OrchestratorScanFindingResponse> findings, Severity severity) {
        return (int) findings.stream()
                .filter(finding -> finding.severity() == severity)
                .count();
    }

    private OrchestratorScanFindingResponse finding(Severity severity) {
        return new OrchestratorScanFindingResponse(
                UUID.randomUUID(),
                SCAN_ID,
                TENANT_ID,
                severity.name() + " finding",
                severity,
                "WEB",
                "/login",
                "payload=****",
                COMPLETED_AT
        );
    }

    private Report storedReport(UUID reportId, UUID tenantId, UUID scanId) throws Exception {
        Report report = new Report(reportId, tenantId, scanId);
        report.setUserId(USER_ID);
        report.setTarget(TARGET);
        report.setScanType(ScanType.WEB);
        report.setStatus(ScanStatus.COMPLETED);
        report.setTotalFindings(1);
        report.setCriticalCount(1);
        report.setHighCount(0);
        report.setMediumCount(0);
        report.setLowCount(0);
        report.setInfoCount(0);
        report.setRiskScore(50);
        report.setFindingsJson(objectMapper.writeValueAsString(List.of(
                new ReportFindingResponse(UUID.randomUUID(), "CRITICAL finding", Severity.CRITICAL, "WEB", "/login", "payload=****", COMPLETED_AT)
        )));
        report.setScanCreatedAt(CREATED_AT);
        report.setScanStartedAt(STARTED_AT);
        report.setScanCompletedAt(COMPLETED_AT);
        report.setGeneratedAt(COMPLETED_AT);
        return report;
    }

    @Nested
    @DisplayName("Generate report")
    class GenerateReport {

        @Test
        @DisplayName("should generate and persist report snapshot when scan is completed")
        void generateReport_quandoScanCompleto_persisteSnapshot() {
            OrchestratorScanFindingResponse finding = finding(Severity.CRITICAL);

            when(orchestratorClient.getScan(TENANT_ID, SCAN_ID)).thenReturn(scan(ScanStatus.COMPLETED));
            when(orchestratorClient.getScanResult(TENANT_ID, SCAN_ID)).thenReturn(result(List.of(finding)));
            when(reportRepository.findByTenantIdAndScanId(TENANT_ID, SCAN_ID)).thenReturn(Optional.empty());
            when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReportResponse response = service.generateReport(SCAN_ID, TENANT_ID);

            assertNotNull(response.id());
            assertEquals(TENANT_ID, response.tenantId());
            assertEquals(SCAN_ID, response.scanId());
            assertEquals(USER_ID, response.userId());
            assertEquals(ScanType.WEB, response.scanType());
            assertEquals(ScanStatus.COMPLETED, response.status());
            assertEquals(1, response.totalFindings());
            assertEquals(1, response.criticalCount());
            assertEquals(50, response.riskScore());
            assertEquals(1, response.findings().size());
            assertEquals("payload=****", response.findings().getFirst().evidence());
            verify(reportRepository).save(any(Report.class));
            verify(eventProducer).publishReportGenerated(response.id(), TENANT_ID, SCAN_ID, response.generatedAt());
        }

        @Test
        @DisplayName("should update existing report snapshot for the same tenant and scan")
        void generateReport_quandoRelatorioExiste_atualizaSnapshot() throws Exception {
            UUID reportId = UUID.randomUUID();
            Report existing = storedReport(reportId, TENANT_ID, SCAN_ID);

            when(orchestratorClient.getScan(TENANT_ID, SCAN_ID)).thenReturn(scan(ScanStatus.COMPLETED));
            when(orchestratorClient.getScanResult(TENANT_ID, SCAN_ID)).thenReturn(result(List.of(finding(Severity.CRITICAL))));
            when(reportRepository.findByTenantIdAndScanId(TENANT_ID, SCAN_ID)).thenReturn(Optional.of(existing));
            when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ReportResponse response = service.generateReport(SCAN_ID, TENANT_ID);

            assertEquals(reportId, response.id());
            assertEquals(SCAN_ID, response.scanId());
            verify(reportRepository).save(existing);
            verify(eventProducer).publishReportGenerated(reportId, TENANT_ID, SCAN_ID, response.generatedAt());
        }

        @Test
        @DisplayName("should reject report generation while scan is still running")
        void generateReport_quandoScanNaoFinalizado_lancaReportNotReadyException() {
            when(orchestratorClient.getScan(TENANT_ID, SCAN_ID)).thenReturn(scan(ScanStatus.RUNNING));
            when(orchestratorClient.getScanResult(TENANT_ID, SCAN_ID)).thenReturn(result(List.of()));

            assertThrows(ReportNotReadyException.class, () -> service.generateReport(SCAN_ID, TENANT_ID));
            verify(reportRepository, never()).save(any());
            verify(eventProducer, never()).publishReportGenerated(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should reject inconsistent tenant data from orchestrator")
        void generateReport_quandoOrchestratorRetornaOutroTenant_lancaReportGenerationException() {
            UUID otherTenantId = UUID.randomUUID();
            OrchestratorScanResponse scan = new OrchestratorScanResponse(
                    SCAN_ID,
                    otherTenantId,
                    USER_ID,
                    TARGET,
                    ScanType.WEB,
                    ScanStatus.COMPLETED,
                    3,
                    300,
                    null,
                    CREATED_AT,
                    STARTED_AT,
                    COMPLETED_AT
            );

            when(orchestratorClient.getScan(TENANT_ID, SCAN_ID)).thenReturn(scan);
            when(orchestratorClient.getScanResult(TENANT_ID, SCAN_ID)).thenReturn(result(List.of()));

            assertThrows(ReportGenerationException.class, () -> service.generateReport(SCAN_ID, TENANT_ID));
            verify(reportRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject finding from another tenant or scan")
        void generateReport_quandoFindingNaoPertenceAoScan_lancaReportGenerationException() {
            OrchestratorScanFindingResponse finding = new OrchestratorScanFindingResponse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    TENANT_ID,
                    "CRITICAL finding",
                    Severity.CRITICAL,
                    "WEB",
                    "/login",
                    "payload=****",
                    COMPLETED_AT
            );

            when(orchestratorClient.getScan(TENANT_ID, SCAN_ID)).thenReturn(scan(ScanStatus.COMPLETED));
            when(orchestratorClient.getScanResult(TENANT_ID, SCAN_ID)).thenReturn(result(List.of(finding)));

            assertThrows(ReportGenerationException.class, () -> service.generateReport(SCAN_ID, TENANT_ID));
            verify(reportRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Read reports")
    class ReadReports {

        @Test
        @DisplayName("should return report when tenant owns it")
        void getReport_quandoPertenceAoTenant_retornaRelatorio() throws Exception {
            UUID reportId = UUID.randomUUID();
            Report report = storedReport(reportId, TENANT_ID, SCAN_ID);

            when(reportRepository.findByTenantIdAndId(TENANT_ID, reportId)).thenReturn(Optional.of(report));

            ReportResponse response = service.getReport(reportId, TENANT_ID);

            assertEquals(reportId, response.id());
            assertEquals(TENANT_ID, response.tenantId());
            assertEquals(1, response.findings().size());
        }

        @Test
        @DisplayName("should throw when report is not found for tenant")
        void getReport_quandoNaoPertenceAoTenant_lancaReportNotFoundException() {
            UUID reportId = UUID.randomUUID();

            when(reportRepository.findByTenantIdAndId(TENANT_ID, reportId)).thenReturn(Optional.empty());

            assertThrows(ReportNotFoundException.class, () -> service.getReport(reportId, TENANT_ID));
        }

        @Test
        @DisplayName("should list reports by tenant and optional scan id")
        void listReports_quandoScanIdInformado_filtraPorScan() throws Exception {
            Report report = storedReport(UUID.randomUUID(), TENANT_ID, SCAN_ID);

            when(reportRepository.findByTenantIdAndScanIdOrderByGeneratedAtDesc(TENANT_ID, SCAN_ID))
                    .thenReturn(List.of(report));

            List<ReportResponse> response = service.listReports(TENANT_ID, SCAN_ID);

            assertEquals(1, response.size());
            assertEquals(SCAN_ID, response.getFirst().scanId());
        }
    }
}
