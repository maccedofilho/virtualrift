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
import com.virtualrift.tenant.model.Tenant;
import com.virtualrift.tenant.model.TenantQuota;
import com.virtualrift.tenant.model.TenantStatus;
import com.virtualrift.tenant.repository.ScanTargetRepository;
import com.virtualrift.tenant.repository.TenantQuotaRepository;
import com.virtualrift.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
