package com.virtualrift.orchestrator.controller;

import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.dto.ScanFindingResponse;
import com.virtualrift.orchestrator.dto.ScanResponse;
import com.virtualrift.orchestrator.dto.ScanResultResponse;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {

    private final ScanOrchestratorService scanOrchestratorService;

    public ScanController(ScanOrchestratorService scanOrchestratorService) {
        this.scanOrchestratorService = scanOrchestratorService;
    }

    @PostMapping
    public ResponseEntity<ScanResponse> createScan(@Valid @RequestBody CreateScanRequest request,
                                                  @RequestHeader("X-Tenant-Id") UUID tenantId,
                                                  @RequestHeader("X-User-Id") UUID userId) {
        ScanResponse response = scanOrchestratorService.createScan(request, tenantId, userId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{scanId}")
    public ResponseEntity<ScanResponse> getScan(@PathVariable UUID scanId,
                                             @RequestHeader("X-Tenant-Id") UUID tenantId) {
        ScanResponse response = scanOrchestratorService.getScan(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/status")
    public ResponseEntity<ScanResponse> getStatus(@PathVariable UUID scanId,
                                                  @RequestHeader("X-Tenant-Id") UUID tenantId) {
        ScanResponse response = scanOrchestratorService.getStatus(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/findings")
    public ResponseEntity<List<ScanFindingResponse>> getFindings(@PathVariable UUID scanId,
                                                                 @RequestHeader("X-Tenant-Id") UUID tenantId) {
        List<ScanFindingResponse> response = scanOrchestratorService.getFindings(scanId, tenantId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{scanId}/result")
    public ResponseEntity<ScanResultResponse> getResult(@PathVariable UUID scanId,
                                                        @RequestHeader("X-Tenant-Id") UUID tenantId) {
        ScanResultResponse response = scanOrchestratorService.getResult(scanId, tenantId);
        return ResponseEntity.ok(response);
    }
}
