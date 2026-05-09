package com.virtualrift.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.orchestrator.dto.CreateScanRequest;
import com.virtualrift.orchestrator.exception.ScanNotFoundException;
import com.virtualrift.orchestrator.exception.ScanQuotaExceededException;
import com.virtualrift.orchestrator.exception.ScanTypeNotAllowedException;
import com.virtualrift.orchestrator.exception.TenantServiceException;
import com.virtualrift.orchestrator.service.ScanOrchestratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScanController.class)
@DisplayName("ScanController Tests")
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ScanOrchestratorService scanOrchestratorService;

    @Test
    @DisplayName("should return 404 when scan is not found")
    void getScan_quandoNaoEncontrado_retorna404() throws Exception {
        UUID scanId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(scanOrchestratorService.getScan(scanId, tenantId))
                .thenThrow(new ScanNotFoundException("Scan not found: " + scanId));

        mockMvc.perform(get("/api/v1/scans/{scanId}", scanId)
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 429 when quota is exceeded")
    void createScan_quandoQuotaExcedida_retorna429() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateScanRequest request = new CreateScanRequest("https://example.com", ScanType.WEB, 3, 300);

        when(scanOrchestratorService.createScan(any(CreateScanRequest.class), eq(tenantId), eq(userId)))
                .thenThrow(new ScanQuotaExceededException("Daily scan quota exceeded"));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request))
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("should return 403 when plan does not allow the requested scan type")
    void createScan_quandoPlanoNaoPermiteTipo_retorna403() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateScanRequest request = new CreateScanRequest("https://example.com", ScanType.SAST, 3, 300);

        when(scanOrchestratorService.createScan(any(CreateScanRequest.class), eq(tenantId), eq(userId)))
                .thenThrow(new ScanTypeNotAllowedException("Scan type SAST is not allowed for plan TRIAL"));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request))
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 503 when tenant service is unavailable")
    void createScan_quandoTenantServiceIndisponivel_retorna503() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateScanRequest request = new CreateScanRequest("https://example.com", ScanType.WEB, 3, 300);

        when(scanOrchestratorService.createScan(any(CreateScanRequest.class), eq(tenantId), eq(userId)))
                .thenThrow(new TenantServiceException("Failed to fetch quota for tenant: " + tenantId, new RuntimeException("down")));

        mockMvc.perform(post("/api/v1/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request))
                        .header("X-Tenant-Id", tenantId)
                        .header("X-User-Id", userId))
                .andExpect(status().isServiceUnavailable());
    }
}
