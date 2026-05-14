package com.virtualrift.reports.controller;

import com.virtualrift.common.security.RoleAccess;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.reports.dto.ReportExportResource;
import com.virtualrift.reports.dto.ReportResponse;
import com.virtualrift.reports.model.ReportExportFormat;
import com.virtualrift.reports.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reports", description = "Geracao e consulta de snapshots de relatorios de scans.")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    private void requireAnyRole(String rolesHeader, UserRole... allowedRoles) {
        if (!RoleAccess.hasAny(rolesHeader, allowedRoles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User role is not allowed to access this resource");
        }
    }

    @PostMapping("/scans/{scanId}")
    @Operation(summary = "Gerar relatorio a partir de um scan", description = "Perfis permitidos: OWNER, ANALYST.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatorio gerado"),
            @ApiResponse(responseCode = "403", description = "Apenas OWNER ou ANALYST podem gerar relatorios"),
            @ApiResponse(responseCode = "404", description = "Scan nao encontrado")
    })
    public ResponseEntity<ReportResponse> generateReport(@PathVariable UUID scanId,
                                                         @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                         @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST);
        return ResponseEntity.ok(reportService.generateReport(scanId, tenantId, rolesHeader));
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Buscar relatorio por id", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatorio encontrado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do relatorio"),
            @ApiResponse(responseCode = "404", description = "Relatorio nao encontrado")
    })
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId,
                                                    @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                    @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        return ResponseEntity.ok(reportService.getReport(reportId, tenantId));
    }

    @GetMapping("/{reportId}/export")
    @Operation(summary = "Exportar relatorio", description = "Perfis permitidos: OWNER, ANALYST, READER. Formatos iniciais: json e html.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arquivo de exportacao retornado"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura do relatorio"),
            @ApiResponse(responseCode = "404", description = "Relatorio nao encontrado"),
            @ApiResponse(responseCode = "400", description = "Formato de exportacao invalido")
    })
    public ResponseEntity<byte[]> exportReport(@PathVariable UUID reportId,
                                               @RequestParam(defaultValue = "json") String format,
                                               @RequestHeader("X-Tenant-Id") UUID tenantId,
                                               @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);

        ReportExportFormat exportFormat;
        try {
            exportFormat = ReportExportFormat.fromWireValue(format);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        ReportExportResource resource = reportService.exportReport(reportId, tenantId, exportFormat);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.fileName() + "\"")
                .contentType(resource.contentType())
                .body(resource.content());
    }

    @GetMapping
    @Operation(summary = "Listar relatorios do tenant", description = "Perfis permitidos: OWNER, ANALYST, READER.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Relatorios retornados"),
            @ApiResponse(responseCode = "403", description = "Perfil sem permissao de leitura dos relatorios")
    })
    public ResponseEntity<List<ReportResponse>> listReports(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                                            @RequestHeader("X-Roles") String rolesHeader,
                                                            @RequestParam(required = false) UUID scanId) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        return ResponseEntity.ok(reportService.listReports(tenantId, scanId));
    }
}
