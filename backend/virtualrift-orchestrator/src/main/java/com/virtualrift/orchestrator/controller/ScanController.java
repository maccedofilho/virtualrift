package com.virtualrift.orchestrator.controller;

import com.virtualrift.common.security.RoleAccess;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanFindingResponse;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.dto.ScanResultResponse;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1/scans")
@Tag(name = "Scans", description = "Criacao, acompanhamento e consulta de resultados de scans.")
public class ScanController {

    private final ScanOrchestratorService scanOrchestratorService;

    public ScanController(ScanOrchestratorService scanOrchestratorService) {
        this.scanOrchestratorService = scanOrchestratorService;
    }

    private void requireAnyRole(String rolesHeader, UserRole... allowedRoles) {
        if (!RoleAccess.hasAny(rolesHeader, allowedRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User role is not allowed to access this resource");
        }
    }

    @PostMapping
    @Operation(summary = "Criar scan", description = "Perfis permitidos: OWNER, ANALYST.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Scan aceito para processamento"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER ou ANALYST podem criar scans"),
            @ApiResponse(responseCode = "429", description = "Quota do plano excedida")
    })
    public ResponseEntity<ScanResponse> createScan(@Valid @RequestBody CreateScanRequest request,
                                                  @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                  @RequestHeader("X-User-Id") UUID userId,
                                                  @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST);
        ScanResponse response = scanOrchestratorService.createScan(request, tenantId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{scanId}")
    @Operation(summary = "Buscar scan por id", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scan encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do scan"),
            @ApiResponse(responseCode = "404", description = "Scan nao encontrado")
    })
    public ResponseEntity<ScanResponse> getScan(@PathVariable UUID scanId,
                                             @RequestHeader("X-Tenant-Id") UUID tenantId,
                                             @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        ScanResponse response = scanOrchestratorService.getScan(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/status")
    @Operation(summary = "Consultar status do scan", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status retornado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do status"),
            @ApiResponse(responseCode = "404", description = "Scan nao encontrado")
    })
    public ResponseEntity<ScanResponse> getStatus(@PathVariable UUID scanId,
                                                  @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                  @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        ScanResponse response = scanOrchestratorService.getStatus(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/findings")
    @Operation(summary = "Listar findings do scan", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Findings retornados"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura dos findings"),
            @ApiResponse(responseCode = "404", description = "Scan nao encontrado")
    })
    public ResponseEntity<List<ScanFindingResponse>> getFindings(@PathVariable UUID scanId,
                                                                 @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                                 @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        List<ScanFindingResponse> response = scanOrchestratorService.getFindings(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/result")
    @Operation(summary = "Consultar resultado agregado do scan", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado retornado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do resultado"),
            @ApiResponse(responseCode = "404", description = "Scan nao encontrado")
    })
    public ResponseEntity<ScanResultResponse> getResult(@PathVariable UUID scanId,
                                                        @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                        @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        ScanResultResponse response = scanOrchestratorService.getResult(scanId, tenantId);
        return ResponseEntity.ok(response);
    }
}
