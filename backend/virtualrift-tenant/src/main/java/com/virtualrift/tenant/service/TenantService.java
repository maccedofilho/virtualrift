package com.virtualrift.tenant.service;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.BillingSummaryResponse;
import com.virtualrift.tenant.dto.BillingUsageResponse;
import com.virtualrift.tenant.dto.CreatePlanChangeRequestRequest;
import com.virtualrift.tenant.dto.CreateTenantInvitationRequest;
import com.virtualrift.tenant.dto.InternalAcceptTenantInvitationResponse;
import com.virtualrift.tenant.dto.InternalTenantInvitationPreviewResponse;
import com.virtualrift.tenant.dto.PlanChangeRequestResponse;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.InternalProvisionTenantRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.TenantInvitationResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.exception.TenantInvitationConflictException;
import com.virtualrift.tenant.exception.TenantInvitationNotFoundException;
import com.virtualrift.tenant.exception.InvalidPlanChangeRequestException;
import com.virtualrift.tenant.exception.PlanChangeRequestAlreadyPendingException;
import com.virtualrift.tenant.exception.SlugAlreadyExistsException;
import com.virtualrift.tenant.exception.TenantNotFoundException;
import com.virtualrift.tenant.exception.TenantQuotaExceededException;
import com.virtualrift.tenant.model.Plan;
import com.virtualrift.tenant.model.PlanChangeRequest;
import com.virtualrift.tenant.model.PlanChangeRequestStatus;
import com.virtualrift.tenant.model.TenantInvitation;
import com.virtualrift.tenant.model.TenantInvitationStatus;
import com.virtualrift.tenant.model.ScanTarget;
import com.virtualrift.tenant.model.ScanTargetVerificationStatus;
import com.virtualrift.tenant.model.TargetType;
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.PlanChangeRequestRepository;
import com.virtualrift.tenant.repository.TenantInvitationRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import com.virtualrift.common.security.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TenantService {

    private static final SecureRandom INVITATION_RANDOM = new SecureRandom();
    private static final int DEFAULT_INVITATION_EXPIRY_DAYS = 7;

    private final TenantRepository tenantRepository;
    private final TenantQuotaRepository quotaRepository;
    private final ScanTargetRepository scanTargetRepository;
    private final PlanChangeRequestRepository planChangeRequestRepository;
    private final TenantInvitationRepository tenantInvitationRepository;
    private final ScanTargetOwnershipVerifier scanTargetOwnershipVerifier;

    public TenantService(TenantRepository tenantRepository,
                        TenantQuotaRepository quotaRepository,
                        ScanTargetRepository scanTargetRepository,
                        PlanChangeRequestRepository planChangeRequestRepository,
                        TenantInvitationRepository tenantInvitationRepository,
                        ScanTargetOwnershipVerifier scanTargetOwnershipVerifier) {
        this.tenantRepository = tenantRepository;
        this.quotaRepository = quotaRepository;
        this.scanTargetRepository = scanTargetRepository;
        this.planChangeRequestRepository = planChangeRequestRepository;
        this.tenantInvitationRepository = tenantInvitationRepository;
        this.scanTargetOwnershipVerifier = scanTargetOwnershipVerifier;
    }

    @Transactional
    public TenantResponse createTenant(CreateTenantRequest request) {
        return createTenantInternal(
                UUID.randomUUID(),
                request.name(),
                request.slug(),
                request.plan(),
                TenantStatus.PENDING_VERIFICATION
        );
    }

    @Transactional
    public TenantResponse provisionTenant(InternalProvisionTenantRequest request) {
        return createTenantInternal(
                request.id(),
                request.name(),
                request.slug(),
                request.plan(),
                request.status()
        );
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

    public BillingSummaryResponse getBillingSummary(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
        TenantQuota quota = quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Quota not found for tenant: " + tenantId));
        long scanTargetsUsed = scanTargetRepository.countByTenantId(tenantId);
        Integer scanTargetsRemaining = quota.getMaxScanTargets() < 0
                ? null
                : Math.max(quota.getMaxScanTargets() - (int) scanTargetsUsed, 0);
        PlanChangeRequestResponse pendingPlanChangeRequest = planChangeRequestRepository
                .findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, PlanChangeRequestStatus.PENDING)
                .map(this::toResponse)
                .orElse(null);

        return new BillingSummaryResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus(),
                tenant.getPlan(),
                new TenantQuotaResponse(
                        quota.getMaxScansPerDay(),
                        quota.getMaxConcurrentScans(),
                        quota.getMaxScanTargets(),
                        quota.getReportRetentionDays(),
                        quota.isSastEnabled()
                ),
                new BillingUsageResponse(scanTargetsUsed, scanTargetsRemaining),
                pendingPlanChangeRequest
        );
    }

    @Transactional
    public PlanChangeRequestResponse createPlanChangeRequest(
            UUID tenantId,
            UUID requestedByUserId,
            CreatePlanChangeRequestRequest request
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        if (request.requestedPlan() == tenant.getPlan()) {
            throw new InvalidPlanChangeRequestException("Requested plan must differ from the current tenant plan");
        }

        if (planChangeRequestRepository.existsByTenantIdAndStatus(tenantId, PlanChangeRequestStatus.PENDING)) {
            throw new PlanChangeRequestAlreadyPendingException("There is already a pending plan change request for this tenant");
        }

        PlanChangeRequest planChangeRequest = new PlanChangeRequest(
                UUID.randomUUID(),
                tenantId,
                requestedByUserId,
                tenant.getPlan(),
                request.requestedPlan(),
                PlanChangeRequestStatus.PENDING,
                normalizeNote(request.note())
        );

        return toResponse(planChangeRequestRepository.save(planChangeRequest));
    }

    @Transactional
    public TenantInvitationResponse createInvitation(
            UUID tenantId,
            UUID invitedByUserId,
            CreateTenantInvitationRequest request
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));

        String normalizedEmail = normalizeEmail(request.email());
        if (tenantInvitationRepository.existsByTenantIdAndEmailAndStatus(tenantId, normalizedEmail, TenantInvitationStatus.PENDING)) {
            throw new TenantInvitationConflictException("There is already a pending invitation for this email in the current workspace");
        }

        String rawToken = generateInvitationToken();
        TenantInvitation invitation = new TenantInvitation(
                UUID.randomUUID(),
                tenant.getId(),
                normalizedEmail,
                request.role(),
                hashToken(rawToken),
                TenantInvitationStatus.PENDING,
                invitedByUserId,
                Instant.now().plusSeconds(resolveExpiryDays(request.expiresInDays()) * 86400L)
        );

        TenantInvitation savedInvitation = tenantInvitationRepository.save(invitation);
        return toResponse(savedInvitation, rawToken);
    }

    @Transactional(readOnly = true)
    public List<TenantInvitationResponse> listInvitations(UUID tenantId) {
        requireTenantExists(tenantId);
        return tenantInvitationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(invitation -> toResponse(invitation, null))
                .toList();
    }

    @Transactional
    public void revokeInvitation(UUID tenantId, UUID invitationId) {
        TenantInvitation invitation = findInvitationForTenant(tenantId, invitationId);
        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new TenantInvitationConflictException("Only pending invitations can be revoked");
        }
        invitation.markRevoked();
        tenantInvitationRepository.save(invitation);
    }

    @Transactional
    public InternalTenantInvitationPreviewResponse previewInvitation(String token) {
        TenantInvitation invitation = findPendingInvitationByToken(token);
        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + invitation.getTenantId()));

        return new InternalTenantInvitationPreviewResponse(
                invitation.getId(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getPlan(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getExpiresAt()
        );
    }

    @Transactional
    public InternalAcceptTenantInvitationResponse acceptInvitation(String token) {
        TenantInvitation invitation = findPendingInvitationByToken(token);
        Tenant tenant = tenantRepository.findById(invitation.getTenantId())
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + invitation.getTenantId()));

        invitation.markAccepted();
        tenantInvitationRepository.save(invitation);

        return new InternalAcceptTenantInvitationResponse(
                invitation.getId(),
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getPlan(),
                invitation.getEmail(),
                invitation.getRole()
        );
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

        return toResponse(scanTarget);
    }

    public List<ScanTargetResponse> getScanTargets(UUID tenantId) {
        return scanTargetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(this::toResponse)
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
                .filter(scanTarget -> scanTarget.getVerificationStatus() == ScanTargetVerificationStatus.VERIFIED)
                .filter(scanTarget -> isCompatible(scanTarget.getType(), scanType))
                .anyMatch(scanTarget -> matches(scanTarget, target));
    }

    @Transactional
    public ScanTargetResponse verifyScanTarget(UUID tenantId, UUID targetId) {
        ScanTarget scanTarget = findTenantScanTarget(tenantId, targetId);
        ScanTargetOwnershipVerificationResult verification = scanTargetOwnershipVerifier.verify(scanTarget);
        if (verification.verified()) {
            scanTarget.markVerified();
        } else {
            scanTarget.markFailed();
        }
        return toResponse(scanTargetRepository.save(scanTarget));
    }

    @Transactional
    public void removeScanTarget(UUID tenantId, UUID targetId) {
        ScanTarget scanTarget = findTenantScanTarget(tenantId, targetId);
        scanTargetRepository.delete(scanTarget);
    }

    public void validateQuota(UUID tenantId, String quotaType) {
        quotaRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Quota not found for tenant: " + tenantId));
    }

    public boolean isSlugAvailable(String slug) {
        String normalizedSlug = slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
        return normalizedSlug != null && !normalizedSlug.isBlank() && !tenantRepository.existsBySlug(normalizedSlug);
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException("Tenant not found: " + tenantId));
        tenantRepository.delete(tenant);
    }

    private void requireTenantExists(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException("Tenant not found: " + tenantId);
        }
    }

    private TenantResponse createTenantInternal(UUID tenantId, String name, String slug, Plan plan, TenantStatus status) {
        String normalizedSlug = slug.trim().toLowerCase(Locale.ROOT);
        if (tenantRepository.existsBySlug(normalizedSlug)) {
            throw new SlugAlreadyExistsException("Slug already exists: " + normalizedSlug);
        }

        Tenant tenant = new Tenant(
                tenantId,
                name.trim(),
                normalizedSlug,
                plan,
                status
        );
        tenant = tenantRepository.save(tenant);

        TenantQuota quota = TenantQuota.forPlan(plan, tenant.getId());
        quotaRepository.save(quota);

        return toResponse(tenant);
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ScanTarget findTenantScanTarget(UUID tenantId, UUID targetId) {
        ScanTarget scanTarget = scanTargetRepository.findById(targetId)
                .orElseThrow(() -> new TenantNotFoundException("Scan target not found: " + targetId));

        if (!scanTarget.getTenantId().equals(tenantId)) {
            throw new TenantNotFoundException("Scan target does not belong to tenant");
        }
        return scanTarget;
    }

    private TenantInvitation findInvitationForTenant(UUID tenantId, UUID invitationId) {
        TenantInvitation invitation = tenantInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new TenantInvitationNotFoundException("Invitation not found: " + invitationId));

        if (!invitation.getTenantId().equals(tenantId)) {
            throw new TenantInvitationNotFoundException("Invitation does not belong to tenant");
        }
        return invitation;
    }

    private TenantInvitation findPendingInvitationByToken(String token) {
        String tokenHash = hashToken(token);
        TenantInvitation invitation = tenantInvitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TenantInvitationNotFoundException("Invitation not found"));

        if (invitation.getStatus() != TenantInvitationStatus.PENDING) {
            throw new TenantInvitationConflictException("Invitation is no longer pending");
        }

        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.markExpired();
            tenantInvitationRepository.save(invitation);
            throw new TenantInvitationConflictException("Invitation has expired");
        }

        return invitation;
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private int resolveExpiryDays(Integer expiresInDays) {
        return expiresInDays == null ? DEFAULT_INVITATION_EXPIRY_DAYS : expiresInDays;
    }

    private String generateInvitationToken() {
        byte[] bytes = new byte[32];
        INVITATION_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private TenantInvitationResponse toResponse(TenantInvitation invitation, String inviteToken) {
        return new TenantInvitationResponse(
                invitation.getId(),
                invitation.getTenantId(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getInvitedByUserId(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCreatedAt(),
                invitation.getUpdatedAt(),
                inviteToken
        );
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

    private ScanTargetResponse toResponse(ScanTarget scanTarget) {
        return new ScanTargetResponse(
                scanTarget.getId(),
                scanTarget.getTarget(),
                scanTarget.getType(),
                scanTarget.getDescription(),
                scanTarget.getVerificationStatus(),
                scanTarget.getVerificationToken(),
                scanTarget.getVerificationCheckedAt(),
                scanTarget.getVerifiedAt(),
                scanTarget.getCreatedAt()
        );
    }

    private PlanChangeRequestResponse toResponse(PlanChangeRequest request) {
        return new PlanChangeRequestResponse(
                request.getId(),
                request.getTenantId(),
                request.getRequestedByUserId(),
                request.getCurrentPlan(),
                request.getRequestedPlan(),
                request.getStatus(),
                request.getNote(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
