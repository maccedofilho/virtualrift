package com.virtualrift.tenant.service;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantQuotaRepository quotaRepository;
    private final ScanTargetRepository scanTargetRepository;

    public TenantService(TenantRepository tenantRepository,
                        TenantQuotaRepository quotaRepository,
                        ScanTargetRepository scanTargetRepository) {
        this.tenantRepository = tenantRepository;
        this.quotaRepository = quotaRepository;
        this.scanTargetRepository = scanTargetRepository;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        if (tenantRepository.existsBySlug(request.slug())) {
            throw new SlugAlreadyExistsException("Slug already exists: " + request.slug());
        }

        Tenant tenant = new Tenant(
                UUID.randomUUID(),
                request.name(),
                request.slug(),
                request.plan(),
                TenantStatus.PENDING_VERIFICATION
        );
        tenant = tenantRepository.save(tenant);

        TenantQuota quota = TenantQuota.forPlan(request.plan(), tenant.getId());
        quotaRepository.save(quota);

        return toResponse(tenant);
    }

    public TenantResponse getTenant(UUID id) {
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + id));
        return toResponse(tenant);
    }

    public TenantResponse getTenantBySlug(String slug) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + slug));
        return toResponse(tenant);
    }

    public TenantQuotaResponse getQuota(UUID tenantId) {
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Quota not found for tenant: " + tenantId));
        return new TenantQuotaResponse(
                quota.getMaxScansPerDay(),
                quota.getMaxConcurrentScans(),
                quota.getMaxScanTargets(),
                quota.getReportRetentionDays(),
                quota.isSastEnabled()
        );
    }

    public Plan getPlan(UUID tenantId) {
        return tenantRepository.findPlanById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
    }

    @Transactional
    public ScanTargetResponse addScanTarget(UUID tenantId, AddScanTargetRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Quota not found for tenant: " + tenantId));

        long currentTargets = scanTargetRepository.countByTenantId(tenantId);
        if (quota.getMaxScanTargets() > 0 && currentTargets >= quota.getMaxScanTargets()) {
            throw new TenantQuotaExceededException("Maximum scan targets limit reached");
        }

        if (scanTargetRepository.existsByTenantIdAndTarget(tenantId, request.target())) {
            throw new SlugAlreadyExistsException("Target already exists: " + request.target());
        }

        ScanTarget scanTarget = new ScanTarget(
                UUID.randomUUID(),
                tenantId,
                request.target(),
                request.type(),
                request.description()
        );
        scanTarget = scanTargetRepository.save(scanTarget);

        return new ScanTargetResponse(
                scanTarget.getId(),
                scanTarget.getTarget(),
                scanTarget.getType(),
                scanTarget.getDescription(),
                scanTarget.getCreatedAt()
        );
    }

    public List<ScanTargetResponse> getScanTargets(UUID tenantId) {
        return scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(st -> new ScanTargetResponse(
                        st.getId(),
                        st.getTarget(),
                        st.getType(),
                        st.getDescription(),
                        st.getCreatedAt()
                ))
                .toList();
    }

    public boolean isScanTargetAuthorized(UUID tenantId, String target, String scanType) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
        if (target == null || target.isBlank() || scanType == null || scanType.isBlank()) {
            return false;
        }

        return scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(scanTarget -> isCompatible(scanTarget.getType(), scanType))
                .anyMatch(scanTarget -> matches(scanTarget, target));
    }

    @Transactional
    public void removeScanTarget(UUID tenantId, UUID targetId) {
        ScanTarget scanTarget = scanTargetRepository.findById(targetId)
                .orElseThrow(() -> new TenantNotFoundException("Scan target not found: " + targetId));

        if (!scanTarget.getTenantId().equals(tenantId)) {
            throw new TenantNotFoundException("Scan target does not belong to tenant");
        }

        scanTargetRepository.delete(scanTarget);
    }

    public void validateQuota(UUID tenantId, String quotaType) {
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Quota not found for tenant: " + tenantId));

        // implement quota validation logic based on quotaType
        // this will be called by orchestrator before triggering scans
    }

    private boolean isCompatible(TargetType targetType, String scanType) {
        return switch (scanType.trim().toUpperCase(Locale.ROOT)) {
            case "WEB" -> targetType == TargetType.URL;
            case "API" -> targetType == TargetType.URL || targetType == TargetType.API_SPEC;
            case "NETWORK" -> targetType == TargetType.URL || targetType == TargetType.IP_RANGE;
            case "SAST" -> targetType == TargetType.REPOSITORY;
            default -> false;
        };
    }

    private boolean matches(ScanTarget scanTarget, String requestedTarget) {
        return switch (scanTarget.getType()) {
            case URL, API_SPEC -> hostMatches(scanTarget.getTarget(), requestedTarget);
            case REPOSITORY -> repositoryMatches(scanTarget.getTarget(), requestedTarget);
            case IP_RANGE -> ipRangeMatches(scanTarget.getTarget(), requestedTarget);
        };
    }

    private boolean hostMatches(String registeredTarget, String requestedTarget) {
        Optional<String> registeredHost = extractHost(registeredTarget);
        Optional<String> requestedHost = extractHost(requestedTarget);
        if (registeredHost.isEmpty() || requestedHost.isEmpty()) {
            return normalize(registeredTarget).equals(normalize(requestedTarget));
        }

        String registered = registeredHost.get();
        String requested = requestedHost.get();
        return requested.equals(registered) || requested.endsWith("." + registered);
    }

    private boolean repositoryMatches(String registeredTarget, String requestedTarget) {
        return normalizeRepository(registeredTarget).equals(normalizeRepository(requestedTarget));
    }

    private boolean ipRangeMatches(String registeredTarget, String requestedTarget) {
        Optional<String> requestedHost = extractHost(requestedTarget);
        if (requestedHost.isEmpty()) {
            return false;
        }

        String range = normalize(registeredTarget);
        String ip = requestedHost.get();
        if (!range.contains("/")) {
            return range.equals(ip);
        }

        String[] parts = range.split("/", 2);
        Optional<Long> base = ipv4ToLong(parts[0]);
        Optional<Long> candidate = ipv4ToLong(ip);
        if (base.isEmpty() || candidate.isEmpty()) {
            return false;
        }

        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefix < 0 || prefix > 32) {
            return false;
        }

        long mask = prefix == 0 ? 0 : 0xffffffffL << (32 - prefix);
        return (base.get() & mask) == (candidate.get() & mask);
    }

    private Optional<Long> ipv4ToLong(String value) {
        String[] octets = value.split("\\.");
        if (octets.length != 4) {
            return Optional.empty();
        }

        long result = 0;
        for (String octet : octets) {
            int parsed;
            try {
                parsed = Integer.parseInt(octet);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (parsed < 0 || parsed > 255) {
                return Optional.empty();
            }
            result = (result << 8) + parsed;
        }
        return Optional.of(result);
    }

    private Optional<String> extractHost(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim();
        try {
            URI uri = normalized.contains("://") ? URI.create(normalized) : URI.create("https://" + normalized);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String normalizeRepository(String value) {
        String normalized = normalize(value);
        try {
            URI uri = normalized.contains("://") ? URI.create(normalized) : URI.create("https://" + normalized);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || path == null || path.isBlank()) {
                return normalized;
            }
            String normalizedPath = path.endsWith(".git") ? path.substring(0, path.length() - 4) : path;
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT) + stripTrailingSlash(normalizedPath);
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }

    private String normalize(String value) {
        return stripTrailingSlash(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    private String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/") && result.length() > 1) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getPlan(),
                tenant.getStatus(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }
}
