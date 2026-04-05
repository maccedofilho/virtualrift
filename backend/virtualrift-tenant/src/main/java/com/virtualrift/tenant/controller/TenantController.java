package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID id) {
        TenantResponse response = tenantService.getTenant(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<TenantResponse> getTenantBySlug(@PathVariable String slug) {
        TenantResponse response = tenantService.getTenantBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/quota")
    public ResponseEntity<TenantQuotaResponse> getQuota(@PathVariable UUID id) {
        TenantQuotaResponse response = tenantService.getQuota(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/scan-targets")
    public ResponseEntity<List<ScanTargetResponse>> getScanTargets(@PathVariable UUID id) {
        List<ScanTargetResponse> response = tenantService.getScanTargets(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/scan-targets")
    public ResponseEntity<ScanTargetResponse> addScanTarget(
            @PathVariable UUID id,
            @Valid @RequestBody AddScanTargetRequest request) {
        ScanTargetResponse response = tenantService.addScanTarget(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{tenantId}/scan-targets/{targetId}")
    public ResponseEntity<Void> removeScanTarget(
            @PathVariable UUID tenantId,
            @PathVariable UUID targetId) {
        tenantService.removeScanTarget(tenantId, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/plan")
    public ResponseEntity<com.virtualrift.tenant.model.Plan> getPlan(@PathVariable UUID id) {
        com.virtualrift.tenant.model.Plan plan = tenantService.getPlan(id);
        return ResponseEntity.ok(plan);
    }
}
