package com.virtualrift.reports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.reports.client.OrchestratorClient;
import com.virtualrift.reports.client.TenantClient;
import com.virtualrift.reports.config.ReportsDatabaseContext;
import com.virtualrift.reports.dto.OrchestratorScanFindingResponse;
import com.virtualrift.reports.dto.OrchestratorScanResponse;
import com.virtualrift.reports.dto.OrchestratorScanResultResponse;
import com.virtualrift.reports.dto.ReportExportResource;
import com.virtualrift.reports.dto.ReportFindingResponse;
import com.virtualrift.reports.dto.ReportResponse;
import com.virtualrift.reports.dto.TenantQuotaResponse;
import com.virtualrift.reports.exception.ReportGenerationException;
import com.virtualrift.reports.exception.ReportNotFoundException;
import com.virtualrift.reports.exception.ReportNotReadyException;
import com.virtualrift.reports.kafka.ReportEventProducer;
import com.virtualrift.reports.model.Report;
import com.virtualrift.reports.model.ReportExportFormat;
import com.virtualrift.reports.repository.ReportRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final TypeReference<List<ReportFindingResponse>> FINDINGS_TYPE = new TypeReference<>() {};

    private final ReportRepository reportRepository;
    private final OrchestratorClient orchestratorClient;
    private final ObjectMapper objectMapper;
    private final ReportEventProducer eventProducer;
    private final TenantClient tenantClient;
    private final ReportsDatabaseContext databaseContext;

    public ReportService(ReportRepository reportRepository,
                         OrchestratorClient orchestratorClient,
                         ObjectMapper objectMapper,
                         ReportEventProducer eventProducer,
                         TenantClient tenantClient,
                         ReportsDatabaseContext databaseContext) {
        this.reportRepository = reportRepository;
        this.orchestratorClient = orchestratorClient;
        this.objectMapper = objectMapper;
        this.eventProducer = eventProducer;
        this.tenantClient = tenantClient;
        this.databaseContext = databaseContext;
    }

    @Transactional
    public ReportResponse generateReport(UUID scanId, UUID tenantId, String rolesHeader) {
        databaseContext.useTenant(tenantId);
        OrchestratorScanResponse scan = orchestratorClient.getScan(tenantId, scanId, rolesHeader);
        OrchestratorScanResultResponse result = orchestratorClient.getScanResult(tenantId, scanId, rolesHeader);
        TenantQuotaResponse quota = tenantClient.getQuota(tenantId, rolesHeader);

        List<OrchestratorScanFindingResponse> sourceFindings = result.findings() == null ? List.of() : result.findings();

        validateSnapshot(tenantId, scan, result, sourceFindings);
        databaseContext.lockReport(tenantId, scanId);

        List<ReportFindingResponse> findings = sourceFindings.stream()
                .map(this::toFindingResponse)
                .toList();

        Report report = reportRepository.findByTenantIdAndScanId(tenantId, scanId)
                .orElseGet(() -> new Report(UUID.randomUUID(), tenantId, scanId));

        Instant generatedAt = Instant.now();
        applySnapshot(report, scan, result, findings, generatedAt, quota.reportRetentionDays());
        Report saved = reportRepository.save(report);

        eventProducer.publishReportGenerated(saved.getId(), tenantId, scanId, saved.getGeneratedAt());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId, UUID tenantId) {
        databaseContext.useTenant(tenantId);
        return reportRepository.findByTenantIdAndId(tenantId, reportId)
                .map(this::toResponse)
                .orElseThrow(() -> new ReportNotFoundException("Report not found: " + reportId));
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(UUID tenantId, UUID scanId) {
        databaseContext.useTenant(tenantId);
        List<Report> reports = scanId == null
                ? reportRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId)
                : reportRepository.findByTenantIdAndScanIdOrderByGeneratedAtDesc(tenantId, scanId);

        return reports.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReportExportResource exportReport(UUID reportId, UUID tenantId, ReportExportFormat format) {
        databaseContext.useTenant(tenantId);
        Report report = reportRepository.findByTenantIdAndId(tenantId, reportId)
                .orElseThrow(() -> new ReportNotFoundException("Report not found: " + reportId));

        ReportResponse response = toResponse(report);
        String fileName = buildFileName(response, format);

        return switch (format) {
            case JSON -> new ReportExportResource(
                    fileName,
                    format.mediaType(),
                    writePrettyJson(response)
            );
            case HTML -> new ReportExportResource(
                    fileName,
                    MediaType.parseMediaType("text/html;charset=UTF-8"),
                    renderHtml(response).getBytes(StandardCharsets.UTF_8)
            );
        };
    }

    private void validateSnapshot(UUID tenantId,
                                  OrchestratorScanResponse scan,
                                  OrchestratorScanResultResponse result,
                                  List<OrchestratorScanFindingResponse> findings) {
        if (!tenantId.equals(scan.tenantId()) || !tenantId.equals(result.tenantId())) {
            throw new ReportGenerationException("Orchestrator returned scan data for a different tenant");
        }
        if (!scan.id().equals(result.scanId())) {
            throw new ReportGenerationException("Orchestrator returned inconsistent scan data");
        }
        if (!scan.status().isFinal()) {
            throw new ReportNotReadyException("Scan is not finished yet: " + scan.id());
        }
        if (scan.status() != result.status()) {
            throw new ReportGenerationException("Orchestrator returned inconsistent scan status");
        }
        if (result.totalFindings() != findings.size()) {
            throw new ReportGenerationException("Orchestrator returned inconsistent finding totals");
        }
        if (result.criticalCount() != countBySeverity(findings, Severity.CRITICAL)
                || result.highCount() != countBySeverity(findings, Severity.HIGH)
                || result.mediumCount() != countBySeverity(findings, Severity.MEDIUM)
                || result.lowCount() != countBySeverity(findings, Severity.LOW)
                || result.infoCount() != countBySeverity(findings, Severity.INFO)) {
            throw new ReportGenerationException("Orchestrator returned inconsistent finding severity totals");
        }
        for (OrchestratorScanFindingResponse finding : findings) {
            if (!tenantId.equals(finding.tenantId()) || !scan.id().equals(finding.scanId())) {
                throw new ReportGenerationException("Orchestrator returned finding data for a different scan");
            }
        }
    }

    private int countBySeverity(List<OrchestratorScanFindingResponse> findings, Severity severity) {
        return (int) findings.stream()
                .filter(finding -> finding.severity() == severity)
                .count();
    }

    private void applySnapshot(Report report,
                               OrchestratorScanResponse scan,
                               OrchestratorScanResultResponse result,
                               List<ReportFindingResponse> findings,
                               Instant generatedAt,
                               int retentionDays) {
        report.setUserId(scan.userId());
        report.setTarget(scan.target());
        report.setScanType(scan.scanType());
        report.setStatus(scan.status());
        report.setTotalFindings(result.totalFindings());
        report.setCriticalCount(result.criticalCount());
        report.setHighCount(result.highCount());
        report.setMediumCount(result.mediumCount());
        report.setLowCount(result.lowCount());
        report.setInfoCount(result.infoCount());
        report.setRiskScore(result.riskScore());
        report.setErrorMessage(result.errorMessage());
        report.setFindingsJson(writeFindings(findings));
        report.setScanCreatedAt(scan.createdAt());
        report.setScanStartedAt(result.startedAt());
        report.setScanCompletedAt(result.completedAt());
        report.setGeneratedAt(generatedAt);
        report.setExpiresAt(generatedAt.plus(Duration.ofDays(retentionDays)));
    }

    private String writeFindings(List<ReportFindingResponse> findings) {
        try {
            return objectMapper.writeValueAsString(findings);
        } catch (JsonProcessingException ex) {
            throw new ReportGenerationException("Failed to serialize report findings", ex);
        }
    }

    private List<ReportFindingResponse> readFindings(String findingsJson) {
        try {
            return objectMapper.readValue(findingsJson, FINDINGS_TYPE);
        } catch (JsonProcessingException ex) {
            throw new ReportGenerationException("Failed to deserialize report findings", ex);
        }
    }

    private ReportFindingResponse toFindingResponse(OrchestratorScanFindingResponse finding) {
        return new ReportFindingResponse(
                finding.id(),
                finding.title(),
                finding.severity(),
                finding.category(),
                finding.location(),
                finding.evidence(),
                finding.detectedAt()
        );
    }

    private ReportResponse toResponse(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getTenantId(),
                report.getScanId(),
                report.getUserId(),
                report.getTarget(),
                report.getScanType(),
                report.getStatus(),
                report.getTotalFindings(),
                report.getCriticalCount(),
                report.getHighCount(),
                report.getMediumCount(),
                report.getLowCount(),
                report.getInfoCount(),
                report.getRiskScore(),
                report.getErrorMessage(),
                report.getScanCreatedAt(),
                report.getScanStartedAt(),
                report.getScanCompletedAt(),
                report.getCreatedAt(),
                report.getGeneratedAt(),
                readFindings(report.getFindingsJson())
        );
    }

    private byte[] writePrettyJson(ReportResponse response) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(response);
        } catch (JsonProcessingException ex) {
            throw new ReportGenerationException("Failed to serialize report export", ex);
        }
    }

    private String buildFileName(ReportResponse report, ReportExportFormat format) {
        return "virtualrift-report-" + report.scanType().name().toLowerCase() + "-" + report.id() + "." + format.fileExtension();
    }

    private String renderHtml(ReportResponse report) {
        String findingsHtml = report.findings().isEmpty()
                ? "<p class=\"empty\">Nenhum finding persistido neste snapshot.</p>"
                : report.findings().stream()
                .map(finding -> """
                        <article class="finding-card">
                          <div class="finding-header">
                            <h3>%s</h3>
                            <span class="badge severity-%s">%s</span>
                          </div>
                          <p class="muted">%s · %s</p>
                          <div class="finding-grid">
                            <div>
                              <span class="label">Evidência</span>
                              <pre>%s</pre>
                            </div>
                            <div>
                              <span class="label">Detectado em</span>
                              <p>%s</p>
                            </div>
                          </div>
                        </article>
                        """.formatted(
                        escapeHtml(finding.title()),
                        finding.severity().name().toLowerCase(),
                        escapeHtml(finding.severity().name()),
                        escapeHtml(finding.category()),
                        escapeHtml(finding.location()),
                        escapeHtml(finding.evidence()),
                        escapeHtml(String.valueOf(finding.detectedAt()))
                ))
                .reduce("", String::concat);

        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Relatório %s</title>
                  <style>
                    :root {
                      color-scheme: light;
                      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", sans-serif;
                    }
                    body {
                      margin: 0;
                      padding: 40px;
                      background: linear-gradient(180deg, #f2f4f7 0%%, #faf5f0 100%%);
                      color: #1d1d1f;
                    }
                    main {
                      max-width: 1080px;
                      margin: 0 auto;
                    }
                    .card {
                      background: rgba(255, 255, 255, 0.72);
                      border: 1px solid rgba(255, 255, 255, 0.68);
                      backdrop-filter: blur(24px) saturate(1.6);
                      border-radius: 24px;
                      box-shadow: 0 24px 80px rgba(15, 23, 42, 0.08);
                      padding: 28px;
                      margin-bottom: 20px;
                    }
                    h1, h2, h3, p {
                      margin-top: 0;
                    }
                    h1 {
                      font-size: 48px;
                      letter-spacing: -0.03em;
                      margin-bottom: 12px;
                    }
                    h2 {
                      font-size: 24px;
                      letter-spacing: -0.02em;
                    }
                    h3 {
                      font-size: 18px;
                      letter-spacing: -0.01em;
                    }
                    .muted, .label {
                      color: #6e6e73;
                    }
                    .hero-topline {
                      text-transform: uppercase;
                      font-size: 14px;
                      letter-spacing: 0.06em;
                      color: #6e6e73;
                      font-weight: 600;
                    }
                    .grid, .finding-grid {
                      display: grid;
                      grid-template-columns: repeat(2, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .stats {
                      display: grid;
                      grid-template-columns: repeat(4, minmax(0, 1fr));
                      gap: 16px;
                    }
                    .stat, .finding-card {
                      background: rgba(255, 255, 255, 0.78);
                      border-radius: 18px;
                      padding: 18px;
                      border: 1px solid rgba(0, 0, 0, 0.04);
                    }
                    .stat strong {
                      display: block;
                      font-size: 28px;
                      margin-top: 10px;
                    }
                    .badge {
                      display: inline-flex;
                      align-items: center;
                      justify-content: center;
                      border-radius: 999px;
                      padding: 6px 12px;
                      font-size: 13px;
                      font-weight: 600;
                    }
                    .severity-critical { background: rgba(255, 59, 48, 0.12); color: #d11a2a; }
                    .severity-high { background: rgba(255, 149, 0, 0.12); color: #b86a00; }
                    .severity-medium { background: rgba(0, 113, 227, 0.12); color: #0071e3; }
                    .severity-low, .severity-info { background: rgba(52, 199, 89, 0.12); color: #248a41; }
                    .finding-header {
                      display: flex;
                      justify-content: space-between;
                      gap: 16px;
                      align-items: flex-start;
                    }
                    pre {
                      white-space: pre-wrap;
                      word-break: break-word;
                      font-family: "SF Mono", ui-monospace, monospace;
                      background: rgba(0, 0, 0, 0.03);
                      border-radius: 14px;
                      padding: 14px;
                    }
                    .empty {
                      color: #6e6e73;
                    }
                    @media print {
                      body {
                        padding: 0;
                        background: white;
                      }
                      .card {
                        box-shadow: none;
                        border-color: rgba(0, 0, 0, 0.08);
                        background: white;
                        break-inside: avoid;
                      }
                    }
                  </style>
                </head>
                <body>
                  <main>
                    <section class="card">
                      <p class="hero-topline">Virtualrift export</p>
                      <h1>Relatório de scan</h1>
                      <p class="muted">Snapshot exportado para compartilhamento, impressão ou arquivamento operacional.</p>
                    </section>

                    <section class="card">
                      <h2>Resumo do relatório</h2>
                      <div class="grid">
                        <div><span class="label">ID do relatório</span><p>%s</p></div>
                        <div><span class="label">ID do scan</span><p>%s</p></div>
                        <div><span class="label">Tenant</span><p>%s</p></div>
                        <div><span class="label">Tipo</span><p>%s</p></div>
                        <div><span class="label">Target</span><p>%s</p></div>
                        <div><span class="label">Gerado em</span><p>%s</p></div>
                        <div><span class="label">Scan iniciado em</span><p>%s</p></div>
                        <div><span class="label">Scan concluído em</span><p>%s</p></div>
                      </div>
                    </section>

                    <section class="card">
                      <h2>Métricas</h2>
                      <div class="stats">
                        <div class="stat"><span class="label">Risco</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Total</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Críticos</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Altos</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Médios</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Baixos</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Informativos</span><strong>%d</strong></div>
                        <div class="stat"><span class="label">Status</span><strong>%s</strong></div>
                      </div>
                    </section>

                    <section class="card">
                      <h2>Findings persistidos</h2>
                      %s
                    </section>
                  </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(report.id().toString()),
                escapeHtml(report.id().toString()),
                escapeHtml(report.scanId().toString()),
                escapeHtml(report.tenantId().toString()),
                escapeHtml(report.scanType().name()),
                escapeHtml(report.target()),
                escapeHtml(String.valueOf(report.generatedAt())),
                escapeHtml(String.valueOf(report.scanStartedAt())),
                escapeHtml(String.valueOf(report.scanCompletedAt())),
                report.riskScore(),
                report.totalFindings(),
                report.criticalCount(),
                report.highCount(),
                report.mediumCount(),
                report.lowCount(),
                report.infoCount(),
                escapeHtml(report.status().name()),
                findingsHtml
        );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "—";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
