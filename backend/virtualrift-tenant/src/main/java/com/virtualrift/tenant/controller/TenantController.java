package com.virtualrift.tenant.controller;

import com.virtualrift.common.security.RoleAccess;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.tenant.dto.AddScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetRequest;
import com.virtualrift.tenant.dto.AuthorizeScanTargetResponse;
import com.virtualrift.tenant.dto.CreateTenantRequest;
import com.virtualrift.tenant.dto.ScanTargetResponse;
import com.virtualrift.tenant.dto.TenantQuotaResponse;
import com.virtualrift.tenant.dto.TenantResponse;
import com.virtualrift.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
@Tag(name = "Tenant", description = "Gestao de tenants, planos, quotas e alvos de scan.")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    private void requireAnyRole(String rolesHeader, UserRole... allowedRoles) {
        if (!RoleAccess.hasAny(rolesHeader, allowedRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User role is not allowed to access this resource");
        }
    }

    @PostMapping
    @Operation(summary = "Criar tenant", description = "Perfis permitidos: OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tenant criado"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER pode criar tenants")
    })
    public ResponseEntity<TenantResponse> createTenant(
            @RequestHeader("X-Roles") String rolesHeader,
            @Valid @RequestBody CreateTenantRequest request) {
        requireAnyRole(rolesHeader, UserRole.OWNER);
        TenantResponse response = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tenant por id", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do tenant")
    })
    public ResponseEntity<TenantResponse> getTenant(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        TenantResponse response = tenantService.getTenant(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Buscar tenant por slug", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tenant encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do tenant")
    })
    public ResponseEntity<TenantResponse> getTenantBySlug(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable String slug) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        TenantResponse response = tenantService.getTenantBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/quota")
    @Operation(summary = "Consultar quota do tenant", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quota retornada"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura da quota")
    })
    public ResponseEntity<TenantQuotaResponse> getQuota(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        TenantQuotaResponse response = tenantService.getQuota(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/scan-targets")
    @Operation(summary = "Listar alvos do tenant", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alvos retornados"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura dos alvos")
    })
    public ResponseEntity<List<ScanTargetResponse>> getScanTargets(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        List<ScanTargetResponse> response = tenantService.getScanTargets(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/scan-targets")
    @Operation(summary = "Adicionar alvo de scan", description = "Perfis permitidos: OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Alvo criado"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER pode cadastrar alvos")
    })
    public ResponseEntity<ScanTargetResponse> addScanTarget(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id,
            @Valid @RequestBody AddScanTargetRequest request) {
        requireAnyRole(rolesHeader, UserRole.OWNER);
        ScanTargetResponse response = tenantService.addScanTarget(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/scan-targets/authorize")
    @Operation(summary = "Validar autorizacao de alvo para scan", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autorizacao avaliada"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de consultar autorizacao")
    })
    public ResponseEntity<AuthorizeScanTargetResponse> authorizeScanTarget(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id,
            @Valid @RequestBody AuthorizeScanTargetRequest request) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        boolean authorized = tenantService.isScanTargetAuthorized(id, request.target(), request.scanType());
        return ResponseEntity.ok(new AuthorizeScanTargetResponse(authorized));
    }

    @PostMapping("/{tenantId}/scan-targets/{targetId}/verify")
    @Operation(summary = "Verificar ownership de alvo", description = "Perfis permitidos: OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alvo verificado"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER pode verificar ownership")
    })
    public ResponseEntity<ScanTargetResponse> verifyScanTarget(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID tenantId,
            @PathVariable UUID targetId) {
        requireAnyRole(rolesHeader, UserRole.OWNER);
        ScanTargetResponse response = tenantService.verifyScanTarget(tenantId, targetId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{tenantId}/scan-targets/{targetId}")
    @Operation(summary = "Remover alvo de scan", description = "Perfis permitidos: OWNER.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Alvo removido"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER pode remover alvos")
    })
    public ResponseEntity<Void> removeScanTarget(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID tenantId,
            @PathVariable UUID targetId) {
        requireAnyRole(rolesHeader, UserRole.OWNER);
        tenantService.removeScanTarget(tenantId, targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/plan")
    @Operation(summary = "Consultar plano do tenant", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plano retornado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do plano")
    })
    public ResponseEntity<com.virtualrift.tenant.model.Plan> getPlan(
            @RequestHeader("X-Roles") String rolesHeader,
            @PathVariable UUID id) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        com.virtualrift.tenant.model.Plan plan = tenantService.getPlan(id);
        return ResponseEntity.ok(plan);
    }
}
