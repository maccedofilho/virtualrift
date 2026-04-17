package com.virtualrift.reports.controller;

import com.virtualrift.common.model.ScanStatus;
import com.virtualrift.common.model.ScanType;
import com.virtualrift.reports.dto.ReportResponse;
import com.virtualrift.reports.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController Tests")
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(reportService);
    }

    private ReportResponse response(UUID reportId, UUID tenantId, UUID scanId) {
        return new ReportResponse(
                reportId,
                tenantId,
                scanId,
                UUID.randomUUID(),
                "https://example.com",
                ScanType.WEB,
                ScanStatus.COMPLETED,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now(),
                List.of()
        );
    }

    @Test
    @DisplayName("should delegate report generation")
    void generateReport_quandoChamado_delegaParaService() {
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ReportResponse expected = response(reportId, tenantId, scanId);

        when(reportService.generateReport(scanId, tenantId)).thenReturn(expected);

        ResponseEntity<ReportResponse> response = controller.generateReport(scanId, tenantId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(reportId, response.getBody().id());
        verify(reportService).generateReport(scanId, tenantId);
    }

    @Test
    @DisplayName("should delegate report lookup")
    void getReport_quandoChamado_delegaParaService() {
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        UUID reportId = UUID.randomUUID();
        ReportResponse expected = response(reportId, tenantId, scanId);

        when(reportService.getReport(reportId, tenantId)).thenReturn(expected);

        ResponseEntity<ReportResponse> response = controller.getReport(reportId, tenantId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(reportId, response.getBody().id());
        verify(reportService).getReport(reportId, tenantId);
    }

    @Test
    @DisplayName("should delegate report listing")
    void listReports_quandoChamado_delegaParaService() {
        UUID tenantId = UUID.randomUUID();
        UUID scanId = UUID.randomUUID();
        ReportResponse expected = response(UUID.randomUUID(), tenantId, scanId);

        when(reportService.listReports(tenantId, scanId)).thenReturn(List.of(expected));

        ResponseEntity<List<ReportResponse>> response = controller.listReports(tenantId, scanId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        verify(reportService).listReports(tenantId, scanId);
    }
}
