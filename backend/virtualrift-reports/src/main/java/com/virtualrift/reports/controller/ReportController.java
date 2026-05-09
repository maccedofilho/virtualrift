package com.virtualrift.reports.controller;

import com.virtualrift.common.security.RoleAccess;
import com.virtualrift.common.security.UserRole;
import com.virtualrift.reports.dto.ReportResponse;
import com.virtualrift.reports.service.ReportService;
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
    public ResponseEntity<ReportResponse> generateReport(@PathVariable UUID scanId,
                                                         @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                         @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST);
        return ResponseEntity.ok(reportService.generateReport(scanId, tenantId));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ReportResponse> getReport(@PathVariable UUID reportId,
                                                    @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                    @RequestHeader("X-Roles") String rolesHeader) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        return ResponseEntity.ok(reportService.getReport(reportId, tenantId));
    }

    @GetMapping
    public ResponseEntity<List<ReportResponse>> listReports(@RequestHeader("X-Tenant-Id") UUID tenantId,
                                                            @RequestHeader("X-Roles") String rolesHeader,
                                                            @RequestParam(required = false) UUID scanId) {
        requireAnyRole(rolesHeader, UserRole.OWNER, UserRole.ANALYST, UserRole.READER);
        return ResponseEntity.ok(reportService.listReports(tenantId, scanId));
    }
}
