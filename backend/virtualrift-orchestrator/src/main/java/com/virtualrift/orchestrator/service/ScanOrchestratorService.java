package com.virtualrift.orchestrator.service;

import com.virtualrift.common.model.Severity;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.common.model.TenantId;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanFindingResponse;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.dto.ScanResultResponse;
import com.virtualrift.orchestrator.exception.InvalidScanCredentialsException;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.exception.ScanQuotaExceededException;
import com.virtualrift.orchestrator.exception.ScanTargetNotAuthorizedException;
import com.virtualrift.orchestrator.exception.ScanTypeNotAllowedException;
import com.virtualrift.orchestrator.kafka.ScanEventProducer;
import com.virtualrift.orchestrator.model.Scan;
import com.virtualrift.orchestrator.model.ScanFinding;
import com.virtualrift.orchestrator.repository.ScanFindingRepository;
import com.virtualrift.orchestrator.repository.ScanRepository;
import com.virtualrift.tenant.client.TenantClient;
import com.virtualrift.tenant.model.TenantQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ScanOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestratorService.class);
    private static final Pattern TOKEN_NAME_PATTERN = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\r\\n\\u0000]");
    private static final Set<String> BLOCKED_HEADER_NAMES = Set.of(
            "connection",
            "content-length",
            "cookie",
            "host",
            "transfer-encoding"
    );
    private static final int MAX_HEADERS = 10;
    private static final int MAX_COOKIES = 10;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_VALUE_LENGTH = 4096;

    private final ScanRepository scanRepository;
    private final ScanFindingRepository scanFindingRepository;
    private final ScanEventProducer eventProducer;
    private final TenantClient tenantClient;

    public ScanOrchestratorService(ScanRepository scanRepository,
                                  ScanFindingRepository scanFindingRepository,
                                  ScanEventProducer eventProducer,
                                  TenantClient tenantClient) {
        this.scanRepository = scanRepository;
        this.scanFindingRepository = scanFindingRepository;
        this.eventProducer = eventProducer;
        this.tenantClient = tenantClient;
    }

    @Transactional
    public ScanResponse createScan(CreateScanRequest request, UUID tenantId, UUID userId) {
        TenantQuota quota = tenantClient.getQuota(tenantId);
        Plan plan = tenantClient.getPlan(tenantId);
        ScanAuthenticationContext authenticationContext = normalizeAuthenticationContext(request);

        validateScanTypeAllowed(request.scanType(), plan);
        validateTargetAuthorized(tenantId, request.target(), request.scanType());
        validateDailyQuota(tenantId, quota);
        validateConcurrentScans(tenantId, quota);

        Scan scan = new Scan(
                UUID.randomUUID(),
                tenantId,
                userId,
                request.target(),
                request.scanType(),
                request.depth(),
                request.timeout(),
                com.virtualrift.common.model.ScanStatus.PENDING
        );
        scan = scanRepository.save(scan);

        eventProducer.publishScanRequested(
                scan.getId(),
                new TenantId(tenantId),
                scan.getTarget(),
                scan.getScanType().name(),
                scan.getDepth(),
                scan.getTimeout(),
                authenticationContext.headers(),
                authenticationContext.cookies()
        );

        return toResponse(scan);
    }

    public ScanResponse getScan(UUID scanId, UUID tenantId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan not found: " + scanId));

        if (!scan.getTenantId().equals(tenantId)) {
            throw new ScanNotFoundException("Scan not found: " + scanId);
        }

        return toResponse(scan);
    }

    public ScanResponse getScanByTenantAndId(UUID tenantId, UUID scanId) {
        return getScan(scanId, tenantId);
    }

    public List<ScanResponse> listScans(UUID tenantId) {
        return scanRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ScanResponse getStatus(UUID scanId, UUID tenantId) {
        return getScan(scanId, tenantId);
    }

    public List<ScanFindingResponse> getFindings(UUID scanId, UUID tenantId) {
        getScan(scanId, tenantId);
        return scanFindingRepository.findByTenantIdAndScanIdOrderByDetectedAtDesc(tenantId, scanId).stream()
                .map(this::toFindingResponse)
                .toList();
    }

    public ScanResultResponse getResult(UUID scanId, UUID tenantId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new ScanNotFoundException("Scan not found: " + scanId));

        if (!scan.getTenantId().equals(tenantId)) {
            throw new ScanNotFoundException("Scan not found: " + scanId);
        }

        List<ScanFindingResponse> findings = scanFindingRepository
                .findByTenantIdAndScanIdOrderByDetectedAtDesc(tenantId, scanId).stream()
                .map(this::toFindingResponse)
                .toList();

        return new ScanResultResponse(
                scan.getId(),
                scan.getTenantId(),
                scan.getStatus(),
                findings.size(),
                countBySeverity(findings, Severity.CRITICAL),
                countBySeverity(findings, Severity.HIGH),
                countBySeverity(findings, Severity.MEDIUM),
                countBySeverity(findings, Severity.LOW),
                countBySeverity(findings, Severity.INFO),
                calculateRiskScore(findings),
                scan.getErrorMessage(),
                scan.getStartedAt(),
                scan.getCompletedAt(),
                findings
        );
    }

    private void validateScanTypeAllowed(ScanType scanType, Plan plan) {
        if (!isScanTypeAllowed(scanType, plan)) {
            throw new ScanTypeNotAllowedException(
                    "Scan type " + scanType + " is not allowed for plan " + plan
            );
        }
    }

    private boolean isScanTypeAllowed(ScanType scanType, Plan plan) {
        return switch (plan) {
            case TRIAL -> scanType == ScanType.WEB;
            case STARTER -> scanType == ScanType.WEB || scanType == ScanType.API;
            case PROFESSIONAL, ENTERPRISE -> true;
        };
    }

    private void validateTargetAuthorized(UUID tenantId, String target, ScanType scanType) {
        if (!tenantClient.isScanTargetAuthorized(tenantId, target, scanType)) {
            throw new ScanTargetNotAuthorizedException(
                    "Target is not registered or authorized for tenant: " + tenantId
            );
        }
    }

    private void validateDailyQuota(UUID tenantId, TenantQuota quota) {
        long todayCount = scanRepository.countByTenantIdSince(tenantId, Instant.now().minus(Duration.ofDays(1)));
        int dailyLimit = quota.getMaxScansPerDay();

        if (dailyLimit > 0 && todayCount >= dailyLimit) {
            throw new ScanQuotaExceededException("Daily scan quota exceeded");
        }
    }

    private void validateConcurrentScans(UUID tenantId, TenantQuota quota) {
        long runningCount = scanRepository.countByTenantIdAndStatus(
                tenantId, com.virtualrift.common.model.ScanStatus.RUNNING
        );

        if (runningCount >= quota.getMaxConcurrentScans()) {
            throw new ScanQuotaExceededException("Maximum concurrent scans limit reached");
        }
    }

    private ScanAuthenticationContext normalizeAuthenticationContext(CreateScanRequest request) {
        Map<String, String> headers = normalizeHeaders(request.headers());
        Map<String, String> cookies = normalizeCookies(request.cookies());

        switch (request.scanType()) {
            case NETWORK -> {
                if (!headers.isEmpty() || !cookies.isEmpty()) {
                    throw new InvalidScanCredentialsException("NETWORK scans do not support authentication headers or cookies");
                }
            }
            case SAST -> {
                if (!cookies.isEmpty()) {
                    throw new InvalidScanCredentialsException("SAST scans do not support cookies");
                }
            }
            case WEB, API -> {
                // WEB and API scans may use both headers and cookies.
            }
        }

        return new ScanAuthenticationContext(headers, cookies);
    }

    private Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        if (headers.size() > MAX_HEADERS) {
            throw new InvalidScanCredentialsException("Too many authentication headers were provided");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> seenNames = new HashSet<>();

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = normalizeName(entry.getKey(), "header");
            String lowerCaseName = name.toLowerCase(Locale.ROOT);
            if (!seenNames.add(lowerCaseName)) {
                throw new InvalidScanCredentialsException("Duplicate authentication header: " + name);
            }
            if (BLOCKED_HEADER_NAMES.contains(lowerCaseName)) {
                throw new InvalidScanCredentialsException("Header is not allowed for scan authentication: " + name);
            }

            normalized.put(name, normalizeValue(entry.getValue(), "header " + name));
        }

        return Map.copyOf(normalized);
    }

    private Map<String, String> normalizeCookies(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return Map.of();
        }
        if (cookies.size() > MAX_COOKIES) {
            throw new InvalidScanCredentialsException("Too many authentication cookies were provided");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        Set<String> seenNames = new HashSet<>();

        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            String name = normalizeName(entry.getKey(), "cookie");
            if (!seenNames.add(name)) {
                throw new InvalidScanCredentialsException("Duplicate authentication cookie: " + name);
            }

            normalized.put(name, normalizeValue(entry.getValue(), "cookie " + name));
        }

        return Map.copyOf(normalized);
    }

    private String normalizeName(String rawName, String fieldLabel) {
        if (rawName == null) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " name cannot be null");
        }

        String normalized = rawName.trim();
        if (normalized.isEmpty()) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " name cannot be blank");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " name is too long");
        }
        if (!TOKEN_NAME_PATTERN.matcher(normalized).matches()) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " name contains invalid characters");
        }

        return normalized;
    }

    private String normalizeValue(String rawValue, String fieldLabel) {
        if (rawValue == null) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " value cannot be null");
        }

        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " value cannot be blank");
        }
        if (normalized.length() > MAX_VALUE_LENGTH) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " value is too long");
        }
        if (CONTROL_CHARACTER_PATTERN.matcher(normalized).find()) {
            throw new InvalidScanCredentialsException("Authentication " + fieldLabel + " value contains control characters");
        }

        return normalized;
    }

    private ScanResponse toResponse(Scan scan) {
        return new ScanResponse(
                scan.getId(),
                scan.getTenantId(),
                scan.getUserId(),
                scan.getTarget(),
                scan.getScanType(),
                scan.getStatus(),
                scan.getDepth(),
                scan.getTimeout(),
                scan.getErrorMessage(),
                scan.getCreatedAt(),
                scan.getStartedAt(),
                scan.getCompletedAt()
        );
    }

    private ScanFindingResponse toFindingResponse(ScanFinding finding) {
        return new ScanFindingResponse(
                finding.getId(),
                finding.getScanId(),
                finding.getTenantId(),
                finding.getTitle(),
                finding.getSeverity(),
                finding.getCategory(),
                finding.getLocation(),
                finding.getEvidence(),
                finding.getDetectedAt()
        );
    }

    private int countBySeverity(List<ScanFindingResponse> findings, Severity severity) {
        return (int) findings.stream()
                .filter(finding -> finding.severity() == severity)
                .count();
    }

    private int calculateRiskScore(List<ScanFindingResponse> findings) {
        if (findings.isEmpty()) {
            return 0;
        }

        int score = findings.stream()
                .mapToInt(finding -> finding.severity().score())
                .sum();

        int normalized = Math.min(100, score / 5);
        if (countBySeverity(findings, Severity.CRITICAL) > 0 && normalized < 50) {
            return 50;
        }
        return normalized;
    }

    private record ScanAuthenticationContext(Map<String, String> headers, Map<String, String> cookies) {
    }
}
