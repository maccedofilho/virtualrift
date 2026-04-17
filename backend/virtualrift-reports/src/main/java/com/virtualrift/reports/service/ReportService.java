package com.virtualrift.reports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.ScanStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ReportService {

    private static final TypeReference<List<ReportFindingResponse>> FINDINGS_TYPE = new TypeReference<>() {};

    private final ReportRepository reportRepository;
    private final OrchestratorClient orchestratorClient;
    private final ObjectMapper objectMapper;
    private final ReportEventProducer eventProducer;

    public ReportService(ReportRepository reportRepository,
                         OrchestratorClient orchestratorClient,
                         ObjectMapper objectMapper,
                         ReportEventProducer eventProducer) {
        this.reportRepository = reportRepository;
        this.orchestratorClient = orchestratorClient;
        this.objectMapper = objectMapper;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public ReportResponse generateReport(UUID scanId, UUID tenantId) {
        OrchestratorScanResponse scan = orchestratorClient.getScan(tenantId, scanId);
        OrchestratorScanResultResponse result = orchestratorClient.getScanResult(tenantId, scanId);

        List<OrchestratorScanFindingResponse> sourceFindings = result.findings() == null ? List.of() : result.findings();

        validateSnapshot(tenantId, scan, result, sourceFindings);

        List<ReportFindingResponse> findings = sourceFindings.stream()
                .map(this::toFindingResponse)
                .toList();

        Report report = reportRepository.findByTenantIdAndScanId(tenantId, scanId)
                .orElseGet(() -> new Report(UUID.randomUUID(), tenantId, scanId));

        Instant generatedAt = Instant.now();
        applySnapshot(report, scan, result, findings, generatedAt);
        Report saved = reportRepository.save(report);

        eventProducer.publishReportGenerated(saved.getId(), tenantId, scanId, saved.getGeneratedAt());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID reportId, UUID tenantId) {
        return reportRepository.findByTenantIdAndId(tenantId, reportId)
                .map(this::toResponse)
                .orElseThrow(() -> new ReportNotFoundException("Report not found: " + reportId));
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(UUID tenantId, UUID scanId) {
        List<Report> reports = scanId == null
                ? reportRepository.findByTenantIdOrderByGeneratedAtDesc(tenantId)
                : reportRepository.findByTenantIdAndScanIdOrderByGeneratedAtDesc(tenantId, scanId);

        return reports.stream()
                .map(this::toResponse)
                .toList();
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
                               Instant generatedAt) {
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
}
