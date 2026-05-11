package com.virtualrift.tenant.controller;

import com.virtualrift.tenant.config.InternalProvisioningConfig;
import com.virtualrift.tenant.dto.InternalProvisionTenantRequest;
import com.virtualrift.tenant.dto.InternalSlugAvailabilityResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Hidden
@RestController
@RequestMapping("/api/internal/tenants")
public class InternalTenantProvisioningController {

    private final TenantService tenantService;
    private final InternalProvisioningConfig config;

    public InternalTenantProvisioningController(TenantService tenantService, InternalProvisioningConfig config) {
        this.tenantService = tenantService;
        this.config = config;
    }

    @GetMapping("/slug/{slug}/availability")
    public ResponseEntity<InternalSlugAvailabilityResponse> getSlugAvailability(
            @RequestHeader("X-Internal-Api-Key") String internalApiKey,
            @PathVariable String slug
    ) {
        requireInternalApiKey(internalApiKey);
        return ResponseEntity.ok(new InternalSlugAvailabilityResponse(slug, tenantService.isSlugAvailable(slug)));
    }

    @PostMapping("/provision")
    public ResponseEntity<TenantResponse> provisionTenant(
            @RequestHeader("X-Internal-Api-Key") String internalApiKey,
            @Valid @RequestBody InternalProvisionTenantRequest request
    ) {
        requireInternalApiKey(internalApiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantService.provisionTenant(request));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<Void> deleteTenant(
            @RequestHeader("X-Internal-Api-Key") String internalApiKey,
            @PathVariable UUID tenantId
    ) {
        requireInternalApiKey(internalApiKey);
        tenantService.deleteTenant(tenantId);
        return ResponseEntity.noContent().build();
    }

    private void requireInternalApiKey(String internalApiKey) {
        if (!config.getApiKey().equals(internalApiKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal API key");
        }
    }
}
